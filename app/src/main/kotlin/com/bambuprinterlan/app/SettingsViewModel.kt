package com.bambuprinterlan.app

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bambuprinterlan.core.data.BambuLanBundle
import com.bambuprinterlan.core.data.SettingsDefaults
import com.bambuprinterlan.core.data.SettingsRepository
import com.bambuprinterlan.net.bambu.integrations.DiscordClient
import com.bambuprinterlan.net.bambu.integrations.HomeAssistantClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Snapshot of the editable settings surfaced in the Settings screen. */
data class UiSettings(
    val relayUrl: String = "",
    val relayToken: String = "",
    val haUrl: String = SettingsDefaults.strings["home_assistant_url"] ?: "",
    val haToken: String = "",
    val haEnabled: Boolean = false,
    val discordUrl: String = "",
    val discordEnabled: Boolean = false,
    val autoSave: Boolean = true,
    val clockSeconds: Boolean = false,
    val clockDate: Boolean = false,
    val clockPrefix: String = "",
    val anthropicApiKey: String = "",
    // Discord per-category notify toggles (port of discord_notify_* keys)
    val discordNotify: Map<String, Boolean> = emptyMap(),
    // Home Assistant domain gating (port of home_assistant_allow_* keys)
    val haAllow: Map<String, Boolean> = emptyMap(),
)

/** Discord notify categories + their config keys. */
val DISCORD_CATEGORIES = listOf(
    "startup" to "discord_notify_startup",
    "project" to "discord_notify_project",
    "export" to "discord_notify_export",
    "autofix" to "discord_notify_autofix",
    "errors" to "discord_notify_errors",
)

/** Home Assistant allowed domains + their config keys. */
val HA_DOMAINS = listOf(
    "lights" to "home_assistant_allow_lights",
    "scenes" to "home_assistant_allow_scenes",
    "switches" to "home_assistant_allow_switches",
    "climate" to "home_assistant_allow_climate",
)

/**
 * Backs both the Tools/Labs feature toggles and the Settings screen, persisting
 * to DataStore (:core:data). Mirrors BambuLan Feature Settings + the app config
 * keys. Secrets are persisted on-device only (TODO: EncryptedSharedPreferences).
 */
class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SettingsRepository(app)
    private val discord = DiscordClient()
    private val homeAssistant = HomeAssistantClient()

    private val _flags = MutableStateFlow(SettingsDefaults.featureFlags)
    val flags: StateFlow<Map<String, Boolean>> = _flags.asStateFlow()

    private val _settings = MutableStateFlow(UiSettings())
    val settings: StateFlow<UiSettings> = _settings.asStateFlow()

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast.asStateFlow()

    init {
        viewModelScope.launch { _flags.value = repo.exportFeatureFlags() }
        viewModelScope.launch {
            _settings.value = UiSettings(
                relayUrl = repo.getString("relay_base_url").first(),
                relayToken = repo.getString("relay_token").first(),
                haUrl = repo.getString("home_assistant_url").first(),
                haToken = repo.getString("home_assistant_token").first(),
                haEnabled = repo.getBool("home_assistant_enabled").first(),
                discordUrl = repo.getString("discord_webhook_url").first(),
                discordEnabled = repo.getBool("discord_webhooks_enabled").first(),
                autoSave = repo.getBool("material_auto_save").first(),
                clockSeconds = repo.getBool("material_clock_show_seconds").first(),
                clockDate = repo.getBool("material_clock_show_date").first(),
                clockPrefix = repo.getString("material_clock_prefix").first(),
                anthropicApiKey = repo.getString("anthropic_api_key").first(),
                discordNotify = DISCORD_CATEGORIES.associate { (cat, key) -> cat to repo.getBool(key).first() },
                haAllow = HA_DOMAINS.associate { (dom, key) -> dom to repo.getBool(key).first() },
            )
        }
    }

    fun setDiscordCategory(category: String, on: Boolean) =
        update { it.copy(discordNotify = it.discordNotify + (category to on)) }

    fun setHaDomain(domain: String, on: Boolean) =
        update { it.copy(haAllow = it.haAllow + (domain to on)) }

    fun setFeature(key: String, enabled: Boolean) {
        _flags.value = _flags.value.toMutableMap().apply { put(key, enabled) }
        viewModelScope.launch { repo.setBool(key, enabled) }
    }

    fun update(transform: (UiSettings) -> UiSettings) { _settings.value = transform(_settings.value) }

    fun persist() {
        val s = _settings.value
        viewModelScope.launch {
            repo.setString("relay_base_url", s.relayUrl)
            repo.setString("relay_token", s.relayToken)
            repo.setString("home_assistant_url", s.haUrl)
            repo.setString("home_assistant_token", s.haToken)
            repo.setBool("home_assistant_enabled", s.haEnabled)
            repo.setString("discord_webhook_url", s.discordUrl)
            repo.setBool("discord_webhooks_enabled", s.discordEnabled)
            repo.setBool("material_auto_save", s.autoSave)
            repo.setBool("material_clock_show_seconds", s.clockSeconds)
            repo.setBool("material_clock_show_date", s.clockDate)
            repo.setString("material_clock_prefix", s.clockPrefix)
            repo.setString("anthropic_api_key", s.anthropicApiKey)
            DISCORD_CATEGORIES.forEach { (cat, key) -> s.discordNotify[cat]?.let { repo.setBool(key, it) } }
            HA_DOMAINS.forEach { (dom, key) -> s.haAllow[dom]?.let { repo.setBool(key, it) } }
            _toast.value = "Saved  已儲存"
        }
    }

    fun testDiscord() {
        val url = _settings.value.discordUrl
        viewModelScope.launch {
            val ok = discord.sendEmbed(
                url, "Bambu Printer LAN", "Test notification  測試通知", DiscordClient.Category.STARTUP,
            )
            _toast.value = if (ok) "Discord OK  成功" else "Discord failed  失敗"
        }
    }

    fun testHomeAssistant() {
        val s = _settings.value
        viewModelScope.launch {
            val entities = homeAssistant.fetchStates(s.haUrl, s.haToken)
            _toast.value = if (entities.isNotEmpty())
                "HA: ${entities.size} entities  個實體" else "HA failed  失敗"
        }
    }

    /** Export every setting + feature flag as a base64 `.bambulan` payload to the clipboard. */
    fun exportBundle() {
        viewModelScope.launch {
            val data = repo.exportAll()
            val payload = BambuLanBundle.export(data.strings, data.booleans, data.ints, data.features)
            val cm = getApplication<Application>()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("bambulan", payload))
            _toast.value = "Exported to clipboard  已複製到剪貼簿"
            EventNotifier.fire(
                getApplication(), "Settings exported  已匯出設定", ".bambulan bundle copied",
                "export", DiscordClient.Category.EXPORT,
            )
        }
    }

    /** Import a base64 `.bambulan` payload and persist every key it carries. */
    fun importBundle(text: String) {
        viewModelScope.launch {
            try {
                repo.importAll(BambuLanBundle.import(text))
                _flags.value = repo.exportFeatureFlags()
                _toast.value = "Imported  已匯入"
            } catch (e: Exception) {
                _toast.value = "Import failed  匯入失敗"
            }
        }
    }

    fun clearToast() { _toast.value = null }
}

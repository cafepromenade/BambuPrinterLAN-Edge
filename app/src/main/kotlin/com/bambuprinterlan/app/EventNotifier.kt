package com.bambuprinterlan.app

import android.content.Context
import com.bambuprinterlan.core.data.SettingsRepository
import com.bambuprinterlan.net.bambu.integrations.DiscordClient
import com.bambuprinterlan.net.bambu.integrations.HomeAssistantClient
import kotlinx.coroutines.flow.first

/**
 * Single place that fires important-event notifications to Discord + Home
 * Assistant, honoring the persisted per-category Discord toggles
 * (discord_notify_*) and the Home Assistant domain-gating toggles
 * (home_assistant_allow_*). Used by startup, export, and device events so every
 * category toggle actually controls real behavior.
 */
object EventNotifier {
    private val discord = DiscordClient()
    private val homeAssistant = HomeAssistantClient()

    suspend fun fire(
        context: Context,
        title: String,
        detail: String,
        category: String,            // startup|project|export|autofix|errors
        discordCategory: DiscordClient.Category,
    ) {
        val repo = SettingsRepository(context)

        if (repo.getBool("discord_webhooks_enabled").first() &&
            repo.getBool("discord_notify_$category").first()
        ) {
            val url = repo.getString("discord_webhook_url").first()
            if (url.isNotBlank()) discord.sendEmbed(url, title, detail.ifBlank { "—" }, discordCategory)
        }

        if (repo.getBool("home_assistant_enabled").first()) {
            val base = repo.getString("home_assistant_url").first()
            val token = repo.getString("home_assistant_token").first()
            if (base.isNotBlank() && token.isNotBlank()) {
                val perms = HomeAssistantClient.Permissions(
                    allowLights = repo.getBool("home_assistant_allow_lights").first(),
                    allowScenes = repo.getBool("home_assistant_allow_scenes").first(),
                    allowSwitches = repo.getBool("home_assistant_allow_switches").first(),
                    allowClimate = repo.getBool("home_assistant_allow_climate").first(),
                )
                val msg = detail.replace("\"", "'")
                homeAssistant.callService(
                    base, token, "persistent_notification", "create",
                    """{"title":"$title","message":"$msg"}""", perms,
                )
            }
        }
    }
}

package com.bambuprinterlan.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bambuprinterlan.core.model.Printer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bambuprinterlan_settings")

/**
 * DataStore-backed app settings — the Android equivalent of libslic3r AppConfig.
 * Keys and defaults mirror [SettingsDefaults] so `.bambulan` import/export stays
 * compatible. Secrets (tokens/access codes) are NOT stored here; they live in
 * EncryptedSharedPreferences (plan §11).
 */
class SettingsRepository(private val context: Context) {

    fun getString(key: String): Flow<String> =
        context.dataStore.data.map { prefs ->
            prefs[stringPreferencesKey(key)] ?: SettingsDefaults.strings[key] ?: ""
        }

    fun getBool(key: String): Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[booleanPreferencesKey(key)]
                ?: SettingsDefaults.booleans[key]
                ?: SettingsDefaults.featureFlags[key]
                ?: false
        }

    fun getInt(key: String): Flow<Int> =
        context.dataStore.data.map { prefs ->
            prefs[intPreferencesKey(key)] ?: SettingsDefaults.ints[key] ?: 0
        }

    /** Feature flag — defaults to the registry's default-on state. */
    fun isFeatureEnabled(key: String): Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[booleanPreferencesKey(key)] ?: SettingsDefaults.featureFlags[key] ?: true
        }

    suspend fun setString(key: String, value: String) {
        context.dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun setBool(key: String, value: Boolean) {
        context.dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    suspend fun setInt(key: String, value: Int) {
        context.dataStore.edit { it[intPreferencesKey(key)] = value }
    }

    /** Snapshot of all feature-flag states for `.bambulan` export. */
    suspend fun exportFeatureFlags(): Map<String, Boolean> {
        val prefs = context.dataStore.data.first()
        return SettingsDefaults.featureFlags.mapValues { (key, default) ->
            prefs[booleanPreferencesKey(key)] ?: default
        }
    }

    /** Full settings snapshot (strings/booleans/ints/features) for `.bambulan` export. */
    suspend fun exportAll(): BambuLanBundle.Imported {
        val prefs = context.dataStore.data.first()
        val strings = SettingsDefaults.strings.mapValues { (k, d) ->
            prefs[stringPreferencesKey(k)] ?: d
        }
        val booleans = SettingsDefaults.booleans.mapValues { (k, d) ->
            prefs[booleanPreferencesKey(k)] ?: d
        }
        val ints = SettingsDefaults.ints.mapValues { (k, d) ->
            prefs[intPreferencesKey(k)] ?: d
        }
        return BambuLanBundle.Imported(strings, booleans, ints, exportFeatureFlags())
    }

    // ---- workspace auto-save (imported models) -----------------------------
    private val workspaceKey = stringPreferencesKey("workspace_models")

    /** Persisted workspace as an opaque JSON string (app owns the schema). */
    fun workspaceJson(): Flow<String> =
        context.dataStore.data.map { it[workspaceKey] ?: "[]" }

    suspend fun saveWorkspace(json: String) {
        context.dataStore.edit { it[workspaceKey] = json }
    }

    // ---- saved printer connections -----------------------------------------
    private val savedPrintersKey = stringPreferencesKey("saved_printers")

    /** Saved printers (LAN: ip + access code + serial). Persisted on connect. */
    fun savedPrinters(): Flow<List<Printer>> =
        context.dataStore.data.map { prefs -> parsePrinters(prefs[savedPrintersKey] ?: "[]") }

    /** Add or update a saved printer (keyed by serial+ip). */
    suspend fun savePrinter(printer: Printer) {
        context.dataStore.edit { prefs ->
            val current = parsePrinters(prefs[savedPrintersKey] ?: "[]")
                .filterNot { it.serial == printer.serial && it.ip == printer.ip }
            prefs[savedPrintersKey] = serializePrinters(current + printer)
        }
    }

    suspend fun removePrinter(serial: String, ip: String) {
        context.dataStore.edit { prefs ->
            val current = parsePrinters(prefs[savedPrintersKey] ?: "[]")
                .filterNot { it.serial == serial && it.ip == ip }
            prefs[savedPrintersKey] = serializePrinters(current)
        }
    }

    private fun parsePrinters(json: String): List<Printer> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Printer(
                serial = o.optString("serial"),
                name = o.optString("name"),
                ip = o.optString("ip"),
                model = o.optString("model"),
                accessCode = o.optString("accessCode"),
            )
        }
    }.getOrDefault(emptyList())

    private fun serializePrinters(printers: List<Printer>): String {
        val arr = JSONArray()
        printers.forEach { p ->
            arr.put(JSONObject()
                .put("serial", p.serial).put("name", p.name).put("ip", p.ip)
                .put("model", p.model).put("accessCode", p.accessCode))
        }
        return arr.toString()
    }

    /** Apply an imported `.bambulan` bundle, persisting every key it carries. */
    suspend fun importAll(data: BambuLanBundle.Imported) {
        context.dataStore.edit { prefs ->
            data.strings.forEach { (k, v) -> prefs[stringPreferencesKey(k)] = v }
            data.booleans.forEach { (k, v) -> prefs[booleanPreferencesKey(k)] = v }
            data.ints.forEach { (k, v) -> prefs[intPreferencesKey(k)] = v }
            data.features.forEach { (k, v) -> prefs[booleanPreferencesKey(k)] = v }
        }
    }
}

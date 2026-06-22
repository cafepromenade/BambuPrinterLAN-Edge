package com.bambuprinterlan.app

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class PrintRecord(val name: String, val timeMillis: Long, val status: String)  // "finished"|"failed"

/**
 * Local print history — records finished/failed jobs (no cloud needed). Persisted
 * to SharedPreferences (covered by Auto Backup) so it survives restarts.
 */
object PrintHistoryStore {
    private const val PREF = "print_history"
    private const val KEY = "records"
    private const val MAX = 100

    private val _records = MutableStateFlow<List<PrintRecord>>(emptyList())
    val records: StateFlow<List<PrintRecord>> = _records.asStateFlow()

    private var prefs: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        _records.value = parse(prefs?.getString(KEY, "[]") ?: "[]")
    }

    fun add(name: String, status: String, timeMillis: Long) {
        val rec = PrintRecord(name.ifBlank { "Print" }, timeMillis, status)
        val list = (listOf(rec) + _records.value).take(MAX)
        _records.value = list
        prefs?.edit()?.putString(KEY, serialize(list))?.apply()
    }

    fun clear() {
        _records.value = emptyList()
        prefs?.edit()?.remove(KEY)?.apply()
    }

    private fun parse(json: String): List<PrintRecord> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PrintRecord(o.optString("name"), o.optLong("time"), o.optString("status"))
        }
    }.getOrDefault(emptyList())

    private fun serialize(list: List<PrintRecord>): String {
        val arr = JSONArray()
        list.forEach { r ->
            arr.put(JSONObject().put("name", r.name).put("time", r.timeMillis).put("status", r.status))
        }
        return arr.toString()
    }
}

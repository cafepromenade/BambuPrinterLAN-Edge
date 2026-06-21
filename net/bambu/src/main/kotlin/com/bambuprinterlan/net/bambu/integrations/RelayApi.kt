package com.bambuprinterlan.net.bambu.integrations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * REST client for the relay's printer registry: list, add, and remove printers
 * at runtime. Token is optional (LAN-open relay).
 */
class RelayApi(private val client: OkHttpClient = OkHttpClient()) {

    data class RelayPrinter(val serial: String, val name: String, val ip: String, val connected: Boolean)

    suspend fun list(baseUrl: String, token: String): List<RelayPrinter> = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(authed(baseUrl, "/printers", token).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use emptyList()
                val arr = JSONArray(resp.body?.string().orEmpty())
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    RelayPrinter(o.optString("serial"), o.optString("name"),
                        o.optString("ip"), o.optBoolean("connected"))
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun add(baseUrl: String, token: String, serial: String, ip: String,
                    accessCode: String, name: String): Boolean = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("serial", serial).put("ip", ip)
            .put("access_code", accessCode).put("name", name)
            .toString().toRequestBody(JSON)
        runCatching {
            client.newCall(authed(baseUrl, "/printers", token).post(body).build())
                .execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    suspend fun remove(baseUrl: String, token: String, serial: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            client.newCall(authed(baseUrl, "/printers/$serial", token).delete().build())
                .execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun authed(baseUrl: String, path: String, token: String): Request.Builder {
        val b = Request.Builder().url(baseUrl.trimEnd('/') + path)
        if (token.isNotBlank()) b.header("Authorization", "Bearer $token")
        return b
    }

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}

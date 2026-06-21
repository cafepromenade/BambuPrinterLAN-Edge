package com.bambuprinterlan.net.bambu.integrations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Home Assistant REST client — port of BambuLan's HA helpers
 * (MainFrame.cpp:1230-1337): fetch states, summarize entities, call services,
 * with per-domain gating (lights/scenes/switches/climate) preserved.
 */
class HomeAssistantClient(private val client: OkHttpClient = OkHttpClient()) {

    data class Permissions(
        val allowLights: Boolean = true,
        val allowScenes: Boolean = true,
        val allowSwitches: Boolean = false,
        val allowClimate: Boolean = false,
    )

    data class Entity(val entityId: String, val state: String, val friendlyName: String?)

    private val json = Json { ignoreUnknownKeys = true }

    fun serviceAllowed(domain: String, service: String, perms: Permissions): Boolean = when (domain) {
        "light" -> perms.allowLights
        "scene" -> perms.allowScenes && service == "turn_on"
        "media_player", "notify", "persistent_notification" -> true
        "switch" -> perms.allowSwitches
        "climate" -> perms.allowClimate
        else -> false
    }

    suspend fun fetchStates(baseUrl: String, token: String): List<Entity> =
        withContext(Dispatchers.IO) {
            val url = baseUrl.trimEnd('/') + "/api/states"
            val req = Request.Builder().url(url)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json").build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use emptyList()
                    val arr = json.parseToJsonElement(resp.body?.string().orEmpty()) as? JsonArray
                        ?: return@use emptyList()
                    arr.mapNotNull { el ->
                        val o = el.jsonObject
                        val id = o["entity_id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val state = o["state"]?.jsonPrimitive?.content ?: ""
                        val fn = o["attributes"]?.jsonObject?.get("friendly_name")?.jsonPrimitive?.content
                        Entity(id, state, fn)
                    }
                }
            }.getOrDefault(emptyList())
        }

    suspend fun callService(
        baseUrl: String, token: String, domain: String, service: String,
        dataJson: String, perms: Permissions,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!serviceAllowed(domain, service, perms)) return@withContext false
        val url = baseUrl.trimEnd('/') + "/api/services/$domain/$service"
        val req = Request.Builder().url(url)
            .header("Authorization", "Bearer $token")
            .post(dataJson.toRequestBody(JSON)).build()
        runCatching {
            client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}

package com.bambuprinterlan.net.bambu.integrations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Minimal Anthropic Messages API client used by the BambuLan AI "labs"
 * (Auto-Fix, Sidechat, Feature Suggestion, Community Miner, AI Filament).
 * The API key is provided at runtime (entered in Settings, stored on-device) —
 * never committed. Returns the assistant's text, or a failure with the error.
 */
class AiClient(private val client: OkHttpClient = defaultClient()) {

    suspend fun complete(
        apiKey: String,
        model: String,
        prompt: String,
        maxTokens: Int = 1024,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalStateException("No API key"))
        val modelId = model.removePrefix("anthropic/").ifBlank { "claude-opus-4-8" }
        val payload = JSONObject()
            .put("model", modelId)
            .put("max_tokens", maxTokens)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            .toString()
            .toRequestBody(JSON)
        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(payload)
            .build()
        runCatching {
            client.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) error("API ${resp.code}: ${text.take(300)}")
                val content = JSONObject(text).optJSONArray("content") ?: JSONArray()
                val sb = StringBuilder()
                for (i in 0 until content.length()) {
                    val block = content.getJSONObject(i)
                    if (block.optString("type") == "text") sb.append(block.optString("text"))
                }
                sb.toString().ifBlank { "(empty response)" }
            }
        }
    }

    companion object {
        private val JSON = "application/json".toMediaType()
        fun defaultClient() = OkHttpClient.Builder()
            .callTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

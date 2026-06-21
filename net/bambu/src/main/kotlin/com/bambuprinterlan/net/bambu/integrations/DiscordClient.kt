package com.bambuprinterlan.net.bambu.integrations

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Discord webhook delivery — port of BambuLan's notify_discord_important_event
 * (MainFrame.cpp:6873) including the per-category embed colours (:1207).
 * Important events are opt-in; URLs are validated like the desktop app.
 */
class DiscordClient(private val client: OkHttpClient = OkHttpClient()) {

    enum class Category(val color: Int) {
        STARTUP(0x1A73E8), PROJECT(0x006A6A), EXPORT(0x386A20),
        AUTOFIX(0x6750A4), ERROR(0xB3261E);
    }

    fun isWebhookUrl(url: String): Boolean =
        url.startsWith("https://discord.com/api/webhooks/") ||
            url.startsWith("https://discordapp.com/api/webhooks/")

    /** Returns true on 2xx. Never throws to the caller. */
    suspend fun sendEmbed(
        webhookUrl: String,
        title: String,
        description: String,
        category: Category = Category.STARTUP,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isWebhookUrl(webhookUrl)) return@withContext false
        val safeTitle = jsonEscape(title)
        val safeDesc = jsonEscape(description)
        val body = """
            {"embeds":[{"title":"$safeTitle","description":"$safeDesc","color":${category.color}}]}
        """.trimIndent().toRequestBody(JSON)
        runCatching {
            client.newCall(Request.Builder().url(webhookUrl).post(body).build()).execute()
                .use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private fun jsonEscape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")

    companion object {
        private val JSON = "application/json".toMediaType()
    }
}

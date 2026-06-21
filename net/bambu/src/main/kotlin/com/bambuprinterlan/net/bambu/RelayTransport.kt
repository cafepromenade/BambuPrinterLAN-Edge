package com.bambuprinterlan.net.bambu

import com.bambuprinterlan.core.model.Command
import com.bambuprinterlan.core.model.DeviceStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/**
 * Talks to the optional relay server (relay/). REST for commands + uploads,
 * WebSocket for live status. The relay holds the real MQTT/FTPS connection and
 * multiplexes it to many clients (protocol §10).
 */
class RelayTransport(
    private val baseUrl: String,            // e.g. https://relay.tailnet.ts.net:8979
    private val token: String,
    override val serial: String,
    private val codec: CommandCodec = CommandCodec(),
    private val client: OkHttpClient = defaultClient(),
) : DeviceTransport {

    private val json = Json { ignoreUnknownKeys = true }
    private var socket: WebSocket? = null

    override fun status(): Flow<DeviceStatus> = callbackFlow {
        val wsUrl = baseUrl.replaceFirst("http", "ws") + "/printers/$serial/status?token=$token"
        val request = Request.Builder().url(wsUrl).build()
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching {
                    val obj = json.parseToJsonElement(text).jsonObject
                    val statusObj = obj["status"]?.jsonObject ?: return
                    trySend(ReportParser.parse(serial, wrapAsReport(statusObj)))
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                close(t)
            }
        }
        socket = client.newWebSocket(request, listener)
        awaitClose { socket?.close(1000, null) }
    }

    override suspend fun connect() { /* WS opens lazily in status() */ }

    override suspend fun disconnect() {
        socket?.close(1000, null)
        socket = null
    }

    override suspend fun send(command: Command) {
        // Relay accepts {category, command:{...}}; reuse the codec's envelope inner.
        val envelope = codec.encode(command)
        val inner = json.parseToJsonElement(envelope).jsonObject[command.category]!!
        val body = """{"category":"${command.category}","command":$inner}"""
            .toRequestBody(JSON_MEDIA)
        client.newCall(authed("/printers/$serial/command").post(body).build()).execute().use { }
    }

    override suspend fun upload(remoteName: String, bytes: ByteArray) {
        val multipart = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("name", remoteName)
            .addFormDataPart("file", remoteName, bytes.toRequestBody(OCTET))
            .build()
        client.newCall(authed("/printers/$serial/files").post(multipart).build()).execute().use { }
    }

    private fun authed(path: String) =
        Request.Builder().url(baseUrl.trimEnd('/') + path).header("Authorization", "Bearer $token")

    /** The relay already sends the parsed `print`-shaped status; wrap so ReportParser can read it. */
    private fun wrapAsReport(status: JsonObject): JsonObject =
        if (status.containsKey("print")) status else JsonObject(mapOf("print" to status))

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()
        private val OCTET = "application/octet-stream".toMediaType()
        fun defaultClient() = OkHttpClient.Builder()
            .pingInterval(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
    }
}

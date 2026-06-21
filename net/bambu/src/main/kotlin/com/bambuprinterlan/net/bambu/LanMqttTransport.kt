package com.bambuprinterlan.net.bambu

import com.bambuprinterlan.core.model.Command
import com.bambuprinterlan.core.model.DeviceStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPSClient
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallback
import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * LAN-direct transport — connects straight to the printer's MQTT broker over TLS
 * (port 8883, username `bblp`, password = access code), no cloud, no relay.
 * Mirrors the protocol in docs/BambuPrinterLan_Protocol_Layer.md §4. The printer's cert
 * is self-signed, so the chain is not verified (LAN TOFU).
 */
class LanMqttTransport(
    private val ip: String,
    private val accessCode: String,
    override val serial: String,
    private val port: Int = 8883,
    private val codec: CommandCodec = CommandCodec(),
) : DeviceTransport {

    private val json = Json { ignoreUnknownKeys = true }
    private val merged = JSONObject()            // accumulates report deltas
    private var client: MqttClient? = null

    private val reportTopic get() = "device/$serial/report"
    private val requestTopic get() = "device/$serial/request"

    override suspend fun connect() = withContext(Dispatchers.IO) {
        val c = MqttClient("ssl://$ip:$port", "bambuprinterlan-$serial", MemoryPersistence())
        val opts = MqttConnectOptions().apply {
            userName = "bblp"
            password = accessCode.toCharArray()
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 10
            keepAliveInterval = 30
            socketFactory = trustAllSocketFactory()
        }
        c.connect(opts)
        client = c
    }

    override fun status(): Flow<DeviceStatus> = callbackFlow {
        val c = client ?: run { close(IllegalStateException("connect() first")); return@callbackFlow }
        c.setCallback(object : MqttCallback {
            override fun connectionLost(cause: Throwable?) { /* auto-reconnect handles it */ }
            override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            override fun messageArrived(topic: String?, message: MqttMessage?) {
                val payload = message?.payload ?: return
                runCatching {
                    deepMerge(merged, JSONObject(String(payload)))
                    val obj = json.parseToJsonElement(merged.toString()).jsonObject
                    trySend(ReportParser.parse(serial, obj))
                }
            }
        })
        c.subscribe(reportTopic, 0)
        // request a full snapshot
        runCatching { c.publish(requestTopic, codec.encode(Command.Pushall()).toByteArray(), 0, false) }
        awaitClose { runCatching { c.unsubscribe(reportTopic) } }
    }

    override suspend fun send(command: Command) = withContext(Dispatchers.IO) {
        val c = client ?: throw IllegalStateException("Not connected")
        c.publish(requestTopic, codec.encode(command).toByteArray(), 0, false)
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        runCatching { client?.disconnect() }
        runCatching { client?.close() }
        client = null
    }

    /** Upload a sliced file to the printer SD over implicit FTPS (port 990). */
    override suspend fun upload(remoteName: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val ftps = FTPSClient(true)            // implicit TLS
        ftps.setTrustManager(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        })
        try {
            ftps.connect(ip, 990)
            if (!ftps.login("bblp", accessCode)) throw IllegalStateException("FTPS login failed")
            ftps.execPBSZ(0)
            ftps.execPROT("P")
            ftps.enterLocalPassiveMode()
            ftps.setFileType(FTP.BINARY_FILE_TYPE)
            bytes.inputStream().use { input ->
                if (!ftps.storeFile(remoteName, input))
                    throw IllegalStateException("FTPS upload failed: ${ftps.replyString}")
            }
        } finally {
            runCatching { ftps.logout() }
            runCatching { ftps.disconnect() }
        }
    }

    private fun deepMerge(dst: JSONObject, src: JSONObject) {
        for (key in src.keys()) {
            val sv = src.get(key)
            val dv = dst.opt(key)
            if (sv is JSONObject && dv is JSONObject) deepMerge(dv, sv) else dst.put(key, sv)
        }
    }

    private fun trustAllSocketFactory() = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }), SecureRandom())
    }.socketFactory
}

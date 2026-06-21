package com.bambuprinterlan.core.model

/** A configured printer (LAN-direct or via relay). */
data class Printer(
    val serial: String,
    val name: String = "",
    val ip: String = "",
    val model: String = "",
    val accessCode: String = "",   // stored encrypted on-device; never logged
    val online: Boolean = false,
)

/** How the app reaches printers. */
sealed interface ConnectionMode {
    data object LanDirect : ConnectionMode
    data class Relay(val baseUrl: String, val token: String) : ConnectionMode
    data class Cloud(val region: CloudRegion) : ConnectionMode
}

enum class CloudRegion(val mqttHost: String, val apiBase: String) {
    GLOBAL("us.mqtt.bambulab.com", "https://api.bambulab.com"),
    CHINA("cn.mqtt.bambulab.com", "https://api.bambulab.cn"),
}

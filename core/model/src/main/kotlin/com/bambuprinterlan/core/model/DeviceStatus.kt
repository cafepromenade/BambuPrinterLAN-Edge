package com.bambuprinterlan.core.model

/**
 * Normalized printer status, mapped from the Bambu `print` report object.
 * Field names mirror the protocol (see docs/BambuPrinterLan_Protocol_Layer.md §4.2)
 * so the mapping from raw JSON is mechanical.
 */
data class DeviceStatus(
    val serial: String = "",
    val online: Boolean = false,
    val gcodeState: GcodeState = GcodeState.UNKNOWN,
    val progressPercent: Int = 0,
    val remainingMinutes: Int = 0,
    val layerNum: Int = 0,
    val totalLayerNum: Int = 0,
    val subtaskName: String = "",
    val stageCur: Int = -1,
    val printError: Long = 0,
    val nozzleTemper: Float = 0f,
    val nozzleTargetTemper: Float = 0f,
    val bedTemper: Float = 0f,
    val bedTargetTemper: Float = 0f,
    val chamberTemper: Float = 0f,
    val coolingFanSpeed: Int = 0,
    val speedLevel: SpeedLevel = SpeedLevel.STANDARD,
    val wifiSignal: String = "",
    val chamberLightOn: Boolean = false,
    val ams: AmsState = AmsState(),
    val hms: List<HmsAlert> = emptyList(),
)

enum class GcodeState { UNKNOWN, IDLE, PREPARE, RUNNING, PAUSE, FINISH, FAILED;
    companion object {
        fun from(raw: String?): GcodeState = when (raw?.uppercase()) {
            "IDLE" -> IDLE; "PREPARE" -> PREPARE; "RUNNING" -> RUNNING
            "PAUSE" -> PAUSE; "FINISH" -> FINISH; "FAILED", "FAILURE" -> FAILED
            else -> UNKNOWN
        }
    }
}

/** Print speed levels accepted by `print.print_speed` (param "1".."4"). */
enum class SpeedLevel(val param: Int) {
    SILENT(1), STANDARD(2), SPORT(3), LUDICROUS(4);
    companion object {
        fun from(value: Int?): SpeedLevel = entries.firstOrNull { it.param == value } ?: STANDARD
    }
}

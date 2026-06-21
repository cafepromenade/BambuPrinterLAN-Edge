package com.bambuprinterlan.net.bambu

import com.bambuprinterlan.core.model.AmsState
import com.bambuprinterlan.core.model.AmsUnit
import com.bambuprinterlan.core.model.DeviceStatus
import com.bambuprinterlan.core.model.FilamentSlot
import com.bambuprinterlan.core.model.GcodeState
import com.bambuprinterlan.core.model.HmsAlert
import com.bambuprinterlan.core.model.SpeedLevel
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Maps a raw Bambu `report` payload (the `print` object) into [DeviceStatus].
 * Reports are deltas; callers should merge then re-parse, or merge into the
 * previous DeviceStatus. Field names follow protocol §4.2.
 */
object ReportParser {
    fun parse(serial: String, report: JsonObject, online: Boolean = true): DeviceStatus {
        val print = report["print"]?.jsonObject ?: JsonObject(emptyMap())
        fun s(key: String) = print[key]?.jsonPrimitive?.contentOrNull
        fun i(key: String) = print[key]?.jsonPrimitive?.int ?: 0
        fun f(key: String) = print[key]?.jsonPrimitive?.float ?: 0f

        return DeviceStatus(
            serial = serial,
            online = online,
            gcodeState = GcodeState.from(s("gcode_state")),
            progressPercent = i("mc_percent"),
            remainingMinutes = i("mc_remaining_time"),
            layerNum = i("layer_num"),
            totalLayerNum = i("total_layer_num"),
            subtaskName = s("subtask_name") ?: "",
            stageCur = print["stg_cur"]?.jsonPrimitive?.int ?: -1,
            printError = print["print_error"]?.jsonPrimitive?.long ?: 0,
            nozzleTemper = f("nozzle_temper"),
            nozzleTargetTemper = f("nozzle_target_temper"),
            bedTemper = f("bed_temper"),
            bedTargetTemper = f("bed_target_temper"),
            chamberTemper = f("chamber_temper"),
            coolingFanSpeed = s("cooling_fan_speed")?.toIntOrNull() ?: i("cooling_fan_speed"),
            speedLevel = SpeedLevel.from(print["spd_lvl"]?.jsonPrimitive?.int),
            wifiSignal = s("wifi_signal") ?: "",
            chamberLightOn = parseChamberLight(print),
            ams = parseAms(print["ams"]?.jsonObject),
            hms = parseHms(print),
        )
    }

    private fun parseChamberLight(print: JsonObject): Boolean {
        val lights = print["lights_report"]?.jsonArray ?: return false
        return lights.any {
            val o = it.jsonObject
            o["node"]?.jsonPrimitive?.contentOrNull == "chamber_light" &&
                o["mode"]?.jsonPrimitive?.contentOrNull == "on"
        }
    }

    private fun parseAms(ams: JsonObject?): AmsState {
        if (ams == null) return AmsState()
        val units = ams["ams"]?.jsonArray?.map { u ->
            val o = u.jsonObject
            AmsUnit(
                id = o["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                humidity = o["humidity"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1,
                temperature = o["temp"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f,
                trays = o["tray"]?.jsonArray?.map { t -> parseSlot(t.jsonObject) } ?: emptyList(),
            )
        } ?: emptyList()
        return AmsState(
            humidity = ams["humidity"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1,
            units = units,
        )
    }

    private fun parseSlot(o: JsonObject) = FilamentSlot(
        id = o["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
        type = o["tray_type"]?.jsonPrimitive?.contentOrNull ?: "",
        colorHex = o["tray_color"]?.jsonPrimitive?.contentOrNull ?: "",
        remainPercent = o["remain"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: -1,
        nozzleTempMin = o["nozzle_temp_min"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
        nozzleTempMax = o["nozzle_temp_max"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
        k = o["k"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f,
        n = o["n"]?.jsonPrimitive?.contentOrNull?.toFloatOrNull() ?: 0f,
    )

    private fun parseHms(print: JsonObject): List<HmsAlert> {
        val arr = print["hms"]?.jsonArray ?: return emptyList()
        return arr.mapNotNull {
            val o = it.jsonObject
            val attr = o["attr"]?.jsonPrimitive?.long ?: return@mapNotNull null
            val code = o["code"]?.jsonPrimitive?.long ?: return@mapNotNull null
            HmsAlert(attr, code)
        }
    }
}

package com.bambuprinterlan.core.model

/**
 * A printer command. Serialized by CommandCodec into the Bambu envelope
 * `{ "<category>": { "command": "...", "sequence_id": "...", ... } }` — identical
 * bytes whether sent over LAN, cloud, or the relay (see protocol §4.1).
 */
sealed interface Command {
    val category: String
    val command: String
    val params: Map<String, Any?>

    data class Pushall(
        override val category: String = "pushing",
        override val command: String = "pushall",
        override val params: Map<String, Any?> = mapOf("version" to 1, "push_target" to 1),
    ) : Command

    data class GcodeLine(val gcode: String) : Command {
        override val category = "print"
        override val command = "gcode_line"
        override val params get() = mapOf("param" to gcode)
    }

    data object Pause : Command {
        override val category = "print"; override val command = "pause"
        override val params = emptyMap<String, Any?>()
    }

    data object Resume : Command {
        override val category = "print"; override val command = "resume"
        override val params = emptyMap<String, Any?>()
    }

    data object Stop : Command {
        override val category = "print"; override val command = "stop"
        override val params = emptyMap<String, Any?>()
    }

    data class SetSpeed(val level: SpeedLevel) : Command {
        override val category = "print"; override val command = "print_speed"
        override val params get() = mapOf("param" to level.param.toString())
    }

    data class SetBedTemp(val celsius: Int) : Command {
        override val category = "print"; override val command = "gcode_line"
        override val params get() = mapOf("param" to "M140 S$celsius\n")
    }

    data class SetNozzleTemp(val celsius: Int) : Command {
        override val category = "print"; override val command = "gcode_line"
        override val params get() = mapOf("param" to "M104 S$celsius\n")
    }

    data class ChamberLight(val on: Boolean) : Command {
        override val category = "system"; override val command = "ledctrl"
        override val params get() = mapOf(
            "led_node" to "chamber_light",
            "led_mode" to if (on) "on" else "off",
            "led_on_time" to 500, "led_off_time" to 500,
            "loop_times" to 0, "interval_time" to 0,
        )
    }

    /** Start a print from an uploaded file (see protocol §6). */
    data class ProjectFile(
        val fileName: String,            // gcode entry, e.g. "Metadata/plate_1.gcode"
        val url: String,                 // "ftp:///<name>.3mf" (LAN SD) or cloud/relay URL
        val subtaskName: String = "",
        val useAms: Boolean = true,
        val amsMapping: List<Int> = emptyList(),
        val bedLeveling: Boolean = true,
        val flowCali: Boolean = true,
        val timelapse: Boolean = false,
    ) : Command {
        override val category = "print"
        override val command = "project_file"
        override val params get() = buildMap<String, Any?> {
            put("param", fileName)
            put("url", url)
            if (subtaskName.isNotEmpty()) put("subtask_name", subtaskName)
            put("use_ams", useAms)
            if (amsMapping.isNotEmpty()) put("ams_mapping", amsMapping)
            put("bed_leveling", bedLeveling)
            put("flow_cali", flowCali)
            put("timelapse", timelapse)
        }
    }

    // ---- motion ------------------------------------------------------------
    data object HomeAll : Command {
        override val category = "print"; override val command = "gcode_line"
        override val params = mapOf<String, Any?>("param" to "G28\n")
    }

    /** Jog an axis by mm (relative). axis = "X"|"Y"|"Z"|"E". */
    data class Move(val axis: String, val mm: Float, val feed: Int = 3000) : Command {
        override val category = "print"; override val command = "gcode_line"
        override val params get() = mapOf("param" to "G91\nG1 $axis$mm F$feed\nG90\n")
    }

    data class Extrude(val mm: Float) : Command {
        override val category = "print"; override val command = "gcode_line"
        override val params get() = mapOf("param" to "G91\nG1 E$mm F300\nG90\n")
    }

    // ---- temperature -------------------------------------------------------
    /** Cool everything down. */
    data object Cooldown : Command {
        override val category = "print"; override val command = "gcode_line"
        override val params = mapOf<String, Any?>("param" to "M104 S0\nM140 S0\n")
    }

    // ---- fans --------------------------------------------------------------
    /** Part-cooling fan, 0–100 %. */
    data class PartFan(val percent: Int) : Command {
        override val category = "print"; override val command = "gcode_line"
        override val params get() = mapOf("param" to
            (if (percent <= 0) "M107\n" else "M106 S${(percent.coerceIn(0, 100) * 255 / 100)}\n"))
    }

    /** Auxiliary fan (P2), 0–100 %. */
    data class AuxFan(val percent: Int) : Command {
        override val category = "print"; override val command = "gcode_line"
        override val params get() = mapOf("param" to "M106 P2 S${(percent.coerceIn(0, 100) * 255 / 100)}\n")
    }

    // ---- calibration -------------------------------------------------------
    data class Calibrate(
        val bedLevel: Boolean = false,
        val vibration: Boolean = false,
        val motorNoise: Boolean = false,
        val xcam: Boolean = false,
    ) : Command {
        override val category = "print"; override val command = "calibration"
        override val params get(): Map<String, Any?> {
            var option = 0
            if (bedLevel) option = option or (1 shl 1)
            if (vibration) option = option or (1 shl 2)
            if (motorNoise) option = option or (1 shl 3)
            if (xcam) option = option or (1 shl 0)
            return mapOf("option" to option)
        }
    }

    // ---- AMS ---------------------------------------------------------------
    data class AmsSelectTray(val trayId: Int) : Command {
        override val category = "print"; override val command = "ams_change_filament"
        override val params get() = mapOf("target" to trayId, "curr_temp" to 220, "tar_temp" to 220)
    }

    data class AmsControl(val action: String) : Command {  // "resume"|"reset"|"pause"
        override val category = "print"; override val command = "ams_control"
        override val params get() = mapOf("action" to action)
    }

    // ---- misc --------------------------------------------------------------
    data object StopBuzzer : Command {
        override val category = "system"; override val command = "set_accessories"
        override val params = mapOf<String, Any?>("accessory_type" to "none")
    }

    /** Toggle recording / timelapse on the chamber camera. */
    data class Record(val on: Boolean) : Command {
        override val category = "camera"; override val command = "ipcam_record_set"
        override val params get() = mapOf("control" to if (on) "enable" else "disable")
    }

    /** Escape hatch for any command not yet modeled. */
    data class Raw(
        override val category: String,
        override val command: String,
        override val params: Map<String, Any?> = emptyMap(),
    ) : Command
}

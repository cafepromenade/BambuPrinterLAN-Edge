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

    /** Escape hatch for any command not yet modeled. */
    data class Raw(
        override val category: String,
        override val command: String,
        override val params: Map<String, Any?> = emptyMap(),
    ) : Command
}

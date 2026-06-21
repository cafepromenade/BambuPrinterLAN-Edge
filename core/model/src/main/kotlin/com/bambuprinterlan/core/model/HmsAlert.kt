package com.bambuprinterlan.core.model

/**
 * Hardware Monitoring System alert from the `hms[]` array.
 * `attr` + `code` combine into a hex code that maps to a wiki troubleshooting URL
 * (the BambuLan "HMS AI Resources" feature enriches these cards).
 */
data class HmsAlert(
    val attr: Long,
    val code: Long,
    val severity: HmsSeverity = HmsSeverity.from(code),
) {
    /** Bambu wiki code form, e.g. "0300_0100_0002_0001". */
    val wikiCode: String
        get() = "%04X_%04X_%04X_%04X".format(
            (attr shr 16) and 0xFFFF, attr and 0xFFFF,
            (code shr 16) and 0xFFFF, code and 0xFFFF,
        )

    val wikiUrl: String
        get() = "https://wiki.bambulab.com/en/x1/troubleshooting/hmscode/$wikiCode"
}

enum class HmsSeverity { FATAL, SERIOUS, COMMON, INFO, UNKNOWN;
    companion object {
        fun from(code: Long): HmsSeverity = when (((code shr 16) and 0xFFFF).toInt()) {
            1 -> FATAL; 2 -> SERIOUS; 3 -> COMMON; 4 -> INFO; else -> UNKNOWN
        }
    }
}

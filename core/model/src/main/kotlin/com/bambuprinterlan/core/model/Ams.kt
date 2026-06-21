package com.bambuprinterlan.core.model

/** AMS (Automatic Material System) state from the `ams` report object. */
data class AmsState(
    val humidity: Int = -1,
    val units: List<AmsUnit> = emptyList(),
    val externalSpool: FilamentSlot? = null,
)

data class AmsUnit(
    val id: Int = 0,
    val humidity: Int = -1,
    val temperature: Float = 0f,
    val trays: List<FilamentSlot> = emptyList(),
)

data class FilamentSlot(
    val id: Int = 0,
    val type: String = "",          // tray_type, e.g. "PLA"
    val colorHex: String = "",      // tray_color RRGGBBAA
    val remainPercent: Int = -1,    // remain
    val nozzleTempMin: Int = 0,
    val nozzleTempMax: Int = 0,
    val k: Float = 0f,              // flow dynamics K factor
    val n: Float = 0f,
)

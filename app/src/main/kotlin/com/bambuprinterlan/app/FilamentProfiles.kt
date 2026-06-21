package com.bambuprinterlan.app

/** A filament preset: type, recommended temps, and Bambu's tray_info_idx. */
data class FilamentProfile(
    val name: String,
    val type: String,
    val nozzleMin: Int,
    val nozzleMax: Int,
    val bed: Int,
    val infoIdx: String,        // Bambu generic preset id
    val defaultColor: String,   // RRGGBBAA
)

/** Built-in generic filament library (mirrors Bambu's generic presets). */
object FilamentProfiles {
    val all = listOf(
        FilamentProfile("Bambu PLA Basic", "PLA", 190, 230, 35, "GFL99", "00AE42FF"),
        FilamentProfile("PLA Matte", "PLA", 190, 230, 35, "GFL95", "8E9089FF"),
        FilamentProfile("PETG", "PETG", 230, 260, 70, "GFG99", "0086D6FF"),
        FilamentProfile("ABS", "ABS", 240, 270, 90, "GFB99", "1A1A1AFF"),
        FilamentProfile("ASA", "ASA", 240, 270, 90, "GFB98", "F2F2F2FF"),
        FilamentProfile("TPU 95A", "TPU", 220, 250, 35, "GFU99", "FF6A00FF"),
        FilamentProfile("PLA-CF", "PLA-CF", 210, 240, 45, "GFL98", "303030FF"),
        FilamentProfile("PA-CF (Nylon)", "PA-CF", 260, 300, 90, "GFN98", "4B4B4BFF"),
        FilamentProfile("PC", "PC", 260, 290, 100, "GFC99", "D9D9D9FF"),
        FilamentProfile("PVA (support)", "PVA", 190, 220, 45, "GFS99", "FFD400FF"),
    )

    fun byType(type: String): FilamentProfile? =
        all.firstOrNull { it.type.equals(type, ignoreCase = true) }
}

package com.bambuprinterlan.engine

/**
 * JNI bridge to the native slicing engine (NDK libslic3r). Phase 1 proves the
 * toolchain; [slice] returns -1 until libslic3r is wired in. Designed so the UI
 * can call a real seam now and gain actual slicing without API changes.
 */
object SlicerBridge {
    private val available: Boolean = runCatching { System.loadLibrary("bambuprinterlan_engine") }.isSuccess

    val isAvailable: Boolean get() = available

    private external fun nativeEngineVersion(): String
    private external fun nativeSlice(inputPath: String, outputPath: String, configIni: String): Int

    /** Native engine version string, or a fallback if the library failed to load. */
    fun version(): String =
        if (available) runCatching { nativeEngineVersion() }.getOrElse { "native load error" }
        else "native engine unavailable"

    /** Slice [inputPath] to gcode at [outputPath]. Returns 0 on success, <0 otherwise. */
    fun slice(inputPath: String, outputPath: String, configIni: String): Int =
        if (available) runCatching { nativeSlice(inputPath, outputPath, configIni) }.getOrDefault(-1)
        else -1
}

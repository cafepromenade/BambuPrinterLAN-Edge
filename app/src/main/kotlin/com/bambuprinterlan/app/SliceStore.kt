package com.bambuprinterlan.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Result of a slice, shared from Prepare to Preview (and on to print). */
data class SliceResult(
    val modelName: String,
    val gcodePath: String,
    val layers: Int,
    val bytes: Long,
    val gcodeHead: String,
    val filamentMeters: Float = 0f,
    val grams: Float = 0f,
    val minutes: Int = 0,
)

/** Process-wide holder for the latest slice so Preview/print can read it. */
object SliceStore {
    private val _result = MutableStateFlow<SliceResult?>(null)
    val result: StateFlow<SliceResult?> = _result.asStateFlow()
    fun set(r: SliceResult) { _result.value = r }
    fun clear() { _result.value = null }
}

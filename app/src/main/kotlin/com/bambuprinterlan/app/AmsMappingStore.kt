package com.bambuprinterlan.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Maps the print's filament(s) to AMS tray ids. Empty = let the printer decide.
 * The Filament screen sets it; DeviceViewModel.sendPrint passes it on ProjectFile.
 */
object AmsMappingStore {
    private val _mapping = MutableStateFlow<List<Int>>(emptyList())
    val mapping: StateFlow<List<Int>> = _mapping.asStateFlow()

    fun setSingleTray(trayId: Int) { _mapping.value = listOf(trayId) }
    fun clear() { _mapping.value = emptyList() }
}

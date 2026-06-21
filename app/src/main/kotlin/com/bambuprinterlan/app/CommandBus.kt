package com.bambuprinterlan.app

import com.bambuprinterlan.core.model.Command
import com.bambuprinterlan.core.model.DeviceStatus
import com.bambuprinterlan.core.model.GcodeState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared bridge to the active device transport. The DeviceViewModel registers
 * [handler] and publishes live [status]; notification PendingIntents call
 * [dispatch]; auxiliary screens (Filament, Calibration) read [status] and
 * forward commands via [send] without holding the view-model.
 */
object CommandBus {
    @Volatile
    var handler: ((Command) -> Unit)? = null

    private val _status = MutableStateFlow<DeviceStatus?>(null)
    val status: StateFlow<DeviceStatus?> = _status.asStateFlow()

    fun publishStatus(st: DeviceStatus?) { _status.value = st }

    val connected: Boolean get() = handler != null

    /** True when a job is on the bed (running / preparing / paused). */
    val printing: Boolean
        get() = _status.value?.gcodeState in setOf(
            GcodeState.RUNNING, GcodeState.PREPARE, GcodeState.PAUSE,
        )

    /** Send any command to the connected transport; returns false if not connected. */
    fun send(cmd: Command): Boolean {
        val h = handler ?: return false
        h(cmd); return true
    }

    fun dispatch(action: String) {
        val cmd = when (action) {
            "pause" -> Command.Pause
            "resume" -> Command.Resume
            "stop" -> Command.Stop
            else -> return
        }
        handler?.invoke(cmd)
    }
}

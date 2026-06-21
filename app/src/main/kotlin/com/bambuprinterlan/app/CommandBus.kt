package com.bambuprinterlan.app

import com.bambuprinterlan.core.model.Command

/**
 * Bridges notification action buttons to the active device transport. The
 * DeviceViewModel registers [handler]; the notification's PendingIntents fire a
 * broadcast that calls [dispatch].
 */
object CommandBus {
    @Volatile
    var handler: ((Command) -> Unit)? = null

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

package com.bambuprinterlan.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Receives notification button taps (pause/resume/stop) and forwards them. */
class PrintActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.getStringExtra("action")?.let { CommandBus.dispatch(it) }
    }
}

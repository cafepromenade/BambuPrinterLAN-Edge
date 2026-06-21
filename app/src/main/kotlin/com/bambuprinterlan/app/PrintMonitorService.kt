package com.bambuprinterlan.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground service that shows an **ongoing (undismissable)** print
 * notification with a progress bar and Pause/Resume/Stop buttons. It stays put
 * while a print runs so you can control the printer from the shade, even with
 * the app in the background.
 */
class PrintMonitorService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent?.getStringExtra("title") ?: "Printing  列印中"
        val text = intent?.getStringExtra("text").orEmpty()
        val progress = intent?.getIntExtra("progress", 0) ?: 0
        val paused = intent?.getBooleanExtra("paused", false) ?: false
        startForeground(NOTIF_ID, build(title, text, progress, paused))
        return START_STICKY
    }

    private fun build(title: String, text: String, progress: Int, paused: Boolean): Notification {
        ensureChannel()
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress.coerceIn(0, 100), false)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            )
            .addAction(0, if (paused) "Resume 繼續" else "Pause 暫停",
                action(if (paused) "resume" else "pause"))
            .addAction(0, "Stop 停止", action("stop"))
            .build()
    }

    private fun action(a: String): PendingIntent {
        val i = Intent(this, PrintActionReceiver::class.java).putExtra("action", a)
        return PendingIntent.getBroadcast(
            this, a.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = getSystemService(NotificationManager::class.java)
            if (mgr.getNotificationChannel(CHANNEL) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Print progress  列印進度",
                        NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
    }

    companion object {
        private const val NOTIF_ID = 4201
        private const val CHANNEL = "print_progress"
        private const val ACTION_STOP = "com.bambuprinterlan.app.STOP_MONITOR"

        fun update(context: Context, title: String, text: String, progress: Int, paused: Boolean) {
            val i = Intent(context, PrintMonitorService::class.java)
                .putExtra("title", title).putExtra("text", text)
                .putExtra("progress", progress).putExtra("paused", paused)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, PrintMonitorService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}

package dev.patrick.astra.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dev.patrick.astra.R

object OverlayForegroundController {
    private const val CHANNEL_ID = "astra_overlay"
    private const val CHANNEL_NAME = "Astra Overlay"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Astra overlay active"
        }
        manager.createNotificationChannel(channel)
    }

    fun buildNotification(context: Context): Notification {
        val stopIntent = Intent(context, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP_OVERLAY
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            0,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Astra overlay is active")
            .setContentText("Tap to manage or stop the overlay")
            .setOngoing(true)
            .addAction(
                NotificationCompat.Action(
                    0,
                    "Stop",
                    stopPendingIntent
                )
            )
            .build()
    }
}

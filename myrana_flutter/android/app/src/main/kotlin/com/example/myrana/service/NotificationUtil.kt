package com.example.myrana.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

object NotificationUtil {
    const val CHANNEL_ID = "myrana_monitor_service"
    const val NOTIFICATION_ID = 7101

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MYRana Monitoring",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "مراقبة الحظر ووقت الشاشة"
        }
        mgr.createNotificationChannel(channel)
    }

    fun buildForegroundNotification(context: Context): Notification {
        val contentTitle = "MYRana"
        val contentText = "المراقبة تعمل"
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}


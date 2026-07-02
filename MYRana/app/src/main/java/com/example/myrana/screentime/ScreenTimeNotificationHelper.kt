package com.example.myrana.screentime

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.myrana.R

/** مؤشر مرئي مستمر في شريط الإشعارات (أخضر / أصفر / أحمر). */
object ScreenTimeNotificationHelper {

    private const val CHANNEL_ID = "screen_time_indicator"
    private const val NOTIFICATION_ID = 42_001

    fun update(
        context: Context,
        packageName: String,
        totalSeconds: Long,
        level: UsageLevel,
        policy: ScreenTimePolicy,
    ) {
        ensureChannel(context)
        val minutes = (totalSeconds / 60).toInt()
        val color = when (level) {
            UsageLevel.GREEN -> Color.parseColor("#4CAF50")
            UsageLevel.YELLOW -> Color.parseColor("#FFC107")
            UsageLevel.RED -> Color.parseColor("#F44336")
            UsageLevel.BLOCKED -> Color.parseColor("#B71C1C")
        }
        val title = when (level) {
            UsageLevel.GREEN -> context.getString(R.string.screen_time_notif_green)
            UsageLevel.YELLOW -> context.getString(R.string.screen_time_notif_yellow)
            UsageLevel.RED -> context.getString(R.string.screen_time_notif_red)
            UsageLevel.BLOCKED -> context.getString(R.string.screen_time_notif_blocked)
        }
        val text = context.getString(
            R.string.screen_time_notif_detail,
            minutes,
            policy.blockMinutes,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_screen_time_dot)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(color)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.screen_time_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.screen_time_channel_desc)
            enableLights(true)
            lightColor = Color.GREEN
        }
        nm.createNotificationChannel(channel)
    }
}

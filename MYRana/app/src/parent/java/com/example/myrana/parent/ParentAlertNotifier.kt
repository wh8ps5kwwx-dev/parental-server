package com.example.myrana.parent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.myrana.R

/** إشعار للأم عند وصول تنبيه خطير جديد (بحث / حظر). */
object ParentAlertNotifier {

    private const val PREFS = "myrana_parent_alerts"
    private const val KEY_LAST_SIG = "last_alert_sig"
    private const val CHANNEL_ID = "parent_danger_alerts"
    private const val NOTIFICATION_ID = 9001

    fun notifyIfNew(context: Context, lines: List<String>) {
        if (lines.isEmpty()) return
        val latest = lines.first().trim()
        if (latest.isEmpty()) return
        if (!isDangerAlert(latest)) return

        val sig = latest.hashCode().toString()
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_LAST_SIG, "") == sig) return
        prefs.edit().putString(KEY_LAST_SIG, sig).apply()

        val app = context.applicationContext
        ensureChannel(app)
        val notification = NotificationCompat.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(app.getString(R.string.parent_alert_notification_title))
            .setContentText(latest.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(latest.take(500)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(app).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS غير ممنوح — المعاينة في الشاشة تكفي
        }
    }

    private fun isDangerAlert(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("تنبيه بحث") ||
            lower.contains("محاولة") ||
            lower.contains("محظور") ||
            lower.contains("youtube") ||
            lower.contains("دارك") ||
            lower.contains("onion")
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.parent_alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.parent_alert_channel_desc)
            },
        )
    }
}

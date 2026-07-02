package com.example.myrana.enforcement

import android.content.Context
import android.content.pm.PackageManager
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.session.ChildSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * مراقبة فتح **جميع** تطبيقات المستخدم (Usage Stats) — تنبيه ولي الأمر
 * عند فتح تطبيق جديد أو تطبيق مراسلة/تواصل.
 */
object AppUsageAlertHelper {

    private const val PREFS = "myrana_app_usage_alerts"
    private const val COOLDOWN_MS = 5 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastForeground: String? = null

    fun onForegroundChanged(context: Context, packageName: String?) {
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isBlank() || pkg == lastForeground) return
        lastForeground = pkg
        if (!MonitoredAppRegistry.shouldMonitorAccessibilityText(pkg)) return
        if (pkg.startsWith("com.example.myrana")) return

        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastAt = prefs.getLong("last_$pkg", 0L)
        val now = System.currentTimeMillis()
        if (now - lastAt < COOLDOWN_MS) return

        val childCode = ChildSession.childCode(app) ?: return
        val label = appLabel(app, pkg)
        val category = MonitoredAppRegistry.appCategoryLabel(pkg)
        val message = when {
            MonitoredAppRegistry.isMessagingApp(pkg) ->
                "مراقبة محادثات — الطفل فتح $label ($category): تُراقَب الرسائل الظاهرة على الشاشة"
            category == "YouTube" ->
                "مراقبة تطبيق — الطفل فتح YouTube: تُفحَص الكلمات والمقاطع الظاهرة"
            else ->
                "مراقبة تطبيق — الطفل يستخدم: $label ($category) — تُفحَص الكلمات الظاهرة على الشاشة"
        }

        scope.launch {
            if (NetworkModule.postAlertSync(childCode, message)) {
                prefs.edit().putLong("last_$pkg", now).apply()
            }
        }
    }

    private fun appLabel(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}

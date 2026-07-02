package com.example.myrana.sync

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.device.DeviceIdentity
import com.example.myrana.session.ChildSession
import com.example.myrana.util.AppIconHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** رفع قائمة تطبيقات الطفل (اسم + أيقونة) للأم — مرة كل 6 ساعات. */
object InstalledAppsSync {

    private const val PREFS = "myrana_installed_apps_sync"
    private const val KEY_LAST_MS = "last_sync_ms"
    private const val INTERVAL_MS = 6L * 60L * 60L * 1000L
    private const val MAX_APPS = 120

    suspend fun syncIfDue(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val last = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_LAST_MS, 0L)
        if (last > 0L && System.currentTimeMillis() - last < INTERVAL_MS) return@withContext
        val childCode = ChildSession.childCode(app) ?: DeviceIdentity.childDeviceId(app)
        if (childCode.isBlank()) return@withContext
        val apps = collectInstalledApps(app)
        if (apps.isEmpty()) return@withContext
        val ok = NetworkModule.postSyncChildApps(childCode, apps)
        if (ok) {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_MS, System.currentTimeMillis())
                .apply()
        }
    }

    private fun collectInstalledApps(context: Context): List<Map<String, String?>> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val activities = pm.queryIntentActivities(launcher, PackageManager.GET_META_DATA)
        val density = context.resources.displayMetrics.density
        val seen = linkedSetOf<String>()
        val result = mutableListOf<Map<String, String?>>()
        for (info in activities) {
            val pkg = info.activityInfo?.packageName?.trim().orEmpty()
            if (pkg.isBlank() || pkg.startsWith("com.example.myrana") || !seen.add(pkg)) continue
            val label = try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (_: Exception) {
                pkg.substringAfterLast('.')
            }
            val iconB64 = try {
                AppIconHelper.toBase64Png(pm.getApplicationIcon(pkg), 48, density)
            } catch (_: Exception) {
                null
            }
            result.add(
                mapOf(
                    "package" to pkg,
                    "app_label" to label,
                    "icon_b64" to iconB64,
                )
            )
            if (result.size >= MAX_APPS) break
        }
        return result
    }
}

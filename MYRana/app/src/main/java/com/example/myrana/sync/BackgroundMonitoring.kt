package com.example.myrana.sync

import android.content.Context
import android.util.Log
import com.example.myrana.enforcement.BlocklistCatalogLoader
import com.example.myrana.enforcement.PolicyFilterCache
import com.example.myrana.permissions.ChildProjectRuntime
import com.example.myrana.permissions.SystemPermissions
import com.example.myrana.service.ParentSyncService
import com.example.myrana.session.ChildSession
import com.example.myrana.worker.MonitoringScheduler

/**
 * مزامنة وحظر ومراقبة Chrome — تُشغَّل فقط عندما يمنح أندرويد الصلاحيات فعلياً.
 */
object BackgroundMonitoring {

    private const val TAG = "BackgroundMonitoring"

    fun prepareCaches(context: Context) {
        val app = context.applicationContext
        PolicyFilterCache.loadFromPrefs(app)
        PolicyFilterCache.loadBuiltInSafetyKeywords()
        BlocklistCatalogLoader.prepare(app)
    }

    fun ensureRunning(context: Context) {
        if (!ChildSession.isSetupComplete(context)) return
        val snap = SystemPermissions.readSnapshot(context)
        if (!snap.mandatoryReady) {
            Log.w(TAG, "skipped — usage=${snap.usage} a11y=${snap.accessibility} (grant in Android settings)")
            return
        }

        val app = context.applicationContext
        prepareCaches(app)

        MonitoringScheduler.schedule(app)
        MonitoringScheduler.runOnceNow(app)
        MonitoringScheduler.scheduleBackgroundLoop(app, 1)

        startForegroundServiceSafe(app, snap)

        Log.i(
            TAG,
            "running — usage=${snap.usage} a11y=${snap.accessibility} " +
                "notif=${snap.notifications} battery=${snap.battery}",
        )
    }

    private fun startForegroundServiceSafe(context: Context, snap: SystemPermissions.Snapshot) {
        if (!snap.notifications) {
            Log.w(TAG, "foreground service skipped — grant notifications in Android for fastest blocking")
            return
        }
        try {
            ParentSyncService.start(context)
        } catch (e: Exception) {
            Log.w(TAG, "foreground service not started: ${e.message}")
        }
    }

    fun canRunForegroundService(context: Context): Boolean =
        SystemPermissions.readSnapshot(context).notifications

    fun isOperational(context: Context): Boolean =
        ChildProjectRuntime.isMonitoringOperational(context)
}

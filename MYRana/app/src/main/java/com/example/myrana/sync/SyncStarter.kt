package com.example.myrana.sync

import android.content.Context
import com.example.myrana.permissions.ChildProjectRuntime
import com.example.myrana.permissions.SystemPermissions

/**
 * تشغيل المزامنة عند جاهزية الصلاحيات الفعلية من أندرويد.
 */
object SyncStarter {

    fun startIfReady(context: Context) {
        if (!ChildProjectRuntime.isMonitoringOperational(context)) return
        BackgroundMonitoring.ensureRunning(context)
    }

    fun canRunForegroundService(context: Context): Boolean =
        SystemPermissions.readSnapshot(context).notifications
}

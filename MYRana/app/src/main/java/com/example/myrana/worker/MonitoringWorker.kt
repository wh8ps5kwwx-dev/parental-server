package com.example.myrana.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myrana.data.repo.OutboxRepository
import com.example.myrana.data.repo.PolicyRepository
import com.example.myrana.device.DeviceIdentity
import com.example.myrana.enforcement.EnforcementEngine
import com.example.myrana.permissions.ChildProjectRuntime
import com.example.myrana.session.ChildSession
import com.example.myrana.sync.BackgroundMonitoring
import com.example.myrana.sync.ScreenTimeSyncHelper
import com.example.myrana.sync.UsageUploadHelper

/**
 * WorkManager — مزامنة سياسة الحظر + تطبيق الحظر + رفع الاستخدام.
 * الحظر السريع (كل ثانيتين) عبر [ParentSyncService] إن وُجد إذن الإشعارات.
 */
class MonitoringWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!ChildProjectRuntime.isMonitoringOperational(applicationContext)) {
            return Result.success()
        }
        BackgroundMonitoring.prepareCaches(applicationContext)

        val deviceId = DeviceIdentity.childDeviceId(applicationContext)
        val childCode = ChildSession.childCode(applicationContext) ?: deviceId

        return try {
            PolicyRepository.get(applicationContext).syncWithServer(deviceId)

            val engine = EnforcementEngine.get(applicationContext)
            engine.refreshFromServer(deviceId)
            engine.tick()

            UsageUploadHelper.uploadPeriodicIfDue(applicationContext, childCode)
            ScreenTimeSyncHelper.syncIfDue(applicationContext)
            OutboxRepository.get(applicationContext).flushPending()

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

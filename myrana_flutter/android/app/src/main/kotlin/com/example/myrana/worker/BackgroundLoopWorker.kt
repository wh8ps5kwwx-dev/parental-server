package com.example.myrana.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myrana.core.ChildContextStore
import com.example.myrana.enforcement.EnforcementEngine

/**
 * حلقة خلفية قصيرة (كل ~2 دقيقة) — تكمّل إذا توقفت الخدمة الأمامية.
 */
class BackgroundLoopWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val childCode = ChildContextStore.getChildCode(applicationContext)
        if (childCode.isBlank()) return Result.success()

        val engine = EnforcementEngine.get(applicationContext)
        engine.ensureInitializedCaches()
        return try {
            engine.syncFromServerIfDue(force = false)
            engine.tick()
            // جدولة نفسها مرة أخرى (Loop)
            MonitoringScheduler.scheduleBackgroundLoop(applicationContext, 2)
            Result.success()
        } catch (_: Exception) {
            MonitoringScheduler.scheduleBackgroundLoop(applicationContext, 2)
            Result.retry()
        }
    }
}


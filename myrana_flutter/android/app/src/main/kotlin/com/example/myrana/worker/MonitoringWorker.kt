package com.example.myrana.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.myrana.core.ChildContextStore
import com.example.myrana.enforcement.EnforcementEngine

class MonitoringWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val childCode = ChildContextStore.getChildCode(applicationContext)
        if (childCode.isBlank()) return Result.success()

        val engine = EnforcementEngine.get(applicationContext)
        engine.ensureInitializedCaches()
        try {
            engine.syncFromServerIfDue(force = false)
            engine.tick()
        } catch (_: Exception) {
            return Result.retry()
        }
        return Result.success()
    }
}


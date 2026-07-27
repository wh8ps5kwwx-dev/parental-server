package com.example.myrana.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object MonitoringScheduler {

    private const val PERIODIC_NAME = "myrana_child_monitoring_15m"
    private const val IMMEDIATE_NAME = "myrana_child_monitoring_now"
    private const val LOOP_NAME = "myrana_child_background_loop"

    fun schedule(context: Context) {
        val periodic = PeriodicWorkRequestBuilder<MonitoringWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodic,
        )
        scheduleBackgroundLoop(context, 1)
    }

    fun runOnceNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<MonitoringWorker>().build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            IMMEDIATE_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * حلقة خلفية قصيرة (تكمّل عندما يتوقف Foreground service).
     */
    fun scheduleBackgroundLoop(context: Context, delayMinutes: Long) {
        val safeDelay = delayMinutes.coerceAtLeast(1)
        val request = OneTimeWorkRequestBuilder<BackgroundLoopWorker>()
            .setInitialDelay(safeDelay, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            LOOP_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }
}


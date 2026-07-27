package com.example.myrana.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.myrana.enforcement.EnforcementEngine
import com.example.myrana.worker.MonitoringScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * خدمة أمامية (Foreground) لتشغيل:
 * - tick للحظر + وقت الشاشة
 * - مزامنة/رفع ضمن محرك EnforcementEngine
 *
 * (WorkManager كنسخة احتياطية عند توقف الخدمة)
 */
class ForegroundMonitorService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private var tickJob: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            tickJob?.cancel()
            job.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        NotificationUtil.ensureChannel(this)
        startForeground(NotificationUtil.NOTIFICATION_ID, NotificationUtil.buildForegroundNotification(this))

        MonitoringScheduler.schedule(applicationContext)
        val engine = EnforcementEngine.get(applicationContext)
        engine.ensureInitializedCaches()
        scope.launch { engine.syncFromServerIfDue(force = true) }

        if (tickJob?.isActive != true) {
            tickJob = scope.launch {
                while (isActive) {
                    engine.tick()
                    delay(2_000L)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        tickJob?.cancel()
        job.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_STOP = "com.example.myrana.action.STOP_FOREGROUND_MONITOR"

        fun start(context: Context) {
            val i = Intent(context, ForegroundMonitorService::class.java)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, ForegroundMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            // لا يلزم startForegroundService هنا لأننا فقط نطلب الإيقاف
            ContextCompat.startForegroundService(context, i)
        }
    }
}


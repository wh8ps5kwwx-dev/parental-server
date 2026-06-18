package com.example.myrana.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.myrana.R
import com.example.myrana.enforcement.EnforcementEngine
import com.example.myrana.permissions.ChildProjectRuntime
import com.example.myrana.sync.BackgroundMonitoring
import com.example.myrana.sync.SyncOrchestrator
import com.example.myrana.ui.academy.AcademyMenuActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * خدمة أمامية (Foreground) لمزامنة سياسة الحظر مع السيرفر كل دقيقة.
 *
 * - تظهر إشعاراً دائماً (مطلوب من أندرويد 8+) — لذلك نطلب POST_NOTIFICATIONS مرة واحدة عند التسجيل.
 * - الحلقة لا تتوقف عند فشل الشبكة؛ تعيد المحاولة بعد [SYNC_INTERVAL_MS].
 * - الضغط على الإشعار يفتح [AcademyMenuActivity] وليس شاشة الإعدادات.
 */
class ParentSyncService : Service() {

    /** نطاق Coroutine مرتبط بعمر الخدمة. */
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.Main.immediate)
    private var syncJob: Job? = null
    private var enforceJob: Job? = null

    /** خدمة غير مربوطة (Bound) — لا حاجة لـ Binder. */
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            syncJob?.cancel()
            enforceJob?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        ensureChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        BackgroundMonitoring.prepareCaches(applicationContext)

        val engine = EnforcementEngine.get(applicationContext)

        if (syncJob?.isActive != true) {
            syncJob = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    SyncOrchestrator.runChildCycle(applicationContext)
                    delay(SYNC_INTERVAL_MS)
                }
            }
        }

        if (enforceJob?.isActive != true) {
            enforceJob = scope.launch(Dispatchers.IO) {
                while (isActive) {
                    engine.tick()
                    delay(ENFORCE_INTERVAL_MS)
                }
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // الطفل أغلق التطبيق — المراقبة تستمر (حتى بدون اللعب)
        ChildProjectRuntime.activateMonitoring(applicationContext)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        syncJob?.cancel()
        enforceJob?.cancel()
        job.cancel()
        ChildProjectRuntime.activateMonitoring(applicationContext)
        super.onDestroy()
    }

    /** إنشاء قناة إشعارات (إلزامي من API 26). */
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_sync_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_sync_description)
        }
        manager.createNotificationChannel(channel)
    }

    /** إشعار دائم — بدون زر إيقاف (المراقبة لا تتوقف عند عدم اللعب). */
    private fun buildNotification(): Notification {
        val piFlags = pendingIntentFlags()
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, AcademyMenuActivity::class.java),
            piFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notification_sync_title))
            .setContentText(getString(R.string.notification_sync_text))
            .setContentIntent(pending)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun pendingIntentFlags(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
    }

    companion object {
        const val ACTION_STOP = "com.example.myrana.action.SYNC_STOP"

        private const val CHANNEL_ID = "myrana_parent_sync"
        private const val NOTIFICATION_ID = 7101
        /** فاصل المزامنة: 60 ثانية. */
        private const val SYNC_INTERVAL_MS = 60_000L
        private const val ENFORCE_INTERVAL_MS = 2_000L

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, ParentSyncService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ParentSyncService::class.java))
        }
    }
}

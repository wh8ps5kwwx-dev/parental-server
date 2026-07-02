package com.example.myrana.screentime

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * إذا لم ترد الأم خلال مهلة بعد التنبيه — يُفعَّل التجميد تلقائياً.
 */
object ParentResponseWatchdog {

    private const val YELLOW_TIMEOUT_MS = 5L * 60L * 1000L
    private const val RED_TIMEOUT_MS = 3L * 60L * 1000L

    private data class PendingWatch(
        val packageName: String,
        val policy: ScreenTimePolicy,
        val totalSeconds: Long,
        val deadlineMs: Long,
    )

    private val pending = ConcurrentHashMap<String, PendingWatch>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun schedule(
        context: Context,
        packageName: String,
        level: UsageLevel,
        totalSeconds: Long,
        policy: ScreenTimePolicy,
    ) {
        if (level == UsageLevel.GREEN || level == UsageLevel.BLOCKED) return
        val timeout = if (level == UsageLevel.RED) RED_TIMEOUT_MS else YELLOW_TIMEOUT_MS
        val deadline = System.currentTimeMillis() + timeout
        pending[packageName] = PendingWatch(packageName, policy, totalSeconds, deadline)
        scope.launch {
            delay(timeout)
            val watch = pending[packageName] ?: return@launch
            if (watch.deadlineMs != deadline) return@launch
            pending.remove(packageName)
            withContext(Dispatchers.IO) {
                ScreenTimeEnforcer(context.applicationContext).enforceTimeLimit(
                    packageName,
                    totalSeconds,
                    policy,
                )
            }
        }
    }

    fun onParentResponded() {
        pending.clear()
    }

    fun cancelForPackage(packageName: String) {
        pending.remove(packageName)
    }
}

package com.example.myrana.screentime

import android.content.Context
import com.example.myrana.enforcement.MonitoredAppRegistry
import kotlinx.coroutines.runBlocking

/**
 * يتابع التطبيق في المقدمة ويحسب مدة الاستخدام اليومية لكل تطبيق مراقَب.
 */
class ScreenTimeTracker private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val repo = ScreenTimeRepository.get(appContext)
    private val enforcer = ScreenTimeEnforcer(appContext)

    private var lastForeground: String? = null
    private var lastTickMs = System.currentTimeMillis()
    private var pendingSeconds = 0L
    private var lastPersistMs = 0L
    private val memoryToday = mutableMapOf<String, Long>()
    private val appsOpenedToday = mutableSetOf<String>()
    private var memoryLoaded = false

    fun tick(foregroundPackage: String?) {
        if (!memoryLoaded) {
            runBlocking {
                memoryToday.clear()
                memoryToday.putAll(repo.todayMap())
                appsOpenedToday.clear()
                appsOpenedToday.addAll(memoryToday.filter { it.value > 0L }.keys)
                memoryLoaded = true
            }
        }

        val now = System.currentTimeMillis()
        val deltaSec = ((now - lastTickMs) / 1000L).coerceAtLeast(0L)
        lastTickMs = now

        val fg = foregroundPackage?.takeIf { it.isNotBlank() }
        val policy = ScreenTimePolicyStore.load(appContext)

        if (fg != null && fg != lastForeground && policy.maxOpenApps > 0) {
            val key = fg.lowercase()
            if (!MonitoredAppRegistry.isNeverBlockPackage(fg) &&
                !fg.startsWith("com.example.myrana") &&
                policy.isMonitored(fg) &&
                !appsOpenedToday.contains(key)
            ) {
                if (appsOpenedToday.size >= policy.maxOpenApps) {
                    enforcer.enforceMaxAppsOpen(fg, policy)
                    lastForeground = null
                    return
                }
                appsOpenedToday.add(key)
            }
        }
        if (fg != null && fg == lastForeground && deltaSec > 0L) {
            accumulate(fg, deltaSec, policy)
        } else if (lastForeground != null && deltaSec > 0L && fg != lastForeground) {
            accumulate(lastForeground!!, deltaSec, policy)
        }
        lastForeground = fg

        if (fg != null && policy.isMonitored(fg) && !policy.isUnlimited(fg)) {
            val total = totalSeconds(fg)
            val level = levelFor(total, policy)
            enforcer.onUsageUpdate(fg, total, level, policy)
            if (ScreenTimeSleepHelper.isSleepTime(policy, now)) {
                enforcer.enforceSleepBlock(fg)
            } else if (level == UsageLevel.BLOCKED) {
                enforcer.enforceTimeLimit(fg, total, policy)
            }
        }

        if (now - lastPersistMs >= PERSIST_INTERVAL_MS && pendingSeconds > 0L && lastForeground != null) {
            flushPending(lastForeground!!)
            lastPersistMs = now
        }
    }

    private fun accumulate(pkg: String, delta: Long, policy: ScreenTimePolicy) {
        if (pkg.startsWith("com.example.myrana")) return
        if (!policy.isMonitored(pkg) || policy.isUnlimited(pkg)) return
        val key = pkg.lowercase()
        memoryToday[key] = (memoryToday[key] ?: 0L) + delta
        pendingSeconds += delta
    }

    private fun totalSeconds(pkg: String): Long =
        memoryToday[pkg.lowercase()] ?: 0L

    private fun flushPending(pkg: String) {
        val sec = pendingSeconds
        if (sec <= 0L) return
        pendingSeconds = 0L
        runBlocking { repo.addSeconds(pkg, sec) }
    }

    private fun levelFor(seconds: Long, policy: ScreenTimePolicy): UsageLevel = when {
        seconds >= policy.blockSeconds() -> UsageLevel.BLOCKED
        seconds >= policy.strongWarnSeconds() -> UsageLevel.RED
        seconds >= policy.warnSeconds() -> UsageLevel.YELLOW
        else -> UsageLevel.GREEN
    }

    companion object {
        private const val PERSIST_INTERVAL_MS = 15_000L

        @Volatile
        private var instance: ScreenTimeTracker? = null

        fun get(context: Context): ScreenTimeTracker {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: ScreenTimeTracker(app).also { instance = it }
            }
        }
    }
}

package com.example.myrana.enforcement

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.data.repo.OutboxRepository
import com.example.myrana.data.repo.PolicyRepository
import com.example.myrana.device.DeviceIdentity
import com.example.myrana.session.ChildSession
import com.example.myrana.screentime.ScreenTimeTracker
import com.example.myrana.ui.BlockWarningActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * تطبيق الحظر الفعلي وتجميع **استخدام الجهاز** (وقت كل تطبيق) للتقرير الأسبوعي.
 */
class EnforcementEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val repo = PolicyRepository.get(appContext)
    private val outbox = OutboxRepository.get(appContext)

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    @Volatile
    private var scheduleBlocked: Set<String> = emptySet()

    private val usageSeconds = mutableMapOf<String, Long>()
    private var lastForeground: String? = null
    private var lastTickMs = System.currentTimeMillis()
    private val lastEnforcedAt = mutableMapOf<String, Long>()
    private val alertScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun refreshFromServer(deviceId: String) = withContext(Dispatchers.IO) {
        val (sites, apps) = repo.localSnapshot()
        blockedPackages = apps.map { it.lowercase() }.toSet()
        com.example.myrana.sync.BackgroundMonitoring.prepareCaches(appContext)
        PolicyFilterCache.updateHosts(sites)
        scheduleBlocked = try {
            NetworkModule.fetchActiveSchedulePackages(deviceId)
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun tick() {
        if (!UsageAccessHelper.hasUsageAccess(appContext)) return

        val fg = ForegroundAppDetector.getForegroundPackage(appContext)
        ScreenTimeTracker.get(appContext).tick(fg)
        if (fg == null) return

        val now = System.currentTimeMillis()
        val deltaSec = ((now - lastTickMs) / 1000L).coerceAtLeast(0L)
        if (fg == lastForeground && deltaSec > 0L && !isOwnPackage(fg)) {
            usageSeconds[fg] = (usageSeconds[fg] ?: 0L) + deltaSec
        }
        lastTickMs = now
        lastForeground = fg

        if (shouldBlock(fg)) {
            enforceBlock(fg)
        }
    }

    suspend fun flushUsage(childCode: String) = withContext(Dispatchers.IO) {
        if (usageSeconds.isEmpty()) return@withContext
        val snapshot = usageSeconds.toMap()
        usageSeconds.clear()
        outbox.submitUsage(childCode, snapshot)
    }

    private fun shouldBlock(packageName: String): Boolean {
        if (isOwnPackage(packageName)) return false
        val pkg = packageName.lowercase()
        return pkg in blockedPackages || pkg in scheduleBlocked
    }

    private fun isOwnPackage(packageName: String): Boolean =
        packageName.startsWith("com.example.myrana")

    private fun enforceBlock(blockedPkg: String) {
        val now = System.currentTimeMillis()
        val last = lastEnforcedAt[blockedPkg] ?: 0L
        if (now - last < ENFORCE_COOLDOWN_MS) return
        lastEnforcedAt[blockedPkg] = now

        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(blockedPkg)

        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        appContext.startActivity(home)

        val warn = Intent(appContext, BlockWarningActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BlockWarningActivity.EXTRA_PACKAGE, blockedPkg)
        }
        appContext.startActivity(warn)
        notifyParentBlocked(blockedPkg)
    }

    private fun notifyParentBlocked(packageName: String) {
        val childCode = ChildSession.childCode(appContext) ?: return
        val label = try {
            val pm = appContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
        val message = "محاولة فتح تطبيق محظور: $label ($packageName)"
        alertScope.launch {
            NetworkModule.postAlertSync(childCode, message)
        }
    }

    companion object {
        private const val ENFORCE_COOLDOWN_MS = 3_000L

        @Volatile
        private var instance: EnforcementEngine? = null

        fun get(context: Context): EnforcementEngine {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: EnforcementEngine(app).also { instance = it }
            }
        }
    }
}

package com.example.myrana.enforcement

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.data.repo.PolicyRepository
import com.example.myrana.data.repo.UsageSyncRepository
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
 * يُحفظ محلياً في Room حتى عند انقطاع النت.
 */
class EnforcementEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val repo = PolicyRepository.get(appContext)
    private val usageSync = UsageSyncRepository.get(appContext)

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    @Volatile
    private var scheduleBlocked: Set<String> = emptySet()

    private val pendingLocal = mutableMapOf<String, Long>()
    private var lastForeground: String? = null
    private var lastTickMs = System.currentTimeMillis()
    private var lastLocalPersistMs = 0L
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

        val now = System.currentTimeMillis()
        val deltaSec = ((now - lastTickMs) / 1000L).coerceAtLeast(0L)
        lastTickMs = now

        if (deltaSec > 0L) {
            when {
                fg != null && fg == lastForeground && !isOwnPackage(fg) -> {
                    pendingLocal[fg] = (pendingLocal[fg] ?: 0L) + deltaSec
                }
                lastForeground != null && !isOwnPackage(lastForeground!!) -> {
                    val prev = lastForeground!!
                    pendingLocal[prev] = (pendingLocal[prev] ?: 0L) + deltaSec
                }
            }
        }
        lastForeground = fg

        if (now - lastLocalPersistMs >= LOCAL_PERSIST_MS && pendingLocal.isNotEmpty()) {
            persistPendingLocal()
            lastLocalPersistMs = now
        }

        if (fg != null && shouldBlock(fg)) {
            enforceBlock(fg)
        }
    }

    /** حفظ الاستهلاك المعلّق محلياً دون محاولة رفع. */
    suspend fun persistPendingUsage() = withContext(Dispatchers.IO) {
        persistPendingLocalBlocking()
    }

    suspend fun flushUsage(childCode: String) = withContext(Dispatchers.IO) {
        persistPendingLocalBlocking()
        usageSync.trySync(childCode)
    }

    private fun persistPendingLocal() {
        if (pendingLocal.isEmpty()) return
        val snapshot = pendingLocal.toMap()
        pendingLocal.clear()
        alertScope.launch {
            usageSync.mergeUsage(snapshot)
        }
    }

    private suspend fun persistPendingLocalBlocking() {
        if (pendingLocal.isEmpty()) return
        val snapshot = pendingLocal.toMap()
        pendingLocal.clear()
        usageSync.mergeUsage(snapshot)
    }

    private fun shouldBlock(packageName: String): Boolean {
        if (MonitoredAppRegistry.isNeverBlockPackage(packageName)) return false
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
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(packageName, 0)
            }
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
        private const val LOCAL_PERSIST_MS = 60_000L

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

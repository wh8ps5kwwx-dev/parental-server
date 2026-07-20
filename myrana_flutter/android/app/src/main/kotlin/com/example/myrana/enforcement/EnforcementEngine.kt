package com.example.myrana.enforcement

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import com.example.myrana.core.ChildContextStore
import com.example.myrana.network.NetworkClientLite
import com.example.myrana.network.NetworkClientLite.UsageEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * engine مبسطة:
 * - تحديث policy من السيرفر
 * - حظر تطبيقات محظورة (kill + HOME)
 * - حظر/تقييد وقت الشاشة عبر إجمالي seconds محلي (delta based)
 * - رفع الاستخدام محلياً (delta flush)
 */
class EnforcementEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val usageDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Volatile
    private var screenTimePolicy: ScreenTimePolicy = ScreenTimePolicyDefaults.defaultPolicy()

    private val usageTodaySeconds: MutableMap<String, Long> = ConcurrentHashMap()
    private val pendingUsageDeltas: MutableMap<String, Long> = ConcurrentHashMap()

    @Volatile
    private var lastUploadAtMs: Long = 0L
    private var lastDayKey: String = dayKey()

    private var lastTickMs: Long = System.currentTimeMillis()
    private var lastForegroundPkg: String? = null

    private val lastEnforcedAt = ConcurrentHashMap<String, Long>()
    @Volatile
    private var scheduleBlocked: Set<String> = emptySet()

    @Volatile
    private var lastPolicySyncAtMs: Long = 0L

    // فواصل مبدئية — يمكن تعديلها حسب الحاجة
    private val enforceCooldownMs = 3_000L
    private val policySyncIntervalMs = 60_000L
    private val uploadIntervalMs = 10 * 60_000L // 10 دقائق

    suspend fun syncFromServerIfDue(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPolicySyncAtMs < policySyncIntervalMs) return
        val childCode = ChildContextStore.getChildCode(appContext)
        if (childCode.isBlank()) return

        // الكاش (blocked hosts/packages/video keywords)
        PolicyFilterCache.updateFromServer(appContext, childCode)
        // schedule freeze/block
        scheduleBlocked = NetworkClientLite.fetchActiveSchedules(childCode).packages.map { it.lowercase() }.toSet()
        // screen-time policy
        screenTimePolicy = NetworkClientLite.fetchScreenTimePolicy(childCode)

        lastPolicySyncAtMs = now
    }

    fun tick() {
        // tick سريع — لا نريد await داخل حلقة 2 ثانية.
        // سنقوم بالمزامنة/الرفع عبر كوروتين عندما يحين الوقت.
        val now = System.currentTimeMillis()
        val deltaSec = max(0L, (now - lastTickMs) / 1000L)
        lastTickMs = now

        // تحديث dayKey
        val dayKeyNow = dayKey()
        if (dayKeyNow != lastDayKey) {
            usageTodaySeconds.clear()
            pendingUsageDeltas.clear()
            lastDayKey = dayKeyNow
        }

        val fgPkg = ForegroundAppDetector.getForegroundPackage(appContext)?.lowercase()?.trim()
        // تراكم وقت آخر package كانت في المقدمة
        val prevPkg = lastForegroundPkg
        if (prevPkg != null && deltaSec > 0) {
            if (screenTimePolicy.isMonitored(prevPkg)) {
                // استخدام هذا الخريطة فقط لأغراض إنفاذ وقت الشاشة
                usageTodaySeconds[prevPkg] = (usageTodaySeconds[prevPkg] ?: 0L) + deltaSec
            }
            // رفع الاستخدام للتقارير: نريد يشمل حتى التطبيقات التعليمية (unlimited)
            if (!MonitoredAppRegistry.isNeverBlockPackage(prevPkg)) {
                pendingUsageDeltas[prevPkg] = (pendingUsageDeltas[prevPkg] ?: 0L) + deltaSec
            }
        }

        lastForegroundPkg = fgPkg

        // enforce سريع
        if (fgPkg != null) {
            if (shouldBlockApp(fgPkg)) {
                enforceBlockApp(fgPkg, label = fgPkg, reason = BlockWarningActivity.REASON_SITE)
            } else {
                enforceScreenTimeIfNeeded(fgPkg)
            }
        }

        // policy sync / upload (غير متزامن)
        val now2 = System.currentTimeMillis()
        if (now2 - lastPolicySyncAtMs >= policySyncIntervalMs) {
            scope.launch { syncFromServerIfDue(force = false) }
        }
        if (pendingUsageDeltas.isNotEmpty() && now2 - lastUploadAtMs >= uploadIntervalMs) {
            scope.launch { flushPendingUsage() }
        }
    }

    fun blockPackageNow(packageName: String): Boolean {
        val pkg = packageName.lowercase().trim()
        if (pkg.isBlank()) return false
        enforceBlockApp(pkg, label = pkg, reason = BlockWarningActivity.REASON_SITE)
        return true
    }

    /**
     * يُستدعى من AccessibilityService عند اكتشاف blockedHost/keyword.
     */
    fun enforceFromAccessibility(packageName: String, label: String, reason: String) {
        val pkg = packageName.lowercase().trim()
        if (pkg.isBlank()) return
        enforceBlockApp(pkg, label = label, reason = reason)
    }

    private fun shouldBlockApp(packageName: String): Boolean {
        if (MonitoredAppRegistry.isNeverBlockPackage(packageName)) return false
        val blocked = PolicyFilterCache.blockedPackages().contains(packageName.lowercase())
        val scheduled = scheduleBlocked.contains(packageName.lowercase())
        return blocked || scheduled
    }

    private fun enforceScreenTimeIfNeeded(packageName: String) {
        if (screenTimePolicy.enabled == false) return
        if (MonitoredAppRegistry.isNeverBlockPackage(packageName)) return
        if (!screenTimePolicy.isMonitored(packageName)) return

        val totalSec = usageTodaySeconds[packageName] ?: 0L
        val nowMs = System.currentTimeMillis()

        // وقت النوم
        if (isInSleepWindow(screenTimePolicy.sleepStart, screenTimePolicy.sleepEnd, nowMs)) {
            if (!screenTimePolicy.allowDuringSleep) {
                enforceBlockApp(packageName, label = packageName, reason = BlockWarningActivity.REASON_TIME)
            }
            return
        }

        if (totalSec >= screenTimePolicy.blockSeconds()) {
            enforceBlockApp(packageName, label = packageName, reason = BlockWarningActivity.REASON_TIME)
        }
    }

    private fun isInSleepWindow(start: String, end: String, nowMs: Long): Boolean {
        // يتعامل مع نافذة تمتد عبر منتصف الليل
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
        val nowMinutes = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val (sh, sm) = start.split(":").takeIf { it.size == 2 }?.map { it.trim().toIntOrNull() ?: 0 } ?: listOf(0, 0)
        val (eh, em) = end.split(":").takeIf { it.size == 2 }?.map { it.trim().toIntOrNull() ?: 0 } ?: listOf(0, 0)
        val startMin = sh * 60 + sm
        val endMin = eh * 60 + em
        return if (startMin <= endMin) {
            nowMinutes in startMin..endMin
        } else {
            nowMinutes >= startMin || nowMinutes <= endMin
        }
    }

    private fun enforceBlockApp(packageName: String, label: String, reason: String) {
        val now = System.currentTimeMillis()
        val last = lastEnforcedAt[packageName] ?: 0L
        if (now - last < enforceCooldownMs) return
        lastEnforcedAt[packageName] = now

        try {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(packageName)
        } catch (_: Exception) {
        }

        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        appContext.startActivity(home)

        val warn = Intent(appContext, BlockWarningActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BlockWarningActivity.EXTRA_PACKAGE, label)
            putExtra(BlockWarningActivity.EXTRA_REASON, reason)
        }
        try {
            appContext.startActivity(warn)
        } catch (_: Exception) {
        }

        // تنبيه ولي الأمر (اختياري لكن مفيد لواجهة Alerts)
        val childCode = ChildContextStore.getChildCode(appContext)
        if (childCode.isNotBlank()) {
            scope.launch {
                val appLabel = label
                val msg = when (reason) {
                    BlockWarningActivity.REASON_TIME -> "تم تطبيق حد وقت الشاشة على: $appLabel"
                    else -> "محاولة فتح تطبيق محظور: $appLabel"
                }
                NetworkClientLite.addAlert(childCode, msg)
            }
        }
    }

    private fun flushPendingUsage() {
        val childCode = ChildContextStore.getChildCode(appContext)
        if (childCode.isBlank()) return

        val day = usageDateFormat.format(Date())
        val snapshot = pendingUsageDeltas.toMap()
        val entries = snapshot.mapNotNull { (pkg, sec) ->
            if (sec <= 0L) return@mapNotNull null
            UsageEntry(day = day, packageName = pkg, seconds = sec)
        }
        if (entries.isEmpty()) return

        val ok = NetworkClientLite.uploadUsageDelta(childCode, entries)
        if (!ok) return

        pendingUsageDeltas.clear()
        lastUploadAtMs = System.currentTimeMillis()
    }

    fun ensureInitializedCaches() {
        // تحميل defaults/محفوظ (مرة خفيفة)
        PolicyFilterCache.loadFromPrefs(appContext)
        if (PolicyFilterCache.videoKeywords().isEmpty()) {
            PolicyFilterCache.loadDefaultsFromAsset(appContext)
        }
    }

    private fun dayKey(): String = usageDateFormat.format(Date())

    companion object {
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


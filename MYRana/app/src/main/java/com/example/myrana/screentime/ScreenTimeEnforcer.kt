package com.example.myrana.screentime

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.session.ChildSession
import com.example.myrana.ui.ScreenTimeLimitActivity
import com.example.myrana.ui.ScreenTimeWarningActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ScreenTimeEnforcer(private val context: Context) {

    private val appContext = context.applicationContext
    private val repo = ScreenTimeRepository.get(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastLevelShown = mutableMapOf<String, UsageLevel>()
    private val lastEnforcedAt = mutableMapOf<String, Long>()

    fun onUsageUpdate(
        packageName: String,
        totalSeconds: Long,
        level: UsageLevel,
        policy: ScreenTimePolicy,
    ) {
        ScreenTimeNotificationHelper.update(appContext, packageName, totalSeconds, level, policy)
        val prev = lastLevelShown[packageName]
        if (level != prev && level != UsageLevel.GREEN) {
            lastLevelShown[packageName] = level
            showWarning(packageName, level, totalSeconds, policy)
            logAndNotifyParent(packageName, level, totalSeconds, policy)
        } else if (level == UsageLevel.GREEN) {
            lastLevelShown.remove(packageName)
        }
    }

    fun enforceTimeLimit(packageName: String, totalSeconds: Long, policy: ScreenTimePolicy) {
        val now = System.currentTimeMillis()
        val last = lastEnforcedAt[packageName] ?: 0L
        if (now - last < ENFORCE_COOLDOWN_MS) return
        lastEnforcedAt[packageName] = now

        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(packageName)

        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        appContext.startActivity(home)

        val limit = Intent(appContext, ScreenTimeLimitActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ScreenTimeLimitActivity.EXTRA_PACKAGE, packageName)
            putExtra(ScreenTimeLimitActivity.EXTRA_MINUTES, policy.blockMinutes)
        }
        appContext.startActivity(limit)

        scope.launch {
            val label = appLabel(packageName)
            val msg = "تجاوز الحد: $label — ${totalSeconds / 60} دقيقة (الحد ${policy.blockMinutes} د)"
            repo.logEvent(EVENT_BLOCKED, packageName, msg, totalSeconds)
            notifyParent(msg)
        }
        ParentResponseWatchdog.cancelForPackage(packageName)
    }

    fun enforceMaxAppsOpen(packageName: String, policy: ScreenTimePolicy) {
        val now = System.currentTimeMillis()
        val key = "maxapps:$packageName"
        val last = lastEnforcedAt[key] ?: 0L
        if (now - last < ENFORCE_COOLDOWN_MS) return
        lastEnforcedAt[key] = now

        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        am.killBackgroundProcesses(packageName)

        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        appContext.startActivity(home)

        val limit = Intent(appContext, ScreenTimeLimitActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ScreenTimeLimitActivity.EXTRA_PACKAGE, packageName)
            putExtra(ScreenTimeLimitActivity.EXTRA_MINUTES, policy.blockMinutes)
        }
        appContext.startActivity(limit)

        scope.launch {
            val label = appLabel(packageName)
            val msg = "تجاوز عدد التطبيقات المفتوحة اليوم: $label (الحد ${policy.maxOpenApps})"
            repo.logEvent(EVENT_MAX_APPS, packageName, msg, 0L)
            notifyParent(msg)
        }
    }

    fun enforceSleepBlock(packageName: String) {
        val now = System.currentTimeMillis()
        val key = "sleep:$packageName"
        val last = lastEnforcedAt[key] ?: 0L
        if (now - last < ENFORCE_COOLDOWN_MS) return
        lastEnforcedAt[key] = now

        val home = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        appContext.startActivity(home)

        val warn = Intent(appContext, ScreenTimeLimitActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(ScreenTimeLimitActivity.EXTRA_PACKAGE, packageName)
            putExtra(ScreenTimeLimitActivity.EXTRA_REASON, ScreenTimeLimitActivity.REASON_SLEEP)
        }
        appContext.startActivity(warn)
    }

    private fun showWarning(
        packageName: String,
        level: UsageLevel,
        totalSeconds: Long,
        policy: ScreenTimePolicy,
    ) {
        if (level == UsageLevel.BLOCKED) return
        val intent = Intent(appContext, ScreenTimeWarningActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(ScreenTimeWarningActivity.EXTRA_PACKAGE, packageName)
            putExtra(ScreenTimeWarningActivity.EXTRA_LEVEL, level.name)
            putExtra(ScreenTimeWarningActivity.EXTRA_MINUTES_USED, (totalSeconds / 60).toInt())
            putExtra(ScreenTimeWarningActivity.EXTRA_BLOCK_MINUTES, policy.blockMinutes)
        }
        appContext.startActivity(intent)
    }

    private fun logAndNotifyParent(
        packageName: String,
        level: UsageLevel,
        totalSeconds: Long,
        policy: ScreenTimePolicy,
    ) {
        scope.launch {
            val label = appLabel(packageName)
            val minutes = totalSeconds / 60
            val (eventType, msg) = when (level) {
                UsageLevel.YELLOW -> EVENT_WARN to
                    "تنبيه أصفر: $label — $minutes د (الحد الأول ${policy.warnMinutes} د)"
                UsageLevel.RED -> EVENT_STRONG_WARN to
                    "تنبيه أحمر: $label — $minutes د (الحد الثاني ${policy.strongWarnMinutes} د)"
                else -> return@launch
            }
            repo.logEvent(eventType, packageName, msg, totalSeconds)
            notifyParent(msg)
        }
        ParentResponseWatchdog.schedule(appContext, packageName, level, totalSeconds, policy)
    }

    private fun notifyParent(message: String) {
        val childCode = ChildSession.childCode(appContext) ?: return
        scope.launch {
            NetworkModule.postAlertSync(childCode, message)
        }
    }

    private fun appLabel(packageName: String): String = try {
        val pm = appContext.packageManager
        val info = pm.getApplicationInfo(packageName, 0)
        pm.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    companion object {
        const val EVENT_WARN = "screen_warn"
        const val EVENT_STRONG_WARN = "screen_strong_warn"
        const val EVENT_BLOCKED = "screen_blocked"
        const val EVENT_MAX_APPS = "screen_max_apps"
        private const val ENFORCE_COOLDOWN_MS = 5_000L
    }
}

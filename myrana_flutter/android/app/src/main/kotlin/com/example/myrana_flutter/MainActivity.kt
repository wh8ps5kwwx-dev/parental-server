package com.example.myrana_flutter

import android.content.Intent
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.example.myrana.core.ChildContextStore
import com.example.myrana.enforcement.AccessibilityHelper
import com.example.myrana.enforcement.EnforcementEngine
import com.example.myrana.enforcement.PolicyFilterCache
import com.example.myrana.enforcement.UsageAccessHelper
import com.example.myrana.enforcement.UsageStatsCollectorLite
import com.example.myrana.service.ForegroundMonitorService
import com.example.myrana_flutter.native.InstalledAppsCollector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * قنوات MethodChannel — مطابقة لـ lib/native/enforcement_channel.dart
 * مصدر الحظر الوحيد: PolicyFilterCache (نفس ما يقرأه EnforcementEngine + Accessibility).
 */
class MainActivity : FlutterActivity() {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        val messenger = flutterEngine.dartExecutor.binaryMessenger

        MethodChannel(messenger, CHANNEL_ACCESSIBILITY).setMethodCallHandler { call, result ->
            when (call.method) {
                "isEnabled" -> result.success(AccessibilityHelper.isServiceEnabled(this))
                "openSettings" -> result.success(AccessibilityHelper.openSettings(this))
                else -> result.notImplemented()
            }
        }

        MethodChannel(messenger, CHANNEL_USAGE).setMethodCallHandler { call, result ->
            when (call.method) {
                "hasPermission" -> result.success(UsageAccessHelper.hasUsageAccess(this))
                "openSettings" -> result.success(UsageAccessHelper.openSettings(this))
                "queryToday" -> {
                    val map = UsageStatsCollectorLite.queryToday(this)
                    result.success(map.mapValues { (_, v) -> v.toInt() })
                }
                else -> result.notImplemented()
            }
        }

        MethodChannel(messenger, CHANNEL_ENFORCEMENT).setMethodCallHandler { call, result ->
            when (call.method) {
                "setChildContext" -> {
                    val childCode = call.argument<String>("childCode")?.trim().orEmpty()
                    ChildContextStore.setChildCode(this, childCode)
                    result.success(true)
                }
                "blockPackage" -> {
                    val pkg = call.argument<String>("package")?.trim().orEmpty()
                    if (pkg.isEmpty()) {
                        result.success(false)
                    } else {
                        PolicyFilterCache.addBlockedPackage(this, pkg)
                        result.success(EnforcementEngine.get(this).blockPackageNow(pkg))
                    }
                }
                "unblockPackage" -> {
                    val pkg = call.argument<String>("package")?.trim().orEmpty()
                    if (pkg.isEmpty()) {
                        result.success(false)
                    } else {
                        PolicyFilterCache.removeBlockedPackage(this, pkg)
                        result.success(true)
                    }
                }
                "clearBlocked" -> {
                    PolicyFilterCache.clearAll(this)
                    result.success(true)
                }
                "getBlockedPackages" -> {
                    PolicyFilterCache.updateFromPrefsIfNeeded(this)
                    result.success(PolicyFilterCache.blockedPackages().toList())
                }
                "blockHost" -> {
                    val host = call.argument<String>("host")?.trim().orEmpty()
                    if (host.isEmpty()) {
                        result.success(false)
                    } else {
                        PolicyFilterCache.addBlockedHost(this, host)
                        result.success(true)
                    }
                }
                "unblockHost" -> {
                    val host = call.argument<String>("host")?.trim().orEmpty()
                    if (host.isEmpty()) {
                        result.success(false)
                    } else {
                        PolicyFilterCache.removeBlockedHost(this, host)
                        result.success(true)
                    }
                }
                "startForeground" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestPermissions(
                            arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                            REQ_NOTIFICATION,
                        )
                    }
                    ForegroundMonitorService.start(this)
                    result.success(true)
                }
                "stopForeground" -> {
                    ForegroundMonitorService.stop(this)
                    result.success(true)
                }
                "getInstalledApps" -> {
                    result.success(InstalledAppsCollector.collect(this))
                }
                "enforceNow" -> {
                    EnforcementEngine.get(this).tick()
                    result.success(true)
                }
                "syncPolicy" -> {
                    ioScope.launch {
                        val ok = try {
                            EnforcementEngine.get(this@MainActivity).syncFromServerIfDue(force = true)
                            true
                        } catch (_: Exception) {
                            false
                        }
                        runOnUiThread { result.success(ok) }
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    companion object {
        private const val CHANNEL_ACCESSIBILITY = "com.example.myrana/accessibility"
        private const val CHANNEL_USAGE = "com.example.myrana/usage_stats"
        private const val CHANNEL_ENFORCEMENT = "com.example.myrana/enforcement"
        private const val REQ_NOTIFICATION = 42
    }
}

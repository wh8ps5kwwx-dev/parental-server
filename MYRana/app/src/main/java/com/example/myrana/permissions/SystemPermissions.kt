package com.example.myrana.permissions

import android.content.Context
import com.example.myrana.enforcement.AccessibilityHelper
import com.example.myrana.enforcement.UsageAccessHelper

/**
 * قراءة وطلب الصلاحيات **من النظام فقط** — نقطة واحدة لكل التطبيق.
 */
object SystemPermissions {

    data class Snapshot(
        val usage: Boolean,
        val accessibility: Boolean,
        val notifications: Boolean,
        val battery: Boolean,
    ) {
        val mandatoryReady: Boolean get() = usage && accessibility
    }

    fun readSnapshot(context: Context): Snapshot = Snapshot(
        usage = UsageAccessHelper.hasUsageAccess(context),
        accessibility = AccessibilityHelper.isServiceEnabled(context),
        notifications = !PermissionCoordinator.needsPostNotificationsPermission() ||
            PermissionCoordinator.hasNotificationPermission(context),
        battery = BatteryOptimizationHelper.isIgnoringOptimizations(context),
    )

    /**
     * يفتح أول صلاحية ناقصة عبر شاشة/حوار أندرويد.
     * @return [OpenResult] يوضح ما إذا فُتحت شاشة النظام.
     */
    fun openFirstMissing(context: Context): OpenResult {
        val snap = readSnapshot(context)
        return when {
            !snap.usage -> OpenResult.Opened(Kind.USAGE, UsageAccessHelper.openSettings(context))
            !snap.accessibility ->
                OpenResult.Opened(Kind.ACCESSIBILITY, AccessibilityHelper.openServiceSettings(context))
            !snap.battery ->
                OpenResult.Opened(Kind.BATTERY, BatteryOptimizationHelper.openRequestScreen(context))
            else -> OpenResult.NothingMissing
        }
    }

    sealed class OpenResult {
        data class Opened(val kind: Kind, val launched: Boolean) : OpenResult()
        object NothingMissing : OpenResult()
    }

    enum class Kind {
        USAGE,
        ACCESSIBILITY,
        NOTIFICATION,
        BATTERY,
    }

    fun needsRuntimeNotificationRequest(context: Context): Boolean =
        PermissionCoordinator.needsPostNotificationsPermission() &&
            !PermissionCoordinator.hasNotificationPermission(context)
}

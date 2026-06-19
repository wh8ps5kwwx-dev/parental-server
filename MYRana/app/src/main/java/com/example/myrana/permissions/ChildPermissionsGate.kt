package com.example.myrana.permissions

import android.content.Context
import com.example.myrana.enforcement.AccessibilityHelper
import com.example.myrana.enforcement.UsageAccessHelper

/**
 * بوابة الصلاحيات — القراءة من أندرويد عبر [SystemPermissions].
 * يُعاد فتح شاشة الصلاحيات إذا لم تُمنح الإعدادات الإلزامية فعلياً.
 */
object ChildPermissionsGate {

    private const val PREFS = "myrana_child_permissions"
    private const val KEY_FLOW_COMPLETE = "permissions_flow_complete"

    fun hasUsageAccess(context: Context): Boolean =
        SystemPermissions.readSnapshot(context).usage

    fun hasAccessibility(context: Context): Boolean =
        SystemPermissions.readSnapshot(context).accessibility

    /** جاهز للمراقبة الأساسية (حظر التطبيقات) — استخدام على الأقل. */
    fun isMonitoringReady(context: Context): Boolean =
        UsageAccessHelper.hasUsageAccess(context)

    /** مراقبة كاملة — استخدام + وصول. */
    fun isFullMonitoringReady(context: Context): Boolean =
        SystemPermissions.readSnapshot(context).mandatoryReady

    fun reconcileWithSystem(context: Context) {
        if (!UsageAccessHelper.hasUsageAccess(context)) {
            prefs(context).edit().putBoolean(KEY_FLOW_COMPLETE, false).apply()
        }
    }

    fun ensurePermissionsRequiredAfterLink(context: Context) {
        reconcileWithSystem(context)
    }

    fun markPermissionsFlowComplete(context: Context) {
        if (!ChildPermissionEvaluator.canMarkFlowComplete(context)) return
        UsageAccessHelper.markUsageFlowDone(context)
        if (AccessibilityHelper.isServiceEnabled(context)) {
            AccessibilityHelper.markAccessibilityFlowDone(context)
        }
        prefs(context).edit().putBoolean(KEY_FLOW_COMPLETE, true).apply()
    }

    /** اكتمل الإعداد — موافقة + بيانات الاستخدام (الحد الأدنى للأكاديمية والحظر). */
    fun isPermissionsFlowComplete(context: Context): Boolean {
        reconcileWithSystem(context)
        if (!ChildPermissionsConsent.hasUserConsented(context)) return false
        return UsageAccessHelper.hasUsageAccess(context)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

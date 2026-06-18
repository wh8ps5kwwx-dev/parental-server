package com.example.myrana.permissions

import android.content.Context
import com.example.myrana.enforcement.AccessibilityHelper
import com.example.myrana.enforcement.UsageAccessHelper

/**
 * بوابة الصلاحيات — القراءة من أندroid عبر [SystemPermissions].
 * يُعاد فتح شاشة الصلاحيات إذا لم تُمنح الإعدادات الإلزامية فعلياً.
 */
object ChildPermissionsGate {

    private const val PREFS = "myrana_child_permissions"
    private const val KEY_FLOW_COMPLETE = "permissions_flow_complete"

    fun hasUsageAccess(context: Context): Boolean =
        SystemPermissions.readSnapshot(context).usage

    fun hasAccessibility(context: Context): Boolean =
        SystemPermissions.readSnapshot(context).accessibility

    /** جاهز للمراقبة — من النظام مباشرة (استخدام + وصول). */
    fun isMonitoringReady(context: Context): Boolean =
        SystemPermissions.readSnapshot(context).mandatoryReady

    /**
     * يزامن العلم المحلي مع أندرويد — إذا أُزيلت صلاحية من الإعدادات نُعيد طلبها.
     */
    fun reconcileWithSystem(context: Context) {
        val snap = SystemPermissions.readSnapshot(context)
        if (!snap.mandatoryReady) {
            prefs(context).edit().putBoolean(KEY_FLOW_COMPLETE, false).apply()
        }
    }

    /** بعد ربط ولي الأمر — لا نتخطى الصلاحيات إلا إذا أندرويد يمنحها فعلياً. */
    fun ensurePermissionsRequiredAfterLink(context: Context) {
        reconcileWithSystem(context)
    }

    fun markPermissionsFlowComplete(context: Context) {
        if (!ChildPermissionEvaluator.canMarkFlowComplete(context)) return
        UsageAccessHelper.markUsageFlowDone(context)
        AccessibilityHelper.markAccessibilityFlowDone(context)
        prefs(context).edit().putBoolean(KEY_FLOW_COMPLETE, true).apply()
    }

    fun isPermissionsFlowComplete(context: Context): Boolean {
        reconcileWithSystem(context)
        if (!ChildPermissionsConsent.hasUserConsented(context)) return false
        if (!SystemPermissions.readSnapshot(context).mandatoryReady) return false
        return prefs(context).getBoolean(KEY_FLOW_COMPLETE, false)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

package com.example.myrana.permissions

import android.content.Context
import com.example.myrana.enforcement.AccessibilityHelper
import com.example.myrana.enforcement.UsageAccessHelper

/**
 * بوابة الصلاحيات — القراءة من أندرويد عبر [SystemPermissions].
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

    fun markPermissionsFlowComplete(context: Context) {
        if (!ChildPermissionEvaluator.canMarkFlowComplete(context)) return
        UsageAccessHelper.markUsageFlowDone(context)
        AccessibilityHelper.markAccessibilityFlowDone(context)
        prefs(context).edit().putBoolean(KEY_FLOW_COMPLETE, true).apply()
    }

    fun isPermissionsFlowComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_FLOW_COMPLETE, false) &&
            ChildPermissionsConsent.hasUserConsented(context)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

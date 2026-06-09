package com.example.myrana.permissions

import android.content.Context
/** موافقة المستخدم قبل طلب الصلاحيات من النظام. */
object ChildPermissionsConsent {

    private const val PREFS = "myrana_child_permissions"
    private const val KEY_CONSENT_GIVEN = "permissions_consent_given"

    fun hasUserConsented(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CONSENT_GIVEN, false)

    fun markUserConsented(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CONSENT_GIVEN, true)
            .apply()
    }

    fun needsNotificationRequest(context: Context): Boolean =
        SystemPermissions.needsRuntimeNotificationRequest(context)

    /** يفتح أول إعداد نظامي ناقص — يُرجع true إذا فُتحت شاشة النظام. */
    fun openNextMissingSystemPermission(context: Context): Boolean {
        if (!hasUserConsented(context)) return false
        return when (val result = SystemPermissions.openFirstMissing(context)) {
            is SystemPermissions.OpenResult.Opened -> result.launched
            SystemPermissions.OpenResult.NothingMissing -> false
        }
    }
}

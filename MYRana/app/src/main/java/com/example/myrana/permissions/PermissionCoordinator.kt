package com.example.myrana.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * إدارة مركزية لصلاحية الإشعارات (POST_NOTIFICATIONS).
 *
 * السياسة:
 * - الطلب التلقائي **مرة واحدة** أثناء [com.example.myrana.ui.ChildPermissionsActivity].
 * - شاشة اللعبة لا تعيد عرض الحوار.
 * - [com.example.myrana.ui.MainActivity] تحتفظ بزر يدوي للمطوّرين فقط.
 */
object PermissionCoordinator {

    private const val PREFS = "myrana_permissions"
    /** بعد true: لن يُعرض حوار الطلب التلقائي مجدداً. */
    private const val KEY_NOTIFICATION_INITIAL_FLOW_DONE = "notification_initial_flow_done"

    val notificationPermission: String
        get() = Manifest.permission.POST_NOTIFICATIONS

    /** هل النظام يتطلب إذناً صريحاً؟ (أندرويد 13 / API 33+). */
    fun needsPostNotificationsPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** قراءة من النظام: هل منح المستخدم الإذن؟ */
    fun hasNotificationPermission(context: Context): Boolean {
        if (!needsPostNotificationsPermission()) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasFinishedInitialNotificationFlow(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_NOTIFICATION_INITIAL_FLOW_DONE, false)
    }

    /**
     * يُستدعى بعد حوار النظام — فقط إذا وافق المستخدم على مسار الصلاحيات.
     * الرفض لا يُحسب كمنح، لكن يُسجَّل أن الحوار عُرض.
     */
    fun markInitialNotificationFlowDone(context: Context) {
        if (!ChildPermissionsConsent.hasUserConsented(context)) return
        prefs(context).edit().putBoolean(KEY_NOTIFICATION_INITIAL_FLOW_DONE, true).apply()
    }

    /**
     * هل نعرض حوار الطلب التلقائي الآن؟
     * الشروط: API 33+ + لم يُمنح + لم يُكمل المسار الأول سابقاً.
     */
    fun shouldOfferAutomaticNotificationPrompt(context: Context): Boolean {
        if (!needsPostNotificationsPermission()) return false
        if (hasNotificationPermission(context)) return false
        if (hasFinishedInitialNotificationFlow(context)) return false
        return true
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

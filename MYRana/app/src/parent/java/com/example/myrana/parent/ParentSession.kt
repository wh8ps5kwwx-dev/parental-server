package com.example.myrana.parent

import android.content.Context

/**
 * حالة ولي الأمر محلياً (نكهة parent فقط).
 * تُحفظ بعد التحقق من البريد وربط الطفل.
 */
object ParentSession {

    private const val PREFS = "myrana_parent_session"
    private const val KEY_EMAIL = "guardian_email"
    private const val KEY_ROLE = "guardian_role"
    private const val KEY_VERIFIED = "email_verified"
    private const val KEY_CHILD_CODE = "child_code"
    private const val KEY_CHILD_NAME = "child_name"
    private const val KEY_CHILD_AGE = "child_age"
    private const val KEY_LINKED = "child_linked"
    private const val KEY_PENDING_EMAIL_CODE = "pending_email_code"
    private const val KEY_PENDING_CHILD_CODE = "pending_link_child_code"
    private const val KEY_PENDING_CHILD_EMAIL = "pending_link_child_email"
    private const val KEY_PENDING_DEVICE_NAME = "pending_link_device_name"
    private const val KEY_PENDING_ANDROID_VERSION = "pending_link_android_version"
    private const val KEY_DEVICE_VERIFIED = "device_link_verified"
    private const val KEY_PENDING_CHILD_NAME = "pending_child_name"
    private const val KEY_PENDING_CHILD_AGE = "pending_child_age"
    private const val KEY_RESTORE_TOKEN_PREFIX = "restore_token_"

    fun guardianEmail(context: Context): String? =
        prefs(context).getString(KEY_EMAIL, null)

    fun guardianRole(context: Context): String =
        prefs(context).getString(KEY_ROLE, "ولي أمر") ?: "ولي أمر"

    fun isEmailVerified(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VERIFIED, false)

    fun isChildLinked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LINKED, false)

    fun childCode(context: Context): String? =
        prefs(context).getString(KEY_CHILD_CODE, null)

    fun childName(context: Context): String? =
        prefs(context).getString(KEY_CHILD_NAME, null)

    fun childAge(context: Context): Int =
        prefs(context).getInt(KEY_CHILD_AGE, 10)

    fun isDeviceLinkVerified(context: Context): Boolean =
        prefs(context).getBoolean(KEY_DEVICE_VERIFIED, false)

    fun saveVerifiedDevice(
        context: Context,
        childCode: String,
        childEmail: String,
        deviceName: String,
        androidVersion: String,
    ) {
        prefs(context).edit()
            .putString(KEY_PENDING_CHILD_CODE, childCode.trim())
            .putString(KEY_PENDING_CHILD_EMAIL, childEmail.trim())
            .putString(KEY_PENDING_DEVICE_NAME, deviceName.trim())
            .putString(KEY_PENDING_ANDROID_VERSION, androidVersion.trim())
            .putBoolean(KEY_DEVICE_VERIFIED, true)
            .apply()
    }

    fun pendingLinkChildCode(context: Context): String? =
        prefs(context).getString(KEY_PENDING_CHILD_CODE, null)

    fun pendingLinkChildEmail(context: Context): String? =
        prefs(context).getString(KEY_PENDING_CHILD_EMAIL, null)

    fun pendingLinkDeviceName(context: Context): String? =
        prefs(context).getString(KEY_PENDING_DEVICE_NAME, null)

    fun pendingLinkAndroidVersion(context: Context): String? =
        prefs(context).getString(KEY_PENDING_ANDROID_VERSION, null)

    fun clearPendingLink(context: Context) {
        prefs(context).edit()
            .remove(KEY_PENDING_CHILD_CODE)
            .remove(KEY_PENDING_CHILD_EMAIL)
            .remove(KEY_PENDING_DEVICE_NAME)
            .remove(KEY_PENDING_ANDROID_VERSION)
            .putBoolean(KEY_DEVICE_VERIFIED, false)
            .apply()
    }

    fun saveGuardian(context: Context, email: String, role: String) {
        prefs(context).edit()
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_ROLE, role.trim())
            .apply()
    }

    fun pendingEmailCode(context: Context): String? =
        prefs(context).getString(KEY_PENDING_EMAIL_CODE, null)

    fun savePendingEmailCode(context: Context, code: String) {
        prefs(context).edit().putString(KEY_PENDING_EMAIL_CODE, code.trim()).apply()
    }

    fun markEmailVerified(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_VERIFIED, true)
            .remove(KEY_PENDING_EMAIL_CODE)
            .apply()
    }

    fun saveLinkedChild(context: Context, childCode: String, childName: String, age: Int = pendingChildAge(context)) {
        prefs(context).edit()
            .putString(KEY_CHILD_CODE, childCode.trim())
            .putString(KEY_CHILD_NAME, childName.trim())
            .putInt(KEY_CHILD_AGE, age.coerceIn(3, 18))
            .putBoolean(KEY_LINKED, true)
            .remove(KEY_PENDING_CHILD_NAME)
            .remove(KEY_PENDING_CHILD_AGE)
            .apply()
        clearPendingLink(context)
    }

    fun saveRestoreToken(context: Context, childCode: String, token: String) {
        val code = childCode.trim()
        val trimmed = token.trim()
        if (code.isEmpty() || trimmed.isEmpty()) return
        prefs(context).edit()
            .putString(KEY_RESTORE_TOKEN_PREFIX + code.uppercase(), trimmed)
            .apply()
    }

    fun restoreToken(context: Context, childCode: String): String? {
        val code = childCode.trim().uppercase()
        if (code.isEmpty()) return null
        return prefs(context).getString(KEY_RESTORE_TOKEN_PREFIX + code, null)
    }

    fun savePendingChildProfile(context: Context, name: String, age: Int) {
        prefs(context).edit()
            .putString(KEY_PENDING_CHILD_NAME, name.trim())
            .putInt(KEY_PENDING_CHILD_AGE, age.coerceIn(3, 18))
            .apply()
    }

    fun pendingChildName(context: Context): String? =
        prefs(context).getString(KEY_PENDING_CHILD_NAME, null)

    fun pendingChildAge(context: Context): Int =
        prefs(context).getInt(KEY_PENDING_CHILD_AGE, 10)

    fun hasPendingChildProfile(context: Context): Boolean =
        !pendingChildName(context).isNullOrBlank()

    /** إعادة ربط نفس الطفل — يُبقي الكود والاسم، بدون مسح بيانات التطبيق. */
    fun startRelinkSameChild(context: Context) {
        val name = childName(context)?.trim().orEmpty().ifBlank { "طفل" }
        prefs(context).edit()
            .putBoolean(KEY_LINKED, false)
            .putString(KEY_PENDING_CHILD_NAME, name)
            .putInt(KEY_PENDING_CHILD_AGE, childAge(context))
            .apply()
        clearPendingLink(context)
    }

    /** العودة لإضافة وربط طفل جديد مع الإبقاء على بريد ولي الأمر. */
    fun startAddAnotherChild(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_LINKED, false)
            .remove(KEY_CHILD_CODE)
            .remove(KEY_CHILD_NAME)
            .remove(KEY_PENDING_CHILD_NAME)
            .remove(KEY_PENDING_CHILD_AGE)
            .apply()
        clearPendingLink(context)
    }

    /** السيرفر لا يعرف الطفل — مسح الربط المحلي. */
    fun markLinkStale(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_LINKED, false)
            .remove(KEY_CHILD_CODE)
            .remove(KEY_CHILD_NAME)
            .apply()
        clearPendingLink(context)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

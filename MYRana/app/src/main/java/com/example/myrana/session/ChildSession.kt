package com.example.myrana.session

import android.content.Context

/**
 * إدارة حالة تسجيل الطفل في SharedPreferences (مرة واحدة).
 *
 * بعد [completeSetup] أو [applyFromPython] — Android للصلاحيات والمراقبة فقط.
 */
object ChildSession {

    private const val PREFS = "myrana_child_session"
    /** `true` بعد تأكيد رمز التحقق بنجاح. */
    private const val KEY_SETUP_DONE = "setup_done"
    private const val KEY_EMAIL = "email"
    private const val KEY_CHILD_CODE = "child_code"
    /** يُحذف بعد الإعداد؛ يُستخدم فقط أثناء خطوة التحقق. */
    private const val KEY_VERIFY_CODE = "verify_code"

    /** هل أنهى الطفل التسجيل والتحقق؟ */
    fun isSetupComplete(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SETUP_DONE, false)

    fun childEmail(context: Context): String? =
        prefs(context).getString(KEY_EMAIL, null)

    fun childCode(context: Context): String? =
        prefs(context).getString(KEY_CHILD_CODE, null)

    /** الرمز المتوقع الذي أرسله السيرفر (للمقارنة محلياً قبل الإعداد). */
    fun expectedVerifyCode(context: Context): String? =
        prefs(context).getString(KEY_VERIFY_CODE, null)

    /**
     * يُستدعى بعد نجاح `/register-child-device` وقبل إدخال الرمز.
     * `setup_done` يبقى `false` حتى [completeSetup].
     */
    fun savePendingRegistration(
        context: Context,
        email: String,
        childCode: String,
        verifyCode: String
    ) {
        prefs(context).edit()
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_CHILD_CODE, childCode.trim())
            .putString(KEY_VERIFY_CODE, verifyCode.trim())
            .putBoolean(KEY_SETUP_DONE, false)
            .apply()
    }

    /** إعداد من Python (تسجيل + ربط على الحاسوب) — بدون شاشات Android. */
    fun applyFromPython(context: Context, childCode: String, email: String) {
        prefs(context).edit()
            .putString(KEY_EMAIL, email.trim())
            .putString(KEY_CHILD_CODE, childCode.trim())
            .putBoolean(KEY_SETUP_DONE, true)
            .remove(KEY_VERIFY_CODE)
            .apply()
    }

    /** إنهاء الإعداد: لن تُعرض شاشة التسجيل مجدداً. */
    fun completeSetup(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_SETUP_DONE, true)
            .remove(KEY_VERIFY_CODE)
            .apply()
    }

    /** تحديث كود الطفل بعد إعادة التسجيل على السيرفر دون إعادة الإعداد. */
    fun updateChildCode(context: Context, childCode: String) {
        prefs(context).edit()
            .putString(KEY_CHILD_CODE, childCode.trim())
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

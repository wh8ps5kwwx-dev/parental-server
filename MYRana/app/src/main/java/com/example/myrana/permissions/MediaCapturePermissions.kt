package com.example.myrana.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * صلاحية الكاميرا والميكروفون — اختيارية.
 * تُطلب من المستخدم وتُبلَّغ حالتها لولي الأمر (جاهزية / رؤية الحالة)،
 * وليست تنصتًا أو تصويرًا صامتًا في الخلفية.
 */
object MediaCapturePermissions {

    private const val PREFS = "myrana_permissions"
    private const val KEY_MEDIA_PROMPT_DONE = "media_capture_prompt_done"

    val permissions: Array<String> = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    fun hasCamera(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasMicrophone(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

    fun hasFinishedPrompt(context: Context): Boolean =
        prefs(context).getBoolean(KEY_MEDIA_PROMPT_DONE, false)

    fun markPromptDone(context: Context) {
        if (!ChildPermissionsConsent.hasUserConsented(context)) return
        prefs(context).edit().putBoolean(KEY_MEDIA_PROMPT_DONE, true).apply()
    }

    /** عرض حوار الطلب مرة واحدة أثناء مسار الصلاحيات (بعد الإشعارات). */
    fun shouldOfferPrompt(context: Context): Boolean {
        if (hasCamera(context) && hasMicrophone(context)) return false
        if (hasFinishedPrompt(context)) return false
        return true
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

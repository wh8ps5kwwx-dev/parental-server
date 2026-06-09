package com.example.myrana.device

import android.content.Context
import android.provider.Settings

/**
 * تحديد معرّف جهاز الطفل على السيرفر (`child_code`).
 *
 * الأولوية:
 * 1. القيمة المحفوظة يدوياً أو بعد التسجيل عبر [setChildDeviceId].
 * 2. `ANDROID_ID` من النظام.
 * 3. UUID عشوائي احتياطي (نادر جداً).
 */
object DeviceIdentity {

    /**
     * المعرّف المستخدم في مسارات REST: `/v1/devices/{id}/…` و `child_code` في التقارير.
     */
    fun childDeviceId(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_CUSTOM_ID, null)?.trim().orEmpty()
        if (stored.isNotEmpty()) return stored

        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()

        return androidId.ifBlank { java.util.UUID.randomUUID().toString() }
    }

    /** يُستدعى بعد التسجيل الناجح لربط الجهاز بكود الطفل على السيرفر. */
    fun setChildDeviceId(context: Context, customId: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CUSTOM_ID, customId.trim())
            .apply()
    }

    private const val PREFS = "myrana_device"
    private const val KEY_CUSTOM_ID = "custom_child_device_id"
}

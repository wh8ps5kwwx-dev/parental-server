package com.example.myrana.identity

import android.content.Context
import com.example.myrana.device.DeviceIdentity
import com.example.myrana.session.ChildSession
import com.example.myrana.util.ChildCodeNormalizer

/**
 * مصدر واحد لمعرّف الطفل — يمنع التعارض بين [ChildSession] و [DeviceIdentity].
 *
 * - [apiCode]: للمسارات REST و query params (بدون بادئة CHILD- أو معها — يُنظَّف دائماً).
 * - [displayCode]: للعرض وللمستخدم (CHILD-XXXXXXXX).
 */
object ChildIdentity {

    /** المعرّف المستخدم في API والمزامنة. */
    fun apiCode(context: Context): String {
        val fromSession = ChildSession.childCode(context)
            ?.let { ChildCodeNormalizer.forApi(it) }
            ?.trim()
        if (!fromSession.isNullOrEmpty()) return fromSession

        val stored = DeviceIdentity.childDeviceId(context)
        return ChildCodeNormalizer.forApi(stored).trim()
    }

    /** الصيغة المعروضة CHILD-XXXXXXXX إن وُجدت. */
    fun displayCode(context: Context): String? {
        ChildSession.childCode(context)?.trim()?.let { saved ->
            val normalized = ChildCodeNormalizer.normalize(saved)
            if (normalized.isNotEmpty()) return normalized
        }
        val fromDevice = ChildCodeNormalizer.normalize(DeviceIdentity.childDeviceId(context))
        return fromDevice.ifEmpty { null }
    }

    /** ربط الجلسة المحلية بعد التسجيل أو الربط. */
    fun bind(context: Context, displayCode: String) {
        val normalized = ChildCodeNormalizer.normalize(displayCode)
        if (normalized.isEmpty()) return
        val api = ChildCodeNormalizer.forApi(normalized)
        DeviceIdentity.setChildDeviceId(context, api)
    }
}

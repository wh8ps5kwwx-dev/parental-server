package com.example.myrana.permissions

import android.content.Context

/**
 * يحسب الصلاحيات **بموافقة المستخدم** + حالة النظام الفعلية.
 *
 * - قبل الموافقة: لا تُحسب أي صلاحية مفعّلة (حتى لو النظام يدعمها تلقائياً).
 * - بعد الموافقة: تُحسب فقط ما منحه المستخدم من إعدادات/حوارات أندرويد.
 */
object ChildPermissionEvaluator {

    enum class Kind {
        USAGE,
        ACCESSIBILITY,
        NOTIFICATION,
        BATTERY,
    }

    val trackableKinds: List<Kind> = Kind.values().toList()

    /** هل النظام يمنح الصلاحية فعلياً؟ (من [SystemPermissions]) */
    fun isSystemGranted(context: Context, kind: Kind): Boolean {
        val snap = SystemPermissions.readSnapshot(context)
        return when (kind) {
            Kind.USAGE -> snap.usage
            Kind.ACCESSIBILITY -> snap.accessibility
            Kind.NOTIFICATION -> snap.notifications
            Kind.BATTERY -> snap.battery
        }
    }

    /** تُحسب مفعّلة فقط بعد موافقة المستخدم ومنح النظام. */
    fun isCountedGranted(context: Context, kind: Kind): Boolean =
        ChildPermissionsConsent.hasUserConsented(context) && isSystemGranted(context, kind)

    fun countedGrantedCount(context: Context): Int =
        trackableKinds.count { isCountedGranted(context, it) }

    /** الإلزامي: استخدام + وصول — بعد موافقة المستخدم. */
    fun isMandatoryReadyWithConsent(context: Context): Boolean =
        isCountedGranted(context, Kind.USAGE) &&
            isCountedGranted(context, Kind.ACCESSIBILITY)

    /** جاهز لبدء اللعبة: موافقة + ما منحه أندرويد فعلياً (استخدام + وصول). */
    fun canEnterGame(context: Context): Boolean =
        ChildPermissionsConsent.hasUserConsented(context) &&
            SystemPermissions.readSnapshot(context).mandatoryReady

    fun canMarkFlowComplete(context: Context): Boolean = canEnterGame(context)
}

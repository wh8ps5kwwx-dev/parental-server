package com.example.myrana.permissions

import android.content.Context

/** حالة صلاحيات جهاز الطفل — تُرسل مع heartbeat لولي الأمر. */
object PermissionStatusReporter {

    fun toPayload(context: Context): Map<String, Any?> {
        val snap = SystemPermissions.readSnapshot(context)
        return mapOf(
            "usage" to snap.usage,
            "accessibility" to snap.accessibility,
            "notifications" to snap.notifications,
            "battery" to snap.battery,
            "mandatory_ok" to snap.mandatoryReady,
            "granted_count" to ChildPermissionEvaluator.countedGrantedCount(context),
        )
    }
}

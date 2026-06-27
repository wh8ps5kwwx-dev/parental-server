package com.example.myrana.parent.ui

import android.content.Context
import com.example.myrana.R

/** عرض حالة صلاحيات جوال الطفل لولي الأمر — نفس مفاتيح [PermissionStatusReporter]. */
object ParentPermissionsFormatter {

    private val permissionKeys = listOf(
        "usage",
        "accessibility",
        "notifications",
        "battery",
        "media_read",
    )

    fun formatLine(context: Context, permissions: Map<String, Any?>): String {
        if (permissions.isEmpty()) {
            return context.getString(R.string.parent_permissions_unknown)
        }
        return context.getString(
            R.string.parent_permissions_status,
            label(context, permissions["usage"]),
            label(context, permissions["accessibility"]),
            label(context, permissions["notifications"]),
            label(context, permissions["battery"]),
            label(context, permissions["media_read"]),
        )
    }

    fun summary(context: Context, permissionsOk: Boolean, permissions: Map<String, Any?>): String {
        val line = formatLine(context, permissions)
        val granted = grantedCount(permissions)
        val total = totalCount(permissions)
        val header = when {
            permissions.isEmpty() -> context.getString(R.string.parent_permissions_unknown)
            granted >= total && total > 0 ->
                context.getString(R.string.parent_permissions_all_ok_count, granted, total)
            permissionsOk || permissions["mandatory_ok"] == true ->
                context.getString(R.string.parent_permissions_mandatory_ok, granted, total)
            else -> context.getString(R.string.parent_permissions_incomplete_count, granted, total)
        }
        return if (permissions.isEmpty()) header else "$header\n$line"
    }

    /** عدد الصلاحيات المفعّلة من بيانات السيرفر (استخدام، وصول، إشعارات، بطارية، ملفات). */
    fun grantedCount(permissions: Map<String, Any?>): Int {
        val fromServer = (permissions["granted_count"] as? Number)?.toInt()
        if (fromServer != null && fromServer >= 0) return fromServer
        return permissionKeys.count { permissions[it] == true }
    }

    fun totalCount(permissions: Map<String, Any?>): Int {
        val fromServer = (permissions["total_count"] as? Number)?.toInt()
        if (fromServer != null && fromServer > 0) return fromServer
        return permissionKeys.size
    }

    fun batteryPermissionOk(permissions: Map<String, Any?>): Boolean =
        permissions["battery"] == true

    fun mediaPermissionOk(permissions: Map<String, Any?>): Boolean =
        permissions["media_read"] == true

    private fun label(context: Context, value: Any?): String {
        return if (value == true) {
            context.getString(R.string.parent_perm_ok)
        } else {
            context.getString(R.string.parent_perm_missing)
        }
    }
}

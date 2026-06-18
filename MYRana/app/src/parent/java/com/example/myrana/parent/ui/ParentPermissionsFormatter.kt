package com.example.myrana.parent.ui

import android.content.Context
import com.example.myrana.R

/** عرض حالة صلاحيات جوال الطفل لولي الأمر — نفس مفاتيح PermissionStatusReporter. */
object ParentPermissionsFormatter {

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
        val header = if (permissionsOk || permissions["mandatory_ok"] == true) {
            context.getString(R.string.parent_permissions_all_ok)
        } else {
            context.getString(R.string.parent_permissions_incomplete)
        }
        return "$header\n$line"
    }

    private fun label(context: Context, value: Any?): String {
        return if (value == true) {
            context.getString(R.string.parent_perm_ok)
        } else {
            context.getString(R.string.parent_perm_missing)
        }
    }
}

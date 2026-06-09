package com.example.myrana.enforcement

import android.app.Activity
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * صلاحية «الوصول لبيانات الاستخدام» — مطلوبة لاكتشاف التطبيق في المقدمة وتطبيق الحظر.
 */
object UsageAccessHelper {

    private const val PREFS = "myrana_usage_access"
    private const val KEY_FLOW_DONE = "usage_flow_done"

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** يفتح شاشة النظام لتفعيل «الوصول لبيانات الاستخدام» لهذا التطبيق. */
    fun openSettings(context: Context): Boolean {
        val pkg = context.packageName
        val candidates = listOf(
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:$pkg")
            },
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                @Suppress("DEPRECATION")
                putExtra("android.intent.extra.PACKAGE_NAME", pkg)
            },
            Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        )
        return startFirstAvailable(context, candidates)
    }

    private fun startFirstAvailable(context: Context, intents: List<Intent>): Boolean {
        for (raw in intents) {
            val intent = Intent(raw).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        }
        return false
    }

    fun hasFinishedUsageFlow(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_FLOW_DONE, false)

    fun markUsageFlowDone(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FLOW_DONE, true)
            .apply()
    }
}

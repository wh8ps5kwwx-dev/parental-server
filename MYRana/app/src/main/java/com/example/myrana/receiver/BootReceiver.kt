package com.example.myrana.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.myrana.permissions.ChildProjectRuntime

/** بعد إقلاع الجهاز أو تحديث التطبيق — إعادة المراقبة بالخلفية. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val restart = action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == "com.htc.intent.action.QUICKBOOT_POWERON"
        if (!restart) return
        ChildProjectRuntime.activateMonitoring(context)
    }
}

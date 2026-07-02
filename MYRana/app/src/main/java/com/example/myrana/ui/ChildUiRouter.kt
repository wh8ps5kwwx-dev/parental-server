package com.example.myrana.ui

import android.content.Context
import android.content.Intent
import com.example.myrana.permissions.ChildPermissionEvaluator
import com.example.myrana.permissions.ChildPermissionsGate
import com.example.myrana.permissions.ChildProjectRuntime
import com.example.myrana.session.ChildSession
import com.example.myrana.sync.SyncStarter
import com.example.myrana.ui.academy.AcademyMenuActivity

/**
 * توجيه واجهة الطفل:
 * - إعداد مرة واحدة (تسجيل + تحقق بريد)
 * - صلاحيات الرقابة (مرة واحدة)
 * - بعدها: **أكاديمية العباقرة** فقط — WorkManager + خدمة أمامية لولي الأمر
 */
object ChildUiRouter {

    /** نقطة الدخول من أيقونة التطبيق (Launcher). */
    fun routeFromLauncher(context: Context) {
        when {
            !ChildSession.isSetupComplete(context) -> openRegistration(context)
            !ChildPermissionsGate.isPermissionsFlowComplete(context) -> openPermissions(context)
            else -> openAcademicGame(context)
        }
    }

    /** الواجهة الدائمة للطفل بعد الإعداد — لعبة أكاديمية فقط. */
    fun openAcademicGame(context: Context) {
        if (ChildPermissionEvaluator.canMarkFlowComplete(context)) {
            ChildPermissionsGate.markPermissionsFlowComplete(context)
        }
        ChildProjectRuntime.activateMonitoring(context)
        SyncStarter.startIfReady(context.applicationContext)
        context.startActivity(gameIntent(context))
    }

    fun openRegistration(context: Context) {
        context.startActivity(
            Intent(context, ChildRegistrationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    fun openPermissions(context: Context) {
        context.startActivity(
            ChildPermissionsActivity.intent(context).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    fun gameIntent(context: Context): Intent =
        Intent(context, AcademyMenuActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
}

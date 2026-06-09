package com.example.myrana.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.myrana.device.DeviceIdentity
import com.example.myrana.session.ChildSession
import com.example.myrana.permissions.ChildProjectRuntime
import com.example.myrana.sync.SyncStarter

/**
 * نقطة الدخول (Launcher) على جهاز الطفل.
 * يوجّه مرة واحدة للتسجيل والصلاحيات، ثم يفتح اللعبة الأكاديمية فقط.
 */
class PermissionsLauncherActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyPythonExtras(intent)
        route()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyPythonExtras(intent)
        route()
    }

    override fun onResume() {
        super.onResume()
        if (ChildProjectRuntime.isMonitoringOperational(this)) {
            ChildProjectRuntime.activateMonitoring(applicationContext)
            SyncStarter.startIfReady(applicationContext)
        }
    }

    private fun applyPythonExtras(intent: Intent?) {
        val code = intent?.getStringExtra(EXTRA_CHILD_CODE)?.trim().orEmpty()
        val email = intent?.getStringExtra(EXTRA_CHILD_EMAIL)?.trim().orEmpty()
        if (code.isNotEmpty()) {
            ChildSession.applyFromPython(this, code, email)
            DeviceIdentity.setChildDeviceId(this, code)
        }
    }

    private fun route() {
        ChildUiRouter.routeFromLauncher(this)
        finish()
    }

    companion object {
        const val EXTRA_CHILD_CODE = "child_code"
        const val EXTRA_CHILD_EMAIL = "child_email"

        fun intent(context: Context, childCode: String, childEmail: String): Intent =
            Intent(context, PermissionsLauncherActivity::class.java).apply {
                putExtra(EXTRA_CHILD_CODE, childCode)
                putExtra(EXTRA_CHILD_EMAIL, childEmail)
            }
    }
}

package com.example.myrana.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.myrana.permissions.ChildPermissionsGate
import com.example.myrana.session.ChildSession

/**
 * شاشة قديمة للمحاكي — تُحوّل تلقائياً للتسجيل أو الصلاحيات أو اللعبة.
 * لا تُعرض للطفل في التدفق العادي بعد الإعداد.
 */
class MonitoringStatusActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        when {
            !ChildSession.isSetupComplete(this) -> ChildUiRouter.openRegistration(this)
            !ChildPermissionsGate.isPermissionsFlowComplete(this) -> ChildUiRouter.openPermissions(this)
            else -> ChildUiRouter.openAcademicGame(this)
        }
        finish()
    }

    companion object {
        const val EXTRA_WAITING_PYTHON = "waiting_python"
    }
}

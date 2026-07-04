package com.example.myrana.ui.academy

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.example.myrana.R
import com.example.myrana.academy.AcademyProgressStore
import com.example.myrana.academy.AcademyPythonBridge
import com.example.myrana.academy.AcademyReporter
import com.example.myrana.academy.ChallengeType
import com.example.myrana.permissions.ChildPermissionEvaluator
import com.example.myrana.permissions.ChildPermissionsGate
import com.example.myrana.permissions.ChildProjectRuntime
import com.example.myrana.session.ChildSession
import com.example.myrana.ui.ChildUiRouter
import com.google.android.material.button.MaterialButton

/**
 * القائمة الرئيسية — أكاديمية العباقرة (منطق Python).
 * المراقبة الأبوية عبر WorkManager + خدمة أمامية في الخلفية.
 */
class AcademyMenuActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ChildSession.isSetupComplete(this)) {
            ChildUiRouter.openRegistration(this)
            finish()
            return
        }
        if (!ChildPermissionEvaluator.canEnterGame(this)) {
            ChildUiRouter.openPermissions(this)
            finish()
            return
        }
        ChildPermissionsGate.markPermissionsFlowComplete(this)

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        setContentView(R.layout.activity_academy_menu)
        ChildProjectRuntime.activateMonitoring(this)
        try {
            AcademyPythonBridge.ensureStarted(applicationContext)
        } catch (e: Exception) {
            android.util.Log.e("AcademyMenu", "Python engine failed: ${e.message}")
        }
        bindMenu()
        refreshStats()
        AcademyReporter.sendReport(this, "academy_opened", "فتح أكاديمية العباقرة")
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
        ChildProjectRuntime.activateMonitoring(this)
    }

    override fun onPause() {
        super.onPause()
        // المراقبة تستمر عند الخروج من اللعبة
        ChildProjectRuntime.activateMonitoring(applicationContext)
    }

    private fun bindMenu() {
        findViewById<MaterialButton>(R.id.btnMath).setOnClickListener {
            openChallenge(ChallengeType.MATH)
        }
        findViewById<MaterialButton>(R.id.btnScience).setOnClickListener {
            openChallenge(ChallengeType.SCIENCE)
        }
        findViewById<MaterialButton>(R.id.btnLogic).setOnClickListener {
            openChallenge(ChallengeType.LOGIC)
        }
        findViewById<MaterialButton>(R.id.btnCity).setOnClickListener {
            startActivity(Intent(this, AcademyCityActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnRewards).setOnClickListener {
            startActivity(Intent(this, AcademyRewardsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnRefresh).setOnClickListener {
            refreshStats()
        }
    }

    private fun openChallenge(type: ChallengeType) {
        startActivity(
            Intent(this, AcademyChallengeActivity::class.java).apply {
                putExtra(AcademyChallengeActivity.EXTRA_TYPE, type.name)
            },
        )
    }

    private fun refreshStats() {
        findViewById<TextView>(R.id.textAcademyStats).text = try {
            AcademyProgressStore.statsLine(this)
        } catch (e: Exception) {
            getString(R.string.academy_stats_fallback)
        }
    }
}

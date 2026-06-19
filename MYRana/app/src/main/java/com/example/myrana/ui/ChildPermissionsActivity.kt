package com.example.myrana.ui

import android.content.Intent
import android.view.View
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.myrana.MyRanaApp
import com.example.myrana.R
import com.example.myrana.permissions.ChildPermissionEvaluator
import com.example.myrana.permissions.ChildPermissionsConsent
import com.example.myrana.permissions.ChildPermissionsGate
import com.example.myrana.enforcement.BlocklistCatalogLoader
import com.example.myrana.permissions.ChildProjectRuntime
import com.example.myrana.permissions.PermissionCoordinator
import com.example.myrana.permissions.StorageAccessHelper
import com.example.myrana.permissions.SystemPermissions
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * إعداد الجهاز — الصلاحيات تُحسب بموافقة المستخدم + ما يمنحه أندرويد فعلياً.
 */
class ChildPermissionsActivity : AppCompatActivity() {

    private lateinit var textStatus: TextView
    private lateinit var textConsentSummary: TextView
    private lateinit var progress: ProgressBar
    private lateinit var btnContinue: Button
    private lateinit var btnRetry: Button

    private var bulkGrantActive = false
    private var pausedUsage = false
    private var pausedA11y = false
    private var pausedBattery = false
    private var consentDialog: AlertDialog? = null
    private var autoOpenedGame = false

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (ChildPermissionsConsent.hasUserConsented(this)) {
            PermissionCoordinator.markInitialNotificationFlowDone(this)
        }
        refreshUi()
        if (bulkGrantActive) {
            requestStorageIfNeeded()
        }
        if (!granted && ChildPermissionsConsent.hasUserConsented(this)) {
            Toast.makeText(this, R.string.permissions_notification_declined, Toast.LENGTH_SHORT).show()
        }
    }

    private val requestStorage = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        refreshUi()
        if (bulkGrantActive) {
            openNextSystemPermissionIfNeeded()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ChildPermissionsGate.isPermissionsFlowComplete(this)) {
            ChildUiRouter.openAcademicGame(this)
            finish()
            return
        }

        setContentView(R.layout.activity_child_permissions)
        textStatus = findViewById(R.id.textPermissionStatus)
        textConsentSummary = findViewById(R.id.textConsentSummary)
        progress = findViewById(R.id.progressDeviceSetup)
        btnContinue = findViewById(R.id.btnContinueToGame)
        btnRetry = findViewById(R.id.btnRetrySystemPermissions)
        btnContinue.setOnClickListener { openGame() }
        btnRetry.setOnClickListener {
            bulkGrantActive = true
            startBulkGrant()
        }

        refreshUi()

        if (ChildPermissionsConsent.hasUserConsented(this)) {
            bulkGrantActive = true
            startBulkGrant()
        } else {
            showRealDeviceConsentDialog()
        }
    }

    override fun onPause() {
        pausedUsage = ChildPermissionEvaluator.isSystemGranted(this, ChildPermissionEvaluator.Kind.USAGE)
        pausedA11y = ChildPermissionEvaluator.isSystemGranted(this, ChildPermissionEvaluator.Kind.ACCESSIBILITY)
        pausedBattery = ChildPermissionEvaluator.isSystemGranted(this, ChildPermissionEvaluator.Kind.BATTERY)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        if (!bulkGrantActive || !ChildPermissionsConsent.hasUserConsented(this)) return
        if (ChildPermissionsConsent.needsNotificationRequest(this)) return

        val usage = ChildPermissionEvaluator.isSystemGranted(this, ChildPermissionEvaluator.Kind.USAGE)
        val a11y = ChildPermissionEvaluator.isSystemGranted(this, ChildPermissionEvaluator.Kind.ACCESSIBILITY)
        val battery = ChildPermissionEvaluator.isSystemGranted(this, ChildPermissionEvaluator.Kind.BATTERY)
        val madeProgress =
            (usage && !pausedUsage) || (a11y && !pausedA11y) || (battery && !pausedBattery)

        if (madeProgress) {
            openNextSystemPermissionIfNeeded()
        }
    }

    override fun onDestroy() {
        consentDialog?.dismiss()
        consentDialog = null
        super.onDestroy()
    }

    private fun showRealDeviceConsentDialog() {
        consentDialog?.dismiss()
        consentDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permissions_real_device_title)
            .setMessage(R.string.permissions_real_device_consent_message)
            .setCancelable(false)
            .setPositiveButton(R.string.permissions_real_device_agree) { _, _ ->
                ChildPermissionsConsent.markUserConsented(this)
                bulkGrantActive = true
                refreshUi()
                startBulkGrant()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                finishAffinity()
            }
            .show()
    }

    private fun startBulkGrant() {
        if (!ChildPermissionsConsent.hasUserConsented(this)) return
        if (ChildPermissionsConsent.needsNotificationRequest(this)) {
            requestNotifications.launch(PermissionCoordinator.notificationPermission)
            return
        }
        requestStorageIfNeeded()
    }

    private fun requestStorageIfNeeded() {
        if (!StorageAccessHelper.hasMediaReadAccess(this)) {
            val perms = StorageAccessHelper.requiredPermissions()
            if (perms.isNotEmpty()) {
                requestStorage.launch(perms)
                return
            }
        }
        openNextSystemPermissionIfNeeded()
    }

    private fun openNextSystemPermissionIfNeeded() {
        if (!ChildPermissionsConsent.hasUserConsented(this)) return
        when (val result = SystemPermissions.openFirstMissing(this)) {
            is SystemPermissions.OpenResult.Opened -> {
                if (!result.launched) {
                    Toast.makeText(this, R.string.permissions_system_open_failed, Toast.LENGTH_LONG).show()
                    bulkGrantActive = false
                }
            }
            SystemPermissions.OpenResult.NothingMissing -> {
                bulkGrantActive = false
                if (ChildPermissionEvaluator.canEnterGame(this)) {
                    startMonitoringWithoutRequiringPlay()
                    scheduleAutoOpenGame()
                }
            }
        }
        refreshUi()
    }

    private fun refreshUi() {
        val consented = ChildPermissionsConsent.hasUserConsented(this)
        val grantedCount = ChildPermissionEvaluator.countedGrantedCount(this)
        val totalCount = ChildPermissionEvaluator.trackableKinds.size
        val ready = ChildPermissionEvaluator.canEnterGame(this)

        textConsentSummary.text = if (consented) {
            getString(R.string.permissions_consent_count, grantedCount, totalCount)
        } else {
            getString(R.string.permissions_consent_pending)
        }

        textStatus.text = buildList {
            add(statusLine(ChildPermissionEvaluator.Kind.USAGE))
            add(statusLine(ChildPermissionEvaluator.Kind.ACCESSIBILITY))
            add(statusLine(ChildPermissionEvaluator.Kind.NOTIFICATION))
            add(statusLine(ChildPermissionEvaluator.Kind.BATTERY))
            add(storageStatusLine())
            if (consented && !ready) {
                add(getString(R.string.permissions_full_apps_required))
            }
        }.joinToString("\n")

        progress.visibility = if (ready) View.GONE else View.VISIBLE
        btnContinue.visibility = if (ready) View.VISIBLE else View.GONE
        btnContinue.isEnabled = ready
        btnContinue.text = getString(R.string.permissions_btn_continue)
        btnRetry.visibility = if (consented && !ready) View.VISIBLE else View.GONE

        if (ready) {
            scheduleAutoOpenGame()
        }
    }

    private fun scheduleAutoOpenGame() {
        if (autoOpenedGame || isFinishing) return
        autoOpenedGame = true
        btnContinue.postDelayed({
            if (!isFinishing && ChildPermissionEvaluator.canEnterGame(this)) {
                openGame()
            } else {
                autoOpenedGame = false
            }
        }, 1500L)
    }

    private fun statusLine(kind: ChildPermissionEvaluator.Kind): String {
        val consented = ChildPermissionsConsent.hasUserConsented(this)
        val counted = ChildPermissionEvaluator.isCountedGranted(this, kind)
        val system = ChildPermissionEvaluator.isSystemGranted(this, kind)
        return when (kind) {
            ChildPermissionEvaluator.Kind.USAGE -> when {
                counted -> getString(R.string.permissions_status_usage_ok)
                consented && !system -> getString(R.string.permissions_status_usage_missing)
                else -> getString(R.string.permissions_status_usage_pending_consent)
            }
            ChildPermissionEvaluator.Kind.ACCESSIBILITY -> when {
                counted -> getString(R.string.permissions_status_accessibility_ok)
                consented && !system -> getString(R.string.permissions_status_accessibility_missing)
                else -> getString(R.string.permissions_status_accessibility_pending_consent)
            }
            ChildPermissionEvaluator.Kind.NOTIFICATION -> when {
                counted -> getString(R.string.permissions_status_notifications_ok)
                consented && !system -> getString(R.string.permissions_status_notifications_missing)
                else -> getString(R.string.permissions_status_notifications_pending_consent)
            }
            ChildPermissionEvaluator.Kind.BATTERY -> when {
                counted -> getString(R.string.permissions_status_battery_ok)
                consented && !system -> getString(R.string.permissions_status_battery_missing)
                else -> getString(R.string.permissions_status_battery_pending_consent)
            }
        }
    }

    private fun storageStatusLine(): String {
        val consented = ChildPermissionsConsent.hasUserConsented(this)
        val ok = StorageAccessHelper.hasMediaReadAccess(this)
        return when {
            ok -> getString(R.string.permissions_status_storage_ok)
            consented -> getString(R.string.permissions_status_storage_missing)
            else -> getString(R.string.permissions_status_storage_pending)
        }
    }

    /** تشغيل المراقبة فوراً — قبل وبعد فتح اللعبة. */
    private fun startMonitoringWithoutRequiringPlay() {
        BlocklistCatalogLoader.syncFromServerNow(this)
        ChildProjectRuntime.activateMonitoring(this)
        (application as? MyRanaApp)?.onChildSetupReady()
    }

    private fun openGame() {
        if (!ChildPermissionEvaluator.canEnterGame(this)) {
            autoOpenedGame = false
            Toast.makeText(this, R.string.permissions_consent_required_mandatory, Toast.LENGTH_LONG).show()
            if (ChildPermissionsConsent.hasUserConsented(this)) {
                bulkGrantActive = true
                startBulkGrant()
            }
            return
        }
        ChildPermissionsGate.markPermissionsFlowComplete(this)
        startMonitoringWithoutRequiringPlay()
        startActivity(ChildUiRouter.gameIntent(this))
        finish()
    }

    companion object {
        fun intent(context: android.content.Context): Intent =
            Intent(context, ChildPermissionsActivity::class.java)
    }
}

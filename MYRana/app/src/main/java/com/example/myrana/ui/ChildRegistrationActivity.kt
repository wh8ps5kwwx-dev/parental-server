package com.example.myrana.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.PatternsCompat
import androidx.lifecycle.lifecycleScope
import com.example.myrana.R
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.data.remote.dto.RegisterChildRequest
import com.example.myrana.device.DeviceIdentity
import com.example.myrana.permissions.ChildPermissionsGate
import com.example.myrana.session.ChildSession
import com.example.myrana.util.ChildCodeNormalizer
import com.example.myrana.util.ServerConnectionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * إعداد الطفل — مرة واحدة.
 *
 * 1. بريد ولي الأمر → تسجيل الجهاز → كود CHILD يُرسل إلى Gmail.
 * 2. انتظار ربط ولي الأمر (رمز الربط — رسالة Gmail الثانية).
 * 3. بعد الربط → صلاحيات → الأكاديمية.
 */
class ChildRegistrationActivity : AppCompatActivity() {

    private lateinit var stepRegister: LinearLayout
    private lateinit var stepWait: LinearLayout
    private lateinit var inputParentEmail: EditText
    private lateinit var textMessage: TextView
    private lateinit var textCodesDisplay: TextView
    private lateinit var textChildCodeValue: TextView
    private lateinit var btnCopyChildCode: Button
    private lateinit var btnCheckParentLink: Button
    private var currentChildCode: String = ""
    private var linkPollJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ChildSession.isSetupComplete(this)) {
            goToPermissionsOrGame()
            return
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishAffinity()
                }
            },
        )

        setContentView(R.layout.activity_child_register)
        stepRegister = findViewById(R.id.stepEmail)
        stepWait = findViewById(R.id.stepVerify)
        inputParentEmail = findViewById(R.id.inputParentEmail)
        textMessage = findViewById(R.id.textMessage)
        textCodesDisplay = findViewById(R.id.textCodesDisplay)
        textChildCodeValue = findViewById(R.id.textChildCodeValue)
        btnCopyChildCode = findViewById(R.id.btnCopyChildCode)
        btnCheckParentLink = findViewById(R.id.btnCheckParentLink)
        btnCopyChildCode.setOnClickListener { copyChildCodeToClipboard() }
        textChildCodeValue.setOnClickListener { copyChildCodeToClipboard() }
        btnCheckParentLink.setOnClickListener { checkLinkNow() }

        ChildSession.childEmail(this)?.orEmpty()?.takeIf { it.isNotBlank() }?.let {
            inputParentEmail.setText(it)
        }

        val pendingCode = ChildSession.childCode(this)
        if (!pendingCode.isNullOrBlank() && !ChildSession.isSetupComplete(this)) {
            showWaitingForLink(pendingCode, emailSent = true)
            ensureRegisteredOnServer(pendingCode) { startLinkPolling(pendingCode) }
        }

        findViewById<Button>(R.id.btnSendCode).setOnClickListener { registerDevice() }
    }

    override fun onResume() {
        super.onResume()
        if (stepWait.visibility == View.VISIBLE && currentChildCode.isNotBlank()) {
            checkLinkNow(silent = true)
            startLinkPolling(currentChildCode)
        }
    }

    override fun onDestroy() {
        linkPollJob?.cancel()
        super.onDestroy()
    }

    private fun registerDevice() {
        val email = inputParentEmail.text.toString().trim()
        if (email.isEmpty()) {
            showMessage(getString(R.string.register_email_required), true)
            return
        }
        if (!PatternsCompat.EMAIL_ADDRESS.matcher(email).matches()) {
            showMessage(getString(R.string.register_email_invalid), true)
            return
        }
        if (!com.example.myrana.network.NetworkMonitor.isOnline(this)) {
            showMessage(ServerConnectionHelper.messageFor(ServerConnectionHelper.ErrorKind.NO_INTERNET), true)
            return
        }
        val existing = ChildSession.childCode(this)?.trim().orEmpty()
        val childCode = existing.ifBlank { "CHILD-${UUID.randomUUID().toString().take(8).uppercase()}" }
        registerOnServer(childCode, email, showWaitUi = false)
    }

    private fun ensureRegisteredOnServer(childCode: String, onReady: () -> Unit) {
        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                NetworkModule.queryChildLinkStatus(childCode)
            }
            when (status.state) {
                NetworkModule.ChildRegistrationState.NOT_ON_SERVER -> {
                    val email = ChildSession.childEmail(this@ChildRegistrationActivity).orEmpty()
                        .ifBlank { inputParentEmail.text.toString().trim() }
                    registerOnServer(childCode, email, showWaitUi = true, onSuccess = onReady)
                }
                NetworkModule.ChildRegistrationState.ERROR -> {
                    showMessage(
                        status.detail.ifBlank { getString(R.string.error_network, "") },
                        true,
                    )
                }
                else -> onReady()
            }
        }
    }

    private fun registerOnServer(
        childCode: String,
        parentEmail: String,
        showWaitUi: Boolean,
        onSuccess: (() -> Unit)? = null,
    ) {
        val email = parentEmail.trim()
        if (email.isEmpty()) {
            showMessage(getString(R.string.register_email_required), true)
            return
        }

        val deviceName = Build.MODEL.ifBlank { "Android" }
        val androidVersion = "Android ${Build.VERSION.RELEASE}"
        val androidDeviceId = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ANDROID_ID,
        ).orEmpty()

        if (!showWaitUi) {
            showMessage(getString(R.string.register_sending), false)
            findViewById<Button>(R.id.btnSendCode).isEnabled = false
        } else {
            showMessage(getString(R.string.register_reregistering), false)
        }

        lifecycleScope.launch {
            val serverCheck = withContext(Dispatchers.IO) {
                ServerConnectionHelper.checkConnectivity(this@ChildRegistrationActivity)
            }
            if (!serverCheck.ok) {
                showMessage(serverCheck.message, true)
                findViewById<Button>(R.id.btnSendCode).isEnabled = true
                return@launch
            }
            try {
                val response = withContext(Dispatchers.IO) {
                    NetworkModule.registerChildDevice(
                        RegisterChildRequest(
                            childCode = ChildCodeNormalizer.forApi(childCode),
                            childEmail = email,
                            guardianEmail = email,
                            parentEmail = email,
                            deviceName = deviceName,
                            androidVersion = androidVersion,
                            androidDeviceId = androidDeviceId,
                        ),
                    )
                }
                val serverChildCode = ChildCodeNormalizer.normalize(
                    response.childCode?.trim().orEmpty().ifBlank { childCode },
                )
                if (response.status != "success") {
                    showMessage(getString(R.string.error_register_failed), true)
                    findViewById<Button>(R.id.btnSendCode).isEnabled = true
                    return@launch
                }

                ChildSession.savePendingRegistration(
                    this@ChildRegistrationActivity,
                    email,
                    serverChildCode,
                    "",
                )
                com.example.myrana.identity.ChildIdentity.bind(this@ChildRegistrationActivity, serverChildCode)
                val emailSent = response.emailSent == true
                showWaitingForLink(serverChildCode, emailSent)
                if (!emailSent) {
                    showMessage(
                        "✓ تم التسجيل على السيرفر\nانسخي الكود: $serverChildCode",
                        false,
                    )
                }
                startLinkPolling(serverChildCode)
                onSuccess?.invoke()
            } catch (e: Exception) {
                showMessage(ServerConnectionHelper.friendlyMessage(e), true)
                findViewById<Button>(R.id.btnSendCode).isEnabled = true
            }
        }
    }

    private fun showWaitingForLink(childCode: String, emailSent: Boolean) {
        currentChildCode = childCode.trim()
        stepRegister.visibility = View.GONE
        stepWait.visibility = View.VISIBLE
        findViewById<TextView>(R.id.textVerifyHint).text =
            getString(R.string.register_wait_parent_link)

        textCodesDisplay.visibility = View.VISIBLE
        textChildCodeValue.visibility = View.VISIBLE
        btnCopyChildCode.visibility = View.VISIBLE
        btnCheckParentLink.visibility = View.VISIBLE
        textChildCodeValue.text = currentChildCode

        if (emailSent) {
            val email = ChildSession.childEmail(this).orEmpty()
            showMessage(
                getString(R.string.register_email_sent, email) +
                    "\n\n" + getString(R.string.register_copy_code_for_parent),
                false,
            )
        } else {
            showMessage(getString(R.string.register_wait_message), false)
        }
    }

    private fun copyChildCodeToClipboard() {
        if (currentChildCode.isBlank()) return
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        clipboard.setPrimaryClip(
            android.content.ClipData.newPlainText(
                getString(R.string.register_child_code_label),
                currentChildCode,
            ),
        )
        showMessage(getString(R.string.child_code_copied), false)
    }

    private fun checkLinkNow(silent: Boolean = false) {
        val code = currentChildCode.ifBlank { ChildSession.childCode(this).orEmpty() }
        if (code.isBlank()) return
        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                NetworkModule.queryChildLinkStatus(code)
            }
            when (status.state) {
                NetworkModule.ChildRegistrationState.LINKED -> onParentLinked()
                NetworkModule.ChildRegistrationState.NOT_ON_SERVER -> {
                    if (!silent) {
                        showMessage(getString(R.string.register_reregistering), true)
                    }
                }
                NetworkModule.ChildRegistrationState.ERROR -> {
                    if (!silent) {
                        showMessage(status.detail.ifBlank { getString(R.string.error_network, "") }, true)
                    }
                }
                else -> {
                    if (!silent) {
                        showMessage(getString(R.string.register_wait_parent_link), false)
                    }
                }
            }
        }
    }

    private fun startLinkPolling(childCode: String) {
        val code = childCode.trim()
        if (code.isBlank()) return
        if (linkPollJob?.isActive == true) return
        linkPollJob = lifecycleScope.launch {
            while (isActive) {
                val state = withContext(Dispatchers.IO) {
                    NetworkModule.queryChildRegistrationState(code)
                }
                when (state) {
                    NetworkModule.ChildRegistrationState.LINKED -> {
                        onParentLinked()
                        return@launch
                    }
                    NetworkModule.ChildRegistrationState.NOT_ON_SERVER -> {
                        showMessage(getString(R.string.register_reregistering), false)
                        val email = ChildSession.childEmail(this@ChildRegistrationActivity).orEmpty()
                        registerOnServer(code, email, showWaitUi = true)
                        delay(5_000L)
                    }
                    else -> delay(3_000L)
                }
            }
        }
    }

    private fun onParentLinked() {
        linkPollJob?.cancel()
        ChildSession.completeSetup(this)
        showMessage(getString(R.string.register_link_detected), false)
        finishSetupAndOpenPermissions()
    }

    private fun finishSetupAndOpenPermissions() {
        ChildPermissionsGate.ensurePermissionsRequiredAfterLink(this)
        startActivity(
            ChildPermissionsActivity.intent(this).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
        )
        finish()
    }

    private fun goToPermissionsOrGame() {
        if (ChildPermissionsGate.isPermissionsFlowComplete(this)) {
            ChildUiRouter.openAcademicGame(this)
        } else {
            ChildUiRouter.openPermissions(this)
        }
        finish()
    }

    private fun showMessage(msg: String, isError: Boolean) {
        textMessage.text = msg
        textMessage.setTextColor(
            getColor(if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark),
        )
    }
}

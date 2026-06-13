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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * إعداد الطفل — مرة واحدة.
 *
 * 1. بريد ولي الأمر → تسجيل الجهاز → كود CHILD يُرسل إلى Gmail.
 * 2. انتظار ربط ولي الأمر (رمز الربط — رسالة Gmail ثانية).
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
    private var currentChildCode: String = ""

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
            }
        )

        setContentView(R.layout.activity_child_register)
        stepRegister = findViewById(R.id.stepEmail)
        stepWait = findViewById(R.id.stepVerify)
        inputParentEmail = findViewById(R.id.inputParentEmail)
        textMessage = findViewById(R.id.textMessage)
        textCodesDisplay = findViewById(R.id.textCodesDisplay)
        textChildCodeValue = findViewById(R.id.textChildCodeValue)
        btnCopyChildCode = findViewById(R.id.btnCopyChildCode)
        btnCopyChildCode.setOnClickListener { copyChildCodeToClipboard() }
        textChildCodeValue.setOnClickListener { copyChildCodeToClipboard() }

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
        val existing = ChildSession.childCode(this)?.trim().orEmpty()
        val childCode = existing.ifBlank { "CHILD-${UUID.randomUUID().toString().take(8).uppercase()}" }
        registerOnServer(childCode, email, showWaitUi = false)
    }

    private fun ensureRegisteredOnServer(childCode: String, onReady: () -> Unit) {
        lifecycleScope.launch {
            val state = withContext(Dispatchers.IO) {
                NetworkModule.queryChildRegistrationState(childCode)
            }
            when (state) {
                NetworkModule.ChildRegistrationState.NOT_ON_SERVER -> {
                    val email = ChildSession.childEmail(this@ChildRegistrationActivity).orEmpty()
                        .ifBlank { inputParentEmail.text.toString().trim() }
                    registerOnServer(childCode, email, showWaitUi = true, onSuccess = onReady)
                }
                NetworkModule.ChildRegistrationState.ERROR ->
                    showMessage(getString(R.string.error_network, ""), true)
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
                        )
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

                val emailSent = response.emailSent == true
                if (!emailSent && response.devFallback != true) {
                    showMessage(getString(R.string.register_email_failed), true)
                    findViewById<Button>(R.id.btnSendCode).isEnabled = true
                    return@launch
                }

                ChildSession.savePendingRegistration(
                    this@ChildRegistrationActivity,
                    email,
                    serverChildCode,
                    "",
                )
                DeviceIdentity.setChildDeviceId(this@ChildRegistrationActivity, serverChildCode)
                showWaitingForLink(serverChildCode, emailSent)
                onSuccess?.invoke()
            } catch (e: Exception) {
                showMessage(getString(R.string.error_network, e.message ?: ""), true)
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

        if (emailSent) {
            textCodesDisplay.visibility = View.GONE
            textChildCodeValue.visibility = View.GONE
            btnCopyChildCode.visibility = View.GONE
            val email = ChildSession.childEmail(this).orEmpty()
            showMessage(getString(R.string.register_email_sent, email), false)
        } else {
            textCodesDisplay.visibility = View.VISIBLE
            textChildCodeValue.visibility = View.VISIBLE
            btnCopyChildCode.visibility = View.VISIBLE
            textChildCodeValue.text = currentChildCode
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
            )
        )
        showMessage(getString(R.string.child_code_copied), false)
    }

    private fun startLinkPolling(childCode: String) {
        lifecycleScope.launch {
            while (isActive) {
                val state = withContext(Dispatchers.IO) {
                    NetworkModule.queryChildRegistrationState(childCode)
                }
                when (state) {
                    NetworkModule.ChildRegistrationState.LINKED -> {
                        ChildSession.completeSetup(this@ChildRegistrationActivity)
                        finishSetupAndOpenGame()
                        return@launch
                    }
                    NetworkModule.ChildRegistrationState.NOT_ON_SERVER -> {
                        showMessage(getString(R.string.register_reregistering), false)
                        val email = ChildSession.childEmail(this@ChildRegistrationActivity).orEmpty()
                        registerOnServer(childCode, email, showWaitUi = true)
                        delay(5_000L)
                    }
                    else -> delay(3_000L)
                }
            }
        }
    }

    private fun finishSetupAndOpenGame() {
        startActivity(
            ChildPermissionsActivity.intent(this).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
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
            getColor(if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark)
        )
    }
}

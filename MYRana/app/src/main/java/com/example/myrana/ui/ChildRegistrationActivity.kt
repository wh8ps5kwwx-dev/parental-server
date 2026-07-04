package com.example.myrana.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.myrana.R
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.data.remote.dto.RegisterChildRequest
import com.example.myrana.data.remote.dto.RegisterChildResponse
import com.example.myrana.device.DeviceIdentity
import com.example.myrana.identity.ChildIdentity
import com.example.myrana.permissions.ChildPermissionEvaluator
import com.example.myrana.session.ChildSession
import com.example.myrana.util.ChildCodeNormalizer
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
 * 1. تسجيل الجهاز على السيرفر (يظهر كود الطفل).
 * 2. انتظار ربط ولي الأمر (رمز التحقق يُرسل لبريدها فقط).
 * 3. بعد الربط → صلاحيات → الأكاديمية.
 */
class ChildRegistrationActivity : AppCompatActivity() {

    private lateinit var stepRegister: LinearLayout
    private lateinit var stepWait: LinearLayout
    private lateinit var textMessage: TextView
    private lateinit var textCodesDisplay: TextView
    private lateinit var textChildCodeValue: TextView
    private lateinit var btnCopyChildCode: Button
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
        textMessage = findViewById(R.id.textMessage)
        textCodesDisplay = findViewById(R.id.textCodesDisplay)
        textChildCodeValue = findViewById(R.id.textChildCodeValue)
        btnCopyChildCode = findViewById(R.id.btnCopyChildCode)
        btnCopyChildCode.setOnClickListener { copyChildCodeToClipboard() }
        textChildCodeValue.setOnClickListener { copyChildCodeToClipboard() }

        val pendingCode = ChildSession.childCode(this)
        if (!pendingCode.isNullOrBlank() && !ChildSession.isSetupComplete(this)) {
            showWaitingForLink(pendingCode)
            ensureRegisteredOnServer(pendingCode)
        }

        findViewById<Button>(R.id.btnSendCode).setOnClickListener { registerDevice() }
    }

    private fun registerDevice() {
        val existing = ChildSession.childCode(this)?.trim().orEmpty()
        val suffix = UUID.randomUUID().toString().replace("-", "").take(8).uppercase()
        val childCode = existing.ifBlank { "CHILD-$suffix" }
        registerOnServer(childCode, showWaitUi = false)
    }

    /** إذا حُذفت قاعدة السيرفر (إعادة نشر Render) — أعيدي تسجيل نفس الكود تلقائياً. */
    private fun ensureRegisteredOnServer(childCode: String) {
        lifecycleScope.launch {
            val normalized = ChildCodeNormalizer.normalize(childCode)
            val state = withContext(Dispatchers.IO) {
                NetworkModule.queryChildRegistrationState(normalized)
            }
            when (state) {
                NetworkModule.ChildRegistrationState.LINKED -> onLinkedOnServer()
                NetworkModule.ChildRegistrationState.NOT_ON_SERVER ->
                    registerOnServer(normalized, showWaitUi = true)
                NetworkModule.ChildRegistrationState.ERROR ->
                    showMessage(getString(R.string.error_network, ""), true)
                else -> startLinkPolling(normalized)
            }
        }
    }

    private fun registerOnServer(
        childCode: String,
        showWaitUi: Boolean,
        onSuccess: (() -> Unit)? = null,
    ) {
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
                            childEmail = "",
                            deviceName = deviceName,
                            androidVersion = androidVersion,
                            androidDeviceId = androidDeviceId,
                        ),
                    )
                }
                val serverChildCode = ChildCodeNormalizer.normalize(
                    response.childCode?.trim().orEmpty().ifBlank { childCode },
                )
                if (!isRegisterSuccess(response)) {
                    showMessage(getString(R.string.error_register_failed), true)
                    findViewById<Button>(R.id.btnSendCode).isEnabled = true
                    return@launch
                }

                ChildSession.savePendingRegistration(
                    this@ChildRegistrationActivity,
                    "",
                    serverChildCode,
                    "",
                )
                DeviceIdentity.setChildDeviceId(this@ChildRegistrationActivity, serverChildCode)
                ChildIdentity.bind(this@ChildRegistrationActivity, serverChildCode)
                showWaitingForLink(serverChildCode)
                startLinkPolling(serverChildCode)
                onSuccess?.invoke()
            } catch (e: Exception) {
                showMessage(getString(R.string.error_network, e.message ?: ""), true)
                findViewById<Button>(R.id.btnSendCode).isEnabled = true
            }
        }
    }

    private fun showWaitingForLink(childCode: String) {
        currentChildCode = childCode.trim()
        stepRegister.visibility = View.GONE
        stepWait.visibility = View.VISIBLE
        findViewById<TextView>(R.id.textVerifyHint).text =
            getString(R.string.register_wait_parent_link)
        textCodesDisplay.visibility = View.VISIBLE
        textChildCodeValue.visibility = View.VISIBLE
        btnCopyChildCode.visibility = View.VISIBLE
        textChildCodeValue.text = currentChildCode
        showMessage(getString(R.string.register_wait_message), false)
    }

    private fun copyChildCodeToClipboard() {
        if (currentChildCode.isBlank()) return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText(getString(R.string.register_child_code_label), currentChildCode),
        )
        showMessage(getString(R.string.child_code_copied), false)
    }

    private fun startLinkPolling(childCode: String) {
        val normalized = ChildCodeNormalizer.normalize(childCode)
        if (normalized.isBlank()) return
        linkPollJob?.cancel()
        linkPollJob = lifecycleScope.launch {
            while (isActive) {
                val state = withContext(Dispatchers.IO) {
                    NetworkModule.queryChildRegistrationState(normalized)
                }
                when (state) {
                    NetworkModule.ChildRegistrationState.LINKED -> {
                        onLinkedOnServer()
                        return@launch
                    }
                    NetworkModule.ChildRegistrationState.NOT_ON_SERVER -> {
                        showMessage(getString(R.string.register_reregistering), false)
                        registerOnServer(normalized, showWaitUi = true)
                        delay(5_000L)
                    }
                    NetworkModule.ChildRegistrationState.ERROR -> {
                        showMessage(getString(R.string.error_network, ""), true)
                        delay(5_000L)
                    }
                    else -> delay(3_000L)
                }
            }
        }
    }

    override fun onDestroy() {
        linkPollJob?.cancel()
        super.onDestroy()
    }

    private fun onLinkedOnServer() {
        val code = ChildCodeNormalizer.normalize(
            ChildSession.childCode(this).orEmpty().ifBlank { currentChildCode },
        )
        if (code.isNotBlank()) {
            ChildIdentity.bind(this, code)
        }
        ChildSession.completeSetup(this)
        goToPermissionsOrGame()
    }

    private fun goToPermissionsOrGame() {
        if (ChildPermissionEvaluator.canEnterGame(this)) {
            ChildUiRouter.openAcademicGame(this)
        } else {
            ChildUiRouter.openPermissions(this)
        }
        finish()
    }

    private fun isRegisterSuccess(response: RegisterChildResponse): Boolean =
        response.status == "success" || response.success == true

    private fun showMessage(msg: String, isError: Boolean) {
        textMessage.text = msg
        textMessage.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark,
            ),
        )
    }
}

package com.example.myrana.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.myrana.R
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.identity.ChildIdentity
import com.example.myrana.sync.LinkStateGuard
import com.example.myrana.util.ChildCodeNormalizer
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * عرض كود الطفل وإعادة التسجيل على السيرفر — بدون مسح بيانات التطبيق.
 */
class ChildLinkHelpActivity : AppCompatActivity() {

    private var pollJob: Job? = null
    private lateinit var textCode: TextView
    private lateinit var textStatus: TextView
    private lateinit var textMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_child_link_help)

        textCode = findViewById(R.id.textChildCodeValue)
        textStatus = findViewById(R.id.textLinkStatus)
        textMessage = findViewById(R.id.textLinkMessage)

        refreshCodeDisplay()
        findViewById<MaterialButton>(R.id.btnCopyChildCode).setOnClickListener { copyCode() }
        findViewById<MaterialButton>(R.id.btnReregisterServer).setOnClickListener { reregisterOnServer() }
        findViewById<MaterialButton>(R.id.btnBackToGame).setOnClickListener { finish() }

        refreshServerStatus()
        startStatusPolling()
    }

    override fun onDestroy() {
        pollJob?.cancel()
        super.onDestroy()
    }

    private fun refreshCodeDisplay() {
        val code = ChildIdentity.displayCode(this).orEmpty()
        textCode.text = code.ifBlank { "—" }
    }

    private fun copyCode() {
        val code = textCode.text?.toString()?.trim().orEmpty()
        if (code.isBlank() || code == "—") {
            toast(getString(R.string.child_link_no_code), true)
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.register_child_code_label), code))
        toast(getString(R.string.child_code_copied), false)
    }

    private fun reregisterOnServer() {
        val btn = findViewById<MaterialButton>(R.id.btnReregisterServer)
        btn.isEnabled = false
        showMessage(getString(R.string.register_reregistering), false)
        lifecycleScope.launch {
            when (withContext(Dispatchers.IO) { LinkStateGuard.ensureServerRegistration(this@ChildLinkHelpActivity) }) {
                LinkStateGuard.Status.OK,
                LinkStateGuard.Status.WAITING_PARENT,
                LinkStateGuard.Status.REREGISTERED,
                -> {
                    showMessage(getString(R.string.child_link_reregister_ok), false)
                    refreshCodeDisplay()
                }
                LinkStateGuard.Status.FAILED -> showMessage(getString(R.string.child_link_reregister_failed), true)
                LinkStateGuard.Status.SKIPPED -> showMessage(getString(R.string.child_link_setup_required), true)
            }
            refreshServerStatus()
            btn.isEnabled = true
        }
    }

    private fun refreshServerStatus() {
        lifecycleScope.launch {
            val code = ChildIdentity.displayCode(this@ChildLinkHelpActivity)?.let {
                ChildCodeNormalizer.normalize(it)
            }.orEmpty()
            if (code.isBlank()) {
                textStatus.text = getString(R.string.child_link_no_code)
                return@launch
            }
            val label = when (
                withContext(Dispatchers.IO) { NetworkModule.queryChildRegistrationState(code) }
            ) {
                NetworkModule.ChildRegistrationState.LINKED ->
                    getString(R.string.child_link_status_linked)
                NetworkModule.ChildRegistrationState.WAITING ->
                    getString(R.string.child_link_status_waiting)
                NetworkModule.ChildRegistrationState.NOT_ON_SERVER ->
                    getString(R.string.child_link_status_not_on_server)
                NetworkModule.ChildRegistrationState.ERROR ->
                    getString(R.string.child_link_status_error)
            }
            textStatus.text = label
        }
    }

    private fun startStatusPolling() {
        pollJob?.cancel()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                refreshServerStatus()
                delay(4_000L)
            }
        }
    }

    private fun showMessage(msg: String, isError: Boolean) {
        textMessage.text = msg
        textMessage.setTextColor(
            ContextCompat.getColor(
                this,
                if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark,
            ),
        )
    }

    private fun toast(msg: String, isError: Boolean) {
        showMessage(msg, isError)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

package com.example.myrana.parent.ui

import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myrana.R
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.parent.ParentSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** إعدادات ولي الأمر: حذف البيانات، البريد، سجل التغييرات. */
class ParentSettingsActivity : AppCompatActivity() {

    private lateinit var inputRetention: EditText
    private lateinit var checkDailyEmail: CheckBox
    private lateinit var checkWeeklyEmail: CheckBox
    private lateinit var checkAlertSound: CheckBox
    private lateinit var textAuditLog: TextView
    private lateinit var textMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent_settings)

        inputRetention = findViewById(R.id.inputRetentionDays)
        checkDailyEmail = findViewById(R.id.checkDailyEmail)
        checkWeeklyEmail = findViewById(R.id.checkWeeklyEmail)
        checkAlertSound = findViewById(R.id.checkAlertSound)
        textAuditLog = findViewById(R.id.textAuditLog)
        textMessage = findViewById(R.id.textSettingsMessage)

        findViewById<Button>(R.id.btnSaveSettings).setOnClickListener { saveSettings() }
        findViewById<Button>(R.id.btnLoadAuditLog).setOnClickListener { loadAuditLog() }
        findViewById<Button>(R.id.btnSendDailyEmail).setOnClickListener { sendSummary("daily") }
        findViewById<Button>(R.id.btnSendWeeklyEmail).setOnClickListener { sendSummary("weekly") }

        loadSettings()
    }

    private fun parentEmail(): String = ParentSession.guardianEmail(this).orEmpty()

    private fun childCode(): String? = ParentSession.childCode(this)

    private fun loadSettings() {
        val email = parentEmail()
        if (email.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = GuardianApi.fetchGuardianSettings(email)) {
                is GuardianApi.ApiResult.GuardianSettingsLoaded -> withContext(Dispatchers.Main) {
                    val s = result.settings
                    inputRetention.setText((s["retention_days"] as? Number)?.toInt()?.toString() ?: "30")
                    checkDailyEmail.isChecked = s["email_daily_enabled"] == true
                    checkWeeklyEmail.isChecked = s["email_weekly_enabled"] == true
                    checkAlertSound.isChecked = s["alert_sound_enabled"] != false
                }
                is GuardianApi.ApiResult.Error -> withContext(Dispatchers.Main) {
                    textMessage.text = result.message
                }
                else -> Unit
            }
        }
    }

    private fun saveSettings() {
        val email = parentEmail()
        if (email.isBlank()) {
            Toast.makeText(this, R.string.parent_link_incomplete, Toast.LENGTH_SHORT).show()
            return
        }
        val retention = inputRetention.text.toString().toIntOrNull()?.coerceIn(7, 90) ?: 30
        val settings = mapOf(
            "retention_days" to retention,
            "email_daily_enabled" to checkDailyEmail.isChecked,
            "email_weekly_enabled" to checkWeeklyEmail.isChecked,
            "alert_sound_enabled" to checkAlertSound.isChecked,
        )
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = GuardianApi.saveGuardianSettings(email, settings)) {
                is GuardianApi.ApiResult.Ok -> withContext(Dispatchers.Main) {
                    textMessage.text = result.message
                    Toast.makeText(this@ParentSettingsActivity, result.message, Toast.LENGTH_SHORT).show()
                }
                is GuardianApi.ApiResult.Error -> withContext(Dispatchers.Main) {
                    textMessage.text = result.message
                }
                else -> Unit
            }
        }
    }

    private fun loadAuditLog() {
        val email = parentEmail()
        val code = childCode()
        if (email.isBlank()) return
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = GuardianApi.fetchAuditLog(email, code)) {
                is GuardianApi.ApiResult.AuditLog -> withContext(Dispatchers.Main) {
                    if (result.lines.isEmpty()) {
                        textAuditLog.text = getString(R.string.parent_audit_empty)
                    } else {
                        textAuditLog.text = result.lines.joinToString("\n\n")
                    }
                }
                is GuardianApi.ApiResult.Error -> withContext(Dispatchers.Main) {
                    textAuditLog.text = result.message
                }
                else -> Unit
            }
        }
    }

    private fun sendSummary(period: String) {
        val email = parentEmail()
        val code = childCode()
        if (email.isBlank() || code.isNullOrBlank()) {
            Toast.makeText(this, R.string.parent_link_incomplete, Toast.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = GuardianApi.sendEmailSummary(email, code, period)) {
                is GuardianApi.ApiResult.Ok -> withContext(Dispatchers.Main) {
                    textMessage.text = result.message
                    Toast.makeText(this@ParentSettingsActivity, result.message, Toast.LENGTH_SHORT).show()
                }
                is GuardianApi.ApiResult.Error -> withContext(Dispatchers.Main) {
                    textMessage.text = result.message
                }
                else -> Unit
            }
        }
    }
}

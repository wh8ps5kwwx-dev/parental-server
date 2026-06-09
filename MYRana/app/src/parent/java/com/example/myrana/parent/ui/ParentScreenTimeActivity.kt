package com.example.myrana.parent.ui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myrana.R
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.parent.ParentSession
import com.example.myrana.screentime.ScreenTimePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** لوحة وقت الاستخدام والمؤشرات لولي الأمر. */
class ParentScreenTimeActivity : AppCompatActivity() {

    private var pollJob: Job? = null
    private lateinit var textChildName: TextView
    private lateinit var textChildDevice: TextView
    private lateinit var textLastUpdate: TextView
    private lateinit var dotOnline: View
    private lateinit var barTodayTime: ProgressBar
    private lateinit var barAppsOpened: ProgressBar
    private lateinit var textTodayTime: TextView
    private lateinit var textAppsOpened: TextView
    private lateinit var textReport: TextView
    private lateinit var textMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent_screen_time)

        textChildName = findViewById(R.id.textChildName)
        textChildDevice = findViewById(R.id.textChildDevice)
        textLastUpdate = findViewById(R.id.textLastUpdate)
        dotOnline = findViewById(R.id.dotOnline)
        barTodayTime = findViewById(R.id.barTodayTime)
        barAppsOpened = findViewById(R.id.barAppsOpened)
        textTodayTime = findViewById(R.id.textTodayTime)
        textAppsOpened = findViewById(R.id.textAppsOpened)
        textReport = findViewById(R.id.textReport)
        textMessage = findViewById(R.id.textScreenTimeMessage)

        findViewById<Button>(R.id.btnSavePolicy).setOnClickListener { savePolicy() }
        findViewById<Button>(R.id.btnDailyReport).setOnClickListener { loadDailyReport() }
        findViewById<Button>(R.id.btnWeeklyReport).setOnClickListener { loadWeeklyReport() }

        loadPolicyIntoForm()
        refreshDashboard()
    }

    override fun onStart() {
        super.onStart()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                refreshDashboard()
                delay(15_000L)
            }
        }
    }

    override fun onStop() {
        pollJob?.cancel()
        super.onStop()
    }

    private fun childCode(): String? = ParentSession.childCode(this)

    private fun loadPolicyIntoForm() {
        val code = childCode() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = GuardianApi.fetchScreenTimePolicy(code)) {
                is GuardianApi.ApiResult.ScreenTimePolicyLoaded -> withContext(Dispatchers.Main) {
                    applyPolicyToForm(result.policy)
                }
                is GuardianApi.ApiResult.Error -> withContext(Dispatchers.Main) {
                    textMessage.text = result.message
                }
                else -> Unit
            }
        }
    }

    private fun applyPolicyToForm(policy: ScreenTimePolicy) {
        findViewById<EditText>(R.id.inputWarnMinutes).setText(policy.warnMinutes.toString())
        findViewById<EditText>(R.id.inputStrongWarnMinutes).setText(policy.strongWarnMinutes.toString())
        findViewById<EditText>(R.id.inputBlockMinutes).setText(policy.blockMinutes.toString())
        findViewById<EditText>(R.id.inputMaxApps).setText(policy.maxOpenApps.toString())
        findViewById<EditText>(R.id.inputSleepStart).setText(policy.sleepStart)
        findViewById<EditText>(R.id.inputSleepEnd).setText(policy.sleepEnd)
        findViewById<EditText>(R.id.inputMonitoredApps).setText(policy.monitoredPackages.joinToString("\n"))
        findViewById<EditText>(R.id.inputUnlimitedApps).setText(policy.unlimitedPackages.joinToString("\n"))
    }

    private fun buildPolicyFromForm(): ScreenTimePolicy {
        fun intField(id: Int, default: Int) =
            findViewById<EditText>(id).text.toString().toIntOrNull() ?: default
        fun lines(id: Int) = findViewById<EditText>(id).text.toString()
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return ScreenTimePolicy(
            monitoredPackages = lines(R.id.inputMonitoredApps),
            unlimitedPackages = lines(R.id.inputUnlimitedApps),
            warnMinutes = intField(R.id.inputWarnMinutes, 60),
            strongWarnMinutes = intField(R.id.inputStrongWarnMinutes, 90),
            blockMinutes = intField(R.id.inputBlockMinutes, 120),
            maxOpenApps = intField(R.id.inputMaxApps, 8),
            sleepStart = findViewById<EditText>(R.id.inputSleepStart).text.toString().ifBlank { "22:00" },
            sleepEnd = findViewById<EditText>(R.id.inputSleepEnd).text.toString().ifBlank { "07:00" },
        )
    }

    private fun savePolicy() {
        val code = childCode()
        if (code.isNullOrBlank()) {
            Toast.makeText(this, R.string.parent_link_incomplete, Toast.LENGTH_SHORT).show()
            return
        }
        val policy = buildPolicyFromForm()
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = GuardianApi.saveScreenTimePolicy(code, policy)) {
                is GuardianApi.ApiResult.Ok -> withContext(Dispatchers.Main) {
                    textMessage.text = result.message
                    Toast.makeText(this@ParentScreenTimeActivity, result.message, Toast.LENGTH_SHORT).show()
                }
                is GuardianApi.ApiResult.Error -> withContext(Dispatchers.Main) {
                    textMessage.text = result.message
                }
                else -> Unit
            }
        }
    }

    private fun refreshDashboard() {
        val code = childCode() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = GuardianApi.fetchChildDashboard(code)) {
                is GuardianApi.ApiResult.ChildDashboard -> withContext(Dispatchers.Main) {
                    renderDashboard(result)
                }
                is GuardianApi.ApiResult.Error -> withContext(Dispatchers.Main) {
                    textMessage.text = result.message
                }
                else -> Unit
            }
        }
    }

    private fun renderDashboard(d: GuardianApi.ChildDashboardData) {
        textChildName.text = d.childName
        textChildDevice.text = d.deviceName.ifBlank { d.childCode }
        dotOnline.setBackgroundColor(if (d.online) Color.parseColor("#4CAF50") else Color.parseColor("#9E9E9E"))
        textLastUpdate.text = if (d.lastSeenMs > 0L) {
            val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            getString(R.string.parent_last_update, fmt.format(Date(d.lastSeenMs)))
        } else {
            getString(R.string.parent_last_update_unknown)
        }

        val blockSec = d.policy.blockMinutes * 60L
        val todayMin = d.todaySeconds / 60
        val timePct = if (blockSec > 0) ((d.todaySeconds * 100) / blockSec).toInt().coerceIn(0, 100) else 0
        barTodayTime.progress = timePct
        barTodayTime.progressTintList = android.content.res.ColorStateList.valueOf(
            barColor(d.todaySeconds, d.policy.warnSeconds(), d.policy.blockSeconds())
        )
        textTodayTime.text = getString(R.string.parent_today_time_value, todayMin)

        val maxApps = d.policy.maxOpenApps.coerceAtLeast(1)
        val appsPct = ((d.appsOpened * 100) / (maxApps + 4)).coerceIn(0, 100)
        barAppsOpened.progress = appsPct
        barAppsOpened.progressTintList = android.content.res.ColorStateList.valueOf(
            when {
                d.appsOpened > maxApps + 2 -> Color.parseColor("#F44336")
                d.appsOpened > maxApps -> Color.parseColor("#FFC107")
                else -> Color.parseColor("#4CAF50")
            }
        )
        textAppsOpened.text = getString(R.string.parent_apps_opened_value, d.appsOpened, maxApps)
    }

    private fun barColor(seconds: Long, warnSec: Long, blockSec: Long): Int = when {
        seconds >= blockSec -> Color.parseColor("#F44336")
        seconds >= warnSec -> Color.parseColor("#FFC107")
        else -> Color.parseColor("#4CAF50")
    }

    private fun loadDailyReport() {
        val code = childCode() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = GuardianApi.fetchDailyReport(code)) {
                is GuardianApi.ApiResult.ReportText -> withContext(Dispatchers.Main) {
                    textReport.text = result.text
                }
                is GuardianApi.ApiResult.Error -> withContext(Dispatchers.Main) {
                    textReport.text = result.message
                }
                else -> Unit
            }
        }
    }

    private fun loadWeeklyReport() {
        val code = childCode() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = GuardianApi.fetchWeeklyUsage(code)) {
                is GuardianApi.ApiResult.UsageList -> withContext(Dispatchers.Main) {
                    if (result.items.isEmpty()) {
                        textReport.text = getString(R.string.parent_usage_empty)
                    } else {
                        val lines = result.items.mapIndexed { i, item ->
                            "${i + 1}. ${item.packageName} — ${item.totalSeconds / 60} د"
                        }
                        textReport.text = getString(R.string.parent_weekly_report_header) +
                            "\n" + lines.joinToString("\n")
                    }
                }
                is GuardianApi.ApiResult.Error -> withContext(Dispatchers.Main) {
                    textReport.text = result.message
                }
                else -> Unit
            }
        }
    }
}

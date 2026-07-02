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

/** لوحة وقت الاستخدام والمؤشرات والرسوم البيانية لولي الأمر. */
class ParentScreenTimeActivity : AppCompatActivity() {

    private var pollJob: Job? = null
    private lateinit var textChildName: TextView
    private lateinit var textChildDevice: TextView
    private lateinit var textChildCode: TextView
    private lateinit var textLastUpdate: TextView
    private lateinit var textChildPermissions: TextView
    private lateinit var dotOnline: View
    private lateinit var barTodayTime: ProgressBar
    private lateinit var barAppsOpened: ProgressBar
    private lateinit var textTodayTime: TextView
    private lateinit var textAppsOpened: TextView
    private lateinit var textAlertsStats: TextView
    private lateinit var textEducationalStats: TextView
    private lateinit var chartDailyUsage: SimpleBarChartView
    private lateinit var chartTopApps: SimpleBarChartView
    private lateinit var textReport: TextView
    private lateinit var textMessage: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent_screen_time)

        textChildName = findViewById(R.id.textChildName)
        textChildDevice = findViewById(R.id.textChildDevice)
        textChildCode = findViewById(R.id.textChildCode)
        textLastUpdate = findViewById(R.id.textLastUpdate)
        textChildPermissions = findViewById(R.id.textChildPermissions)
        dotOnline = findViewById(R.id.dotOnline)
        barTodayTime = findViewById(R.id.barTodayTime)
        barAppsOpened = findViewById(R.id.barAppsOpened)
        textTodayTime = findViewById(R.id.textTodayTime)
        textAppsOpened = findViewById(R.id.textAppsOpened)
        textAlertsStats = findViewById(R.id.textAlertsStats)
        textEducationalStats = findViewById(R.id.textEducationalStats)
        chartDailyUsage = findViewById(R.id.chartDailyUsage)
        chartTopApps = findViewById(R.id.chartTopApps)
        textReport = findViewById(R.id.textReport)
        textMessage = findViewById(R.id.textScreenTimeMessage)

        findViewById<Button>(R.id.btnSavePolicy).setOnClickListener { savePolicy() }
        findViewById<Button>(R.id.btnDailyReport).setOnClickListener { loadDailyReport() }
        findViewById<Button>(R.id.btnWeeklyReport).setOnClickListener { loadWeeklyReport() }

        loadPolicyIntoForm()
        refreshDashboard()
        loadCharts()
    }

    override fun onStart() {
        super.onStart()
        pollJob = lifecycleScope.launch {
            while (isActive) {
                refreshDashboard()
                loadCharts()
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
                    renderDashboard(result.data)
                }
                is GuardianApi.ApiResult.Error -> withContext(Dispatchers.Main) {
                    textMessage.text = result.message
                }
                else -> Unit
            }
        }
    }

    private fun loadCharts() {
        val code = childCode() ?: return
        lifecycleScope.launch(Dispatchers.IO) {
            when (val result = GuardianApi.fetchWeeklyChart(code)) {
                is GuardianApi.ApiResult.WeeklyChart -> withContext(Dispatchers.Main) {
                    renderCharts(result.data)
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
        textChildDevice.text = d.deviceName.ifBlank { "—" }
        textChildCode.text = getString(R.string.parent_child_code_label, d.childCode)
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

        textAlertsStats.text = getString(
            R.string.parent_alerts_stats,
            d.alertsToday,
            d.alertsWeek,
            0,
        )
        textEducationalStats.text = getString(
            R.string.parent_educational_stats,
            d.educationalSeconds / 60,
            d.monitoredSeconds / 60,
        )
        textChildPermissions.text = ParentPermissionsFormatter.summary(this, d.permissionsOk, d.permissions)

        if (d.topAppsToday.isNotEmpty()) {
            val topBars = d.topAppsToday.take(6).map { row ->
                val pkg = row["package_name"]?.toString().orEmpty()
                val sec = (row["total_seconds"] as? Number)?.toLong() ?: 0L
                val edu = row["educational"] == true
                SimpleBarChartView.BarEntry(
                    label = shortPkg(pkg),
                    value = (sec / 60f).coerceAtLeast(0f),
                    color = if (edu) Color.parseColor("#2196F3") else Color.parseColor("#FF9800"),
                )
            }
            chartTopApps.setData(topBars, "د")
        }
    }

    private fun renderCharts(d: GuardianApi.WeeklyChartData) {
        textAlertsStats.text = getString(
            R.string.parent_alerts_stats,
            d.alertsToday,
            d.alertsWeek,
            d.sleepViolationsWeek,
        )

        val dayBars = d.usageByDay.map { row ->
            val day = row["day"]?.toString().orEmpty()
            val sec = (row["total_seconds"] as? Number)?.toLong() ?: 0L
            val label = if (day.length >= 5) day.substring(5) else day
            val minutes = sec / 60f
            SimpleBarChartView.BarEntry(
                label = label,
                value = minutes.coerceAtLeast(0f),
                color = barColorForMinutes(minutes.toLong()),
            )
        }
        chartDailyUsage.setData(dayBars, "د")

        if (d.topApps.isNotEmpty()) {
            val appBars = d.topApps.take(6).map { row ->
                val pkg = row["package_name"]?.toString().orEmpty()
                val sec = (row["total_seconds"] as? Number)?.toLong() ?: 0L
                val edu = d.educationalApps.any {
                    it["package_name"]?.toString().equals(pkg, ignoreCase = true)
                }
                SimpleBarChartView.BarEntry(
                    label = shortPkg(pkg),
                    value = (sec / 60f).coerceAtLeast(0f),
                    color = if (edu) Color.parseColor("#2196F3") else Color.parseColor("#FF9800"),
                )
            }
            chartTopApps.setData(appBars, "د")
        }
    }

    private fun shortPkg(pkg: String): String {
        val parts = pkg.split(".")
        return parts.lastOrNull()?.take(8)?.ifBlank { pkg.take(8) } ?: pkg.take(8)
    }

    private fun barColor(seconds: Long, warnSec: Long, blockSec: Long): Int = when {
        seconds >= blockSec -> Color.parseColor("#F44336")
        seconds >= warnSec -> Color.parseColor("#FFC107")
        else -> Color.parseColor("#4CAF50")
    }

    private fun barColorForMinutes(minutes: Long): Int = when {
        minutes >= 120 -> Color.parseColor("#F44336")
        minutes >= 60 -> Color.parseColor("#FFC107")
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

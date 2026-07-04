package com.example.myrana.parent.ui

import android.content.Intent
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.myrana.R
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.parent.ParentReportCache
import com.example.myrana.parent.ParentSession
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParentReportsActivity : ParentShellActivity() {

    override fun screenTitle(): String = getString(R.string.parent_hub_reports)

    override fun contentLayoutId(): Int = R.layout.content_parent_reports

    override fun onShellReady() {
        findViewById<MaterialButton>(R.id.btnRequestUsageReport).setOnClickListener { requestUsageReport() }
        findViewById<MaterialButton>(R.id.btnOpenScreenTime).setOnClickListener {
            startActivity(Intent(this, ParentScreenTimeActivity::class.java))
        }
        loadCharts()
    }

    private fun loadCharts() {
        val code = ParentControlHelper.requireChildCode(this) ?: return
        lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { GuardianApi.fetchWeeklyChart(code, 7) }) {
                is GuardianApi.ApiResult.WeeklyChart -> {
                    ParentReportCache.saveWeeklyChart(this@ParentReportsActivity, code, result.data)
                    ParentDashboardBinder.bindReportsCharts(this@ParentReportsActivity, result.data)
                }
                else -> {
                    ParentReportCache.loadWeeklyChart(this@ParentReportsActivity, code)?.let { cached ->
                        ParentDashboardBinder.bindReportsCharts(this@ParentReportsActivity, cached)
                    }
                }
            }
        }
    }

    private fun requestUsageReport() {
        val childCode = ParentSession.childCode(this)
        val email = ParentSession.guardianEmail(this)
        if (childCode.isNullOrBlank() || email.isNullOrBlank()) {
            Toast.makeText(this, "اربط الطفل أولاً", Toast.LENGTH_SHORT).show()
            return
        }
        val status = findViewById<TextView>(R.id.textReportsMessage)
        val btn = findViewById<MaterialButton>(R.id.btnRequestUsageReport)
        btn.isEnabled = false
        status.text = getString(R.string.parent_usage_loading)
        lifecycleScope.launch {
            val requestResult = withContext(Dispatchers.IO) {
                GuardianApi.requestUsageFromChild(childCode, email)
            }
            if (requestResult is GuardianApi.ApiResult.Error) {
                status.text = requestResult.message
                Toast.makeText(this@ParentReportsActivity, requestResult.message, Toast.LENGTH_SHORT).show()
                btn.isEnabled = true
                return@launch
            }
            delay(5_000L)
            when (val report = withContext(Dispatchers.IO) { GuardianApi.fetchWeeklyUsage(childCode, 7) }) {
                is GuardianApi.ApiResult.UsageList -> {
                    status.text = getString(R.string.parent_usage_report_title) + " — ${report.items.size} تطبيق"
                    loadCharts()
                    startActivity(
                        Intent(this@ParentReportsActivity, ParentScreenTimeActivity::class.java)
                            .putExtra(
                                ParentScreenTimeActivity.EXTRA_REPORT_PERIOD,
                                ParentScreenTimeActivity.PERIOD_WEEKLY,
                            ),
                    )
                }
                is GuardianApi.ApiResult.Error -> {
                    status.text = report.message
                    Toast.makeText(this@ParentReportsActivity, report.message, Toast.LENGTH_SHORT).show()
                }
                else -> {
                    status.text = "فشل تحميل التقرير"
                    Toast.makeText(this@ParentReportsActivity, "فشل تحميل التقرير", Toast.LENGTH_SHORT).show()
                }
            }
            btn.isEnabled = true
        }
    }
}

package com.example.myrana.parent.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.myrana.R
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.parent.ParentAlertNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ParentAlertsActivity : ParentShellActivity() {

    override fun screenTitle(): String = getString(R.string.parent_hub_alerts)

    override fun contentLayoutId(): Int = R.layout.content_parent_alerts

    override fun onShellReady() {
        findViewById<Button>(R.id.btnRefreshAlerts).setOnClickListener { refreshAlerts() }
        refreshAlerts()
    }

    private fun refreshAlerts() {
        val childCode = ParentControlHelper.requireChildCode(this) ?: return
        lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { GuardianApi.fetchAlerts(childCode) }) {
                is GuardianApi.ApiResult.Alerts -> {
                    val body = findViewById<TextView>(R.id.textAlertsBody)
                    if (result.error != null) {
                        body.text = result.error
                    } else if (result.lines.isEmpty()) {
                        body.text = getString(R.string.parent_alerts_waiting)
                        Toast.makeText(this@ParentAlertsActivity, getString(R.string.parent_alerts_waiting), Toast.LENGTH_SHORT).show()
                    } else {
                        body.text = "${getString(R.string.parent_alerts_preview_title)}\n\n${result.lines.joinToString("\n\n")}"
                        ParentAlertNotifier.notifyIfNew(this@ParentAlertsActivity, result.lines)
                        Toast.makeText(this@ParentAlertsActivity, "تم تحديث التنبيهات", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> Toast.makeText(this@ParentAlertsActivity, "فشل جلب التنبيهات", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

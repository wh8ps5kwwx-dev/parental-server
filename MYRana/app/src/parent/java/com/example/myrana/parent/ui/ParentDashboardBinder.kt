package com.example.myrana.parent.ui

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import com.example.myrana.R
import com.example.myrana.data.remote.GuardianApi

/** ربط عناصر لوحة التحكم الجديدة بالبيانات الحالية — عرض فقط. */
object ParentDashboardBinder {

    fun bindDashboard(activity: Activity, data: GuardianApi.ChildDashboardData) {
        activity.findViewById<TextView>(R.id.textDashChildName)?.text =
            data.childName.ifBlank { activity.getString(R.string.parent_dash_child_placeholder) }
        activity.findViewById<TextView>(R.id.textDashDeviceName)?.text =
            data.deviceName.ifBlank { "Android" }

        val online = data.online
        activity.findViewById<TextView>(R.id.textDashOnlineStatus)?.apply {
            text = if (online) {
                activity.getString(R.string.parent_status_online_short)
            } else {
                activity.getString(R.string.parent_status_offline_short)
            }
            setTextColor(
                activity.getColor(
                    if (online) R.color.parent_online else R.color.parent_offline,
                ),
            )
        }
        activity.findViewById<View>(R.id.viewDashOnlineDot)?.setBackgroundResource(
            if (online) R.drawable.bg_dot_online else R.drawable.bg_dot_offline,
        )

        activity.findViewById<TextView>(R.id.textDashConnection)?.text =
            if (online) activity.getString(R.string.parent_status_online) else activity.getString(R.string.parent_status_offline)
        activity.findViewById<TextView>(R.id.textDashProtection)?.text =
            if (data.permissionsOk) {
                activity.getString(R.string.parent_protection_ok)
            } else {
                activity.getString(R.string.parent_protection_needs)
            }
        activity.findViewById<TextView>(R.id.textDashBattery)?.text = "—"

        val minutes = (data.todaySeconds / 60).toInt()
        val hours = minutes / 60
        val mins = minutes % 60
        activity.findViewById<TextView>(R.id.textStatUsageValue)?.text =
            if (hours > 0) "${hours} س ${mins} د" else "${mins} د"
        activity.findViewById<TextView>(R.id.textStatAlertsValue)?.text =
            data.alertsToday.toString()
        activity.findViewById<TextView>(R.id.textStatAppsValue)?.text =
            data.appsOpened.toString()
        activity.findViewById<TextView>(R.id.textStatBlockedValue)?.text = "—"

        val blockMin = data.policy.blockMinutes.coerceAtLeast(1)
        val pct = ((minutes * 100) / blockMin).coerceIn(0, 100)
        activity.findViewById<ProgressBar>(R.id.progressStatUsage)?.progress = pct
        activity.findViewById<TextView>(R.id.textStatUsageSub)?.text =
            "من أصل ${blockMin} د"

        val topApps = buildTopAppsText(activity, data.topAppsToday)
        activity.findViewById<TextView>(R.id.textDashTopApps)?.text = topApps

        activity.findViewById<TextView>(R.id.textPermissionsMini)?.text =
            ParentPermissionsFormatter.summary(activity, data.permissionsOk, data.permissions)
    }

    fun bindWeeklyChart(activity: Activity, chartData: GuardianApi.WeeklyChartData) {
        val chart = activity.findViewById<SimpleBarChartView>(R.id.chartWeeklyUsage) ?: return
        val purple = Color.parseColor("#8B5CF6")
        val purpleDark = Color.parseColor("#6D28D9")
        val bars = chartData.usageByDay.mapIndexed { index, row ->
            val label = row["day"]?.toString()?.takeLast(3) ?: "?"
            val hours = ((row["total_seconds"] as? Number)?.toLong() ?: 0L) / 3600f
            SimpleBarChartView.BarEntry(
                label = label,
                value = hours,
                color = if (index == chartData.usageByDay.lastIndex) purpleDark else purple,
            )
        }
        chart.setData(bars, "س")
    }

    fun updateAlertsPreview(activity: Activity, text: CharSequence) {
        activity.findViewById<TextView>(R.id.textAlertsPreview)?.text = text
        activity.findViewById<TextView>(R.id.textDashAlertsPreview)?.text = text
    }

    private fun buildTopAppsText(
        activity: Activity,
        topApps: List<Map<String, Any?>>,
    ): String {
        if (topApps.isEmpty()) {
            return activity.getString(R.string.parent_dash_top_apps_empty)
        }
        return topApps.take(4).joinToString("\n") { row ->
            val pkg = row["package_name"]?.toString().orEmpty()
            val sec = (row["total_seconds"] as? Number)?.toLong() ?: 0L
            val m = (sec / 60).toInt()
            val name = pkg.substringAfterLast('.').ifBlank { pkg }
            "• $name — ${m} د"
        }
    }
}

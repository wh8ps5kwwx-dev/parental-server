package com.example.myrana.parent.ui



import android.app.Activity

import android.graphics.Color

import android.view.View

import android.widget.ProgressBar

import android.widget.TextView

import androidx.core.content.ContextCompat

import com.example.myrana.R

import com.example.myrana.data.remote.GuardianApi

import java.text.SimpleDateFormat

import java.util.Locale



/** ربط عناصر لوحة التحكم الجديدة بالبيانات الحالية — عرض فقط. */

object ParentDashboardBinder {



    private val dayLabelFmt = SimpleDateFormat("MM-dd", Locale.US)

    private val dayParseFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)



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

                ContextCompat.getColor(

                    activity,

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
            permissionCountLabel(activity, data.permissions, data.permissionsOk)

        activity.findViewById<TextView>(R.id.textDashBattery)?.text =
            batteryLabel(activity, data.batteryPct, data.permissions)



        val minutes = (data.todaySeconds / 60).toInt()

        val hours = minutes / 60

        val mins = minutes % 60

        activity.findViewById<TextView>(R.id.textStatUsageValue)?.text =

            if (hours > 0) "${hours} س ${mins} د" else "${mins} د"

        activity.findViewById<TextView>(R.id.textStatAlertsValue)?.text =

            data.alertsToday.toString()

        activity.findViewById<TextView>(R.id.textStatAppsValue)?.text =

            data.appsOpened.toString()

        activity.findViewById<TextView>(R.id.textStatBlockedValue)?.text =
            data.alertsWeek.toString()

        activity.findViewById<TextView>(R.id.textStatAlertsSub)?.apply {
            if (data.alertsToday > 0) {
                visibility = View.VISIBLE
                text = activity.getString(R.string.parent_stat_alerts_new)
            } else {
                visibility = View.GONE
            }
        }

        activity.findViewById<TextView>(R.id.textDashLastSeen)?.text =
            lastSeenLabel(activity, data.lastSeenMs, data.online)

        val blockMin = data.policy.blockMinutes.coerceAtLeast(1)
        val pct = ((minutes * 100) / blockMin).coerceIn(0, 100)

        activity.findViewById<ProgressBar>(R.id.progressStatUsage)?.progress = pct

        activity.findViewById<TextView>(R.id.textStatUsageSub)?.text =
            if (blockMin >= 60) {
                "من أصل ${blockMin / 60} س"
            } else {
                "من أصل ${blockMin} د"
            }



        val topApps = buildTopAppsText(activity, data.topAppsToday)

        activity.findViewById<TextView>(R.id.textDashTopApps)?.text = topApps



        activity.findViewById<TextView>(R.id.textPermissionsMini)?.text =

            ParentPermissionsFormatter.summary(activity, data.permissionsOk, data.permissions)

    }



    fun bindWeeklyChart(activity: Activity, chartData: GuardianApi.WeeklyChartData) {

        bindWeeklyUsageChart(activity.findViewById(R.id.chartWeeklyUsage), chartData)

    }



    fun bindReportsCharts(activity: Activity, chartData: GuardianApi.WeeklyChartData) {

        bindWeeklyUsageChart(activity.findViewById(R.id.chartReportsWeekly), chartData)

        bindTopAppsChart(activity.findViewById(R.id.chartReportsTopApps), chartData)

    }

    fun bindMainCharts(activity: Activity, chartData: GuardianApi.WeeklyChartData) {
        bindWeeklyUsageChart(activity.findViewById(R.id.chartWeeklyUsage), chartData)
    }

    fun bindEducationPieChart(chart: SimplePieChartView?, chartData: GuardianApi.WeeklyChartData) {
        if (chart == null) return
        val eduPkgs = chartData.educationalApps
            .mapNotNull { it["package_name"]?.toString()?.lowercase() }
            .toSet()
        var eduSec = 0L
        var funSec = 0L
        for (row in chartData.topApps) {
            val pkg = row["package_name"]?.toString().orEmpty().lowercase()
            val sec = (row["total_seconds"] as? Number)?.toLong() ?: 0L
            if (pkg in eduPkgs) eduSec += sec else funSec += sec
        }
        val eduMin = (eduSec / 60f).coerceAtLeast(0f)
        val funMin = (funSec / 60f).coerceAtLeast(0f)
        if (eduMin <= 0f && funMin <= 0f) {
            chart.setData(emptyList())
            return
        }
        chart.setData(
            listOf(
                SimplePieChartView.Slice(
                    label = chart.context.getString(R.string.parent_chart_edu_label),
                    value = eduMin,
                    color = Color.parseColor("#2196F3"),
                ),
                SimplePieChartView.Slice(
                    label = chart.context.getString(R.string.parent_chart_fun_label),
                    value = funMin,
                    color = Color.parseColor("#FF9800"),
                ),
            ),
        )
    }



    fun bindWeeklyUsageChart(chart: SimpleBarChartView?, chartData: GuardianApi.WeeklyChartData) {

        if (chart == null) return

        val purple = Color.parseColor("#8B5CF6")

        val purpleDark = Color.parseColor("#6D28D9")

        val bars = chartData.usageByDay.mapIndexed { index, row ->

            val day = row["day"]?.toString().orEmpty()

            val sec = (row["total_seconds"] as? Number)?.toLong() ?: 0L

            val minutes = sec / 60f

            SimpleBarChartView.BarEntry(

                label = formatDayLabel(day),

                value = minutes.coerceAtLeast(0f),

                color = if (index == chartData.usageByDay.lastIndex) purpleDark else purple,

            )

        }

        chart.setData(bars, "د")

        val avgMin = (chartData.avgDailyScreenSeconds / 60f).coerceAtLeast(0f)
        if (avgMin > 0f) {
            chart.setReferenceLine(avgMin, "معدل ${avgMin.toInt()}د")
        } else {
            chart.setReferenceLine(null)
        }

    }



    fun bindTopAppsChart(chart: SimpleBarChartView?, chartData: GuardianApi.WeeklyChartData) {

        if (chart == null) return

        if (chartData.topApps.isEmpty()) {

            chart.setData(emptyList(), "")

            chart.setReferenceLine(null)

            return

        }

        val appBars = chartData.topApps.take(6).map { row ->

            val pkg = row["package_name"]?.toString().orEmpty()

            val minutes = appAvgMinutes(row, chartData.days)

            val edu = chartData.educationalApps.any {

                it["package_name"]?.toString().equals(pkg, ignoreCase = true)

            }

            SimpleBarChartView.BarEntry(

                label = shortPkg(pkg),

                value = minutes,

                color = if (edu) Color.parseColor("#2196F3") else Color.parseColor("#FF9800"),

            )

        }

        chart.setData(appBars, "د/ي")

        chart.setReferenceLine(null)

    }



    fun appAvgMinutes(row: Map<String, Any?>, days: Int): Float {

        val avgSec = (row["avg_seconds_per_day"] as? Number)?.toLong()

        val sec = avgSec ?: (((row["total_seconds"] as? Number)?.toLong() ?: 0L) / days.coerceAtLeast(1))

        return (sec / 60f).coerceAtLeast(0f)

    }



    private fun lastSeenLabel(activity: Activity, lastSeenMs: Long, online: Boolean): String {
        if (online) {
            return activity.getString(R.string.parent_dash_last_seen_now)
        }
        if (lastSeenMs <= 0L) {
            return activity.getString(R.string.parent_dash_last_seen_unknown)
        }
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return fmt.format(java.util.Date(lastSeenMs))
    }

    private fun formatDayLabel(day: String): String {

        if (day.length >= 10) {

            return try {

                dayLabelFmt.format(dayParseFmt.parse(day)!!)

            } catch (_: Exception) {

                day.substring(5)

            }

        }

        return day

    }



    private fun shortPkg(pkg: String): String {

        val name = pkg.substringAfterLast('.').ifBlank { pkg }

        return if (name.length > 8) name.take(7) + "…" else name

    }



    private fun permissionCountLabel(
        activity: Activity,
        permissions: Map<String, Any?>,
        permissionsOk: Boolean,
    ): String {
        if (permissions.isEmpty()) {
            return if (permissionsOk) {
                activity.getString(R.string.parent_protection_ok)
            } else {
                activity.getString(R.string.parent_protection_needs)
            }
        }
        val granted = ParentPermissionsFormatter.grantedCount(permissions)
        val total = ParentPermissionsFormatter.totalCount(permissions)
        return activity.getString(R.string.parent_protection_count, granted, total)
    }

    private fun batteryLabel(
        activity: Activity,
        batteryPct: Int,
        permissions: Map<String, Any?>,
    ): String {
        val pctText = when (batteryPct) {
            in 0..100 -> "$batteryPct%"
            else -> "—"
        }
        val permOk = ParentPermissionsFormatter.batteryPermissionOk(permissions)
        return when {
            permissions.isEmpty() -> pctText
            permOk -> pctText
            pctText == "—" -> activity.getString(R.string.parent_battery_perm_missing)
            else -> "$pctText\n⚠"
        }
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



package com.example.myrana.parent.ui



import android.app.Activity

import android.graphics.Color

import android.view.View

import android.widget.ProgressBar

import android.widget.TextView

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

        bindWeeklyUsageChart(activity.findViewById(R.id.chartWeeklyUsage), chartData)

    }



    fun bindReportsCharts(activity: Activity, chartData: GuardianApi.WeeklyChartData) {

        bindWeeklyUsageChart(activity.findViewById(R.id.chartReportsWeekly), chartData)

        bindTopAppsChart(activity.findViewById(R.id.chartReportsTopApps), chartData)

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

    }



    fun bindTopAppsChart(chart: SimpleBarChartView?, chartData: GuardianApi.WeeklyChartData) {

        if (chart == null) return

        if (chartData.topApps.isEmpty()) {

            chart.setData(emptyList(), "")

            return

        }

        val appBars = chartData.topApps.take(6).map { row ->

            val pkg = row["package_name"]?.toString().orEmpty()

            val sec = (row["total_seconds"] as? Number)?.toLong() ?: 0L

            val edu = chartData.educationalApps.any {

                it["package_name"]?.toString().equals(pkg, ignoreCase = true)

            }

            SimpleBarChartView.BarEntry(

                label = shortPkg(pkg),

                value = (sec / 60f).coerceAtLeast(0f),

                color = if (edu) Color.parseColor("#2196F3") else Color.parseColor("#FF9800"),

            )

        }

        chart.setData(appBars, "د")

    }



    fun updateAlertsPreview(activity: Activity, text: CharSequence) {

        activity.findViewById<TextView>(R.id.textAlertsPreview)?.text = text

        activity.findViewById<TextView>(R.id.textDashAlertsPreview)?.text = text

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



package com.example.myrana.parent

import android.content.Context
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.util.ChildCodeNormalizer
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * حفظ التقارير محلياً على جوال الأم — تُعرض عند فقدان بيانات Render.
 */
object ParentReportCache {

    private const val PREFS = "myrana_parent_report_cache"
    private val gson = Gson()

    fun saveWeeklyChart(context: Context, childCode: String, data: GuardianApi.WeeklyChartData) {
        val code = ChildCodeNormalizer.normalize(childCode)
        if (code.isBlank()) return
        val payload = mapOf(
            "usage_by_day" to data.usageByDay,
            "top_apps" to data.topApps,
            "educational_apps" to data.educationalApps,
            "alerts_today" to data.alertsToday,
            "alerts_week" to data.alertsWeek,
            "sleep_violations_week" to data.sleepViolationsWeek,
            "days" to data.days,
            "avg_daily_screen_seconds" to data.avgDailyScreenSeconds,
        )
        prefs(context).edit()
            .putString(chartKey(code), gson.toJson(payload))
            .putLong(savedAtKey(code), System.currentTimeMillis())
            .apply()
    }

    fun loadWeeklyChart(context: Context, childCode: String): GuardianApi.WeeklyChartData? {
        val code = ChildCodeNormalizer.normalize(childCode)
        if (code.isBlank()) return null
        val raw = prefs(context).getString(chartKey(code), null) ?: return null
        return try {
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val json: Map<String, Any?> = gson.fromJson(raw, mapType)
            @Suppress("UNCHECKED_CAST")
            GuardianApi.WeeklyChartData(
                usageByDay = (json["usage_by_day"] as? List<Map<String, Any?>>).orEmpty(),
                topApps = (json["top_apps"] as? List<Map<String, Any?>>).orEmpty(),
                educationalApps = (json["educational_apps"] as? List<Map<String, Any?>>).orEmpty(),
                alertsToday = (json["alerts_today"] as? Number)?.toInt() ?: 0,
                alertsWeek = (json["alerts_week"] as? Number)?.toInt() ?: 0,
                sleepViolationsWeek = (json["sleep_violations_week"] as? Number)?.toInt() ?: 0,
                days = (json["days"] as? Number)?.toInt()?.coerceIn(1, 30) ?: 7,
                avgDailyScreenSeconds = (json["avg_daily_screen_seconds"] as? Number)?.toLong() ?: 0L,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun chartKey(childCode: String) = "weekly_chart_${childCode.uppercase()}"
    private fun savedAtKey(childCode: String) = "weekly_saved_at_${childCode.uppercase()}"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

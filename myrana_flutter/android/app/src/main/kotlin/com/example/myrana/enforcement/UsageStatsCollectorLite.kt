package com.example.myrana.enforcement

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context

object UsageStatsCollectorLite {

    fun queryForegroundSecondsSince(context: Context, sinceEpochMs: Long): Map<String, Long> {
        if (!UsageAccessHelper.hasUsageAccess(context)) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        if (end <= sinceEpochMs) return emptyMap()

        @Suppress("DEPRECATION")
        val stats: List<UsageStats> = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            sinceEpochMs,
            end,
        ) ?: return emptyMap()

        val result = mutableMapOf<String, Long>()
        for (stat in stats) {
            val pkg = stat.packageName ?: continue
            if (pkg.startsWith("com.example.myrana_flutter")) continue
            val sec = stat.totalTimeInForeground / 1000L
            if (sec > 0L) result[pkg] = sec
        }
        return result
    }

    fun queryToday(context: Context): Map<String, Long> {
        return queryForegroundSecondsSince(context, startOfTodayEpochMs())
    }

    private fun startOfTodayEpochMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}


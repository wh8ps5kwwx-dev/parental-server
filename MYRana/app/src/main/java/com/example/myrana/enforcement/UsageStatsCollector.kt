package com.example.myrana.enforcement

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context

/** قراءة وقت استخدام التطبيقات من النظام (للتقرير في الخلفية). */
object UsageStatsCollector {

    /**
     * ثوانٍ في المقدمة لكل حزمة بين [sinceEpochMs] والآن.
     * يتطلب صلاحية «الوصول لبيانات الاستخدام».
     */
    fun foregroundSecondsSince(context: Context, sinceEpochMs: Long): Map<String, Long> {
        if (!UsageAccessHelper.hasUsageAccess(context)) return emptyMap()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val end = System.currentTimeMillis()
        if (end <= sinceEpochMs) return emptyMap()

        @Suppress("DEPRECATION")
        val stats: List<UsageStats> = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            sinceEpochMs,
            end
        ) ?: return emptyMap()

        val result = mutableMapOf<String, Long>()
        for (stat in stats) {
            val pkg = stat.packageName ?: continue
            if (pkg.startsWith("com.example.myrana")) continue
            val sec = stat.totalTimeInForeground / 1000L
            if (sec > 0L) {
                result[pkg] = sec
            }
        }
        return result
    }
}

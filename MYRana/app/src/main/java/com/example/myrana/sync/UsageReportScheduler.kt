package com.example.myrana.sync

import android.content.Context

/**
 * جدولة رفع تقرير استخدام الجهاز: مرة كل **24 ساعة** تلقائياً.
 * الطلب الفوري من الأم (`request_usage`) يتجاوز المدة.
 */
object UsageReportScheduler {

    /** 24 ساعة بالميلي ثانية. */
    const val INTERVAL_MS: Long = 24L * 60L * 60L * 1000L

    private const val PREFS = "myrana_usage_report"
    private const val KEY_LAST_UPLOAD_MS = "last_usage_upload_ms"

    /** هل حان موعد الرفع التلقائي؟ */
    fun isDue(context: Context): Boolean {
        val last = prefs(context).getLong(KEY_LAST_UPLOAD_MS, 0L)
        if (last == 0L) return true
        return System.currentTimeMillis() - last >= INTERVAL_MS
    }

    /** بداية فترة الجمع (من آخر رفع ناجح). */
    fun collectionSinceEpochMs(context: Context): Long {
        val last = prefs(context).getLong(KEY_LAST_UPLOAD_MS, 0L)
        return if (last == 0L) {
            System.currentTimeMillis() - INTERVAL_MS
        } else {
            last
        }
    }

    fun markUploaded(context: Context) {
        prefs(context).edit()
            .putLong(KEY_LAST_UPLOAD_MS, System.currentTimeMillis())
            .apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

package com.example.myrana.sync

import android.content.Context
import com.example.myrana.data.repo.UsageSyncRepository
import com.example.myrana.enforcement.EnforcementEngine
import com.example.myrana.enforcement.UsageStatsCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * رفع تقرير استخدام الجهاز إلى السيرفر.
 * - تلقائياً: مرة كل [UsageReportScheduler.INTERVAL_MS] (24 ساعة).
 * - فوراً: عند أمر الأم `request_usage`.
 * - يُحفظ محلياً أولاً ثم يُرفع عند توفر النت.
 */
object UsageUploadHelper {

    /** رفع إن حان موعد الـ 24 ساعة. */
    suspend fun uploadPeriodicIfDue(context: Context, childCode: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!UsageReportScheduler.isDue(context)) return@withContext false
            uploadNow(context, childCode)
        }

    /** رفع فوري (يتجاوز شرط الـ 24 ساعة). */
    suspend fun uploadNow(context: Context, childCode: String): Boolean =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val usageSync = UsageSyncRepository.get(app)

            EnforcementEngine.get(app).persistPendingUsage()

            val todayStats = UsageStatsCollector.foregroundSecondsSince(
                app,
                UsageSyncRepository.startOfTodayEpochMs(),
            )
            usageSync.reconcileWithSystemStats(todayStats)

            val synced = usageSync.trySync(childCode)
            if (synced) {
                UsageReportScheduler.markUploaded(app)
            }
            synced
        }
}

package com.example.myrana.data.repo

import android.content.Context
import com.example.myrana.data.local.AppDatabase
import com.example.myrana.data.local.PendingOutboxEntity
import com.example.myrana.data.local.UsageSyncBufferEntity
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.network.NetworkMonitor
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * قاعدة بيانات محلية لاستهلاك التطبيقات — تُحفظ أولاً ثم تُرفع للأم عند توفر النت.
 */
class UsageSyncRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).usageSyncDao()
    private val outboxDao = AppDatabase.getInstance(appContext).pendingOutboxDao()
    private val gson = Gson()
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** دمج ثوانٍ جديدة في التخزين المحلي (لا يُمسح عند فشل الرفع). */
    suspend fun mergeUsage(
        secondsByPackage: Map<String, Long>,
        day: String = today(),
    ) = withContext(Dispatchers.IO) {
        mergeUsageInternal(secondsByPackage, day)
    }

    /**
     * مطابقة مع إحصائيات النظام — يُضاف الفرق فقط (بدون عدّ مزدوج).
     */
    suspend fun reconcileWithSystemStats(
        secondsByPackage: Map<String, Long>,
        day: String = today(),
    ) = withContext(Dispatchers.IO) {
        if (secondsByPackage.isEmpty()) return@withContext
        val gaps = mutableMapOf<String, Long>()
        for ((pkg, statSec) in secondsByPackage) {
            if (statSec <= 0L) continue
            val normalized = pkg.trim().lowercase()
            if (normalized.isBlank() || normalized.startsWith("com.example.myrana")) continue
            val currentTotal = dao.get(day, normalized)?.totalSeconds ?: 0L
            if (statSec > currentTotal) {
                gaps[normalized] = statSec - currentTotal
            }
        }
        mergeUsageInternal(gaps, day)
    }

    /** محاولة رفع كل ما هو معلّق — تُرجع true إذا لم يبقَ شيء أو نجح الكل. */
    suspend fun trySync(childCode: String): Boolean = withContext(Dispatchers.IO) {
        importLegacyOutbox()
        if (!NetworkMonitor.isOnline(appContext)) return@withContext dao.countPending() == 0
        val pending = dao.listPending()
        if (pending.isEmpty()) return@withContext true

        val entries = pending.mapNotNull { row ->
            val delta = row.pendingSeconds()
            if (delta <= 0L) return@mapNotNull null
            Triple(row.day, row.packageName, delta)
        }
        if (entries.isEmpty()) return@withContext true

        val ok = NetworkModule.uploadUsageEntriesSync(childCode, entries)
        if (!ok) return@withContext false

        val now = System.currentTimeMillis()
        for (row in pending) {
            if (row.pendingSeconds() <= 0L) continue
            dao.markUploaded(row.day, row.packageName, row.totalSeconds, now)
        }
        true
    }

    suspend fun pendingCount(): Int = withContext(Dispatchers.IO) {
        importLegacyOutbox()
        dao.countPending()
    }

    private suspend fun mergeUsageInternal(
        secondsByPackage: Map<String, Long>,
        day: String,
    ) {
        if (secondsByPackage.isEmpty()) return
        val now = System.currentTimeMillis()
        for ((pkg, sec) in secondsByPackage) {
            if (sec <= 0L) continue
            val normalized = pkg.trim().lowercase()
            if (normalized.isBlank() || normalized.startsWith("com.example.myrana")) continue
            val existing = dao.get(day, normalized)
            if (existing == null) {
                dao.upsert(
                    UsageSyncBufferEntity(
                        day = day,
                        packageName = normalized,
                        totalSeconds = sec,
                        uploadedSeconds = 0L,
                        updatedAtMs = now,
                    )
                )
            } else {
                dao.upsert(
                    existing.copy(
                        totalSeconds = existing.totalSeconds + sec,
                        updatedAtMs = now,
                    )
                )
            }
        }
    }

    /** استيراد طابور outbox القديم إلى الجدول الجديد (مرة واحدة). */
    private suspend fun importLegacyOutbox() {
        val legacy = outboxDao.listOldest(100)
        if (legacy.isEmpty()) return
        for (item in legacy) {
            if (item.kind != PendingOutboxEntity.KIND_USAGE) {
                outboxDao.deleteById(item.id)
                continue
            }
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val body: Map<String, Any?> = gson.fromJson(item.payloadJson, mapType)
            @Suppress("UNCHECKED_CAST")
            val entries = body["entries"] as? List<Map<String, Any?>> ?: emptyList()
            val byDay = mutableMapOf<String, MutableMap<String, Long>>()
            for (entry in entries) {
                val pkg = entry["package"]?.toString()?.trim()?.lowercase().orEmpty()
                val day = entry["day"]?.toString()?.trim().orEmpty().ifBlank { today() }
                val sec = (entry["seconds"] as? Number)?.toLong() ?: 0L
                if (pkg.isBlank() || sec <= 0L) continue
                val dayBatch = byDay.getOrPut(day) { mutableMapOf() }
                dayBatch[pkg] = (dayBatch[pkg] ?: 0L) + sec
            }
            for ((day, batch) in byDay) {
                mergeUsageInternal(batch, day)
            }
            outboxDao.deleteById(item.id)
        }
    }

    private fun today(): String = dayFormat.format(Date())

    companion object {
        fun startOfTodayEpochMs(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        @Volatile
        private var instance: UsageSyncRepository? = null

        fun get(context: Context): UsageSyncRepository {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: UsageSyncRepository(app).also { instance = it }
            }
        }
    }
}

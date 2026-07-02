package com.example.myrana.screentime

import android.content.Context
import com.example.myrana.data.local.AppDatabase
import com.example.myrana.data.local.DailyAppUsageEntity
import com.example.myrana.data.local.ScreenTimeEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScreenTimeRepository private constructor(context: Context) {

    private val db = AppDatabase.getInstance(context.applicationContext)

    suspend fun todaySeconds(packageName: String): Long = withContext(Dispatchers.IO) {
        val day = today()
        val row = db.dailyAppUsageDao().get(day, packageName.lowercase())
        row?.secondsUsed ?: 0L
    }

    suspend fun todayMap(): Map<String, Long> = withContext(Dispatchers.IO) {
        db.dailyAppUsageDao().listForDay(today()).associate { it.packageName to it.secondsUsed }
    }

    suspend fun addSeconds(packageName: String, delta: Long) = withContext(Dispatchers.IO) {
        if (delta <= 0L) return@withContext
        val pkg = packageName.lowercase()
        val day = today()
        val current = db.dailyAppUsageDao().get(day, pkg)?.secondsUsed ?: 0L
        db.dailyAppUsageDao().upsert(
            DailyAppUsageEntity(day = day, packageName = pkg, secondsUsed = current + delta)
        )
    }

    suspend fun distinctAppsToday(): Int = withContext(Dispatchers.IO) {
        db.dailyAppUsageDao().distinctAppsToday(today())
    }

    suspend fun totalTodaySeconds(): Long = withContext(Dispatchers.IO) {
        db.dailyAppUsageDao().listForDay(today()).sumOf { it.secondsUsed }
    }

    suspend fun logEvent(
        eventType: String,
        packageName: String,
        message: String,
        secondsUsed: Long = 0L,
    ) = withContext(Dispatchers.IO) {
        db.screenTimeEventDao().insert(
            ScreenTimeEventEntity(
                eventType = eventType,
                packageName = packageName.lowercase(),
                message = message,
                secondsUsed = secondsUsed,
            )
        )
    }

    suspend fun flushPendingEvents(): List<ScreenTimeEventEntity> = withContext(Dispatchers.IO) {
        db.screenTimeEventDao().listPendingUpload()
    }

    suspend fun markEventsUploaded(ids: List<Long>) = withContext(Dispatchers.IO) {
        if (ids.isNotEmpty()) db.screenTimeEventDao().markUploaded(ids)
    }

    companion object {
        private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun today(): String = dayFormat.format(Date())

        @Volatile
        private var instance: ScreenTimeRepository? = null

        fun get(context: Context): ScreenTimeRepository {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: ScreenTimeRepository(app).also { instance = it }
            }
        }
    }
}

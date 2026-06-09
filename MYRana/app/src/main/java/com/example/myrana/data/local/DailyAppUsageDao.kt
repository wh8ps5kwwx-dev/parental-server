package com.example.myrana.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface DailyAppUsageDao {

    @Upsert
    suspend fun upsert(row: DailyAppUsageEntity)

    @Query("SELECT * FROM daily_app_usage WHERE day = :day AND package_name = :pkg LIMIT 1")
    suspend fun get(day: String, pkg: String): DailyAppUsageEntity?

    @Query("SELECT * FROM daily_app_usage WHERE day = :day ORDER BY seconds_used DESC")
    suspend fun listForDay(day: String): List<DailyAppUsageEntity>

    @Query("SELECT COUNT(DISTINCT package_name) FROM daily_app_usage WHERE day = :day AND seconds_used > 0")
    suspend fun distinctAppsToday(day: String): Int
}

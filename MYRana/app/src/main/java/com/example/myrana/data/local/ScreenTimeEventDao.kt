package com.example.myrana.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface ScreenTimeEventDao {

    @Insert
    suspend fun insert(event: ScreenTimeEventEntity): Long

    @Query("SELECT * FROM screen_time_events WHERE uploaded = 0 ORDER BY created_at_ms ASC LIMIT 50")
    suspend fun listPendingUpload(): List<ScreenTimeEventEntity>

    @Query("UPDATE screen_time_events SET uploaded = 1 WHERE id IN (:ids)")
    suspend fun markUploaded(ids: List<Long>)
}

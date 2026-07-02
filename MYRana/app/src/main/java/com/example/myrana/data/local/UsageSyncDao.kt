package com.example.myrana.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface UsageSyncDao {

    @Upsert
    suspend fun upsert(row: UsageSyncBufferEntity)

    @Query("SELECT * FROM usage_sync_buffer WHERE day = :day AND package_name = :pkg LIMIT 1")
    suspend fun get(day: String, pkg: String): UsageSyncBufferEntity?

    @Query(
        """
        SELECT * FROM usage_sync_buffer
        WHERE total_seconds > uploaded_seconds
        ORDER BY day ASC, package_name ASC
        """
    )
    suspend fun listPending(): List<UsageSyncBufferEntity>

    @Query("SELECT COUNT(*) FROM usage_sync_buffer WHERE total_seconds > uploaded_seconds")
    suspend fun countPending(): Int

    @Query(
        """
        UPDATE usage_sync_buffer
        SET uploaded_seconds = :uploadedSeconds, updated_at_ms = :updatedAtMs
        WHERE day = :day AND package_name = :pkg
        """
    )
    suspend fun markUploaded(day: String, pkg: String, uploadedSeconds: Long, updatedAtMs: Long)
}

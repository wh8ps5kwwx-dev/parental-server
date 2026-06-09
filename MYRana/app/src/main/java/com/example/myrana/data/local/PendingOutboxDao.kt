package com.example.myrana.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingOutboxEntity): Long

    @Query("SELECT * FROM pending_outbox ORDER BY created_at_ms ASC LIMIT :limit")
    suspend fun listOldest(limit: Int = 50): List<PendingOutboxEntity>

    @Query("SELECT COUNT(*) FROM pending_outbox")
    suspend fun count(): Int

    @Query("DELETE FROM pending_outbox WHERE id = :id")
    suspend fun deleteById(id: Long)
}

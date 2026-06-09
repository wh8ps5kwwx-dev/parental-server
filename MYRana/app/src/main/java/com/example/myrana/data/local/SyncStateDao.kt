package com.example.myrana.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * واجهة Room لصف حالة المزامنة الوحيد (`sync_state`, id=1).
 */
@Dao
interface SyncStateDao {

    /** قراءة الحالة الحالية؛ قد تكون `null` قبل أول مزامنة. */
    @Query("SELECT * FROM sync_state WHERE id = 1 LIMIT 1")
    suspend fun get(): SyncStateEntity?

    /** استبدال الصف بالكامل (REPLACE على المفتاح الأساسي). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: SyncStateEntity)
}

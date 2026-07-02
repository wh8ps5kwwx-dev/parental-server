package com.example.myrana.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * واجهة Room لجدول حزم التطبيقات المحظورة.
 * نفس عقد [BlockedSiteDao] مع أعمدة الحزمة بدل النطاق.
 */
@Dao
interface BlockedAppDao {

    @Query("SELECT * FROM blocked_apps ORDER BY package_name COLLATE NOCASE")
    suspend fun listAll(): List<BlockedAppEntity>

    @Query("SELECT * FROM blocked_apps WHERE pending_upload = 1")
    suspend fun listPendingUpload(): List<BlockedAppEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BlockedAppEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BlockedAppEntity)

    @Query("DELETE FROM blocked_apps")
    suspend fun deleteAll()

    @Query("UPDATE blocked_apps SET pending_upload = 0 WHERE id IN (:ids)")
    suspend fun clearPendingFlags(ids: List<Long>)
}

package com.example.myrana.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * واجهة Room لجدول المواقع المحظورة.
 * كل الاستعلامات `suspend` لتعمل مع Coroutines على خيط IO.
 */
@Dao
interface BlockedSiteDao {

    /** كل المواقع مرتبة أبجدياً (للعرض أو التصدير). */
    @Query("SELECT * FROM blocked_sites ORDER BY host_pattern COLLATE NOCASE")
    suspend fun listAll(): List<BlockedSiteEntity>

    /** الصفوف التي لم تُرفَع بعد للسيرفر (`pending_upload = 1`). */
    @Query("SELECT * FROM blocked_sites WHERE pending_upload = 1")
    suspend fun listPendingUpload(): List<BlockedSiteEntity>

    /** إدراج أو استبدال دفعة (عند سحب السياسة من الخادم). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<BlockedSiteEntity>)

    /** إدراج أو استبدال موقع واحد (عند إضافة محليّة من الطفل/الأمر). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: BlockedSiteEntity)

    /** مسح الجدول بالكامل قبل استبداله بنسخة السيرفر. */
    @Query("DELETE FROM blocked_sites")
    suspend fun deleteAll()

    /** بعد رفع ناجح: تصفير علم الرفع للصفوف المحددة. */
    @Query("UPDATE blocked_sites SET pending_upload = 0 WHERE id IN (:ids)")
    suspend fun clearPendingFlags(ids: List<Long>)
}

package com.example.myrana.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * صف في جدول `blocked_sites` يمثّل موقعاً أو نطاقاً محظوراً.
 *
 * @param hostPattern نص النطاق (مثل `bad.example`) بأحرف صغيرة بعد التطبيع.
 * @param createdAtEpochMs وقت الإضافة بالميلي ثانية (Unix epoch).
 * @param pendingUpload إن كان `true` يُرفَع للسيرفر في أول [PolicyRepository.syncWithServer].
 */
@Entity(
    tableName = "blocked_sites",
    indices = [Index(value = ["host_pattern"], unique = true)]
)
data class BlockedSiteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "host_pattern") val hostPattern: String,
    @ColumnInfo(name = "created_at_ms") val createdAtEpochMs: Long,
    @ColumnInfo(name = "pending_upload") val pendingUpload: Boolean = false
)

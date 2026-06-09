package com.example.myrana.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * صف في جدول `blocked_apps` يمثّل حزمة تطبيق (applicationId) محظورة.
 *
 * @param packageName معرّف الحزمة (مثل `com.example.game`) بأحرف صغيرة.
 * @param pendingUpload يُشار للرفع عند المزامنة التالية مع السيرفر.
 */
@Entity(
    tableName = "blocked_apps",
    indices = [Index(value = ["package_name"], unique = true)]
)
data class BlockedAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** معرّف الحزمة، مثل com.dvloper.granny */
    @ColumnInfo(name = "package_name") val packageName: String,
    /** وقت الإضافة (ميلي ثانية) */
    @ColumnInfo(name = "created_at_ms") val createdAtEpochMs: Long,
    /** true = لم يُرفَع بعد إلى السيرفر */
    @ColumnInfo(name = "pending_upload") val pendingUpload: Boolean = false
)

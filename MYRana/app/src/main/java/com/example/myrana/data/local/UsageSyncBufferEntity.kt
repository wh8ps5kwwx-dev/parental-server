package com.example.myrana.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * استخدام محلي لكل تطبيق/يوم — يُحفظ على الجهاز حتى يرفع للسيرفر.
 *
 * [totalSeconds]: إجمالي ما جُمع محلياً.
 * [uploadedSeconds]: ما وصل للسيرفر بنجاح.
 * الفرق = معلّق للرفع عند عودة النت.
 */
@Entity(
    tableName = "usage_sync_buffer",
    primaryKeys = ["day", "package_name"],
)
data class UsageSyncBufferEntity(
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "total_seconds") val totalSeconds: Long = 0L,
    @ColumnInfo(name = "uploaded_seconds") val uploadedSeconds: Long = 0L,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long = 0L,
) {
    fun pendingSeconds(): Long = (totalSeconds - uploadedSeconds).coerceAtLeast(0L)
}

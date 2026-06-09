package com.example.myrana.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * صف في طابور إرسال **استخدام الجهاز** — يُحفظ عند انقطاع النت ويُرفع لاحقاً.
 *
 * @param kind حالياً [KIND_USAGE] فقط (`/upload-usage`).
 * @param payloadJson قائمة entries: package، day، seconds.
 */
@Entity(
    tableName = "pending_outbox",
    indices = [Index(value = ["created_at_ms"])]
)
data class PendingOutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "child_code") val childCode: String,
    @ColumnInfo(name = "payload_json") val payloadJson: String,
    @ColumnInfo(name = "created_at_ms") val createdAtEpochMs: Long
) {
    companion object {
        const val KIND_USAGE = "usage"
    }
}

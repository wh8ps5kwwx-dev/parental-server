package com.example.myrana.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** سجل أحداث وقت الاستخدام — يُرفع لولي الأمر. */
@Entity(tableName = "screen_time_events")
data class ScreenTimeEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "message") val message: String,
    @ColumnInfo(name = "seconds_used") val secondsUsed: Long = 0L,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "uploaded") val uploaded: Boolean = false,
)

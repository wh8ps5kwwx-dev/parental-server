package com.example.myrana.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity

/** استخدام يومي لكل تطبيق — يبقى بعد إعادة التشغيل. */
@Entity(
    tableName = "daily_app_usage",
    primaryKeys = ["day", "package_name"],
)
data class DailyAppUsageEntity(
    @ColumnInfo(name = "day") val day: String,
    @ColumnInfo(name = "package_name") val packageName: String,
    @ColumnInfo(name = "seconds_used") val secondsUsed: Long = 0L,
)

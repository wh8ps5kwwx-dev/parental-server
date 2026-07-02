package com.example.myrana.data.remote.dto

/** صف واحد في تقرير استخدام تطبيق على جهاز الطفل. */
data class UsageAppItem(
    val packageName: String,
    val totalSeconds: Long
) {
    val totalMinutes: Long get() = totalSeconds / 60
}

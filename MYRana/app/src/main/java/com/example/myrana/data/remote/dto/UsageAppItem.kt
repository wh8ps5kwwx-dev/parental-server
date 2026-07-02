package com.example.myrana.data.remote.dto

/** صف واحد في تقرير استخدام تطبيق على جهاز الطفل. */
data class UsageAppItem(
    val packageName: String,
    val totalSeconds: Long,
    val avgSecondsPerDay: Long = totalSeconds,
    val appLabel: String? = null,
    val iconBase64: String? = null,
    val showDailyTotal: Boolean = false,
) {
    val totalMinutes: Long get() = totalSeconds / 60
    val avgMinutesPerDay: Long get() = avgSecondsPerDay / 60

    fun displayLabel(fallback: String): String =
        appLabel?.takeIf { it.isNotBlank() } ?: fallback
}

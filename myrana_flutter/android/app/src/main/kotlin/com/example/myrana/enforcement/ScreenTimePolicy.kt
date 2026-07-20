package com.example.myrana.enforcement

import com.google.gson.annotations.SerializedName

/**
 * جزء مُبسّط من ScreenTimePolicy في مشروع MYRana.
 * نستخدمه لحدّ الحظر + وقت النوم فقط.
 */
data class ScreenTimePolicy(
    @SerializedName("monitored_packages")
    val monitoredPackages: List<String> = emptyList(),

    @SerializedName("unlimited_packages")
    val unlimitedPackages: List<String> = emptyList(),

    @SerializedName("warn_minutes")
    val warnMinutes: Int = 60,

    @SerializedName("strong_warn_minutes")
    val strongWarnMinutes: Int = 90,

    @SerializedName("block_minutes")
    val blockMinutes: Int = 120,

    @SerializedName("sleep_start")
    val sleepStart: String = "22:00",

    @SerializedName("sleep_end")
    val sleepEnd: String = "07:00",

    @SerializedName("allow_during_sleep")
    val allowDuringSleep: Boolean = false,

    // السيرفر قد يرجع keys إضافية مثل max_open_apps... نحن نتجاهلها
    @SerializedName("enabled")
    val enabled: Boolean? = null,
) {
    fun warnSeconds(): Long = warnMinutes.coerceAtLeast(1) * 60L
    fun strongWarnSeconds(): Long = strongWarnMinutes.coerceAtLeast(warnMinutes + 1) * 60L
    fun blockSeconds(): Long = blockMinutes.coerceAtLeast(strongWarnMinutes + 1) * 60L

    fun isMonitored(packageName: String): Boolean {
        if (MonitoredAppRegistry.isNeverBlockPackage(packageName)) return false
        val pkg = packageName.lowercase()
        if (pkg.startsWith("com.example.myrana_flutter")) return false
        if (unlimitedPackages.any { it.equals(pkg, ignoreCase = true) }) return false
        if (monitoredPackages.isEmpty()) return true
        return monitoredPackages.any { it.equals(pkg, ignoreCase = true) }
    }

    fun isUnlimited(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        return unlimitedPackages.any { it.equals(pkg, ignoreCase = true) }
    }
}

object ScreenTimePolicyDefaults {
    fun defaultPolicy(): ScreenTimePolicy = ScreenTimePolicy()
}

enum class UsageLevel { GREEN, YELLOW, RED, BLOCKED }


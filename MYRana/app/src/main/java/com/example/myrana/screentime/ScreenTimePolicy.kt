package com.example.myrana.screentime

import com.example.myrana.enforcement.MonitoredAppRegistry
import com.google.gson.annotations.SerializedName

/**
 * سياسة وقت الاستخدام — يحددها ولي الأمر (قابلة للتعديل).
 */
data class ScreenTimePolicy(
    /** تطبيقات خاضعة للمراقبة (فارغ = الكل ما عدا المسموح). */
    @SerializedName("monitored_packages") val monitoredPackages: List<String> = emptyList(),
    /** تطبيقات تعليمية بدون حد يومي. */
    @SerializedName("unlimited_packages") val unlimitedPackages: List<String> = emptyList(),
    @SerializedName("warn_minutes") val warnMinutes: Int = 60,
    @SerializedName("strong_warn_minutes") val strongWarnMinutes: Int = 90,
    @SerializedName("block_minutes") val blockMinutes: Int = 120,
    @SerializedName("max_open_apps") val maxOpenApps: Int = 8,
    @SerializedName("max_open_sites") val maxOpenSites: Int = 8,
    @SerializedName("sleep_start") val sleepStart: String = "22:00",
    @SerializedName("sleep_end") val sleepEnd: String = "07:00",
    @SerializedName("allow_during_sleep") val allowDuringSleep: Boolean = false,
    @SerializedName("vacation_mode") val vacationMode: Boolean = false,
    @SerializedName("vacation_same_rules") val vacationSameRules: Boolean = true,
) {
    fun warnSeconds(): Long = warnMinutes.coerceAtLeast(1) * 60L
    fun strongWarnSeconds(): Long = strongWarnMinutes.coerceAtLeast(warnMinutes + 1) * 60L
    fun blockSeconds(): Long = blockMinutes.coerceAtLeast(strongWarnMinutes + 1) * 60L

    fun isMonitored(packageName: String): Boolean {
        if (MonitoredAppRegistry.isNeverBlockPackage(packageName)) {
            return false
        }
        val pkg = packageName.lowercase()
        if (pkg.startsWith("com.example.myrana")) return false
        if (unlimitedPackages.any { it.equals(pkg, ignoreCase = true) }) return false
        if (monitoredPackages.isEmpty()) return true
        return monitoredPackages.any { it.equals(pkg, ignoreCase = true) }
    }

    fun isUnlimited(packageName: String): Boolean =
        unlimitedPackages.any { it.equals(packageName, ignoreCase = true) }
}

enum class UsageLevel { GREEN, YELLOW, RED, BLOCKED }

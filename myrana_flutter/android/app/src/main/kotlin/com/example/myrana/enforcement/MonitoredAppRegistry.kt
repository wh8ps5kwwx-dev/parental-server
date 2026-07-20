package com.example.myrana.enforcement

/**
 * قواعد: حزم النظام لا تُحظر. المتصفحات/يوتيوب تُفحص عبر Accessibility.
 */
object MonitoredAppRegistry {

    fun isNeverBlockPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase().trim()
        if (pkg.isBlank()) return false
        if (pkg == "android") return true
        // تطبيقنا
        if (pkg.startsWith("com.example.myrana_flutter")) return true
        // بادئات النظام
        if (pkg.startsWith("com.android.systemui")) return true
        if (pkg.startsWith("com.android.launcher")) return true
        if (pkg.startsWith("com.android.settings")) return true
        return false
    }

    fun shouldMonitorAccessibilityText(packageName: String): Boolean {
        val pkg = packageName.lowercase().trim()
        if (pkg.isBlank()) return false
        if (isNeverBlockPackage(pkg)) return false
        return true
    }

    fun isMessagingApp(packageName: String): Boolean = false

    fun isBrowserPackage(packageName: String): Boolean =
        packageName.lowercase() in AccessibilityHelper.BROWSER_PACKAGES
}


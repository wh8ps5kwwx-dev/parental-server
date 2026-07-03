package com.example.myrana.enforcement

/**
 * تطبيقات تُراقَب نصوصها عبر Accessibility — مشروع التخرج:
 * متصفحات، YouTube، واتساب/تيليغرام، تواصل، وجميع تطبيقات المستخدم.
 */
object MonitoredAppRegistry {

    /** لا يُحظر أبداً — مثبّت التطبيقات وإعدادات النظام. */
    fun isNeverBlockPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase().trim()
        if (pkg.isBlank()) return false
        if (pkg.startsWith("com.example.myrana")) return true
        if (systemExcludePrefixes.any { pkg.startsWith(it) }) return true
        if (pkg.contains("packageinstaller")) return true
        if (pkg.contains("documentsui")) return true
        if (pkg == "com.android.vending" || pkg == "com.sec.android.app.samsungapps") return true
        if (pkg.contains("installer") && pkg.startsWith("com.")) return true
        return false
    }

    val messagingPackages: Set<String> = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "com.facebook.orca",
        "com.facebook.mlite",
        "com.instagram.android",
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill",
        "com.snapchat.android",
        "com.twitter.android",
        "com.viber.voip",
        "jp.naver.line.android",
        "com.discord",
        "com.Slack",
        "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
    )

    private val systemExcludePrefixes: List<String> = listOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.android.settings",
        "com.google.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.android.inputmethod",
        "com.google.android.inputmethod",
        "com.samsung.android.app.telephonyui",
        "com.example.myrana",
    )

    /** هل نراقب نصوص هذا التطبيق (Accessibility)؟ — جميع تطبيقات المستخدم ما عدا النظام. */
    fun shouldMonitorAccessibilityText(packageName: String): Boolean {
        val pkg = packageName.lowercase().trim()
        if (pkg.isBlank()) return false
        if (systemExcludePrefixes.any { pkg.startsWith(it) }) return false
        return true
    }

    fun isMessagingApp(packageName: String): Boolean =
        packageName.lowercase() in messagingPackages

    fun isBrowserPackage(packageName: String): Boolean =
        packageName.lowercase() in browserPackages()

    fun browserPackages(): Set<String> = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.brave.browser",
        "com.aloha.browser",
        "com.duckduckgo.mobile.android",
        "org.mozilla.firefox",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.kiwibrowser.browser",
        "org.torproject.torbrowser",
    )

    fun appCategoryLabel(packageName: String): String = when {
        isMessagingApp(packageName) -> "مراسلة"
        isBrowserPackage(packageName) -> "متصفح"
        packageName.equals(AccessibilityHelper.YOUTUBE_PACKAGE, ignoreCase = true) -> "YouTube"
        else -> "تطبيق"
    }
}

package com.example.myrana.enforcement

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

object AccessibilityHelper {

    private const val PREFS = "myrana_accessibility"
    private const val KEY_FLOW_DONE = "accessibility_flow_done"

    fun isServiceEnabled(context: Context): Boolean {
        val expected =
            "${context.packageName}/${ContentFilterAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun serviceComponentName(context: Context): ComponentName =
        ComponentName(context, ContentFilterAccessibilityService::class.java)

    /** شاشة تفاصيل خدمة الوصول لهذا التطبيق (تفعيل فعلي للخدمة). */
    fun openServiceSettings(context: Context): Boolean {
        val component = serviceComponentName(context)
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(
                    Intent(ACTION_ACCESSIBILITY_DETAILS_SETTINGS).apply {
                        putExtra(Intent.EXTRA_COMPONENT_NAME, component)
                        putExtra(
                            EXTRA_ACCESSIBILITY_SERVICE_COMPONENT_NAME,
                            component.flattenToString(),
                        )
                    },
                )
            }
            add(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        return startFirstAvailable(context, candidates)
    }

    fun openSettings(context: Context): Boolean = openServiceSettings(context)

    private fun startFirstAvailable(context: Context, intents: List<Intent>): Boolean {
        for (raw in intents) {
            val intent = Intent(raw).apply {
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        }
        return false
    }

    fun markAccessibilityFlowDone(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_FLOW_DONE, true)
            .apply()
    }

    fun isMonitoredPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        if (pkg in BROWSER_PACKAGES) return true
        return pkg == YOUTUBE_PACKAGE
    }

    const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
        "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    private const val EXTRA_ACCESSIBILITY_SERVICE_COMPONENT_NAME =
        "android.settings.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME"

    private val BROWSER_PACKAGES = setOf(
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
}

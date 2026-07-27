package com.example.myrana.enforcement

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * مساعدات Accessibility + تحديد حزم المتصفح/يوتيوب.
 */
object AccessibilityHelper {

    fun isServiceEnabled(context: Context): Boolean {
        val expected = "${context.packageName}/${
            ContentFilterAccessibilityService::class.java.name
        }"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    fun isBrowserOrSearchPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        if (pkg in BROWSER_PACKAGES) return true
        if (pkg in SEARCH_APP_PACKAGES) return true
        return pkg == YOUTUBE_PACKAGE
    }

    /** حزم تُفحص من ناحية المحتوى: متصفحات + YouTube. */
    fun isContentScanPackage(packageName: String): Boolean {
        val pkg = packageName.lowercase()
        if (pkg in BROWSER_PACKAGES) return true
        return pkg == YOUTUBE_PACKAGE
    }

    val BROWSER_PACKAGES: Set<String> = setOf(
        "com.android.chrome",
        "com.brave.browser",
        "org.mozilla.firefox",
        "com.opera.browser",
        "com.sec.android.app.sbrowser",
        "com.UCMobile",
        "org.torproject.torbrowser",
    )

    private val SEARCH_APP_PACKAGES: Set<String> = setOf(
        "com.google.android.googlequicksearchbox",
        "com.android.quicksearchbox",
        "com.bing.android",
    )

    const val YOUTUBE_PACKAGE = "com.google.android.youtube"

    private const val ACTION_ACCESSIBILITY_DETAILS_SETTINGS =
        "android.settings.ACCESSIBILITY_DETAILS_SETTINGS"
    private const val EXTRA_ACCESSIBILITY_SERVICE_COMPONENT_NAME =
        "android.settings.extra.ACCESSIBILITY_SERVICE_COMPONENT_NAME"

    fun openSettings(context: Context): Boolean {
        val component = ComponentName(context, ContentFilterAccessibilityService::class.java)
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

    private fun startFirstAvailable(context: Context, intents: List<Intent>): Boolean {
        for (raw in intents) {
            val intent = Intent(raw).apply {
                if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
                return true
            }
        }
        return false
    }
}


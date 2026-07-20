package com.example.myrana.enforcement

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * فلترة المحتوى/المواقع عبر Accessibility:
 * - المتصفحات: تطابق blockedHosts / .onion
 * - YouTube: تطابق videoKeywords
 */
class ContentFilterAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastScanAtMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        EnforcementEngine.get(applicationContext).ensureInitializedCaches()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString()?.trim().orEmpty()
        if (pkg.isBlank()) return
        if (MonitoredAppRegistry.isNeverBlockPackage(pkg)) return
        if (!MonitoredAppRegistry.shouldMonitorAccessibilityText(pkg)) return
        if (!AccessibilityHelper.isContentScanPackage(pkg)) return

        val now = System.currentTimeMillis()
        if (now - lastScanAtMs < 2_000L && event.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            return
        }
        lastScanAtMs = now

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // نعمل scan كامل للـ root فقط إذا كان من المتصفح/يوتيوب
                val root = rootInActiveWindow ?: return
                try {
                    val allTexts = mutableListOf<String>()
                    val searchTexts = mutableListOf<String>()
                    collectText(root, allTexts)
                    collectSearchLikeText(root, searchTexts)

                    // YouTube: نستخدم videoKeywords
                    val isYoutube = pkg.equals(AccessibilityHelper.YOUTUBE_PACKAGE, ignoreCase = true)
                    if (isYoutube) {
                        PolicyFilterCache.matchVideoKeyword(allTexts)
                            ?.let { kw ->
                                EnforcementEngine.get(applicationContext).enforceFromAccessibility(
                                    packageName = pkg,
                                    label = kw,
                                    reason = BlockWarningActivity.REASON_YOUTUBE,
                                )
                            }
                        return
                    }

                    // Browser: blockedHosts / onion
                    PolicyFilterCache.matchBlockedHost(allTexts)
                        ?.let { host ->
                            EnforcementEngine.get(applicationContext).enforceFromAccessibility(
                                packageName = pkg,
                                label = host,
                                reason = BlockWarningActivity.REASON_SITE,
                            )
                        }
                    if (PolicyFilterCache.matchOnionDomain(allTexts)) {
                        EnforcementEngine.get(applicationContext).enforceFromAccessibility(
                            packageName = pkg,
                            label = ".onion",
                            reason = BlockWarningActivity.REASON_SITE,
                        )
                    }
                } finally {
                    root.recycle()
                }
            }
        }
    }

    override fun onInterrupt() = Unit

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, out)
            child.recycle()
        }
    }

    /**
     * يساعد في زيادة دقة الاكتشاف (مثلاً عندما يظهر رابط/بحث في شريط).
     * حالياً نستخدمه فقط لجمع نصوص إضافية إذا أردنا لاحقاً.
     */
    private fun collectSearchLikeText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        val viewId = node.viewIdResourceName?.lowercase().orEmpty()
        val searchLike = viewId.contains("search") ||
            viewId.contains("url") ||
            viewId.contains("omnibox") ||
            viewId.contains("address") ||
            viewId.contains("query") ||
            viewId.contains("searchbar")
        if (searchLike) {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(out::add)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectSearchLikeText(child, out)
            child.recycle()
        }
    }
}


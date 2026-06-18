package com.example.myrana.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.session.ChildSession
import com.example.myrana.permissions.ChildProjectRuntime
import com.example.myrana.ui.BlockWarningActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * مراقبة **جميع** تطبيقات المستخدم عبر Accessibility — ليس Chrome/YouTube فقط.
 */
class ContentFilterAccessibilityService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastEnforcedAt = 0L
    private val lastAlertAt = mutableMapOf<String, Long>()

    override fun onServiceConnected() {
        super.onServiceConnected()
        ChildProjectRuntime.activateMonitoring(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val pkg = event.packageName?.toString() ?: return
        if (!AccessibilityHelper.isMonitoredPackage(pkg)) return

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED
        ) {
            val texts = mutableListOf<String>()
            event.text?.forEach { part ->
                part?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { texts.add(it) }
            }
            val source = event.source
            if (source != null) {
                try {
                    collectSearchFieldText(source, texts)
                } finally {
                    source.recycle()
                }
            }
            if (texts.isNotEmpty()) {
                inspectTexts(texts, pkg, fromTyping = true)
                return
            }
        }

        val root = rootInActiveWindow ?: return
        try {
            val texts = mutableListOf<String>()
            collectText(root, texts)
            collectSearchFieldText(root, texts)
            inspectTexts(texts, pkg, fromTyping = false)
        } finally {
            root.recycle()
        }
    }

    override fun onInterrupt() = Unit

    private fun inspectTexts(texts: List<String>, pkg: String, fromTyping: Boolean) {
        if (texts.isEmpty()) return
        val isBrowser = MonitoredAppRegistry.isBrowserPackage(pkg)

        if (isBrowser) {
            PolicyFilterCache.matchBlockedHost(texts)?.let { host ->
                enforceBlock(
                    blockedLabel = host,
                    alertMessage = "محاولة زيارة موقع محظور: $host",
                    reason = BlockWarningActivity.REASON_SITE,
                )
                return
            }
            if (PolicyFilterCache.matchOnionDomain(texts)) {
                enforceBlock(
                    blockedLabel = ".onion",
                    alertMessage = "محاولة دخول دارك ويب (.onion)",
                    reason = BlockWarningActivity.REASON_SITE,
                )
                return
            }
        }

        // كل التطبيقات — كلمات فيديو/محتوى خطر → حظر + تنبيه
        PolicyFilterCache.matchVideoKeyword(texts)?.let { kw ->
            val appLabel = packageLabel(pkg)
            enforceBlock(
                blockedLabel = kw,
                alertMessage = "محتوى خطر في $appLabel: $kw",
                reason = BlockWarningActivity.REASON_CONTENT,
            )
            return
        }

        // كل التطبيقات — كلمات SafetyKeywordCatalog → تنبيه ولي الأمر
        PolicyFilterCache.matchSafetySearch(texts)?.let { hit ->
            notifyParentSearchAlert(hit, pkg)
        }
    }

    private fun collectText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectText(child, out)
            child.recycle()
        }
    }

    /** شريط بحث أو أي حقل نص في أي تطبيق. */
    private fun collectSearchFieldText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        val className = node.className?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty().lowercase()
        val searchLike = viewId.contains("search") || viewId.contains("url") ||
            viewId.contains("omnibox") || viewId.contains("address") || viewId.contains("query")
        val editable = node.isEditable || className.contains("EditText", ignoreCase = true)
        if (editable || searchLike) {
            node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            collectSearchFieldText(child, out)
            child.recycle()
        }
    }

    private fun notifyParentSearchAlert(hit: SafetyKeywordCatalog.Entry, pkg: String) {
        val alertKey = "${hit.category}:${hit.keyword}"
        val now = System.currentTimeMillis()
        val last = lastAlertAt[alertKey] ?: 0L
        if (now - last < ALERT_COOLDOWN_MS) return
        lastAlertAt[alertKey] = now

        val appLabel = packageLabel(pkg)
        val prefix = when {
            MonitoredAppRegistry.isMessagingApp(pkg) -> "تنبيه محادثة"
            MonitoredAppRegistry.appCategoryLabel(pkg) == "متصفح" -> "تنبيه بحث"
            else -> "تنبيه محتوى"
        }
        val message =
            "$prefix (خلفية): الطفل كتب «${hit.keyword}» (فئة: ${hit.category}) في $appLabel"
        val childCode = ChildSession.childCode(this) ?: return
        scope.launch {
            NetworkModule.postAlertSync(childCode, message)
        }
    }

    private fun packageLabel(packageName: String): String = try {
        val info = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(info).toString()
    } catch (_: Exception) {
        packageName
    }

    private fun enforceBlock(blockedLabel: String, alertMessage: String, reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastEnforcedAt < ENFORCE_COOLDOWN_MS) return
        lastEnforcedAt = now

        performGlobalAction(GLOBAL_ACTION_HOME)

        val warn = Intent(this, BlockWarningActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(BlockWarningActivity.EXTRA_PACKAGE, blockedLabel)
            putExtra(BlockWarningActivity.EXTRA_REASON, reason)
        }
        startActivity(warn)

        val childCode = ChildSession.childCode(this) ?: return
        scope.launch {
            NetworkModule.postAlertSync(childCode, alertMessage)
        }
    }

    companion object {
        private const val ENFORCE_COOLDOWN_MS = 3_000L
        private const val ALERT_COOLDOWN_MS = 90_000L
    }
}

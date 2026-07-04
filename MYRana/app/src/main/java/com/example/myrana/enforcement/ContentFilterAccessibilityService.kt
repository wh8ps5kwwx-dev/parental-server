package com.example.myrana.enforcement

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.identity.ChildIdentity
import com.example.myrana.permissions.ChildProjectRuntime
import com.example.myrana.ui.BlockWarningActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * مراقبة البحث في المتصفحات وتطبيقات البحث وYouTube — تنبيه الأم عند كتابة كلمات خطرة.
 * حظر المواقع في المتصفحات وحظر فيديو YouTube منفصل عن تنبيهات البحث.
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
        val pkg = event.packageName?.toString()?.trim().orEmpty()
        if (pkg.isBlank()) return
        if (MonitoredAppRegistry.isNeverBlockPackage(pkg)) return
        if (!MonitoredAppRegistry.shouldMonitorAccessibilityText(pkg)) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> handleSearchTyping(event, pkg)
            else -> handleWindowScan(pkg)
        }
    }

    override fun onInterrupt() = Unit

    /** تنبيه الأم — فقط من شريط البحث / الكتابة، وليس من محتوى الصفحة. */
    private fun handleSearchTyping(event: AccessibilityEvent, pkg: String) {
        val texts = mutableListOf<String>()
        appendEventText(event, texts)
        val source = event.source
        if (source != null) {
            try {
                if (AccessibilityHelper.isBrowserOrSearchPackage(pkg) && isEditableOrSearchNode(source)) {
                    appendNodeText(source, texts)
                }
                collectSearchFieldText(source, texts)
            } finally {
                source.recycle()
            }
        }
        if (texts.isEmpty()) return
        PolicyFilterCache.matchSafetySearch(texts.distinct())?.let { hit ->
            notifyParentSearchAlert(hit, pkg)
        }
    }

    /** حظر مواقع / فيديو YouTube + تنبيه بحث من حقول البحث الظاهرة فقط. */
    private fun handleWindowScan(pkg: String) {
        if (!AccessibilityHelper.isContentScanPackage(pkg)) return
        val root = rootInActiveWindow ?: return
        try {
            val allTexts = mutableListOf<String>()
            val searchTexts = mutableListOf<String>()
            collectText(root, allTexts)
            collectSearchFieldText(root, searchTexts)
            if (searchTexts.isNotEmpty()) {
                PolicyFilterCache.matchSafetySearch(searchTexts.distinct())?.let { hit ->
                    notifyParentSearchAlert(hit, pkg)
                }
            }
            inspectBlocking(allTexts, pkg)
        } finally {
            root.recycle()
        }
    }

    private fun inspectBlocking(texts: List<String>, pkg: String) {
        if (texts.isEmpty()) return
        val isBrowser = MonitoredAppRegistry.isBrowserPackage(pkg)
        val isYoutube = pkg.equals(AccessibilityHelper.YOUTUBE_PACKAGE, ignoreCase = true)

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

        if (isYoutube) {
            PolicyFilterCache.matchVideoKeyword(texts)?.let { kw ->
                enforceBlock(
                    blockedLabel = kw,
                    alertMessage = "محتوى فيديو محظور على YouTube: $kw",
                    reason = BlockWarningActivity.REASON_YOUTUBE,
                )
            }
        }
    }

    private fun appendEventText(event: AccessibilityEvent, out: MutableList<String>) {
        val eventText = event.text
        for (i in 0 until eventText.size) {
            eventText[i]?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
        }
    }

    private fun appendNodeText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { out.add(it) }
    }

    private fun isEditableOrSearchNode(node: AccessibilityNodeInfo): Boolean {
        val className = node.className?.toString().orEmpty()
        val viewId = node.viewIdResourceName.orEmpty().lowercase()
        val searchLike = viewId.contains("search") || viewId.contains("url") ||
            viewId.contains("omnibox") || viewId.contains("address") || viewId.contains("query")
        val editable = node.isEditable || className.contains("EditText", ignoreCase = true)
        return editable || searchLike
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

    /** حقول البحث في Chrome والمتصفحات وتطبيقات التواصل — حتى والتطبيق بالخلفية. */
    private fun collectSearchFieldText(node: AccessibilityNodeInfo, out: MutableList<String>) {
        val viewId = node.viewIdResourceName.orEmpty().lowercase()
        val searchLike = viewId.contains("search") || viewId.contains("url") ||
            viewId.contains("omnibox") || viewId.contains("address") || viewId.contains("query") ||
            viewId.contains("find") || viewId.contains("query_edit") || viewId.contains("search_src") ||
            viewId.contains("search_box") || viewId.contains("searchbar") || viewId.contains("et_search")
        if (searchLike) {
            appendNodeText(node, out)
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
        val message =
            "تنبيه بحث: الطفل كتب «${hit.keyword}» (فئة: ${hit.category}) في $appLabel"
        val childCode = ChildIdentity.apiCode(this)
        if (childCode.isBlank()) return
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

        val childCode = ChildIdentity.apiCode(this)
        if (childCode.isBlank()) return
        scope.launch {
            NetworkModule.postAlertSync(childCode, alertMessage)
        }
    }

    companion object {
        private const val ENFORCE_COOLDOWN_MS = 3_000L
        private const val ALERT_COOLDOWN_MS = 90_000L
    }
}

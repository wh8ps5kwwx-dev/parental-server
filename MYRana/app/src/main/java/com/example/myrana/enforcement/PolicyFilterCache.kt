package com.example.myrana.enforcement

import android.content.Context

/**
 * ذاكرة فلاتر التنبيه والحظر — تُحدَّث من catalog.json والسيرمحلياً.
 */
object PolicyFilterCache {

    private const val PREFS = "myrana_policy_filter"
    private const val KEY_KEYWORDS = "video_keywords"

    /** مواقع من catalog.json (مدمج أو من السيرفر). */
    @Volatile
    private var catalogBlockedHosts: Set<String> = emptySet()

    /** مواقع/تطبيقات من سياسة الجهاز (أوامر الأم + Room). */
    @Volatile
    private var deviceBlockedHosts: Set<String> = emptySet()

    @Volatile
    private var blockedHosts: Set<String> = emptySet()

    @Volatile
    private var videoKeywords: Set<String> = emptySet()

    /** كلمات من catalog.json — للتنبيه عند البحث في Chrome. */
    @Volatile
    private var catalogKeywords: Set<String> = emptySet()

    /** كلمات إضافية من سياسة السيرفر بعد المزامنة. */
    @Volatile
    private var serverKeywords: Set<String> = emptySet()

    /** يُستدعى عند بدء المراقبة — [SafetyKeywordCatalog] جاهز دائماً. */
    fun loadBuiltInSafetyKeywords() {
        // لا شيء — الكلمات المدمجة في SafetyKeywordCatalog
    }

    /** دمج catalog.json (مواقع + كلمات) في الفلاتر — لا يُمسح بمزامنة السياسة. */
    fun applyCatalogFilter(sites: Collection<String>, keywords: Collection<String>) {
        val siteSet = sites.map { normalizeHost(it) }.filter { it.isNotEmpty() }.toSet()
        if (siteSet.isNotEmpty()) {
            catalogBlockedHosts = siteSet
        }
        catalogKeywords = keywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        rebuildBlockedHosts()
        rebuildKeywordSets()
    }

    fun update(hosts: Collection<String>, keywords: Collection<String>) {
        deviceBlockedHosts = hosts.map { normalizeHost(it) }.filter { it.isNotEmpty() }.toSet()
        serverKeywords = keywords.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
        rebuildBlockedHosts()
        rebuildKeywordSets()
    }

    fun updateHosts(hosts: Collection<String>) {
        deviceBlockedHosts = hosts.map { normalizeHost(it) }.filter { it.isNotEmpty() }.toSet()
        rebuildBlockedHosts()
    }

    fun clearDevicePolicy() {
        deviceBlockedHosts = emptySet()
        serverKeywords = emptySet()
        rebuildBlockedHosts()
        rebuildKeywordSets()
    }

    private fun rebuildBlockedHosts() {
        blockedHosts = deviceBlockedHosts + catalogBlockedHosts
    }

    fun persistKeywords(context: Context, keywords: Collection<String>) {
        val list = keywords.map { it.trim() }.filter { it.isNotEmpty() }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_KEYWORDS, list.toSet())
            .apply()
        serverKeywords = list.map { it.lowercase() }.toSet()
        rebuildKeywordSets()
    }

    fun loadFromPrefs(context: Context) {
        val stored = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_KEYWORDS, emptySet()).orEmpty()
        serverKeywords = stored.map { it.lowercase() }.toSet()
        rebuildKeywordSets()
    }

    fun matchBlockedHost(texts: Collection<String>): String? {
        if (blockedHosts.isEmpty()) return null
        val blob = texts.joinToString(" ").lowercase()
        if (blob.isBlank()) return null
        for (host in blockedHosts) {
            if (host.isNotEmpty() && blob.contains(host)) return host
        }
        return null
    }

    fun matchVideoKeyword(texts: Collection<String>): String? {
        if (videoKeywords.isEmpty()) return null
        val blob = texts.joinToString(" ")
        if (blob.isBlank()) return null
        for (kw in videoKeywords) {
            if (kw.isNotEmpty() && SafetyKeywordCatalog.normalize(blob).contains(
                    SafetyKeywordCatalog.normalize(kw)
                )
            ) {
                return kw
            }
        }
        return null
    }

    /**
     * مطابقة كلمات التنبيه — بالترتيب:
     * 1. [SafetyKeywordCatalog] المدمج
     * 2. catalog.json
     * 3. سياسة السيرفر
     */
    fun matchSafetySearch(texts: Collection<String>): SafetyKeywordCatalog.Entry? {
        val blob = texts.joinToString(" ")
        if (blob.isBlank()) return null

        SafetyKeywordCatalog.matchIn(blob)?.let { return it }

        val normalized = SafetyKeywordCatalog.normalize(blob)
        for (kw in catalogKeywords) {
            val needle = SafetyKeywordCatalog.normalize(kw)
            if (needle.length >= 3 && normalized.contains(needle)) {
                return SafetyKeywordCatalog.Entry("قائمة الحظر", kw)
            }
        }
        for (kw in serverKeywords) {
            val needle = SafetyKeywordCatalog.normalize(kw)
            if (needle.length >= 3 && normalized.contains(needle)) {
                return SafetyKeywordCatalog.Entry("من السيرفر", kw)
            }
        }
        return null
    }

    fun matchOnionDomain(texts: Collection<String>): Boolean {
        val blob = texts.joinToString(" ").lowercase()
        return blob.contains(".onion")
    }

    private fun rebuildKeywordSets() {
        videoKeywords = (catalogKeywords + serverKeywords).filter { it.isNotEmpty() }.toSet()
    }

    private fun normalizeHost(raw: String): String {
        var h = raw.trim().lowercase()
        h = h.removePrefix("http://").removePrefix("https://")
        h = h.removePrefix("www.")
        val slash = h.indexOf('/')
        if (slash > 0) h = h.substring(0, slash)
        return h.trim()
    }
}

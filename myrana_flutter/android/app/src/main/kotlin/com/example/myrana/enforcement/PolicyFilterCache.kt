package com.example.myrana.enforcement

import android.content.Context
import android.content.SharedPreferences
import com.example.myrana.network.NetworkClientLite
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

/**
 * cache سريع للسياسة (blockedHosts/blockedPackages/videoKeywords).
 * - يخدم AccessibilityService
 * - يُحدَّث من ForegroundMonitorService/WorkManager
 */
object PolicyFilterCache {

    private const val PREFS = "myrana_policy_cache"
    private const val KEY_BLOCKED_HOSTS = "blocked_hosts"
    private const val KEY_BLOCKED_PACKAGES = "blocked_packages"
    private const val KEY_VIDEO_KEYWORDS = "video_keywords"

    private val gson = Gson()

    @Volatile
    private var blockedHosts: Set<String> = emptySet()

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    @Volatile
    private var videoKeywords: Set<String> = emptySet()

    @Volatile
    private var lastLoadAtMs: Long = 0L

    fun blockedHosts(): Set<String> = blockedHosts
    fun blockedPackages(): Set<String> = blockedPackages
    fun videoKeywords(): Set<String> = videoKeywords

    fun loadFromPrefs(context: Context) {
        val p = prefs(context)
        blockedHosts = p.getString(KEY_BLOCKED_HOSTS, "[]")?.let { decodeStringSet(it) } ?: emptySet()
        blockedPackages =
            p.getString(KEY_BLOCKED_PACKAGES, "[]")?.let { decodeStringSet(it) } ?: emptySet()
        videoKeywords =
            p.getString(KEY_VIDEO_KEYWORDS, "[]")?.let { decodeStringSet(it) } ?: emptySet()
        lastLoadAtMs = System.currentTimeMillis()
    }

    fun loadDefaultsFromAsset(context: Context) {
        // يعتمد على وجود ملف blocklists/catalog.json داخل assets.
        try {
            val input = context.assets.open("blocklists/catalog.json")
            val text = input.bufferedReader().use { it.readText() }
            val map: Map<String, Any?> = gson.fromJson(
                text,
                object : TypeToken<Map<String, Any?>>() {}.type,
            )
            val sites = ((map["sites"] as? List<*>) ?: emptyList<Any>()).mapNotNull { it?.toString() }
            val keywords =
                ((map["video_keywords"] as? List<*>) ?: emptyList<Any>()).mapNotNull { it?.toString() }

            blockedHosts = sites.map { it.lowercase(Locale.getDefault()).trim() }.filter { it.isNotEmpty() }.toSet()
            videoKeywords = keywords.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            // blockedPackages لا تأتي من assets — ستأتي من سياسة السيرفر
        } catch (_: Exception) {
            // لا نُعطّل التنفيذ إذا لم يوجد الأصل
        }
    }

    fun updateFromServer(context: Context, childCode: String) {
        val policy = NetworkClientLite.fetchDevicePolicy(childCode)
        blockedHosts = policy.blockedHosts.map { it.lowercase(Locale.getDefault()).trim() }.filter { it.isNotEmpty() }.toSet()
        blockedPackages = policy.blockedPackages.map { it.lowercase(Locale.getDefault()).trim() }.filter { it.isNotEmpty() }.toSet()
        // videoKeywords قد تكون كبيرة؛ استخدمها كسلاسل substring فقط.
        videoKeywords = policy.videoKeywords.map { it.trim() }.filter { it.isNotEmpty() }.toSet()

        persist(context)
    }

    /** إضافة حزمة محلياً فوراً (قبل مزامنة السيرفر التالية). */
    fun addBlockedPackage(context: Context, packageName: String) {
        val pkg = packageName.lowercase(Locale.getDefault()).trim()
        if (pkg.isEmpty()) return
        blockedPackages = blockedPackages + pkg
        persist(context)
    }

    fun removeBlockedPackage(context: Context, packageName: String) {
        val pkg = packageName.lowercase(Locale.getDefault()).trim()
        if (pkg.isEmpty()) return
        blockedPackages = blockedPackages - pkg
        persist(context)
    }

    fun clearBlockedPackages(context: Context) {
        blockedPackages = emptySet()
        persist(context)
    }

    fun addBlockedHost(context: Context, host: String) {
        val h = host.lowercase(Locale.getDefault()).trim()
        if (h.isEmpty()) return
        blockedHosts = blockedHosts + h
        persist(context)
    }

    fun removeBlockedHost(context: Context, host: String) {
        val h = host.lowercase(Locale.getDefault()).trim()
        if (h.isEmpty()) return
        blockedHosts = blockedHosts - h
        persist(context)
    }

    fun clearAll(context: Context) {
        blockedHosts = emptySet()
        blockedPackages = emptySet()
        videoKeywords = emptySet()
        persist(context)
    }

    fun updateFromPrefsIfNeeded(context: Context) {
        if (System.currentTimeMillis() - lastLoadAtMs < 5_000L) return
        loadFromPrefs(context)
    }

    private fun persist(context: Context) {
        prefs(context).edit()
            .putString(KEY_BLOCKED_HOSTS, gson.toJson(blockedHosts.toList()))
            .putString(KEY_BLOCKED_PACKAGES, gson.toJson(blockedPackages.toList()))
            .putString(KEY_VIDEO_KEYWORDS, gson.toJson(videoKeywords.toList()))
            .apply()
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun decodeStringSet(jsonArray: String): Set<String> {
        val list: List<String> = gson.fromJson(
            jsonArray,
            object : TypeToken<List<String>>() {}.type,
        )
        return list.map { it.lowercase(Locale.getDefault()).trim() }.filter { it.isNotEmpty() }.toSet()
    }

    /**
     * تطابق بسيط:
     * - تبحث عن أي pattern محظور داخل النصوص (substring)
     */
    fun matchBlockedHost(texts: List<String>): String? {
        if (blockedHosts.isEmpty()) return null
        val hay = texts.joinToString(" ").lowercase(Locale.getDefault())
        return blockedHosts.firstOrNull { host -> hay.contains(host.lowercase(Locale.getDefault())) }
    }

    fun matchVideoKeyword(texts: List<String>): String? {
        if (videoKeywords.isEmpty()) return null
        val hay = texts.joinToString(" ").lowercase(Locale.getDefault())
        return videoKeywords.firstOrNull { kw -> hay.contains(kw.lowercase(Locale.getDefault())) }
    }

    fun matchOnionDomain(texts: List<String>): Boolean {
        val hay = texts.joinToString(" ").lowercase(Locale.getDefault())
        return hay.contains(".onion") || hay.contains("tor browser") || hay.contains("dark web")
    }
}


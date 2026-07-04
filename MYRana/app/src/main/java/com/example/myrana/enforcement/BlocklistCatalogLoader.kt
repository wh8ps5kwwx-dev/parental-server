package com.example.myrana.enforcement

import android.content.Context
import android.util.Log
import com.example.myrana.data.remote.NetworkModule
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * يحمّل [catalog.json] من السيرفر (`GET /blocklist/catalog`) ويطبّقه على فلاتر التنبيه.
 *
 * المصادر الثلاثة للفلترة:
 * 1. [SafetyKeywordCatalog] — مدمج في التطبيق
 * 2. catalog.json — من السيرفر (مواقع + كلمات فيديو/بحث)
 * 3. سياسة الجهاز — بعد المزامنة من [com.example.myrana.data.repo.PolicyRepository]
 */
object BlocklistCatalogLoader {

    private const val TAG = "BlocklistCatalog"
    private const val PREFS = "myrana_blocklist_catalog"
    private const val KEY_SITES = "catalog_sites"
    private const val KEY_KEYWORDS = "catalog_keywords"
    private const val KEY_LAST_SYNC_MS = "catalog_last_sync_ms"
    private const val SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private const val KEY_BUNDLED_LOADED = "bundled_catalog_loaded"
    private const val ASSET_PATH = "blocklists/catalog.json"
    private val gson = Gson()

    data class Catalog(
        val sites: List<String>,
        val keywords: List<String>,
    )

    /** تحميل من assets ثم الذاكرة المحلية ثم محاولة تحديث من السيرفر. */
    fun prepare(context: Context) {
        ensureBundledCatalog(context)
        loadCachedIntoFilter(context)
        syncFromServerIfStale(context)
    }

    /** قائمة الحظر الافتراضية مدمجة في APK — تعمل بدون Turso أو اتصال دائم. */
    private fun ensureBundledCatalog(context: Context) {
        val app = context.applicationContext
        val prefs = prefs(app)
        if (prefs.getBoolean(KEY_BUNDLED_LOADED, false)) return
        try {
            app.assets.open(ASSET_PATH).bufferedReader().use { reader ->
                parseCatalogJson(reader.readText())?.let { catalog ->
                    saveAndApply(app, catalog)
                    prefs.edit().putBoolean(KEY_BUNDLED_LOADED, true).apply()
                    Log.i(TAG, "bundled catalog loaded from assets")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "bundled catalog missing or invalid: ${e.message}")
        }
    }

    private fun parseCatalogJson(text: String): Catalog? {
        if (text.isBlank()) return null
        return try {
            val mapType = object : TypeToken<Map<String, Any?>>() {}.type
            val root: Map<String, Any?> = gson.fromJson(text, mapType)
            @Suppress("UNCHECKED_CAST")
            val cat = root["catalog"] as? Map<String, Any?> ?: root
            val sites = (cat["sites"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val keywords = (cat["video_keywords"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            Catalog(sites, keywords)
        } catch (e: Exception) {
            Log.w(TAG, "parse catalog failed: ${e.message}")
            null
        }
    }

    fun loadCachedIntoFilter(context: Context) {
        val prefs = prefs(context)
        val sites = prefs.getStringSet(KEY_SITES, emptySet()).orEmpty().toList()
        val keywords = prefs.getStringSet(KEY_KEYWORDS, emptySet()).orEmpty().toList()
        if (sites.isNotEmpty() || keywords.isNotEmpty()) {
            PolicyFilterCache.applyCatalogFilter(sites, keywords)
            Log.i(TAG, "cached catalog — sites=${sites.size} keywords=${keywords.size}")
        }
    }

    fun syncFromServerIfStale(context: Context) {
        val app = context.applicationContext
        val last = prefs(app).getLong(KEY_LAST_SYNC_MS, 0L)
        if (System.currentTimeMillis() - last < SYNC_INTERVAL_MS) return
        scope.launch {
            try {
                val catalog = NetworkModule.fetchBlocklistCatalog() ?: return@launch
                saveAndApply(app, catalog)
            } catch (e: Exception) {
                Log.w(TAG, "catalog sync failed: ${e.message}")
            }
        }
    }

    /** فرض تحديث فوري (بعد ربط الطفل أو زر الأم «قائمة الحظر الافتراضية»). */
    fun syncFromServerNow(context: Context) {
        scope.launch {
            try {
                val catalog = NetworkModule.fetchBlocklistCatalog() ?: return@launch
                saveAndApply(context.applicationContext, catalog)
            } catch (e: Exception) {
                Log.w(TAG, "catalog sync now failed: ${e.message}")
            }
        }
    }

    private fun saveAndApply(context: Context, catalog: Catalog) {
        prefs(context).edit()
            .putStringSet(KEY_SITES, catalog.sites.map { it.trim().lowercase() }.toSet())
            .putStringSet(KEY_KEYWORDS, catalog.keywords.map { it.trim().lowercase() }.toSet())
            .putLong(KEY_LAST_SYNC_MS, System.currentTimeMillis())
            .apply()
        PolicyFilterCache.applyCatalogFilter(catalog.sites, catalog.keywords)
        Log.i(TAG, "catalog synced — sites=${catalog.sites.size} keywords=${catalog.keywords.size}")
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

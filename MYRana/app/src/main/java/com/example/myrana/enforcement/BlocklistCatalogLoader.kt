package com.example.myrana.enforcement

import android.content.Context
import android.util.Log
import com.example.myrana.data.remote.NetworkModule
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

    data class Catalog(
        val sites: List<String>,
        val keywords: List<String>,
    )

    /** تحميل من الذاكرة المحلية ثم محاولة تحديث من السيرفر. */
    fun prepare(context: Context) {
        loadCachedIntoFilter(context)
        syncFromServerIfStale(context)
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

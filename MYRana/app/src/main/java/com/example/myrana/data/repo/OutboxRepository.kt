package com.example.myrana.data.repo

import android.content.Context
import com.example.myrana.data.local.AppDatabase
import com.example.myrana.data.local.PendingOutboxEntity
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.network.NetworkMonitor
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * طابور محلي **لاستخدام الجهاز فقط** (وقت التطبيقات) — يُرفع إلى `/upload-usage`
 * عند توفر النت أو يُخزَّن عند الانقطاع.
 */
class OutboxRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).pendingOutboxDao()
    private val gson = Gson()

    /** حفظ/إرسال دفعة استخدام (ثوانٍ لكل حزمة تطبيق). */
    suspend fun submitUsage(childCode: String, secondsByPackage: Map<String, Long>) =
        withContext(Dispatchers.IO) {
            if (secondsByPackage.isEmpty()) return@withContext
            val day = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                .format(java.util.Date())
            val entries = secondsByPackage.map { (pkg, sec) ->
                mapOf("package" to pkg, "day" to day, "seconds" to sec)
            }
            if (NetworkMonitor.isOnline(appContext) &&
                NetworkModule.uploadUsageSync(childCode, secondsByPackage)
            ) {
                return@withContext
            }
            dao.insert(
                PendingOutboxEntity(
                    kind = PendingOutboxEntity.KIND_USAGE,
                    childCode = childCode,
                    payloadJson = gson.toJson(mapOf("entries" to entries)),
                    createdAtEpochMs = System.currentTimeMillis()
                )
            )
        }

    /** رفع كل سجلات الاستخدام المعلّقة. */
    suspend fun flushPending(): Int = withContext(Dispatchers.IO) {
        if (!NetworkMonitor.isOnline(appContext)) return@withContext 0
        var sent = 0
        for (item in dao.listOldest(50)) {
            if (item.kind != PendingOutboxEntity.KIND_USAGE) {
                dao.deleteById(item.id)
                continue
            }
            if (sendUsage(item)) {
                dao.deleteById(item.id)
                sent++
            }
        }
        sent
    }

    private fun sendUsage(item: PendingOutboxEntity): Boolean {
        val mapType = object : TypeToken<Map<String, Any?>>() {}.type
        val body: Map<String, Any?> = gson.fromJson(item.payloadJson, mapType)
        @Suppress("UNCHECKED_CAST")
        val entries = body["entries"] as? List<Map<String, Any?>> ?: return true
        val secondsByPackage = mutableMapOf<String, Long>()
        for (entry in entries) {
            val pkg = entry["package"]?.toString() ?: continue
            val sec = (entry["seconds"] as? Number)?.toLong() ?: 0L
            if (sec > 0) secondsByPackage[pkg] = sec
        }
        if (secondsByPackage.isEmpty()) return true
        return NetworkModule.uploadUsageSync(item.childCode, secondsByPackage)
    }

    companion object {
        @Volatile
        private var instance: OutboxRepository? = null

        fun get(context: Context): OutboxRepository {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: OutboxRepository(app).also { instance = it }
            }
        }
    }
}

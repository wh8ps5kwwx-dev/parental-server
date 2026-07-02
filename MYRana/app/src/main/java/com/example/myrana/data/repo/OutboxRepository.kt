package com.example.myrana.data.repo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * واجهة قديمة — تُحوّل إلى [UsageSyncRepository] (حفظ محلي ثم رفع).
 */
class OutboxRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val usageSync = UsageSyncRepository.get(appContext)

    suspend fun submitUsage(childCode: String, secondsByPackage: Map<String, Long>) =
        withContext(Dispatchers.IO) {
            usageSync.mergeUsage(secondsByPackage)
            usageSync.trySync(childCode)
        }

    suspend fun flushPending(): Int = withContext(Dispatchers.IO) {
        val childCode = com.example.myrana.session.ChildSession.childCode(appContext)
            ?: com.example.myrana.device.DeviceIdentity.childDeviceId(appContext)
        if (childCode.isBlank()) return@withContext 0
        val before = usageSync.pendingCount()
        usageSync.trySync(childCode)
        val after = usageSync.pendingCount()
        (before - after).coerceAtLeast(0)
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

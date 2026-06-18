package com.example.myrana.data.repo

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.myrana.data.local.AppDatabase
import com.example.myrana.data.local.BlockedAppEntity
import com.example.myrana.data.local.BlockedSiteEntity
import com.example.myrana.data.local.SyncStateEntity
import com.example.myrana.enforcement.BlocklistCatalogLoader
import com.example.myrana.enforcement.PolicyFilterCache
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.data.remote.dto.PolicyPushDto
import com.example.myrana.identity.ChildIdentity
import com.example.myrana.sync.GuardianMessageNotifier
import com.example.myrana.sync.UsageUploadHelper
import com.example.myrana.util.ChildCodeNormalizer
import com.example.myrana.worker.MonitoringScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * مستودع السياسة: يجمع Room (محلي) و REST (سيرفر).
 *
 * ترتيب [syncWithServer]:
 * 1. تطبيق أوامر الأم من `/get-command` (FIFO — حتى 8 أوامر).
 * 2. رفع الصفوف ذات `pending_upload`.
 * 3. سحب السياسة إذا تغيّر revision (وإلا تخطي).
 *
 * المصدر الموثوق بعد المزامنة: **الخادم**.
 */
class PolicyRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val api = NetworkModule.api()

    suspend fun syncWithServer(deviceId: String) = withContext(Dispatchers.IO) {
        val apiId = ChildCodeNormalizer.forApi(deviceId).ifBlank { deviceId }
        applyParentCommands(apiId)
        pushPending(apiId)
        pullAndApply(apiId)
    }

    /** يستهلك أوامر الأم بالترتيب (الأقدم أولاً على السيرفر). */
    private suspend fun applyParentCommands(deviceId: String) {
        repeat(MAX_COMMANDS_PER_SYNC) {
            val cmd = try {
                NetworkModule.pollParentCommand(deviceId)
            } catch (_: Exception) {
                null
            } ?: return

            when (cmd.action) {
                "block_app", "freeze_app" -> cmd.value?.let { addBlockedPackage(it) }
                "block_site" -> cmd.value?.let { addBlockedSite(it) }
                "allow" -> {
                    // السيرفر أفرغ السياسة — pullAndApply يحدّث Room لاحقاً
                }
                "apply_default_blocklist" -> {
                    // السيرفر طبّق catalog — pullAndApply يحدّث Room لاحقاً
                }
                "request_usage" -> uploadUsageNow(deviceId)
                "guardian_message" -> cmd.value?.let { GuardianMessageNotifier.show(appContext, it) }
            }
        }
    }

    private suspend fun uploadUsageNow(childCode: String) {
        UsageUploadHelper.uploadNow(appContext, childCode)
        MonitoringScheduler.runOnceNow(appContext)
    }

    suspend fun addBlockedSite(hostPattern: String) = withContext(Dispatchers.IO) {
        val trimmed = hostPattern.trim().lowercase()
        if (trimmed.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        db.blockedSiteDao().upsert(
            BlockedSiteEntity(
                hostPattern = trimmed,
                createdAtEpochMs = now,
                pendingUpload = true
            )
        )
    }

    suspend fun addBlockedPackage(packageName: String) = withContext(Dispatchers.IO) {
        val trimmed = packageName.trim().lowercase()
        if (trimmed.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        db.blockedAppDao().upsert(
            BlockedAppEntity(
                packageName = trimmed,
                createdAtEpochMs = now,
                pendingUpload = true
            )
        )
    }

    suspend fun localSnapshot(): Pair<List<String>, List<String>> = withContext(Dispatchers.IO) {
        val sites = db.blockedSiteDao().listAll().map { it.hostPattern }
        val apps = db.blockedAppDao().listAll().map { it.packageName }
        sites to apps
    }

    private suspend fun pushPending(deviceId: String) {
        val pendingSites = db.blockedSiteDao().listPendingUpload()
        val pendingApps = db.blockedAppDao().listPendingUpload()
        if (pendingSites.isEmpty() && pendingApps.isEmpty()) return

        val body = PolicyPushDto(
            blockedHosts = pendingSites.map { it.hostPattern },
            blockedPackages = pendingApps.map { it.packageName }
        )
        val response = api.pushPolicy(deviceId, body)
        val siteIds = pendingSites.map { it.id }
        val appIds = pendingApps.map { it.id }
        val now = System.currentTimeMillis()
        val prev = db.syncStateDao().get()

        db.withTransaction {
            if (siteIds.isNotEmpty()) {
                db.blockedSiteDao().clearPendingFlags(siteIds)
            }
            if (appIds.isNotEmpty()) {
                db.blockedAppDao().clearPendingFlags(appIds)
            }
            db.syncStateDao().upsert(
                SyncStateEntity(
                    lastPullEpochMs = prev?.lastPullEpochMs ?: 0L,
                    lastPushEpochMs = now,
                    lastKnownRevision = response.revision ?: prev?.lastKnownRevision ?: 0L
                )
            )
        }
    }

    private suspend fun pullAndApply(deviceId: String) {
        val prev = db.syncStateDao().get()
        val sinceRev = prev?.lastKnownRevision ?: 0L
        val envelope = api.fetchPolicy(deviceId, sinceRev)

        if (envelope.unchanged == true) {
            Log.d(TAG, "سياسة بدون تغيير revision=$sinceRev")
            return
        }

        val now = System.currentTimeMillis()
        val sites = envelope.blockedHosts.orEmpty().map { host ->
            BlockedSiteEntity(
                hostPattern = host.trim().lowercase(),
                createdAtEpochMs = now,
                pendingUpload = false
            )
        }
        val apps = envelope.blockedPackages.orEmpty().map { pkg ->
            BlockedAppEntity(
                packageName = pkg.trim().lowercase(),
                createdAtEpochMs = now,
                pendingUpload = false
            )
        }

        db.withTransaction {
            db.blockedSiteDao().deleteAll()
            db.blockedAppDao().deleteAll()
            if (sites.isNotEmpty()) {
                db.blockedSiteDao().upsertAll(sites)
            }
            if (apps.isNotEmpty()) {
                db.blockedAppDao().upsertAll(apps)
            }
            db.syncStateDao().upsert(
                SyncStateEntity(
                    lastPullEpochMs = now,
                    lastPushEpochMs = prev?.lastPushEpochMs ?: 0L,
                    lastKnownRevision = envelope.revision ?: prev?.lastKnownRevision ?: 0L
                )
            )
        }
        val kw = envelope.videoKeywords.orEmpty()
        PolicyFilterCache.update(sites.map { it.hostPattern }, kw)
        PolicyFilterCache.persistKeywords(appContext, kw)
        BlocklistCatalogLoader.loadCachedIntoFilter(appContext)
    }

    companion object {
        private const val TAG = "PolicyRepository"
        private const val MAX_COMMANDS_PER_SYNC = 8

        @Volatile
        private var instance: PolicyRepository? = null

        fun get(context: Context): PolicyRepository {
            val app = context.applicationContext
            return instance ?: synchronized(this) {
                instance ?: PolicyRepository(app.applicationContext).also { instance = it }
            }
        }
    }
}

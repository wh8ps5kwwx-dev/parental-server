package com.example.myrana.data.repo

import android.content.Context
import androidx.room.withTransaction
import com.example.myrana.data.local.AppDatabase
import com.example.myrana.data.local.BlockedAppEntity
import com.example.myrana.data.local.BlockedSiteEntity
import com.example.myrana.data.local.SyncStateEntity
import com.example.myrana.enforcement.BlocklistCatalogLoader
import com.example.myrana.enforcement.PolicyFilterCache
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.data.remote.dto.PolicyPushDto
import com.example.myrana.session.ChildSession
import com.example.myrana.sync.GuardianMessageNotifier
import com.example.myrana.sync.InstalledAppsSync
import com.example.myrana.sync.UsageUploadHelper
import com.example.myrana.screentime.ParentResponseWatchdog
import com.example.myrana.worker.MonitoringScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * مستودع السياسة: يجمع Room (محلي) و REST (سيرفر parent_monitor_project).
 *
 * ترتيب [syncWithServer]:
 * 1. تطبيق أمر الأم الأخير من `/get-command`.
 * 2. رفع الصفوف ذات `pending_upload`.
 * 3. سحب السياسة من السيرفر ودمجها مع المحلي (لا يُمسح الحظر عند فقدان بيانات Render).
 */
class PolicyRepository private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val db = AppDatabase.getInstance(appContext)
    private val api = NetworkModule.api()

    /**
     * دورة مزامنة كاملة — تُستدعى من [ParentSyncService] أو أثناء اللعب.
     * @param deviceId عادةً `child_code` من [com.example.myrana.device.DeviceIdentity].
     */
    suspend fun syncWithServer(deviceId: String) = withContext(Dispatchers.IO) {
        applyParentCommand(deviceId)
        pushPending(deviceId)
        pullAndApply(deviceId)
    }

    /**
     * يقرأ أمراً واحداً من الأم ويضيفه محلياً (حظر موقع/تطبيق).
     * أمر `allow` يُفرّغ السياسة على السيرفر؛ [pullAndApply] يحدّث Room لاحقاً.
     */
    private suspend fun applyParentCommand(deviceId: String) {
        val cmd = try {
            NetworkModule.pollParentCommand(deviceId)
        } catch (_: Exception) {
            null
        } ?: return

        when (cmd.action) {
            "block_app", "freeze_app" -> cmd.value?.let { addBlockedPackage(it) }
            "block_site" -> cmd.value?.let { addBlockedSite(it) }
            "allow" -> {
                ParentResponseWatchdog.onParentResponded()
                clearLocalPolicy()
            }
            "request_usage" -> {
                val childCode = ChildSession.childCode(appContext) ?: deviceId
                uploadUsageNow(childCode)
            }
            "sync_installed_apps" -> InstalledAppsSync.syncNow(appContext)
            "guardian_message" -> cmd.value?.let { GuardianMessageNotifier.show(appContext, it) }
        }
    }

    private suspend fun uploadUsageNow(childCode: String) {
        UsageUploadHelper.uploadNow(appContext, childCode)
        MonitoringScheduler.runOnceNow(appContext)
    }

    /**
     * إضافة نطاق محظور محلياً مع وسمه للرفع في المزامنة التالية.
     */
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

    /**
     * إضافة حزمة تطبيق محظورة محلياً.
     */
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

    /** قراءة القوائم المحلية فقط — بدون شبكة (لشاشة التطوير). */
    suspend fun localSnapshot(): Pair<List<String>, List<String>> = withContext(Dispatchers.IO) {
        val sites = db.blockedSiteDao().listAll().map { it.hostPattern }
        val apps = db.blockedAppDao().listAll().map { it.packageName }
        sites to apps
    }

    /** يرفع كل الصفوف المعلّقة ثم يصفّر `pending_upload` داخل معاملة واحدة. */
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

    /** يفرّغ الحظر المحلي — عند أمر «سماح» من الأم. */
    private suspend fun clearLocalPolicy() = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.blockedSiteDao().deleteAll()
            db.blockedAppDao().deleteAll()
        }
        PolicyFilterCache.clearDevicePolicy()
    }

    /**
     * يدمج سياسة السيرفر مع المحلي — لا يمسح الحظر عند فقدان بيانات Render.
     */
    private suspend fun pullAndApply(deviceId: String) {
        val envelope = api.fetchPolicy(deviceId)
        val now = System.currentTimeMillis()
        val localSites = db.blockedSiteDao().listAll()
        val localApps = db.blockedAppDao().listAll()

        val serverSitePatterns = envelope.blockedHosts.orEmpty()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        val serverAppPatterns = envelope.blockedPackages.orEmpty()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

        val mergedSitePatterns = LinkedHashSet<String>().apply {
            addAll(serverSitePatterns)
            localSites.forEach { add(it.hostPattern) }
        }
        val mergedAppPatterns = LinkedHashSet<String>().apply {
            addAll(serverAppPatterns)
            localApps.forEach { add(it.packageName) }
        }

        val sites = mergedSitePatterns.map { host ->
            val local = localSites.find { it.hostPattern == host }
            BlockedSiteEntity(
                hostPattern = host,
                createdAtEpochMs = local?.createdAtEpochMs ?: now,
                pendingUpload = local?.pendingUpload == true && host !in serverSitePatterns,
            )
        }
        val apps = mergedAppPatterns.map { pkg ->
            val local = localApps.find { it.packageName == pkg }
            BlockedAppEntity(
                packageName = pkg,
                createdAtEpochMs = local?.createdAtEpochMs ?: now,
                pendingUpload = local?.pendingUpload == true && pkg !in serverAppPatterns,
            )
        }
        val prev = db.syncStateDao().get()

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

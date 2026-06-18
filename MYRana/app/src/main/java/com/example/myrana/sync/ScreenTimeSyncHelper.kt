package com.example.myrana.sync

import android.content.Context
import com.example.myrana.identity.ChildIdentity
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.screentime.ScreenTimePolicyStore
import com.example.myrana.screentime.ScreenTimeRepository
import com.example.myrana.permissions.PermissionStatusReporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** مزامنة سياسة وقت الشاشة وأحداث التحذير مع السيرفر. */
object ScreenTimeSyncHelper {

    suspend fun syncIfDue(context: Context) = withContext(Dispatchers.IO) {
        val childCode = ChildIdentity.apiCode(context)
        if (childCode.isBlank()) return@withContext
        try {
            NetworkModule.fetchScreenTimePolicy(childCode)?.let {
                ScreenTimePolicyStore.save(context, it)
            }
            NetworkModule.postChildHeartbeat(childCode, PermissionStatusReporter.toPayload(context))
            uploadPendingEvents(context, childCode)
        } catch (_: Exception) {
        }
    }

    private suspend fun uploadPendingEvents(context: Context, childCode: String) {
        val repo = ScreenTimeRepository.get(context)
        val pending = repo.flushPendingEvents()
        if (pending.isEmpty()) return
        val ok = NetworkModule.postScreenTimeEvents(
            childCode,
            pending.map {
                mapOf(
                    "event_type" to it.eventType,
                    "package_name" to it.packageName,
                    "message" to it.message,
                    "seconds_used" to it.secondsUsed,
                    "created_at_ms" to it.createdAtMs,
                )
            },
        )
        if (ok) {
            repo.markEventsUploaded(pending.map { it.id })
        }
    }
}

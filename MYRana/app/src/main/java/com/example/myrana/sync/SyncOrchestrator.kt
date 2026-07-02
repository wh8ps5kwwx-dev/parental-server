package com.example.myrana.sync

import android.content.Context
import android.util.Log
import com.example.myrana.data.repo.OutboxRepository
import com.example.myrana.data.repo.PolicyRepository
import com.example.myrana.enforcement.EnforcementEngine
import com.example.myrana.enforcement.MediaLibraryScanner
import com.example.myrana.identity.ChildIdentity
import com.example.myrana.session.ChildSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * دورة مزامنة وتحكم موحّدة لجهاز الطفل.
 *
 * الترتيب المنطقي:
 * 1) التحقق من الربط على السيرفر (وإعادة التسجيل إن لزم).
 * 2) أوامر الأم + رفع السياسة + سحب السياسة (المصدر الموثوق: السيرفر).
 * 3) فرض الحظر والجداول الزمنية.
 * 4) وقت الشاشة + نبضة اتصال + الصلاحيات.
 * 5) الاستخدام والوسائط و Outbox.
 */
object SyncOrchestrator {

    private const val TAG = "SyncOrchestrator"

    suspend fun runChildCycle(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        if (!ChildSession.isSetupComplete(app)) return@withContext

        when (LinkStateGuard.ensureServerRegistration(app)) {
            LinkStateGuard.Status.REREGISTERED ->
                Log.i(TAG, "أُعيد تسجيل الطفل على السيرفر بعد فقدان البيانات")
            LinkStateGuard.Status.FAILED ->
                Log.w(TAG, "تعذّر التحقق من الربط — تُؤجَّل المزامنة")
            else -> Unit
        }

        val deviceId = ChildIdentity.apiCode(app)
        if (deviceId.isBlank()) {
            Log.w(TAG, "لا يوجد child_code — تخطي المزامنة")
            return@withContext
        }

        try {
            val repo = PolicyRepository.get(app)
            val engine = EnforcementEngine.get(app)
            val outbox = OutboxRepository.get(app)

            repo.syncWithServer(deviceId)
            engine.refreshFromServer(deviceId)
            ScreenTimeSyncHelper.syncIfDue(app)
            UsageUploadHelper.uploadPeriodicIfDue(app, deviceId)
            MediaLibraryScanner.scanIfDue(app)
            outbox.flushPending()
        } catch (e: Exception) {
            Log.w(TAG, "فشلت دورة المزامنة: ${e.message}")
        }
    }
}

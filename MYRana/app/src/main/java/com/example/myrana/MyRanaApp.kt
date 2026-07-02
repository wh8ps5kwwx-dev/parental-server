package com.example.myrana

import android.app.Application
import com.example.myrana.data.local.AppDatabase
import com.example.myrana.data.repo.OutboxRepository
import com.example.myrana.data.repo.UsageSyncRepository
import com.example.myrana.network.NetworkMonitor
import com.example.myrana.device.DeviceIdentity
import com.example.myrana.permissions.ChildProjectRuntime
import com.example.myrana.session.ChildSession
import com.example.myrana.sync.UsageUploadHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * نقطة دخول التطبيق (Application).
 *
 * **نكهة الطفل فقط** (`com.example.myrana.child`):
 * - تهيئة Room
 * - بعد إتمام التسجيل: تشغيل [SyncStarter] (WorkManager + خدمة أمامية)
 * - عند عودة الإنترنت: إفراغ طابور رفع الاستخدام [OutboxRepository]
 *
 * نكهة الأم لا تشغّل مراقبة خلفية من هنا.
 */
class MyRanaApp : Application() {

    /** نطاق Coroutine على مستوى التطبيق — يعيش طوال عمر العملية. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** إلغاء تسجيل مراقب الشبكة عند إعادة التسجيل. */
    private var unregisterNetwork: (() -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        AppDatabase.getInstance(this)
        if (!isChildApp()) return
        if (ChildProjectRuntime.isMonitoringOperational(this)) {
            ChildProjectRuntime.activateMonitoring(this)
            registerNetworkFlush()
        }
    }

    /** يُستدعى بعد الصلاحيات — المراقبة تبدأ حتى بدون فتح اللعبة. */
    fun onChildSetupReady() {
        if (!isChildApp()) return
        if (!ChildProjectRuntime.isMonitoringOperational(this)) return
        ChildProjectRuntime.activateMonitoring(this)
        registerNetworkFlush()
        appScope.launch {
            val code = ChildSession.childCode(this@MyRanaApp)
                ?: DeviceIdentity.childDeviceId(this@MyRanaApp)
            UsageSyncRepository.get(this@MyRanaApp).trySync(code)
            OutboxRepository.get(this@MyRanaApp).flushPending()
        }
    }

    private fun registerNetworkFlush() {
        unregisterNetwork?.invoke()
        unregisterNetwork = NetworkMonitor.registerOnAvailable(this) {
            appScope.launch {
                val code = ChildSession.childCode(this@MyRanaApp)
                    ?: DeviceIdentity.childDeviceId(this@MyRanaApp)
                UsageUploadHelper.uploadPeriodicIfDue(this@MyRanaApp, code)
                UsageSyncRepository.get(this@MyRanaApp).trySync(code)
                OutboxRepository.get(this@MyRanaApp).flushPending()
            }
        }
    }

    private fun isChildApp(): Boolean = packageName.endsWith(".child")
}

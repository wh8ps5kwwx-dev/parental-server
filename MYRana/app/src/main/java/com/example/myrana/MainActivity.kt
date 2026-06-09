package com.example.myrana

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.myrana.data.repo.PolicyRepository
import com.example.myrana.device.DeviceIdentity
import com.example.myrana.permissions.PermissionCoordinator
import com.example.myrana.service.ParentSyncService
import kotlinx.coroutines.launch

/**
 * شاشة **تطوير/اختبار** — غير ظاهرة للطفل (ليست LAUNCHER).
 *
 * تُستخدم لفحص Room والمزامنة يدوياً أثناء التطوير.
 * التجربة الإنتاجية للطفل: [com.example.myrana.ui.GameActivity] فقط.
 *
 * تنفيذ الحظر الفعلي على التطبيقات/الشبكة يحتاج VPN أو Accessibility لاحقاً.
 */
class MainActivity : AppCompatActivity() {

    private val repo by lazy { PolicyRepository.get(this) }

    /** كاش صلاحية الإشعارات لهذه الجلسة. */
    private var notificationsGrantedCache: Boolean = false

    private val requestNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        PermissionCoordinator.markInitialNotificationFlowDone(this)
        notificationsGrantedCache =
            granted || PermissionCoordinator.hasNotificationPermission(this)
        Toast.makeText(
            this,
            if (notificationsGrantedCache) {
                getString(R.string.btn_permissions) + ": OK"
            } else {
                "تم الرفض — يمكن الإعادة من زر الصلاحيات"
            },
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val inputChildCode = findViewById<EditText>(R.id.inputChildCode)
        val inputHost = findViewById<EditText>(R.id.inputHost)
        val inputPackage = findViewById<EditText>(R.id.inputPackage)
        val textLists = findViewById<TextView>(R.id.textLists)

        inputChildCode.setText(DeviceIdentity.childDeviceId(this))

        findViewById<Button>(R.id.btnSetChildCode).setOnClickListener {
            val code = inputChildCode.text.toString().trim()
            if (code.isEmpty()) {
                Toast.makeText(this, "أدخل كود الطفل", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            DeviceIdentity.setChildDeviceId(this, code)
            Toast.makeText(this, "تم الحفظ", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch { refreshLists(textLists) }
        }

        syncNotificationPermissionSnapshotFromSystem()

        if (PermissionCoordinator.shouldOfferAutomaticNotificationPrompt(this)) {
            requestNotifications.launch(PermissionCoordinator.notificationPermission)
        }

        findViewById<Button>(R.id.btnPermissions).setOnClickListener {
            if (!PermissionCoordinator.needsPostNotificationsPermission()) {
                Toast.makeText(this, "غير مطلوب على هذا الإصدار", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            requestNotifications.launch(PermissionCoordinator.notificationPermission)
        }

        findViewById<Button>(R.id.btnSyncStart).setOnClickListener {
            if (PermissionCoordinator.needsPostNotificationsPermission() && !notificationsGrantedCache) {
                Toast.makeText(
                    this,
                    "فعّل صلاحية الإشعارات أولاً على أندرويد 13+",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            ParentSyncService.start(this)
        }

        findViewById<Button>(R.id.btnSyncStop).setOnClickListener {
            ParentSyncService.stop(this)
        }

        findViewById<Button>(R.id.btnSyncOnce).setOnClickListener {
            lifecycleScope.launch {
                try {
                    repo.syncWithServer(DeviceIdentity.childDeviceId(this@MainActivity))
                    Toast.makeText(this@MainActivity, "تمت المحاولة", Toast.LENGTH_SHORT).show()
                    refreshLists(textLists)
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "فشل: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        findViewById<Button>(R.id.btnAddSite).setOnClickListener {
            lifecycleScope.launch {
                repo.addBlockedSite(inputHost.text.toString())
                refreshLists(textLists)
            }
        }

        findViewById<Button>(R.id.btnAddApp).setOnClickListener {
            lifecycleScope.launch {
                repo.addBlockedPackage(inputPackage.text.toString())
                refreshLists(textLists)
            }
        }

        lifecycleScope.launch { refreshLists(textLists) }
    }

    override fun onResume() {
        super.onResume()
        if (PermissionCoordinator.needsPostNotificationsPermission() && !notificationsGrantedCache) {
            notificationsGrantedCache = PermissionCoordinator.hasNotificationPermission(this)
            if (notificationsGrantedCache) {
                PermissionCoordinator.markInitialNotificationFlowDone(this)
            }
        }
    }

    private fun syncNotificationPermissionSnapshotFromSystem() {
        when {
            !PermissionCoordinator.needsPostNotificationsPermission() -> {
                notificationsGrantedCache = true
                PermissionCoordinator.markInitialNotificationFlowDone(this)
            }
            PermissionCoordinator.hasNotificationPermission(this) -> {
                notificationsGrantedCache = true
                PermissionCoordinator.markInitialNotificationFlowDone(this)
            }
            else -> notificationsGrantedCache = false
        }
    }

    private suspend fun refreshLists(target: TextView) {
        val (sites, apps) = repo.localSnapshot()
        val text = buildString {
            appendLine("كود الطفل: ${DeviceIdentity.childDeviceId(this@MainActivity)}")
            appendLine()
            appendLine("مواقع:")
            if (sites.isEmpty()) appendLine(" — فارغ")
            else sites.forEach { appendLine(" • $it") }
            appendLine()
            appendLine("حزم:")
            if (apps.isEmpty()) appendLine(" — فارغ")
            else apps.forEach { appendLine(" • $it") }
        }
        target.text = text
    }
}

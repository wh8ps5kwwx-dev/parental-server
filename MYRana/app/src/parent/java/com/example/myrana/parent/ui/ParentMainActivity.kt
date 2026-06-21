package com.example.myrana.parent.ui

import android.graphics.Color
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.PatternsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.myrana.BuildConfig
import com.example.myrana.R
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.parent.ParentLinkSync
import com.example.myrana.parent.ParentSession
import android.net.Uri
import com.example.myrana.util.ChildCodeNormalizer
import com.example.myrana.util.ServerConnectionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * تطبيق ولي الأمر (نكهة `parent`) — لوحة التحكم الكاملة.
 *
 * **الخطوات:**
 * 0. صفحة رئيسية — تعريف بالتطبيق ونصائح
 * 1. بريد + صفة → تحقق
 * 2. ربط جهاز الطفل (كود CHILD + رمز الربط)
 * 3. اسم الطفل → إتمام الربط
 * 4. تحكم: حظر، وقت استخدام، تنبيهات، إضافة طفل آخر
 *
 * **السيرفر:** [com.example.myrana.data.remote.GuardianApi] + [NetworkModule]
 */
class ParentMainActivity : AppCompatActivity() {

    private data class LinkedChildInfo(
        val code: String,
        val name: String,
        val online: Boolean,
        val todayMinutes: Int = -1,
        val sleepStart: String = "22:00",
        val sleepEnd: String = "07:00",
    )

    private lateinit var parentScroll: ScrollView
    private lateinit var stepWelcome: LinearLayout
    private lateinit var stepLogin: LinearLayout
    private lateinit var stepVerify: LinearLayout
    private lateinit var stepAddChild: LinearLayout
    private lateinit var stepLink: LinearLayout
    private lateinit var stepLinkConfirm: LinearLayout
    private lateinit var stepControl: LinearLayout
    private lateinit var textStepIndicator: TextView
    private lateinit var textMessage: TextView
    private lateinit var textLinkedChild: TextView
    private lateinit var textDashboardMini: TextView
    private lateinit var textPermissionsMini: TextView
    private lateinit var spinnerChildren: Spinner
    private lateinit var textSelectActiveChild: TextView
    private lateinit var layoutLinkedChildrenList: LinearLayout
    private lateinit var textMultiChildHint: TextView
    private lateinit var checkApplyAllChildren: CheckBox
    private lateinit var textAlertsPreview: TextView
    private var linkedChildrenRows: List<LinkedChildInfo> = emptyList()
    private val selectedChildCodes = linkedSetOf<String>()
    private var childSpinnerIgnoreSelection = false
    private lateinit var textUsageTitle: TextView
    private var alertPollingJob: Job? = null
    private lateinit var textUsageEmpty: TextView
    private lateinit var recyclerUsage: RecyclerView
    private lateinit var usageAdapter: UsageReportAdapter
    private lateinit var parentBottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent_main)

        parentScroll = findViewById(R.id.parentScroll)
        stepWelcome = findViewById(R.id.stepWelcome)
        stepLogin = findViewById(R.id.stepLogin)
        stepVerify = findViewById(R.id.stepVerify)
        stepAddChild = findViewById(R.id.stepAddChild)
        stepLink = findViewById(R.id.stepLink)
        stepLinkConfirm = findViewById(R.id.stepLinkConfirm)
        stepControl = findViewById(R.id.stepControl)
        textStepIndicator = findViewById(R.id.textStepIndicator)
        textMessage = findViewById(R.id.textParentMessage)
        textLinkedChild = findViewById(R.id.textLinkedChild)
        textDashboardMini = findViewById(R.id.textDashboardMini)
        textPermissionsMini = findViewById(R.id.textPermissionsMini)
        spinnerChildren = findViewById(R.id.spinnerChildren)
        textSelectActiveChild = findViewById(R.id.textSelectActiveChild)
        layoutLinkedChildrenList = findViewById(R.id.layoutLinkedChildrenList)
        textMultiChildHint = findViewById(R.id.textMultiChildHint)
        checkApplyAllChildren = findViewById(R.id.checkApplyAllChildren)
        textAlertsPreview = findViewById(R.id.textAlertsPreview)
        textUsageTitle = findViewById(R.id.textUsageReportTitle)
        textUsageEmpty = findViewById(R.id.textUsageEmpty)
        recyclerUsage = findViewById(R.id.recyclerUsage)

        usageAdapter = UsageReportAdapter(packageManager)
        recyclerUsage.layoutManager = LinearLayoutManager(this)
        recyclerUsage.adapter = usageAdapter

        setupRoleSpinner()
        spinnerChildren.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (childSpinnerIgnoreSelection) return
                val row = linkedChildrenRows.getOrNull(position) ?: return
                selectActiveChild(row.code, row.name)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        findViewById<Button>(R.id.btnSendEmailCode).setOnClickListener { sendEmailCode() }
        findViewById<Button>(R.id.btnVerifyEmail).setOnClickListener { verifyEmail() }
        // بعد الربط (كود + OTP) ندخل بيانات الطفل ثم "إتمام الربط"
        findViewById<Button>(R.id.btnContinueToLink).setOnClickListener { linkChild() }
        findViewById<Button>(R.id.btnAutoLink).setOnClickListener { autoLinkChild() }
        findViewById<Button>(R.id.btnSendLinkCode).setOnClickListener { sendLinkCode() }
        findViewById<Button>(R.id.btnVerifyDevice).setOnClickListener { verifyDeviceCode() }
        findViewById<Button>(R.id.btnPasteChildCode).setOnClickListener { pasteChildCodeFromClipboard() }
        findViewById<Button>(R.id.btnCheckChildCode).setOnClickListener { checkChildCodeOnServer() }
        findViewById<Button>(R.id.btnCheckServer).setOnClickListener { checkServerConnection() }
        findViewById<Button>(R.id.btnOpenServerBrowser).setOnClickListener { openServerInBrowser() }
        // في خطوة الربط: نتحقق من (CHILD + OTP) ثم ننتقل لإدخال بيانات الطفل
        findViewById<Button>(R.id.btnLinkChild).setOnClickListener { goToAddChildAfterLinkInputs() }
        findViewById<Button>(R.id.btnLinkChildOnLinkStep).setOnClickListener { goToAddChildAfterLinkInputs() }
        findViewById<Button>(R.id.btnSendLinkCodeConfirm).setOnClickListener { sendLinkCode() }
        findViewById<Button>(R.id.btnAddAnotherChild).setOnClickListener { addAnotherChild() }
        findViewById<Button>(R.id.btnCancelAddAnother).setOnClickListener { cancelAddAnotherChild() }
        findViewById<Button>(R.id.btnStartSetup)?.setOnClickListener { startSetupFromWelcome() }
        findViewById<View>(R.id.btnBackFromWelcome)?.setOnClickListener { showControl() }
        findViewById<Button>(R.id.btnShowWelcome)?.setOnClickListener { showWelcome(fromControl = true) }
        findViewById<Button>(R.id.btnCheckServerWelcome)?.setOnClickListener { checkServerConnection() }
        findViewById<Button>(R.id.btnViewInstructions)?.setOnClickListener { showWelcomeInstructions() }
        findViewById<View>(R.id.btnWelcomeMenu)?.setOnClickListener { showWelcomeInstructions() }
        findViewById<TextView>(R.id.textWelcomeServerUrl)?.text =
            com.example.myrana.util.ServerConfig.healthUrl()
        findViewById<TextView>(R.id.textWelcomeVersion)?.text =
            "إصدار ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        findViewById<Button>(R.id.btnSelectAllFromList)?.setOnClickListener { toggleSelectAllFromList() }

        findViewById<Button>(R.id.btnBlockSite).setOnClickListener {
            sendCommand("block_site", findViewById<EditText>(R.id.inputTarget).text.toString())
        }
        findViewById<Button>(R.id.btnBlockApp).setOnClickListener {
            sendCommand("block_app", findViewById<EditText>(R.id.inputTarget).text.toString())
        }
        findViewById<Button>(R.id.btnFreezeApp).setOnClickListener {
            sendCommand("freeze_app", findViewById<EditText>(R.id.inputTarget).text.toString())
        }
        findViewById<Button>(R.id.btnAllow).setOnClickListener {
            sendCommand("allow", "")
        }
        findViewById<Button>(R.id.btnScheduleFreeze).setOnClickListener { scheduleFreeze() }
        findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            startActivity(Intent(this, ParentSettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnOpenScreenTime).setOnClickListener {
            startActivity(Intent(this, ParentScreenTimeActivity::class.java))
        }
        findViewById<Button>(R.id.btnRequestUsageReport).setOnClickListener { requestUsageReport() }
        findViewById<Button>(R.id.btnApplyDefaultBlocklist).setOnClickListener { applyDefaultBlocklist() }
        findViewById<Button>(R.id.btnViewAlerts).setOnClickListener { viewAlerts() }
        findViewById<Button>(R.id.btnSendMessage).setOnClickListener { sendGuardianMessage() }

        ParentSession.sanitizeInvalidPendingCodes(this)
        findViewById<TextView>(R.id.textAppVersion).text =
            "إصدار ${BuildConfig.VERSION_NAME} — سيرفر: ${com.example.myrana.util.ServerConfig.healthUrl()}"

        setupBottomNavigation()
        setupQuickActions()
        restoreWizardState()
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            if (ParentSession.isAddingAnotherChild(this@ParentMainActivity)) {
                showLink()
                return@launch
            }
            if (ParentSession.isChildLinked(this@ParentMainActivity)) {
                when (withContext(Dispatchers.IO) { ParentLinkSync.refreshFromServer(this@ParentMainActivity) }) {
                    ParentLinkSync.Result.STALE_CLEARED ->
                        toast("انقطع الربط على السيرفر — أعيدي إضافة الطفل والربط", true)
                    else -> Unit
                }
            } else {
                val email = ParentSession.guardianEmail(this@ParentMainActivity).orEmpty()
                if (email.isNotBlank()) {
                    when (withContext(Dispatchers.IO) { ParentLinkSync.refreshFromServer(this@ParentMainActivity) }) {
                        ParentLinkSync.Result.OK -> {
                            if (ParentSession.isChildLinked(this@ParentMainActivity)) {
                                ParentSession.markWelcomeSeen(this@ParentMainActivity)
                                showControl()
                            } else {
                                alertPollingJob?.cancel()
                                restoreWizardState()
                            }
                        }
                        else -> {
                            alertPollingJob?.cancel()
                            restoreWizardState()
                        }
                    }
                    if (!ParentSession.isChildLinked(this@ParentMainActivity)) return@launch
                } else {
                    alertPollingJob?.cancel()
                    restoreWizardState()
                    return@launch
                }
            }
            if (ParentSession.isChildLinked(this@ParentMainActivity)) {
                refreshAlertsQuietly()
                refreshLinkedChildrenSummary()
                refreshDashboardMini()
                startAlertPolling()
            }
        }
    }

    override fun onPause() {
        alertPollingJob?.cancel()
        super.onPause()
    }

    private fun setupRoleSpinner() {
        val roles = listOf(
            getString(R.string.parent_role_mother),
            getString(R.string.parent_role_father),
            getString(R.string.parent_role_guardian),
        )
        val spinner = findViewById<Spinner>(R.id.spinnerGuardianRole)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, roles)
        val saved = ParentSession.guardianRole(this)
        val idx = roles.indexOf(saved).coerceAtLeast(0)
        spinner.setSelection(idx)
    }

    private fun selectedGuardianRole(): String {
        return findViewById<Spinner>(R.id.spinnerGuardianRole).selectedItem?.toString()
            ?: getString(R.string.parent_role_guardian)
    }

    /** يوجّه للخطوة الصحيحة حسب الجلسة ويُمرّر الشاشة للأعلى. */
    private fun restoreWizardState() {
        val email = ParentSession.guardianEmail(this)
        if (!email.isNullOrBlank()) {
            findViewById<EditText>(R.id.inputGuardianEmail).setText(email)
        }
        when {
            ParentSession.isChildLinked(this) && ParentSession.isAddingAnotherChild(this) -> showLink()
            ParentSession.isChildLinked(this) -> showControl()
            // بعد CHILD+OTP نكون في خطوة ٣ (بيانات الطفل) حتى لو لم يُحفظ الاسم بعد
            ParentSession.hasPendingChildProfile(this) || ParentSession.isDeviceLinkVerified(this) -> showAddChild()
            ParentSession.isEmailVerified(this) -> showLink()
            !email.isNullOrBlank() -> showVerify()
            !ParentSession.hasSeenWelcome(this) -> showWelcome()
            else -> showLogin()
        }
        scrollWizardToTop()
    }

    private fun scrollWizardToTop() {
        parentScroll.post { parentScroll.scrollTo(0, 0) }
    }

    private fun restoreUiState() = restoreWizardState()

    /** خطوة 2: بعد إدخال كود CHILD + رمز الربط نتحقق من السيرفر ثم ننتقل لإدخال اسم الطفل. */
    private fun goToAddChildAfterLinkInputs() {
        val childCode = ParentSession.pendingLinkChildCode(this)?.takeIf { it.isNotBlank() }
            ?: ensureChildCodeFromInput()
            ?: return
        val verify = linkVerifyCode()
        if (verify.isEmpty()) {
            toast("أدخلي رمز الربط (6 أرقام) في الحقل — من Gmail", true)
            return
        }
        syncLinkVerifyFields(verify)
        setLinkButtonsEnabled(false)
        lifecycleScope.launch {
            if (!ensureServerReady()) {
                setLinkButtonsEnabled(true)
                return@launch
            }
            when (
                val result = withContext(Dispatchers.IO) {
                    GuardianApi.verifyChildDeviceCode(childCode, verify)
                }
            ) {
                is GuardianApi.ApiResult.DeviceVerified -> {
                    ParentSession.saveVerifiedDevice(
                        this@ParentMainActivity,
                        result.childCode,
                        result.childEmail,
                        result.deviceName,
                        result.androidVersion,
                        verify,
                    )
                    childCodeField().setText(result.childCode)
                    showAddChild()
                    scrollWizardToTop()
                    toast("الخطوة ٣ — اكتبي اسم الطفل ثم اضغطي «إتمام الربط»", false)
                }
                is GuardianApi.ApiResult.Error -> toast(result.message, true)
                else -> {}
            }
            setLinkButtonsEnabled(true)
        }
    }

    private fun verifyDeviceCode() {
        val childCode = normalizedChildCodeFromInput()
        val verify = findViewById<EditText>(R.id.inputDeviceVerify).text.toString().trim()
        if (childCode.isEmpty() || verify.isEmpty()) {
            toast(getString(R.string.parent_link_incomplete), true)
            return
        }
        lifecycleScope.launch {
            if (!ensureServerReady()) return@launch
            when (
                val result = withContext(Dispatchers.IO) {
                    GuardianApi.verifyChildDeviceCode(childCode, verify)
                }
            ) {
                is GuardianApi.ApiResult.DeviceVerified -> {
                    ParentSession.saveVerifiedDevice(
                        this@ParentMainActivity,
                        result.childCode,
                        result.childEmail,
                        result.deviceName,
                        result.androidVersion,
                        verify,
                    )
                    childCodeField().setText(result.childCode)
                    syncLinkVerifyFields(verify)
                    showAddChild()
                    scrollWizardToTop()
                    toast("تم التحقق — اكتبي اسم الطفل ثم «إتمام الربط»", false)
                }
                is GuardianApi.ApiResult.Error -> toast(result.message, true)
                else -> {}
            }
        }
    }

    private fun addAnotherChild() {
        ParentSession.beginAnotherChildLink(this)
        findViewById<EditText>(R.id.inputAddChildName).text?.clear()
        findViewById<EditText>(R.id.inputAddChildAge).text?.clear()
        childCodeField().text?.clear()
        findViewById<EditText>(R.id.inputDeviceVerify).text?.clear()
        findViewById<EditText>(R.id.inputLinkConfirmVerify).text?.clear()
        textMessage.text = ""
        showLink()
        scrollWizardToTop()
        toast(getString(R.string.parent_adding_another_hint), false)
    }

    private fun cancelAddAnotherChild() {
        ParentSession.cancelAnotherChildLink(this)
        showControl()
        scrollWizardToTop()
        toast(getString(R.string.parent_cancel_add_another_done), false)
    }

    /**
     * 1) إرسال أمر للطفل لرفع الاستخدام فوراً.
     * 2) انتظار قصير ثم جلب التقرير وعرضه في قائمة.
     */
    /** أهداف الأمر: المحدّدون من ☑ القائمة، أو الكل، أو الطفل النشط. */
    private fun commandTargets(): List<Pair<String, String>> {
        if (selectedChildCodes.isNotEmpty()) {
            return linkedChildrenRows
                .filter { selectedChildCodes.contains(ChildCodeNormalizer.forApi(it.code)) }
                .map { it.code to it.name }
        }
        if (checkApplyAllChildren.isChecked && linkedChildrenRows.size > 1) {
            return linkedChildrenRows.map { it.code to it.name }
        }
        val code = ParentSession.childCode(this)?.trim().orEmpty()
        if (code.isBlank()) return emptyList()
        val name = ParentSession.childName(this).orEmpty().ifBlank { "طفل" }
        return listOf(code to name)
    }

    private fun toggleSelectAllFromList() {
        if (linkedChildrenRows.isEmpty()) return
        if (selectedChildCodes.size == linkedChildrenRows.size) {
            selectedChildCodes.clear()
        } else {
            selectedChildCodes.clear()
            linkedChildrenRows.forEach { selectedChildCodes.add(ChildCodeNormalizer.forApi(it.code)) }
        }
        checkApplyAllChildren.isChecked = false
        renderChildrenChips()
        toast(
            if (selectedChildCodes.isEmpty()) "تم إلغاء التحديد" else "تم تحديد ${selectedChildCodes.size} من القائمة",
            false,
        )
    }

    private fun openScreenTimeForChild(code: String, focusSleep: Boolean = false) {
        selectActiveChild(code, linkedChildrenRows.firstOrNull {
            ChildCodeNormalizer.forApi(it.code) == ChildCodeNormalizer.forApi(code)
        }?.name ?: "طفل")
        startActivity(
            Intent(this, ParentScreenTimeActivity::class.java).apply {
                putExtra(ParentScreenTimeActivity.EXTRA_CHILD_CODE, code)
                if (focusSleep) putExtra(ParentScreenTimeActivity.EXTRA_FOCUS_SLEEP, true)
            },
        )
    }

    private fun requestReportForChild(code: String, name: String) {
        val email = ParentSession.guardianEmail(this).orEmpty()
        if (email.isBlank()) {
            toast("اربط الطفل أولاً", true)
            return
        }
        toast(getString(R.string.parent_usage_loading), false)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { GuardianApi.requestUsageFromChild(code, email) }
            delay(4_000L)
            when (val report = withContext(Dispatchers.IO) { GuardianApi.fetchDailyReport(code) }) {
                is GuardianApi.ApiResult.ReportText -> {
                    AlertDialog.Builder(this@ParentMainActivity)
                        .setTitle(getString(R.string.parent_child_report_title, name))
                        .setMessage(report.text.ifBlank { getString(R.string.parent_usage_empty) })
                        .setPositiveButton("حسناً", null)
                        .show()
                }
                is GuardianApi.ApiResult.Error -> toast(report.message, true)
                else -> toast("فشل التقرير", true)
            }
        }
    }

    private fun showSleepInfoForChild(info: LinkedChildInfo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.parent_child_sleep_title, info.name))
            .setMessage(
                getString(
                    R.string.parent_child_sleep_message,
                    info.sleepStart,
                    info.sleepEnd,
                ),
            )
            .setPositiveButton(getString(R.string.parent_btn_edit_sleep)) { _, _ ->
                openScreenTimeForChild(info.code, focusSleep = true)
            }
            .setNegativeButton("إغلاق", null)
            .show()
    }

    private fun toastBroadcastResult(success: Int, failed: Int, total: Int) {
        when {
            total <= 1 && failed == 0 -> Unit
            failed == 0 -> toast(getString(R.string.parent_broadcast_done, success, total), false)
            else -> toast(getString(R.string.parent_broadcast_partial, success, failed, total), true)
        }
    }

    private suspend fun broadcastCommand(
        action: String,
        value: String,
        email: String,
        targets: List<Pair<String, String>>,
    ): Pair<Int, Int> {
        var ok = 0
        var fail = 0
        for ((code, _) in targets) {
            when (
                withContext(Dispatchers.IO) {
                    GuardianApi.sendCommand(action, value, code, email)
                }
            ) {
                is GuardianApi.ApiResult.Ok -> ok++
                is GuardianApi.ApiResult.Error -> fail++
                else -> fail++
            }
        }
        return ok to fail
    }

    private fun applyDefaultBlocklist() {
        val targets = commandTargets()
        if (targets.isEmpty()) {
            toast("اربط الطفل أولاً", true)
            return
        }
        findViewById<Button>(R.id.btnApplyDefaultBlocklist).isEnabled = false
        lifecycleScope.launch {
            var ok = 0
            var fail = 0
            for ((code, _) in targets) {
                when (withContext(Dispatchers.IO) { GuardianApi.applyDefaultBlocklist(code) }) {
                    is GuardianApi.ApiResult.Ok -> ok++
                    is GuardianApi.ApiResult.Error -> fail++
                    else -> fail++
                }
            }
            if (targets.size == 1 && fail == 0) {
                toast("تم تطبيق قائمة الحظر", false)
            } else {
                toastBroadcastResult(ok, fail, targets.size)
            }
            findViewById<Button>(R.id.btnApplyDefaultBlocklist).isEnabled = true
        }
    }

    private fun viewAlerts() {
        val childCode = ParentSession.childCode(this)
        if (childCode.isNullOrBlank()) {
            toast("اربط الطفل أولاً", true)
            return
        }
        lifecycleScope.launch {
            val updated = refreshAlertsQuietly()
            if (updated) {
                toast("تم تحديث التنبيهات", false)
            } else {
                toast(getString(R.string.parent_alerts_waiting), false)
            }
        }
    }

    private fun startAlertPolling() {
        alertPollingJob?.cancel()
        if (!ParentSession.isChildLinked(this)) return
        alertPollingJob = lifecycleScope.launch {
            while (isActive) {
                delay(15_000L)
                refreshAlertsQuietly()
            }
        }
    }

    /** @return true إذا وُجدت تنبيهات */
    private suspend fun refreshAlertsQuietly(): Boolean {
        val childCode = ParentSession.childCode(this) ?: return false
        return when (val result = withContext(Dispatchers.IO) { GuardianApi.fetchAlerts(childCode) }) {
            is GuardianApi.ApiResult.Alerts -> {
                if (result.error != null) {
                    ParentDashboardBinder.updateAlertsPreview(this@ParentMainActivity, result.error)
                    false
                } else if (result.lines.isEmpty()) {
                    ParentDashboardBinder.updateAlertsPreview(
                        this@ParentMainActivity,
                        getString(R.string.parent_alerts_waiting),
                    )
                    false
                } else {
                    val preview = result.lines.take(8).joinToString("\n\n")
                    val full =
                        "${getString(R.string.parent_alerts_preview_title)}\n\n$preview"
                    ParentDashboardBinder.updateAlertsPreview(this@ParentMainActivity, full)
                    true
                }
            }
            else -> false
        }
    }

    private fun onLinkSuccess(childCode: String, name: String) {
        val wasAddingAnother = ParentSession.isAddingAnotherChild(this)
        ParentSession.markWelcomeSeen(this)
        ParentSession.saveLinkedChild(this, childCode, name)
        showControl()
        startAlertPolling()
        val msg = if (wasAddingAnother) {
            getString(R.string.parent_another_child_linked, name)
        } else {
            getString(R.string.parent_link_success_monitoring)
        }
        toast(msg, false)
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { GuardianApi.applyDefaultBlocklist(childCode) }
            refreshAlertsQuietly()
            refreshLinkedChildrenSummary()
            refreshDashboardMini()
        }
    }

    /** ملخص سريع للوحة المؤشرات في شاشة التحكم الرئيسية. */
    private suspend fun refreshDashboardMini() {
        val code = ParentSession.childCode(this@ParentMainActivity) ?: return
        when (val result = withContext(Dispatchers.IO) { GuardianApi.fetchChildDashboard(code) }) {
            is GuardianApi.ApiResult.ChildDashboard -> {
                val d = result.data
                val status = if (d.online) {
                    getString(R.string.parent_status_online)
                } else {
                    getString(R.string.parent_status_offline)
                }
                val mini = getString(
                    R.string.parent_dashboard_mini,
                    d.todaySeconds / 60,
                    d.appsOpened,
                    d.alertsToday,
                )
                val sleep = getString(R.string.parent_dashboard_sleep, d.policy.sleepStart, d.policy.sleepEnd)
                textDashboardMini.text = "$status — $mini\n$sleep\n${d.childCode}"
                textPermissionsMini.text = ParentPermissionsFormatter.summary(
                    this@ParentMainActivity,
                    d.permissionsOk,
                    d.permissions,
                )
                ParentDashboardBinder.bindDashboard(this@ParentMainActivity, d)
            }
            else -> Unit
        }
        when (val chart = withContext(Dispatchers.IO) { GuardianApi.fetchWeeklyChart(code) }) {
            is GuardianApi.ApiResult.WeeklyChart ->
                ParentDashboardBinder.bindWeeklyChart(this@ParentMainActivity, chart.data)
            else -> Unit
        }
    }

    private fun selectActiveChild(childCode: String, childName: String) {
        ParentSession.saveLinkedChild(this, childCode, childName)
        lifecycleScope.launch {
            refreshDashboardMini()
            refreshAlertsQuietly()
            updateActiveChildHeader(linkedChildrenRows.size)
            renderChildrenChips()
        }
    }

    /** جلب قائمة الأطفال من السيرفر — دعم تعدد الأطفال لولي الأمر الواحد. */
    private suspend fun refreshLinkedChildrenSummary() {
        val email = ParentSession.guardianEmail(this@ParentMainActivity).orEmpty()
        if (email.isBlank()) {
            applyLinkedChildrenFromCache()
            return
        }
        when (val result = withContext(Dispatchers.IO) { GuardianApi.fetchLinkedChildren(email) }) {
            is GuardianApi.ApiResult.ChildrenList -> {
                if (result.children.isEmpty()) {
                    applyLinkedChildrenFromCache()
                    return
                }
                val rows = result.children.mapNotNull { child ->
                    val code = child["child_code"]?.toString().orEmpty()
                    if (code.isBlank()) return@mapNotNull null
                    val name = child["name"]?.toString()?.ifBlank { "طفل" } ?: "طفل"
                    val online = if (child["online"] == true) "متصل" else "غير متصل"
                    Triple(code, name, online)
                }
                if (rows.isEmpty()) {
                    applyLinkedChildrenFromCache()
                    return
                }
                ParentSession.saveLinkedChildrenCache(
                    this@ParentMainActivity,
                    rows.map { it.first to it.second },
                )
                applyLinkedChildrenRows(rows)
                enrichAndRenderChildren(rows)
            }
            else -> applyLinkedChildrenFromCache()
        }
    }

    private fun enrichAndRenderChildren(rows: List<Triple<String, String, String>>) {
        lifecycleScope.launch {
            linkedChildrenRows = enrichChildrenRows(rows)
            renderChildrenChips()
        }
    }

    private suspend fun enrichChildrenRows(
        rows: List<Triple<String, String, String>>,
    ): List<LinkedChildInfo> = coroutineScope {
        rows.map { (code, name, statusLabel) ->
            async {
                val onlineFromList = statusLabel == "متصل"
                when (val dash = withContext(Dispatchers.IO) { GuardianApi.fetchChildDashboard(code) }) {
                    is GuardianApi.ApiResult.ChildDashboard -> {
                        val d = dash.data
                        LinkedChildInfo(
                            code = code,
                            name = name,
                            online = d.online,
                            todayMinutes = (d.todaySeconds / 60).toInt(),
                            sleepStart = d.policy.sleepStart,
                            sleepEnd = d.policy.sleepEnd,
                        )
                    }
                    else -> LinkedChildInfo(code, name, onlineFromList)
                }
            }
        }.map { it.await() }
    }

    private fun applyLinkedChildrenFromCache() {
        val cached = ParentSession.linkedChildrenCached(this)
        if (cached.isEmpty()) return
        val rows = cached.map { (code, name) -> Triple(code, name, "—") }
        applyLinkedChildrenRows(rows)
        enrichAndRenderChildren(rows)
    }

    private fun applyLinkedChildrenRows(rows: List<Triple<String, String, String>>) {
        if (rows.isEmpty()) return
        linkedChildrenRows = rows.map { (code, name, status) ->
            LinkedChildInfo(code, name, status == "متصل")
        }
        val labels = rows.map { (code, name, status) ->
            getString(R.string.parent_child_spinner_item, name, code, status)
        }
        childSpinnerIgnoreSelection = true
        spinnerChildren.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            labels,
        )
        val active = ParentSession.childCode(this)
        val activeNorm = ChildCodeNormalizer.forApi(active.orEmpty())
        var selectIndex = rows.indexOfFirst { ChildCodeNormalizer.forApi(it.first) == activeNorm }
        if (selectIndex < 0) {
            selectIndex = 0
            ParentSession.saveLinkedChild(this, rows[0].first, rows[0].second)
        }
        spinnerChildren.setSelection(selectIndex)
        childSpinnerIgnoreSelection = false
        updateChildrenPickerUi(rows.size)
        renderChildrenChips()
    }

    /** بطاقة لكل طفل: متصل، تقرير، رسم، وقت نوم، تحديد للإرسال الجماعي. */
    private fun renderChildrenChips() {
        layoutLinkedChildrenList.removeAllViews()
        if (linkedChildrenRows.isEmpty()) return
        val activeNorm = ChildCodeNormalizer.forApi(ParentSession.childCode(this).orEmpty())
        val dp8 = (8 * resources.displayMetrics.density).toInt()
        val dp12 = (12 * resources.displayMetrics.density).toInt()

        for (info in linkedChildrenRows) {
            val codeNorm = ChildCodeNormalizer.forApi(info.code)
            val isActive = codeNorm == activeNorm
            val isSelected = selectedChildCodes.contains(codeNorm)

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp12, dp12, dp12, dp12)
                setBackgroundColor(
                    when {
                        isActive -> getColor(R.color.parent_chip_active)
                        isSelected -> getColor(R.color.parent_chip_selected)
                        else -> getColor(R.color.parent_chip_default)
                    },
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = dp8 }
            }

            val statusLine = if (info.online) {
                if (info.todayMinutes >= 0) {
                    getString(
                        R.string.parent_child_row_online,
                        info.name,
                        info.code,
                        info.todayMinutes,
                        info.sleepStart,
                        info.sleepEnd,
                    )
                } else {
                    getString(R.string.parent_child_row_online_short, info.name, info.code)
                }
            } else {
                getString(R.string.parent_child_row_offline, info.name, info.code)
            }
            card.addView(
                TextView(this).apply {
                    text = statusLine
                    textSize = 14f
                    setTextColor(
                        getColor(
                            if (info.online) R.color.parent_online else R.color.parent_offline,
                        ),
                    )
                },
            )

            val actions = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp8 }
            }

            val check = CheckBox(this).apply {
                text = getString(R.string.parent_select_for_bulk)
                isChecked = isSelected
                setOnCheckedChangeListener { _, checked ->
                    if (checked) {
                        selectedChildCodes.add(codeNorm)
                        checkApplyAllChildren.isChecked = false
                    } else {
                        selectedChildCodes.remove(codeNorm)
                    }
                }
            }
            actions.addView(check)

            fun smallBtn(label: String, onClick: () -> Unit): Button =
                Button(this, null, android.R.attr.borderlessButtonStyle).apply {
                    text = label
                    textSize = 12f
                    setOnClickListener { onClick() }
                }

            actions.addView(smallBtn(getString(R.string.parent_btn_child_report)) {
                requestReportForChild(info.code, info.name)
            })
            actions.addView(smallBtn(getString(R.string.parent_btn_child_chart)) {
                openScreenTimeForChild(info.code, focusSleep = false)
            })
            actions.addView(smallBtn(getString(R.string.parent_btn_child_sleep)) {
                showSleepInfoForChild(info)
            })
            if (!isActive) {
                actions.addView(smallBtn(getString(R.string.parent_btn_child_activate)) {
                    selectActiveChild(info.code, info.name)
                })
            }

            card.addView(actions)
            layoutLinkedChildrenList.addView(card)
        }
    }

    /** قائمة الأطفال تظهر دائماً بعد الربط — حتى لطفل واحد. */
    private fun updateChildrenPickerUi(childCount: Int) {
        val show = childCount >= 1 && ParentSession.isChildLinked(this)
        val multi = childCount > 1
        textSelectActiveChild.visibility = if (show) View.VISIBLE else View.GONE
        spinnerChildren.visibility = if (multi) View.VISIBLE else View.GONE
        layoutLinkedChildrenList.visibility = if (show) View.VISIBLE else View.GONE
        textMultiChildHint.visibility = if (show) View.VISIBLE else View.GONE
        checkApplyAllChildren.visibility = if (multi) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnSelectAllFromList).visibility = if (multi) View.VISIBLE else View.GONE
        if (multi) {
            checkApplyAllChildren.text = getString(R.string.parent_apply_all_children_count, childCount)
        }
        updateActiveChildHeader(childCount)
    }

    private fun updateActiveChildHeader(childCount: Int) {
        val name = ParentSession.childName(this).orEmpty().ifBlank { "طفل" }
        val code = ParentSession.childCode(this).orEmpty()
        textLinkedChild.text = when {
            childCount > 1 -> getString(R.string.parent_linked_children_summary, childCount)
            childCount == 1 -> getString(R.string.parent_single_child_active, name, code)
            else -> getString(
                R.string.parent_linked_with_role,
                name,
                ParentSession.guardianRole(this),
                code,
            )
        }
    }

    private fun requestUsageReport() {
        val childCode = ParentSession.childCode(this)
        val email = ParentSession.guardianEmail(this)
        if (childCode.isNullOrBlank() || email.isNullOrBlank()) {
            toast("اربط الطفل أولاً", true)
            return
        }

        findViewById<Button>(R.id.btnRequestUsageReport).isEnabled = false
        toast(getString(R.string.parent_usage_loading), false)

        lifecycleScope.launch {
            val requestResult = withContext(Dispatchers.IO) {
                GuardianApi.requestUsageFromChild(childCode, email)
            }
            if (requestResult is GuardianApi.ApiResult.Error) {
                toast(requestResult.message, true)
                findViewById<Button>(R.id.btnRequestUsageReport).isEnabled = true
                return@launch
            }

            delay(5_000L)

            when (val report = withContext(Dispatchers.IO) { GuardianApi.fetchWeeklyUsage(childCode) }) {
                is GuardianApi.ApiResult.UsageList -> showUsageList(report.items)
                is GuardianApi.ApiResult.Error -> toast(report.message, true)
                else -> toast("فشل تحميل التقرير", true)
            }
            findViewById<Button>(R.id.btnRequestUsageReport).isEnabled = true
        }
    }

    private fun showUsageList(items: List<com.example.myrana.data.remote.dto.UsageAppItem>) {
        if (items.isEmpty()) {
            textUsageTitle.visibility = View.GONE
            recyclerUsage.visibility = View.GONE
            textUsageEmpty.visibility = View.VISIBLE
            toast("لا بيانات استخدام بعد", true)
            return
        }
        textUsageEmpty.visibility = View.GONE
        textUsageTitle.visibility = View.VISIBLE
        recyclerUsage.visibility = View.VISIBLE
        usageAdapter.submit(items)
        toast("تم عرض ${items.size} تطبيق", false)
    }

    private fun sendEmailCode() {
        val email = findViewById<EditText>(R.id.inputGuardianEmail).text.toString().trim()
        if (!PatternsCompat.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("أدخل بريداً صحيحاً", true)
            return
        }
        ParentSession.saveGuardian(this, email, selectedGuardianRole())
        lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { GuardianApi.sendEmailCode(email) }) {
                is GuardianApi.ApiResult.EmailCodeSent -> {
                    showVerify()
                    scrollWizardToTop()
                    textMessage.text = result.message
                    findViewById<EditText>(R.id.inputEmailCode).text?.clear()
                    toast("تحققي من بريدك ($email) وأدخلي الرمز يدوياً", false)
                }
                is GuardianApi.ApiResult.Error -> toast(result.message, true)
                else -> {}
            }
        }
    }

    private fun childCodeField(): EditText = findViewById(R.id.inputChildCode)

    /** يضيف CHILD- تلقائياً إذا لصقتِ الجزء فقط (مثل 2776E398). */
    private fun normalizedChildCodeFromInput(): String {
        val fieldText = childCodeField().text.toString()
        val extracted = ChildCodeNormalizer.extractFromPaste(fieldText)
        val normalized = extracted.ifBlank { ChildCodeNormalizer.normalize(fieldText) }
        if (normalized.isNotEmpty() && ChildCodeNormalizer.isValid(normalized)) {
            childCodeField().setText(normalized)
        }
        return if (ChildCodeNormalizer.isValid(normalized)) normalized else ""
    }

    /** رمزا Gmail: 1 تحقق بريد + 2 رمز ربط — ثم ربط الطفل. */
    private fun openServerInBrowser() {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(ServerConnectionHelper.healthUrl()))
        )
        toast("انتظري 60–90 ثانية حتى يظهر running ثم «فحص الاتصال»", false)
    }

    private fun checkServerConnection() {
        listOfNotNull(
            findViewById<Button>(R.id.btnCheckServer),
            findViewById<Button>(R.id.btnCheckServerWelcome),
        ).forEach { it.isEnabled = false }
        updateWelcomeServerStatus(checking = true)
        toast(getString(R.string.parent_checking_server), false)
        lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { GuardianApi.checkServerConnection(this@ParentMainActivity) }) {
                is GuardianApi.ApiResult.Ok -> {
                    toast(result.message, false)
                    updateWelcomeServerStatus(connected = true)
                }
                is GuardianApi.ApiResult.Error -> {
                    toast(result.message, true)
                    updateWelcomeServerStatus(connected = false)
                }
                else -> updateWelcomeServerStatus(connected = false)
            }
            listOfNotNull(
                findViewById<Button>(R.id.btnCheckServer),
                findViewById<Button>(R.id.btnCheckServerWelcome),
            ).forEach { it.isEnabled = true }
        }
    }

    private fun updateWelcomeServerStatus(
        checking: Boolean = false,
        connected: Boolean? = null,
    ) {
        val dot = findViewById<View>(R.id.viewWelcomeServerDot) ?: return
        val status = findViewById<TextView>(R.id.textWelcomeServerStatus) ?: return
        when {
            checking -> {
                dot.setBackgroundResource(R.drawable.bg_server_dot_offline)
                status.text = getString(R.string.parent_server_status_checking)
            }
            connected == true -> {
                dot.setBackgroundResource(R.drawable.bg_server_dot_online)
                status.text = getString(R.string.parent_server_status_connected)
            }
            connected == false -> {
                dot.setBackgroundResource(R.drawable.bg_server_dot_offline)
                status.text = getString(R.string.parent_server_status_disconnected)
            }
        }
    }

    private fun showWelcomeInstructions() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.parent_welcome_instructions_title))
            .setMessage(
                getString(
                    R.string.parent_welcome_instructions_body,
                    getString(R.string.parent_child_not_on_server_steps),
                ),
            )
            .setPositiveButton("حسناً", null)
            .show()
    }

    /** @return false إذا السيرفر غير متاح */
    private suspend fun ensureServerReady(): Boolean {
        when (val result = withContext(Dispatchers.IO) { GuardianApi.checkServerConnection(this@ParentMainActivity) }) {
            is GuardianApi.ApiResult.Ok -> {
                toast(result.message, false)
                return true
            }
            is GuardianApi.ApiResult.Error -> {
                toast(result.message, true)
                return false
            }
            else -> return false
        }
    }

    private fun checkChildCodeOnServer(showSuccess: Boolean = true): Boolean {
        val childCode = normalizedChildCodeFromInput()
        if (childCode.isEmpty()) {
            ensureChildCodeFromInput()
            return false
        }
        lifecycleScope.launch {
            val status = withContext(Dispatchers.IO) {
                NetworkModule.queryChildLinkStatus(childCode)
            }
            when (status.state) {
                NetworkModule.ChildRegistrationState.WAITING -> {
                    childCodeField().setText(childCode)
                    if (showSuccess) {
                        toast(status.detail.ifBlank { getString(R.string.parent_child_code_ok, childCode) }, false)
                    }
                }
                NetworkModule.ChildRegistrationState.LINKED ->
                    toast("الجهاز مربوط مسبقاً — $childCode", false)
                NetworkModule.ChildRegistrationState.NOT_ON_SERVER -> {
                    val bad = childCodeField().text.toString()
                    if (ChildCodeNormalizer.looksLikeEmailMistake(bad)) {
                        showInvalidChildCodeDialog(bad)
                    } else {
                        childCodeField().text?.clear()
                        AlertDialog.Builder(this@ParentMainActivity)
                            .setTitle("الطفل غير مسجل على السيرفر")
                            .setMessage(
                                getString(R.string.parent_child_not_on_server_steps) +
                                    "\n\n" + (status.detail.ifBlank { getString(R.string.parent_child_code_missing) }),
                            )
                            .setPositiveButton("حسناً", null)
                            .show()
                    }
                }
                else -> toast(
                    status.detail.ifBlank { "تعذّر فحص السيرفر — اضغطي «فحص الاتصال»" },
                    true,
                )
            }
        }
        return true
    }

    private fun autoLinkChild() {
        ensureChildCodeFromInput() ?: return
        val childCode = normalizedChildCodeFromInput()
        val verifyFromField = findViewById<EditText>(R.id.inputDeviceVerify).text.toString().trim()
        val email = ParentSession.guardianEmail(this).orEmpty()
        if (!PatternsCompat.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("أدخل بريد ولي الأمر أولاً", true)
            return
        }
        if (!ParentSession.isEmailVerified(this)) {
            toast("تحققي من بريدك أولاً (الرمز الأول من Gmail)", true)
            return
        }

        // إذا OTP موجود: انتقلي لخطوة بيانات الطفل (لا تربطي مباشرة)
        if (verifyFromField.isNotEmpty()) {
            goToAddChildAfterLinkInputs()
            return
        }

        setLinkButtonsEnabled(false)
        toast("جاري إرسال رمز الربط إلى Gmail…", false)
        lifecycleScope.launch {
            if (!ensureServerReady()) {
                setLinkButtonsEnabled(true)
                return@launch
            }
            val status = withContext(Dispatchers.IO) {
                NetworkModule.queryChildLinkStatus(childCode)
            }
            if (status.state == NetworkModule.ChildRegistrationState.NOT_ON_SERVER) {
                val bad = childCodeField().text.toString()
                if (ChildCodeNormalizer.looksLikeEmailMistake(bad)) {
                    showInvalidChildCodeDialog(bad)
                } else {
                    AlertDialog.Builder(this@ParentMainActivity)
                        .setTitle("الطفل غير مسجل على السيرفر")
                        .setMessage(getString(R.string.parent_child_not_on_server_steps))
                        .setPositiveButton("حسناً", null)
                        .show()
                }
                setLinkButtonsEnabled(true)
                return@launch
            }
            if (status.state == NetworkModule.ChildRegistrationState.ERROR) {
                toast(status.detail, true)
                setLinkButtonsEnabled(true)
                return@launch
            }
            when (val result = withContext(Dispatchers.IO) { GuardianApi.sendLinkCode(email, childCode) }) {
                is GuardianApi.ApiResult.EmailCodeSent -> {
                    textMessage.text = result.message
                    toast("افتحي Gmail — أدخلي OTP ثم اضغطي «متابعة لبيانات الطفل»", false)
                }
                is GuardianApi.ApiResult.Error -> toast(result.message, true)
                else -> {}
            }
            setLinkButtonsEnabled(true)
        }
    }

    private fun sendLinkCode() {
        val pending = ParentSession.pendingLinkChildCode(this)?.trim().orEmpty()
        if (pending.isNotEmpty() && !ChildCodeNormalizer.isValid(pending)) {
            ParentSession.clearPendingLink(this)
            showInvalidChildCodeDialog(pending)
            return
        }
        val childCode = pending.takeIf { it.isNotBlank() }
            ?: ensureChildCodeFromInput()
            ?: return
        childCodeField().setText(childCode)
        val email = ParentSession.guardianEmail(this).orEmpty()
        if (!PatternsCompat.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("أدخل بريد ولي الأمر أولاً", true)
            return
        }
        if (!ParentSession.isEmailVerified(this)) {
            toast("تحققي من بريدك أولاً (الرمز الأول من Gmail)", true)
            return
        }
        lifecycleScope.launch {
            if (!ensureServerReady()) return@launch
            when (val result = withContext(Dispatchers.IO) { GuardianApi.sendLinkCode(email, childCode) }) {
                is GuardianApi.ApiResult.EmailCodeSent -> {
                    textMessage.text = result.message
                    toast("تحققي من Gmail — الرمز الثاني — وأدخليه يدوياً", false)
                }
                is GuardianApi.ApiResult.Error -> toast(result.message, true)
                else -> {}
            }
        }
    }

    private fun showInvalidChildCodeDialog(raw: String) {
        val hint = ChildCodeNormalizer.pasteErrorHint(raw)
            ?: "الكود غير صالح"
        childCodeField().text?.clear()
        ParentSession.clearPendingLink(this)
        AlertDialog.Builder(this)
            .setTitle("كود الطفل غلط")
            .setMessage(
                "$hint\n\n" +
                    "① جوال الطفل: تسجيل الجهاز\n" +
                    "② انسخي CHILD-88278A25 (8 أحرف)\n" +
                    "③ ليس البريد الإلكتروني"
            )
            .setPositiveButton("حسناً", null)
            .show()
        toast(hint, true)
    }

    private fun ensureChildCodeFromInput(): String? {
        val raw = childCodeField().text.toString().trim()
        if (raw.isNotEmpty() && !ChildCodeNormalizer.isValid(raw)) {
            val extracted = ChildCodeNormalizer.extractFromPaste(raw)
            if (ChildCodeNormalizer.isValid(extracted)) {
                childCodeField().setText(extracted)
            } else {
                showInvalidChildCodeDialog(raw)
                return null
            }
        }
        var childCode = normalizedChildCodeFromInput()
        if (childCode.isEmpty()) {
            pasteChildCodeFromClipboard(silent = true)
            childCode = normalizedChildCodeFromInput()
        }
        if (childCode.isEmpty()) {
            val hint = ChildCodeNormalizer.pasteErrorHint(childCodeField().text.toString())
                ?: "أدخلي كود CHILD-XXXXXXXX من Gmail (رسالة «كود جهاز الطفل») — ليس البريد"
            toast(hint, true)
            return null
        }
        return childCode
    }

    private fun pendingChildName(): String =
        ParentSession.pendingChildName(this).orEmpty().ifBlank {
            findViewById<EditText>(R.id.inputAddChildName).text.toString().trim()
        }.ifBlank { "طفل" }

    private fun setLinkButtonsEnabled(enabled: Boolean) {
        findViewById<Button>(R.id.btnAutoLink).isEnabled = enabled
        findViewById<Button>(R.id.btnSendLinkCode).isEnabled = enabled
        findViewById<Button>(R.id.btnSendLinkCodeConfirm).isEnabled = enabled
        findViewById<Button>(R.id.btnVerifyDevice).isEnabled = enabled
        findViewById<Button>(R.id.btnLinkChild).isEnabled = enabled
        findViewById<Button>(R.id.btnLinkChildOnLinkStep).isEnabled = enabled
    }

    private fun verifyEmail() {
        val email = ParentSession.guardianEmail(this).orEmpty()
        val code = findViewById<EditText>(R.id.inputEmailCode).text.toString().trim()
        if (code.isEmpty()) {
            toast("أدخل رمز البريد", true)
            return
        }
        lifecycleScope.launch { verifyEmailSilently(email, code) }
    }

    private suspend fun verifyEmailSilently(email: String, code: String) {
        when (val result = withContext(Dispatchers.IO) { GuardianApi.verifyEmailCode(email, code) }) {
            is GuardianApi.ApiResult.Ok -> {
                ParentSession.markEmailVerified(this@ParentMainActivity)
                // بعد تحقق البريد ننتقل مباشرة لربط جهاز الطفل
                showLink()
                scrollWizardToTop()
                toast(result.message, false)
                pasteChildCodeFromClipboard(silent = true)
            }
            is GuardianApi.ApiResult.Error -> toast(result.message, true)
            else -> {}
        }
    }

    private fun linkVerifyCode(): String {
        val confirm = findViewById<EditText>(R.id.inputLinkConfirmVerify).text.toString().trim()
        if (confirm.isNotEmpty()) return confirm
        val linkStep = findViewById<EditText>(R.id.inputDeviceVerify).text.toString().trim()
        if (linkStep.isNotEmpty()) return linkStep
        return ParentSession.pendingDeviceVerifyCode(this).orEmpty()
    }

    private fun syncLinkVerifyFields(code: String) {
        findViewById<EditText>(R.id.inputDeviceVerify).setText(code)
        findViewById<EditText>(R.id.inputLinkConfirmVerify).setText(code)
    }

    private fun linkChild() {
        val childCode = ParentSession.pendingLinkChildCode(this)?.takeIf { it.isNotBlank() }
            ?: ensureChildCodeFromInput()
            ?: return
        val verify = linkVerifyCode()
        if (verify.isEmpty()) {
            toast("أدخلي رمز الربط (6 أرقام) — من Gmail", true)
            return
        }
        syncLinkVerifyFields(verify)
        if (!ParentSession.isEmailVerified(this)) {
            toast("تحققي من بريدك أولاً (الرمز الأول من Gmail)", true)
            return
        }
        val name = findViewById<EditText>(R.id.inputAddChildName).text.toString().trim()
        if (name.isEmpty()) {
            toast(getString(R.string.parent_add_child_name_required), true)
            return
        }
        val age = findViewById<EditText>(R.id.inputAddChildAge).text.toString().toIntOrNull() ?: 10
        ParentSession.savePendingChildProfile(this, name, age)
        setLinkButtonsEnabled(false)
        lifecycleScope.launch {
            if (!ensureServerReady()) {
                setLinkButtonsEnabled(true)
                return@launch
            }
            performLink(childCode, verify, name, age)
            setLinkButtonsEnabled(true)
        }
    }

    private suspend fun performLink(
        childCode: String,
        verifyCode: String,
        name: String,
        age: Int,
    ) {
        val guardianEmail = ParentSession.guardianEmail(this@ParentMainActivity).orEmpty()
        when (
            val result = withContext(Dispatchers.IO) {
                GuardianApi.addChild(
                    name = name,
                    age = age,
                    childEmail = ParentSession.pendingLinkChildEmail(this@ParentMainActivity)
                        ?: guardianEmail,
                    device = ParentSession.pendingLinkDeviceName(this@ParentMainActivity)
                        ?: "Android",
                    androidVersion = ParentSession.pendingLinkAndroidVersion(this@ParentMainActivity)
                        ?: "Android",
                    childCode = childCode,
                    deviceVerifyCode = verifyCode,
                    guardianEmail = guardianEmail,
                    guardianRole = ParentSession.guardianRole(this@ParentMainActivity),
                )
            }
        ) {
            is GuardianApi.ApiResult.LinkSuccess -> {
                val linked = result.childCode?.takeIf { it.isNotBlank() } ?: childCode
                onLinkSuccess(linked, name)
                toast(
                    result.message.ifBlank { getString(R.string.parent_auto_link_success) },
                    false,
                )
            }
            is GuardianApi.ApiResult.Ok -> {
                onLinkSuccess(childCode, name)
                toast(getString(R.string.parent_auto_link_success), false)
            }
            is GuardianApi.ApiResult.Error -> toast(result.message, true)
            else -> {}
        }
    }

    private fun scheduleFreeze() {
        val email = ParentSession.guardianEmail(this)
        val targets = commandTargets()
        val pkg = findViewById<EditText>(R.id.inputTarget).text.toString().trim()
        val start = findViewById<EditText>(R.id.inputScheduleStart).text.toString().trim()
        val end = findViewById<EditText>(R.id.inputScheduleEnd).text.toString().trim()
        if (email.isNullOrBlank() || targets.isEmpty() || pkg.isBlank() || start.isBlank() || end.isBlank()) {
            toast("أدخل الحزمة ووقت البداية والنهاية", true)
            return
        }
        lifecycleScope.launch {
            var ok = 0
            var fail = 0
            for ((code, _) in targets) {
                when (
                    withContext(Dispatchers.IO) {
                        GuardianApi.addSchedule(code, "freeze_app", pkg, start, end)
                    }
                ) {
                    is GuardianApi.ApiResult.Ok -> ok++
                    is GuardianApi.ApiResult.Error -> fail++
                    else -> fail++
                }
            }
            if (targets.size == 1 && fail == 0) {
                toast("تمت جدولة التجميد", false)
            } else {
                toastBroadcastResult(ok, fail, targets.size)
            }
        }
    }

    private fun sendGuardianMessage() {
        val email = ParentSession.guardianEmail(this)
        val targets = commandTargets()
        val text = findViewById<EditText>(R.id.inputTarget).text.toString().trim()
        if (email.isNullOrBlank() || targets.isEmpty()) {
            toast("اربط الطفل أولاً", true)
            return
        }
        if (text.isBlank()) {
            toast("اكتبي الرسالة في الحقل أعلاه", true)
            return
        }
        val role = ParentSession.guardianRole(this)
        lifecycleScope.launch {
            var ok = 0
            var fail = 0
            for ((code, _) in targets) {
                when (
                    withContext(Dispatchers.IO) {
                        GuardianApi.sendGuardianMessage(code, role, text)
                    }
                ) {
                    is GuardianApi.ApiResult.Ok -> ok++
                    is GuardianApi.ApiResult.Error -> fail++
                    else -> fail++
                }
            }
            if (ok > 0) {
                toast(getString(R.string.parent_message_sent), false)
                viewAlerts()
            }
            if (targets.size > 1) {
                toastBroadcastResult(ok, fail, targets.size)
            }
        }
    }

    private fun sendCommand(action: String, value: String) {
        val email = ParentSession.guardianEmail(this)
        val targets = commandTargets()
        if (email.isNullOrBlank() || targets.isEmpty()) {
            toast("اربط الطفل أولاً", true)
            return
        }
        if (action != "allow" && value.isBlank()) {
            toast("أدخل اسم الموقع أو الحزمة", true)
            return
        }
        lifecycleScope.launch {
            val (ok, fail) = broadcastCommand(action, value, email, targets)
            if (targets.size == 1) {
                when {
                    ok == 1 -> toast("تم إرسال الأمر", false)
                    else -> toast("فشل إرسال الأمر", true)
                }
            } else {
                toastBroadcastResult(ok, fail, targets.size)
            }
        }
    }

    private fun startSetupFromWelcome() {
        ParentSession.markWelcomeSeen(this)
        showLogin()
        toast(getString(R.string.parent_welcome_steps_title), false)
    }

    private fun showWelcome(fromControl: Boolean = false) {
        hideAllSteps()
        stepWelcome.visibility = View.VISIBLE
        findViewById<View>(R.id.parentHeaderInclude)?.visibility = View.GONE
        textStepIndicator.text = getString(R.string.parent_step_welcome)
        findViewById<View>(R.id.btnStartSetup).visibility =
            if (fromControl) View.GONE else View.VISIBLE
        findViewById<View>(R.id.btnBackFromWelcome).visibility =
            if (fromControl && ParentSession.isChildLinked(this)) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.textWelcomeServerUrl)?.text =
            com.example.myrana.util.ServerConfig.healthUrl()
        scrollWizardToTop()
    }

    private fun hideAllSteps() {
        alertPollingJob?.cancel()
        stepWelcome.visibility = View.GONE
        stepLogin.visibility = View.GONE
        stepVerify.visibility = View.GONE
        stepAddChild.visibility = View.GONE
        stepLink.visibility = View.GONE
        stepLinkConfirm.visibility = View.GONE
        stepControl.visibility = View.GONE
        parentBottomNav.visibility = View.GONE
        findViewById<View>(R.id.parentHeaderInclude)?.visibility = View.VISIBLE
    }

    private fun showLogin() {
        hideAllSteps()
        stepLogin.visibility = View.VISIBLE
        textStepIndicator.text = getString(R.string.parent_step_email)
        scrollWizardToTop()
    }

    private fun showVerify() {
        hideAllSteps()
        stepVerify.visibility = View.VISIBLE
        textStepIndicator.text = getString(R.string.parent_step_email)
        scrollWizardToTop()
    }

    private fun showAddChild() {
        hideAllSteps()
        stepAddChild.visibility = View.VISIBLE
        textStepIndicator.text = getString(R.string.parent_step_add_child)
        val savedName = ParentSession.pendingChildName(this)
        if (!savedName.isNullOrBlank()) {
            findViewById<EditText>(R.id.inputAddChildName).setText(savedName)
            findViewById<EditText>(R.id.inputAddChildAge).setText(
                ParentSession.pendingChildAge(this).toString()
            )
        }
        scrollWizardToTop()
    }

    private fun showLink() {
        hideAllSteps()
        stepLink.visibility = View.VISIBLE
        textStepIndicator.text = if (ParentSession.isAddingAnotherChild(this)) {
            getString(R.string.parent_step_add_another_child)
        } else {
            getString(R.string.parent_step_link_device)
        }
        findViewById<Button>(R.id.btnCancelAddAnother).visibility =
            if (ParentSession.isAddingAnotherChild(this)) View.VISIBLE else View.GONE
        val pendingName = ParentSession.pendingChildName(this).orEmpty()
        if (pendingName.isNotEmpty()) {
            textStepIndicator.text = "${getString(R.string.parent_step_link_device)} — $pendingName"
        }
        val code = ParentSession.pendingLinkChildCode(this).orEmpty()
        if (code.isNotBlank() && ChildCodeNormalizer.isValid(code)) {
            childCodeField().setText(code)
        } else {
            val current = childCodeField().text.toString()
            if (current.isNotBlank() && !ChildCodeNormalizer.isValid(current)) {
                childCodeField().text?.clear()
            }
            if (childCodeField().text.toString().isBlank()) {
                pasteChildCodeFromClipboard(silent = true)
            }
        }
        syncLinkVerifyFields(ParentSession.pendingDeviceVerifyCode(this).orEmpty())
        val currentCode = childCodeField().text.toString().trim()
        if (currentCode.isNotEmpty() && !ChildCodeNormalizer.isValid(currentCode)) {
            showInvalidChildCodeDialog(currentCode)
        }
        scrollWizardToTop()
    }

    private fun showLinkConfirm() {
        hideAllSteps()
        stepLinkConfirm.visibility = View.VISIBLE
        textStepIndicator.text = getString(R.string.parent_step_link_device)
        val email = ParentSession.pendingLinkChildEmail(this).orEmpty()
        val code = ParentSession.pendingLinkChildCode(this).orEmpty()
        val name = ParentSession.pendingChildName(this).orEmpty()
        val age = ParentSession.pendingChildAge(this)
        childCodeField().setText(code)
        syncLinkVerifyFields(ParentSession.pendingDeviceVerifyCode(this).orEmpty())
        findViewById<TextView>(R.id.textLinkVerifiedInfo).text =
            getString(R.string.parent_link_verified_info, code, email)
        findViewById<TextView>(R.id.textPendingChildName).text =
            getString(R.string.parent_pending_child_label, name, age)
    }

    private fun showControl() {
        hideAllSteps()
        stepControl.visibility = View.VISIBLE
        parentBottomNav.visibility = View.VISIBLE
        findViewById<View>(R.id.parentHeaderInclude)?.visibility = View.GONE
        textStepIndicator.text = getString(R.string.parent_step_control)
        parentBottomNav.selectedItemId = R.id.nav_home
        showDashboardTab(R.id.panelHome)
        updateActiveChildHeader(
            ParentSession.linkedChildCount(this).coerceAtLeast(1),
        )
        renderChildrenChips()
        ParentDashboardBinder.updateAlertsPreview(
            this,
            getString(R.string.parent_alerts_waiting),
        )
        startAlertPolling()
        lifecycleScope.launch { refreshLinkedChildrenSummary() }
    }

    private fun pasteChildCodeFromClipboard(silent: Boolean = false) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip() || clipboard.primaryClipDescription?.hasMimeType(
                ClipDescription.MIMETYPE_TEXT_PLAIN
            ) != true
        ) {
            if (!silent) toast(getString(R.string.parent_paste_empty), true)
            return
        }
        val pasted = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim().orEmpty()
        if (pasted.isBlank()) {
            if (!silent) toast(getString(R.string.parent_paste_empty), true)
            return
        }
        val normalized = ChildCodeNormalizer.extractFromPaste(pasted)
            .ifBlank { ChildCodeNormalizer.normalize(pasted) }
        if (!ChildCodeNormalizer.isValid(normalized)) {
            if (!silent) showInvalidChildCodeDialog(pasted)
            return
        }
        childCodeField().setText(normalized)
        if (!silent) toast("تم: $normalized", false)
    }

    private fun toast(msg: String, isError: Boolean) {
        textMessage.text = msg
        textMessage.setTextColor(
            getColor(if (isError) android.R.color.holo_red_dark else android.R.color.holo_green_dark)
        )
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun setupBottomNavigation() {
        parentBottomNav = findViewById(R.id.parentBottomNav)
        parentBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> showDashboardTab(R.id.panelHome)
                R.id.nav_alerts -> showDashboardTab(R.id.panelAlerts)
                R.id.nav_reports -> showDashboardTab(R.id.panelReports)
                R.id.nav_children -> showDashboardTab(R.id.panelChildren)
                R.id.nav_settings -> showDashboardTab(R.id.panelSettings)
            }
            true
        }
    }

    private fun showDashboardTab(panelId: Int) {
        listOf(
            R.id.panelHome,
            R.id.panelAlerts,
            R.id.panelReports,
            R.id.panelChildren,
            R.id.panelSettings,
        ).forEach { id ->
            findViewById<View>(id)?.visibility =
                if (id == panelId) View.VISIBLE else View.GONE
        }
        scrollWizardToTop()
    }

    private fun setupQuickActions() {
        findViewById<View>(R.id.btnSwitchChild)?.setOnClickListener {
            parentBottomNav.selectedItemId = R.id.nav_children
            showDashboardTab(R.id.panelChildren)
        }
        findViewById<View>(R.id.btnQuickBlock)?.setOnClickListener {
            parentBottomNav.selectedItemId = R.id.nav_settings
            showDashboardTab(R.id.panelSettings)
            findViewById<EditText>(R.id.inputTarget).requestFocus()
        }
        findViewById<View>(R.id.btnQuickLock)?.setOnClickListener {
            findViewById<Button>(R.id.btnOpenScreenTime).performClick()
        }
        findViewById<View>(R.id.btnQuickScreenTime)?.setOnClickListener {
            findViewById<Button>(R.id.btnOpenScreenTime).performClick()
        }
        findViewById<View>(R.id.btnQuickNotify)?.setOnClickListener {
            findViewById<Button>(R.id.btnSendMessage).performClick()
        }
    }
}

package com.example.myrana.parent.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.PatternsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myrana.BuildConfig
import com.example.myrana.R
import com.example.myrana.data.remote.GuardianApi
import com.example.myrana.parent.ParentSession
import com.example.myrana.util.ChildCodeNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * تطبيق ولي الأمر (نكهة `parent`) — لوحة التحكم الكاملة.
 *
 * **الخطوات:**
 * 1. بريد + صفة → تحقق
 * 2. إضافة الطفل (الاسم والعمر)
 * 3. ربط الجهاز (كود + رمز الربط) → تأكيد
 * 4. تحكم: حظر، وقت استخدام، تنبيهات، إضافة طفل آخر
 *
 * **السيرفر:** [com.example.myrana.data.remote.GuardianApi] + [NetworkModule]
 */
class ParentMainActivity : AppCompatActivity() {

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
    private lateinit var textAlertsPreview: TextView
    private var linkedChildrenRows: List<Pair<String, String>> = emptyList()
    private var childSpinnerIgnoreSelection = false
    private lateinit var textUsageTitle: TextView
    private var alertPollingJob: Job? = null
    private lateinit var textUsageEmpty: TextView
    private lateinit var recyclerUsage: RecyclerView
    private lateinit var usageAdapter: UsageReportAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_parent_main)

        findViewById<TextView>(R.id.textAppVersion)?.text =
            getString(R.string.parent_app_version_label, BuildConfig.VERSION_NAME)

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
        textAlertsPreview = findViewById(R.id.textAlertsPreview)
        textUsageTitle = findViewById(R.id.textUsageReportTitle)
        textUsageEmpty = findViewById(R.id.textUsageEmpty)
        recyclerUsage = findViewById(R.id.recyclerUsage)

        usageAdapter = UsageReportAdapter(packageManager) { item ->
            findViewById<EditText>(R.id.inputTarget).setText(item.packageName)
            sendCommand("freeze_app", item.packageName)
        }
        recyclerUsage.layoutManager = LinearLayoutManager(this)
        recyclerUsage.adapter = usageAdapter

        setupRoleSpinner()
        spinnerChildren.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (childSpinnerIgnoreSelection) return
                val row = linkedChildrenRows.getOrNull(position) ?: return
                ParentSession.saveLinkedChild(this@ParentMainActivity, row.first, row.second)
                lifecycleScope.launch { refreshDashboardMini() }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        findViewById<Button>(R.id.btnSendEmailCode).setOnClickListener { sendEmailCode() }
        findViewById<Button>(R.id.btnVerifyEmail).setOnClickListener { verifyEmail() }
        findViewById<Button>(R.id.btnContinueToLink).setOnClickListener { continueToLink() }
        findViewById<Button>(R.id.btnAutoLink).setOnClickListener { autoLinkChild() }
        findViewById<Button>(R.id.btnSendLinkCode).setOnClickListener { sendLinkCode() }
        findViewById<Button>(R.id.btnVerifyDevice).setOnClickListener { verifyDeviceCode() }
        findViewById<Button>(R.id.btnPasteChildCode).setOnClickListener { pasteChildCodeFromClipboard() }
        findViewById<Button>(R.id.btnLinkChild).setOnClickListener { linkChild() }
        findViewById<Button>(R.id.btnAddAnotherChild).setOnClickListener { addAnotherChild() }
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

        restoreUiState()
    }

    override fun onResume() {
        super.onResume()
        if (ParentSession.isChildLinked(this)) {
            lifecycleScope.launch {
                refreshAlertsQuietly()
                refreshLinkedChildrenSummary()
                refreshDashboardMini()
            }
            startAlertPolling()
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

    private fun restoreUiState() {
        val email = ParentSession.guardianEmail(this)
        if (!email.isNullOrBlank()) {
            findViewById<EditText>(R.id.inputGuardianEmail).setText(email)
        }
        when {
            ParentSession.isChildLinked(this) -> showControl()
            ParentSession.isDeviceLinkVerified(this) -> showLinkConfirm()
            ParentSession.hasPendingChildProfile(this) -> showLink()
            ParentSession.isEmailVerified(this) -> showAddChild()
            !email.isNullOrBlank() -> showVerify()
            else -> showLogin()
        }
    }

    private fun continueToLink() {
        val name = findViewById<EditText>(R.id.inputAddChildName).text.toString().trim()
            .ifBlank { "طفل" }
        val age = findViewById<EditText>(R.id.inputAddChildAge).text.toString().toIntOrNull() ?: 10
        ParentSession.savePendingChildProfile(this, name, age)
        showLink()
        toast("تم — اضغطي «ربط تلقائي» بعد لصق كود الطفل", false)
    }

    private fun verifyDeviceCode() {
        val childCode = normalizedChildCodeFromInput()
        val verify = findViewById<EditText>(R.id.inputDeviceVerify).text.toString().trim()
        if (childCode.isEmpty() || verify.isEmpty()) {
            toast(getString(R.string.parent_link_incomplete), true)
            return
        }
        lifecycleScope.launch {
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
                    )
                    childCodeField().setText(result.childCode)
                    findViewById<EditText>(R.id.inputDeviceVerify).setText(verify)
                    performLink(
                        childCode = result.childCode,
                        verifyCode = verify,
                        name = pendingChildName(),
                        age = ParentSession.pendingChildAge(this@ParentMainActivity),
                    )
                }
                is GuardianApi.ApiResult.Error -> toast(result.message, true)
                else -> {}
            }
        }
    }

    private fun addAnotherChild() {
        ParentSession.startAddAnotherChild(this)
        findViewById<EditText>(R.id.inputAddChildName).text?.clear()
        findViewById<EditText>(R.id.inputAddChildAge).text?.clear()
        childCodeField().text?.clear()
        findViewById<EditText>(R.id.inputDeviceVerify).text?.clear()
        showAddChild()
        toast("أدخلي بيانات الطفل الجديد", false)
    }

    /**
     * 1) إرسال أمر للطفل لرفع الاستخدام فوراً.
     * 2) انتظار قصير ثم جلب التقرير وعرضه في قائمة.
     */
    private fun applyDefaultBlocklist() {
        val childCode = ParentSession.childCode(this)
        if (childCode.isNullOrBlank()) {
            toast("اربط الطفل أولاً", true)
            return
        }
        findViewById<Button>(R.id.btnApplyDefaultBlocklist).isEnabled = false
        lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { GuardianApi.applyDefaultBlocklist(childCode) }) {
                is GuardianApi.ApiResult.Ok -> toast(result.message, false)
                is GuardianApi.ApiResult.Error -> toast(result.message, true)
                else -> toast("فشل تطبيق القائمة", true)
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
                    textAlertsPreview.text = result.error
                    false
                } else if (result.lines.isEmpty()) {
                    textAlertsPreview.text = getString(R.string.parent_alerts_waiting)
                    false
                } else {
                    val preview = result.lines.take(8).joinToString("\n\n")
                    textAlertsPreview.text =
                        "${getString(R.string.parent_alerts_preview_title)}\n\n$preview"
                    true
                }
            }
            else -> false
        }
    }

    private fun onLinkSuccess(childCode: String, name: String) {
        ParentSession.saveLinkedChild(this, childCode, name)
        showControl()
        startAlertPolling()
        toast(getString(R.string.parent_link_success_monitoring), false)
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
                textDashboardMini.text = "$status — $mini\n${d.childCode}"
                textPermissionsMini.text = ParentPermissionsFormatter.summary(
                    this@ParentMainActivity,
                    d.permissionsOk,
                    d.permissions,
                )
                bindUsageApps(usageItemsFromDashboard(d.topAppsToday), quiet = true)
            }
            else -> Unit
        }
    }

    private fun usageItemsFromDashboard(
        topApps: List<Map<String, Any?>>,
    ): List<com.example.myrana.data.remote.dto.UsageAppItem> {
        return topApps.mapNotNull { row ->
            val pkg = row["package_name"]?.toString()?.trim().orEmpty()
            if (pkg.isEmpty()) return@mapNotNull null
            val sec = (row["total_seconds"] as? Number)?.toLong() ?: 0L
            com.example.myrana.data.remote.dto.UsageAppItem(
                packageName = pkg,
                totalSeconds = sec,
                avgSecondsPerDay = sec,
                appLabel = row["app_label"]?.toString(),
                iconBase64 = row["icon_b64"]?.toString(),
                showDailyTotal = true,
            )
        }
    }

    private fun bindUsageApps(
        items: List<com.example.myrana.data.remote.dto.UsageAppItem>,
        quiet: Boolean = false,
    ) {
        if (items.isEmpty()) {
            textUsageTitle.visibility = View.VISIBLE
            textUsageEmpty.visibility = View.VISIBLE
            recyclerUsage.visibility = View.GONE
            if (!quiet) toast(getString(R.string.parent_usage_empty), true)
            return
        }
        textUsageEmpty.visibility = View.GONE
        textUsageTitle.visibility = View.VISIBLE
        recyclerUsage.visibility = View.VISIBLE
        usageAdapter.submit(items)
        if (!quiet) toast("تم عرض ${items.size} تطبيق", false)
    }

    /** جلب قائمة الأطفال من السيرفر — دعم تعدد الأطفال لولي الأمر الواحد. */
    private suspend fun refreshLinkedChildrenSummary() {
        val email = ParentSession.guardianEmail(this@ParentMainActivity).orEmpty()
        if (email.isBlank()) return
        when (val result = withContext(Dispatchers.IO) { GuardianApi.fetchLinkedChildren(email) }) {
            is GuardianApi.ApiResult.ChildrenList -> {
                if (result.children.isEmpty()) return
                val rows = result.children.mapNotNull { child ->
                    val code = child["child_code"]?.toString().orEmpty()
                    if (code.isBlank()) return@mapNotNull null
                    val name = child["name"]?.toString()?.ifBlank { "طفل" } ?: "طفل"
                    val online = if (child["online"] == true) "متصل" else "غير متصل"
                    Triple(code, name, online)
                }
                if (rows.isEmpty()) return
                linkedChildrenRows = rows.map { it.first to it.second }
                val labels = rows.map { (code, name, status) ->
                    getString(R.string.parent_child_spinner_item, name, code, status)
                }
                childSpinnerIgnoreSelection = true
                spinnerChildren.adapter = ArrayAdapter(
                    this@ParentMainActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    labels,
                )
                val active = ParentSession.childCode(this@ParentMainActivity)
                val activeNorm = ChildCodeNormalizer.forApi(active.orEmpty())
                var selectIndex = rows.indexOfFirst { ChildCodeNormalizer.forApi(it.first) == activeNorm }
                if (selectIndex < 0) {
                    selectIndex = 0
                    ParentSession.saveLinkedChild(
                        this@ParentMainActivity,
                        rows[0].first,
                        rows[0].second,
                    )
                }
                spinnerChildren.setSelection(selectIndex)
                childSpinnerIgnoreSelection = false
                textLinkedChild.text = getString(R.string.parent_linked_children_summary, rows.size)
            }
            else -> {}
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

            when (val report = withContext(Dispatchers.IO) { GuardianApi.fetchWeeklyUsage(childCode, 7) }) {
                is GuardianApi.ApiResult.UsageList -> {
                    showUsageList(report.items)
                    startActivity(
                        Intent(this@ParentMainActivity, ParentScreenTimeActivity::class.java)
                            .putExtra(ParentScreenTimeActivity.EXTRA_REPORT_PERIOD, ParentScreenTimeActivity.PERIOD_WEEKLY)
                    )
                }
                is GuardianApi.ApiResult.Error -> toast(report.message, true)
                else -> toast("فشل تحميل التقرير", true)
            }
            findViewById<Button>(R.id.btnRequestUsageReport).isEnabled = true
        }
    }

    private fun showUsageList(items: List<com.example.myrana.data.remote.dto.UsageAppItem>) {
        bindUsageApps(items, quiet = false)
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
                    textMessage.text = result.message
                    findViewById<EditText>(R.id.inputEmailCode).text?.clear()
                    result.verificationCode?.let { code ->
                        findViewById<EditText>(R.id.inputEmailCode).setText(code)
                    }
                    if (!result.verificationCode.isNullOrBlank()) {
                        verifyEmailSilently(email, result.verificationCode)
                    } else if (result.devFallback) {
                        toast(getString(R.string.parent_code_copied_hint), false)
                    } else {
                        toast("تحققي من بريدك ($email) وأدخلي الرمز", false)
                    }
                }
                is GuardianApi.ApiResult.Error -> toast(result.message, true)
                else -> {}
            }
        }
    }

    private fun childCodeField(): EditText = findViewById(R.id.inputChildCode)

    /** يضيف CHILD- تلقائياً إذا لصقتِ الجزء فقط (مثل 2776E398). */
    private fun normalizedChildCodeFromInput(): String {
        val normalized = ChildCodeNormalizer.normalize(childCodeField().text.toString())
        if (normalized.isNotEmpty()) {
            childCodeField().setText(normalized)
        }
        return normalized
    }

    /** ربط كامل بزر واحد: إرسال رمز الربط + ربط الطفل على السيرفر. */
    private fun autoLinkChild() {
        ensureChildCodeFromInput() ?: return
        val childCode = normalizedChildCodeFromInput()
        val email = ParentSession.guardianEmail(this).orEmpty()
        if (!PatternsCompat.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("أدخل بريد ولي الأمر أولاً", true)
            return
        }
        val name = pendingChildName()
        val age = ParentSession.pendingChildAge(this).takeIf { it > 0 }
            ?: findViewById<EditText>(R.id.inputAddChildAge).text.toString().toIntOrNull()
            ?: 10
        ParentSession.savePendingChildProfile(this, name, age)

        setLinkButtonsEnabled(false)
        toast(getString(R.string.parent_auto_linking), false)
        lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { GuardianApi.sendLinkCode(email, childCode) }) {
                is GuardianApi.ApiResult.EmailCodeSent -> {
                    val verifyCode = result.verificationCode?.trim().orEmpty()
                    if (verifyCode.isEmpty()) {
                        textMessage.text = result.message
                        toast("أدخلي رمز الربط من البريد في الحقل ثم اضغطي «ربط تلقائي» مرة أخرى", true)
                    } else {
                        findViewById<EditText>(R.id.inputDeviceVerify).setText(verifyCode)
                        performLink(childCode, verifyCode, name, age)
                    }
                }
                is GuardianApi.ApiResult.Error -> toast(result.message, true)
                else -> {}
            }
            setLinkButtonsEnabled(true)
        }
    }

    private fun sendLinkCode() {
        ensureChildCodeFromInput() ?: return
        val email = ParentSession.guardianEmail(this).orEmpty()
        val childCode = normalizedChildCodeFromInput()
        if (!PatternsCompat.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("أدخل بريد ولي الأمر أولاً", true)
            return
        }
        lifecycleScope.launch {
            when (val result = withContext(Dispatchers.IO) { GuardianApi.sendLinkCode(email, childCode) }) {
                is GuardianApi.ApiResult.EmailCodeSent -> {
                    textMessage.text = result.message
                    result.verificationCode?.let { code ->
                        findViewById<EditText>(R.id.inputDeviceVerify).setText(code)
                    }
                    if (result.verificationCode != null) {
                        performLink(
                            childCode = childCode,
                            verifyCode = result.verificationCode,
                            name = pendingChildName(),
                            age = ParentSession.pendingChildAge(this@ParentMainActivity),
                        )
                    } else {
                        toast("تحققي من بريدك وأدخلي رمز الربط", false)
                    }
                }
                is GuardianApi.ApiResult.Error -> toast(result.message, true)
                else -> {}
            }
        }
    }

    private fun ensureChildCodeFromInput(): String? {
        var childCode = normalizedChildCodeFromInput()
        if (childCode.isEmpty()) {
            pasteChildCodeFromClipboard(silent = true)
            childCode = normalizedChildCodeFromInput()
        }
        if (childCode.isEmpty()) {
            toast("أدخل كود الطفل من جواله (CHILD-...)", true)
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
        findViewById<Button>(R.id.btnVerifyDevice).isEnabled = enabled
        findViewById<Button>(R.id.btnLinkChild).isEnabled = enabled
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
                showAddChild()
                toast(result.message, false)
                pasteChildCodeFromClipboard(silent = true)
            }
            is GuardianApi.ApiResult.Error -> toast(result.message, true)
            else -> {}
        }
    }

    private fun linkChild() {
        val childCode = normalizedChildCodeFromInput()
        val verify = findViewById<EditText>(R.id.inputDeviceVerify).text.toString().trim()
        if (childCode.isEmpty() || verify.isEmpty()) {
            toast(getString(R.string.parent_link_incomplete), true)
            return
        }
        lifecycleScope.launch {
            performLink(
                childCode = childCode,
                verifyCode = verify,
                name = pendingChildName(),
                age = ParentSession.pendingChildAge(this@ParentMainActivity),
            )
        }
    }

    private suspend fun performLink(childCode: String, verifyCode: String, name: String, age: Int) {
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
        val childCode = ParentSession.childCode(this)
        val pkg = findViewById<EditText>(R.id.inputTarget).text.toString().trim()
        val start = findViewById<EditText>(R.id.inputScheduleStart).text.toString().trim()
        val end = findViewById<EditText>(R.id.inputScheduleEnd).text.toString().trim()
        if (childCode.isNullOrBlank() || pkg.isBlank() || start.isBlank() || end.isBlank()) {
            toast("أدخل الحزمة ووقت البداية والنهاية", true)
            return
        }
        lifecycleScope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    GuardianApi.addSchedule(childCode, "freeze_app", pkg, start, end)
                }
            ) {
                is GuardianApi.ApiResult.Ok -> toast(result.message, false)
                is GuardianApi.ApiResult.Error -> toast(result.message, true)
                else -> {}
            }
        }
    }

    private fun sendGuardianMessage() {
        val childCode = ParentSession.childCode(this)
        val text = findViewById<EditText>(R.id.inputTarget).text.toString().trim()
        if (childCode.isNullOrBlank()) {
            toast("اربط الطفل أولاً", true)
            return
        }
        if (text.isBlank()) {
            toast("اكتبي الرسالة في الحقل أعلاه", true)
            return
        }
        val role = ParentSession.guardianRole(this)
        lifecycleScope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    GuardianApi.sendGuardianMessage(childCode, role, text)
                }
            ) {
                is GuardianApi.ApiResult.Ok -> {
                    toast(getString(R.string.parent_message_sent), false)
                    textMessage.text = result.message
                    viewAlerts()
                }
                is GuardianApi.ApiResult.Error -> toast(result.message, true)
                else -> {}
            }
        }
    }

    private fun sendCommand(action: String, value: String) {
        val childCode = ParentSession.childCode(this)
        val email = ParentSession.guardianEmail(this)
        if (childCode.isNullOrBlank() || email.isNullOrBlank()) {
            toast("اربط الطفل أولاً", true)
            return
        }
        if (action != "allow" && value.isBlank()) {
            toast("أدخل اسم الموقع أو الحزمة", true)
            return
        }
        lifecycleScope.launch {
            when (
                val result = withContext(Dispatchers.IO) {
                    GuardianApi.sendCommand(action, value, childCode, email)
                }
            ) {
                is GuardianApi.ApiResult.Ok -> toast(result.message, false)
                is GuardianApi.ApiResult.Error -> toast(result.message, true)
                else -> {}
            }
        }
    }

    private fun hideAllSteps() {
        stepLogin.visibility = View.GONE
        stepVerify.visibility = View.GONE
        stepAddChild.visibility = View.GONE
        stepLink.visibility = View.GONE
        stepLinkConfirm.visibility = View.GONE
        stepControl.visibility = View.GONE
    }

    private fun showLogin() {
        hideAllSteps()
        stepLogin.visibility = View.VISIBLE
        textStepIndicator.text = getString(R.string.parent_step_email)
    }

    private fun showVerify() {
        hideAllSteps()
        stepVerify.visibility = View.VISIBLE
        textStepIndicator.text = getString(R.string.parent_step_email)
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
    }

    private fun showLink() {
        hideAllSteps()
        stepLink.visibility = View.VISIBLE
        textStepIndicator.text = getString(R.string.parent_step_link_device)
        val pendingName = ParentSession.pendingChildName(this).orEmpty()
        if (pendingName.isNotEmpty()) {
            textStepIndicator.text = "${getString(R.string.parent_step_link_device)} — $pendingName"
        }
        if (childCodeField().text.toString().isBlank()) {
            pasteChildCodeFromClipboard(silent = true)
        }
    }

    private fun showLinkConfirm() {
        hideAllSteps()
        stepLinkConfirm.visibility = View.VISIBLE
        textStepIndicator.text = getString(R.string.parent_step_link_device)
        val email = ParentSession.pendingLinkChildEmail(this).orEmpty()
        val code = ParentSession.pendingLinkChildCode(this).orEmpty()
        val name = ParentSession.pendingChildName(this).orEmpty()
        val age = ParentSession.pendingChildAge(this)
        findViewById<TextView>(R.id.textLinkVerifiedInfo).text =
            getString(R.string.parent_link_verified_info, code, email)
        findViewById<TextView>(R.id.textPendingChildName).text =
            getString(R.string.parent_pending_child_label, name, age)
    }

    private fun showControl() {
        hideAllSteps()
        stepControl.visibility = View.VISIBLE
        textStepIndicator.text = getString(R.string.parent_step_control)
        val name = ParentSession.childName(this).orEmpty()
        val code = ParentSession.childCode(this).orEmpty()
        textLinkedChild.text = getString(
            R.string.parent_linked_with_role,
            name,
            ParentSession.guardianRole(this),
            code
        )
        textAlertsPreview.text = getString(R.string.parent_alerts_waiting)
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
        val normalized = ChildCodeNormalizer.normalize(pasted)
        if (normalized.isEmpty()) {
            if (!silent) toast("كود الطفل غير صالح", true)
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
}

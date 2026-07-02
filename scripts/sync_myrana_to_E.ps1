# Sync MYRana from C to E + apply patches
# Run: powershell -ExecutionPolicy Bypass -File E:\parent_monitor_project\scripts\sync_myrana_to_E.ps1

$src = "C:\Users\rannn\AndroidStudioProjects\MYRana"
$dst = "E:\parent_monitor_project\MYRana"
$patches = "E:\parent_monitor_project\patches\android"

if (-not (Test-Path $src)) {
    Write-Host "ERROR: Android source not found: $src" -ForegroundColor Red
    exit 1
}

Write-Host "Step 1: Copy project C -> E (no build/.gradle)..." -ForegroundColor Cyan
& robocopy $src $dst /MIR /XD build .gradle .idea captures /XF *.apk /R:1 /W:1 | Out-Null
$rc = $LASTEXITCODE
if ($rc -ge 8) {
    Write-Host "ERROR: robocopy failed (code $rc)" -ForegroundColor Red
    exit $rc
}

Write-Host "Step 2: Copy patch files..." -ForegroundColor Cyan
$patchMap = @{
    "PermissionStatusReporter.kt"    = "$dst\app\src\main\java\com\example\myrana\permissions\PermissionStatusReporter.kt"
    "ParentSettingsActivity.kt"      = "$dst\app\src\parent\java\com\example\myrana\parent\ui\ParentSettingsActivity.kt"
    "ParentPermissionsFormatter.kt"  = "$dst\app\src\parent\java\com\example\myrana\parent\ui\ParentPermissionsFormatter.kt"
    "activity_parent_settings.xml"   = "$dst\app\src\parent\res\layout\activity_parent_settings.xml"
}
foreach ($k in $patchMap.Keys) {
    $from = Join-Path $patches $k
    if (Test-Path $from) {
        $dir = Split-Path $patchMap[$k] -Parent
        if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }
        Copy-Item -Force $from $patchMap[$k]
        Write-Host "  OK $k"
    }
}

Write-Host "Step 3: Patch NetworkModule.kt..." -ForegroundColor Cyan
$nm = "$dst\app\src\main\java\com\example\myrana\data\remote\NetworkModule.kt"
if (Test-Path $nm) {
    $t = Get-Content $nm -Raw -Encoding UTF8
    $old = @'
    /** نبضة اتصال من جهاز الطفل. */
    fun postChildHeartbeat(childCode: String): Boolean {
        val code = ChildCodeNormalizer.forApi(childCode)
        if (code.isBlank()) return false
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return false
        val url = base.newBuilder().addPathSegments("child-heartbeat").build()
        val payload = mapOf("child_code" to code, "ts_ms" to System.currentTimeMillis())
'@
    $new = @'
    /** نبضة اتصال من جهاز الطفل — تتضمن حالة الصلاحيات لولي الأمر. */
    fun postChildHeartbeat(childCode: String, permissions: Map<String, Any?>? = null): Boolean {
        val code = ChildCodeNormalizer.forApi(childCode)
        if (code.isBlank()) return false
        val base = BuildConfig.SERVER_ROOT_URL.toHttpUrlOrNull() ?: return false
        val url = base.newBuilder().addPathSegments("child-heartbeat").build()
        val payload = mutableMapOf<String, Any?>(
            "child_code" to code,
            "ts_ms" to System.currentTimeMillis(),
        )
        if (!permissions.isNullOrEmpty()) {
            payload["permissions"] = permissions
        }
'@
    if ($t -match 'fun postChildHeartbeat\(childCode: String, permissions') {
        Write-Host "  SKIP NetworkModule (already patched)" -ForegroundColor Yellow
    } elseif ($t.Contains($old)) {
        $t = $t.Replace($old, $new)
        [System.IO.File]::WriteAllText($nm, $t, [System.Text.UTF8Encoding]::new($false))
        Write-Host "  OK NetworkModule.kt"
    } else {
        Write-Host "  WARN NetworkModule pattern not found" -ForegroundColor Yellow
    }
}

Write-Host "Step 3b: Patch GuardianApi.kt..." -ForegroundColor Cyan
$ga = "$dst\app\src\main\java\com\example\myrana\data\remote\GuardianApi.kt"
$gaFns = Join-Path $patches "GuardianApi_functions.kt"
if ((Test-Path $ga) -and (Test-Path $gaFns)) {
    $t = Get-Content $ga -Raw -Encoding UTF8
    if (-not $t.Contains("fun fetchGuardianSettings")) {
        $insert = Get-Content $gaFns -Raw -Encoding UTF8
        $marker = '    /** قائمة الأطفال المرتبطين بولي الأمر'
        if ($t.Contains($marker)) {
            $t = $t.Replace($marker, ($insert + "`r`n`r`n" + $marker))
            Write-Host "  OK GuardianApi functions"
        }
    } else {
        Write-Host "  SKIP GuardianApi functions (present)"
    }
    if (-not $t.Contains("permissionsOk = json")) {
        $t = $t.Replace(
            '                    policy = policy,',
            "                    policy = policy,`r`n                    permissionsOk = json[`"permissions_ok`"] == true,`r`n                    permissions = (json[`"permissions`"] as? Map<String, Any?>) ?: emptyMap(),"
        )
        Write-Host "  OK GuardianApi dashboard permissions"
    }
    if (-not $t.Contains("GuardianSettingsLoaded")) {
        $t = $t.Replace(
            '        data class ReportText(val text: String) : ApiResult()',
            "        data class ReportText(val text: String) : ApiResult()`r`n        data class GuardianSettingsLoaded(val settings: Map<String, Any?>) : ApiResult()`r`n        data class AuditLog(val lines: List<String>) : ApiResult()"
        )
        Write-Host "  OK GuardianApi ApiResult types"
    }
    if (-not $t.Contains("val permissionsOk: Boolean")) {
        $t = $t.Replace(
            '        val policy: ScreenTimePolicy,',
            "        val policy: ScreenTimePolicy,`r`n        val permissionsOk: Boolean = false,`r`n        val permissions: Map<String, Any?> = emptyMap(),"
        )
        Write-Host "  OK GuardianApi ChildDashboardData"
    }
    [System.IO.File]::WriteAllText($ga, $t, [System.Text.UTF8Encoding]::new($false))
}

Write-Host "Step 4: Patch ScreenTimeSyncHelper.kt..." -ForegroundColor Cyan
$st = "$dst\app\src\main\java\com\example\myrana\sync\ScreenTimeSyncHelper.kt"
if (Test-Path $st) {
    $t = Get-Content $st -Raw -Encoding UTF8
    if (-not $t.Contains("PermissionStatusReporter")) {
        $t = $t.Replace(
            'import com.example.myrana.session.ChildSession',
            "import com.example.myrana.permissions.PermissionStatusReporter`r`nimport com.example.myrana.session.ChildSession"
        )
        $t = $t.Replace(
            'NetworkModule.postChildHeartbeat(childCode)',
            'NetworkModule.postChildHeartbeat(childCode, PermissionStatusReporter.toPayload(context))'
        )
        [System.IO.File]::WriteAllText($st, $t, [System.Text.UTF8Encoding]::new($false))
        Write-Host "  OK ScreenTimeSyncHelper.kt"
    }
}

Write-Host "Step 5: Patch AndroidManifest.xml..." -ForegroundColor Cyan
$manifest = "$dst\app\src\parent\AndroidManifest.xml"
if ((Test-Path $manifest) -and -not (Select-String -Path $manifest -Pattern "ParentSettingsActivity" -Quiet)) {
    $t = Get-Content $manifest -Raw -Encoding UTF8
    $activity = @'

        <activity
            android:name=".parent.ui.ParentSettingsActivity"
            android:exported="false"
            android:windowSoftInputMode="adjustResize" />
'@
    $t = $t.Replace('    </application>', ($activity + '    </application>'))
    [System.IO.File]::WriteAllText($manifest, $t, [System.Text.UTF8Encoding]::new($false))
    Write-Host "  OK AndroidManifest.xml"
}

Write-Host "Step 6: Patch strings_parent.xml..." -ForegroundColor Cyan
$strings = "$dst\app\src\parent\res\values\strings_parent.xml"
$extraArabic = @'

    <string name="parent_settings_title">إعدادات ولي الأمر</string>
    <string name="parent_settings_retention_hint">حذف البيانات الأقدم من (7–90 يوماً)</string>
    <string name="parent_settings_daily_email">ملخص يومي بالبريد</string>
    <string name="parent_settings_weekly_email">ملخص أسبوعي بالبريد</string>
    <string name="parent_settings_alert_sound">صوت التنبيه</string>
    <string name="parent_btn_save_settings">حفظ الإعدادات</string>
    <string name="parent_btn_send_daily_email">إرسال الملخص اليومي الآن</string>
    <string name="parent_btn_send_weekly_email">إرسال الملخص الأسبوعي الآن</string>
    <string name="parent_btn_load_audit">عرض سجل التغييرات</string>
    <string name="parent_btn_open_settings">إعدادات وسجل التغييرات</string>
    <string name="parent_audit_empty">لا تغييرات مسجّلة بعد</string>
    <string name="parent_perm_ok">مفعّل</string>
    <string name="parent_perm_missing">غير مفعّل</string>
    <string name="parent_permissions_all_ok">صلاحيات جوال الطفل الأساسية مفعّلة ✓</string>
    <string name="parent_permissions_incomplete">بعض الصلاحيات غير مفعّلة على جوال الطفل — فعّلي الوصول وبيانات الاستخدام</string>
    <string name="parent_permissions_unknown">صلاحيات الطفل: بانتظار أول اتصال من جوال الطفل</string>
    <string name="parent_permissions_status">الاستخدام %1$s | الوصول %2$s | الإشعارات %3$s | البطارية %4$s</string>
'@
if ((Test-Path $strings) -and -not (Select-String -Path $strings -Pattern "parent_btn_open_settings" -Quiet)) {
    $t = Get-Content $strings -Raw -Encoding UTF8
    $t = $t.Replace('</resources>', ($extraArabic + '</resources>'))
    [System.IO.File]::WriteAllText($strings, $t, [System.Text.UTF8Encoding]::new($false))
    Write-Host "  OK strings_parent.xml"
} elseif ((Test-Path $strings) -and (Select-String -Path $strings -Pattern 'parent_settings_title">Settings' -Quiet)) {
    $t = Get-Content $strings -Raw -Encoding UTF8
    $t = $t.Replace('parent_settings_title">Settings', 'parent_settings_title">إعدادات ولي الأمر')
    $t = $t.Replace('parent_settings_retention_hint">Delete data older than (days 7-90)', 'parent_settings_retention_hint">حذف البيانات الأقدم من (7–90 يوماً)')
    $t = $t.Replace('parent_settings_daily_email">Daily email summary', 'parent_settings_daily_email">ملخص يومي بالبريد')
    $t = $t.Replace('parent_settings_weekly_email">Weekly email summary', 'parent_settings_weekly_email">ملخص أسبوعي بالبريد')
    $t = $t.Replace('parent_settings_alert_sound">Alert sound', 'parent_settings_alert_sound">صوت التنبيه')
    $t = $t.Replace('parent_btn_save_settings">Save settings', 'parent_btn_save_settings">حفظ الإعدادات')
    $t = $t.Replace('parent_btn_send_daily_email">Send daily summary now', 'parent_btn_send_daily_email">إرسال الملخص اليومي الآن')
    $t = $t.Replace('parent_btn_send_weekly_email">Send weekly summary now', 'parent_btn_send_weekly_email">إرسال الملخص الأسبوعي الآن')
    $t = $t.Replace('parent_btn_load_audit">Show change log', 'parent_btn_load_audit">عرض سجل التغييرات')
    $t = $t.Replace('parent_btn_open_settings">Settings and audit log', 'parent_btn_open_settings">إعدادات وسجل التغييرات')
    $t = $t.Replace('parent_audit_empty">No changes logged yet', 'parent_audit_empty">لا تغييرات مسجّلة بعد')
    $t = $t.Replace('parent_permissions_status">Permissions: usage %1$s | accessibility %2$s | notifications %3$s', 'parent_permissions_status">الاستخدام %1$s | الوصول %2$s | الإشعارات %3$s | البطارية %4$s')
    if (-not $t.Contains('parent_perm_ok')) {
        $t = $t.Replace('</resources>', ($extraArabic + '</resources>'))
    }
    [System.IO.File]::WriteAllText($strings, $t, [System.Text.UTF8Encoding]::new($false))
    Write-Host "  OK strings_parent.xml (Arabic fix)"
}

Write-Host "Step 7: Patch ParentMainActivity.kt..." -ForegroundColor Cyan
$pma = "$dst\app\src\parent\java\com\example\myrana\parent\ui\ParentMainActivity.kt"
if ((Test-Path $pma) -and -not (Select-String -Path $pma -Pattern "btnOpenSettings" -Quiet)) {
    $t = Get-Content $pma -Raw -Encoding UTF8
    $old = 'findViewById<Button>(R.id.btnOpenScreenTime).setOnClickListener {'
    $new = @'
findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
            startActivity(Intent(this, ParentSettingsActivity::class.java))
        }
        findViewById<Button>(R.id.btnOpenScreenTime).setOnClickListener {
'@
    if ($t.Contains($old)) {
        $t = $t.Replace($old, $new)
        [System.IO.File]::WriteAllText($pma, $t, [System.Text.UTF8Encoding]::new($false))
        Write-Host "  OK ParentMainActivity.kt"
    }
}

Write-Host "Step 8: Patch activity_parent_main.xml..." -ForegroundColor Cyan
$layout = "$dst\app\src\parent\res\layout\activity_parent_main.xml"
if ((Test-Path $layout) -and -not (Select-String -Path $layout -Pattern "btnOpenSettings" -Quiet)) {
    $t = Get-Content $layout -Raw -Encoding UTF8
    $old = '<Button
                android:id="@+id/btnOpenScreenTime"'
    $new = @'
<Button
                android:id="@+id/btnOpenSettings"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:text="@string/parent_btn_open_settings" />

            <Button
                android:id="@+id/btnOpenScreenTime"
'@
    if ($t.Contains($old)) {
        $t = $t.Replace($old, $new)
        [System.IO.File]::WriteAllText($layout, $t, [System.Text.UTF8Encoding]::new($false))
        Write-Host "  OK activity_parent_main.xml"
    }
}

Write-Host "Step 9: Patch permissions display (ParentMainActivity)..." -ForegroundColor Cyan
$pma2 = "$dst\app\src\parent\java\com\example\myrana\parent\ui\ParentMainActivity.kt"
if ((Test-Path $pma2) -and -not (Select-String -Path $pma2 -Pattern "textPermissionsMini" -Quiet)) {
    $t = Get-Content $pma2 -Raw -Encoding UTF8
    $t = $t.Replace(
        '    private lateinit var textDashboardMini: TextView',
        "    private lateinit var textDashboardMini: TextView`r`n    private lateinit var textPermissionsMini: TextView"
    )
    $t = $t.Replace(
        '        textDashboardMini = findViewById(R.id.textDashboardMini)',
        "        textDashboardMini = findViewById(R.id.textDashboardMini)`r`n        textPermissionsMini = findViewById(R.id.textPermissionsMini)"
    )
    $old = '                textDashboardMini.text = "$status — $mini\n${d.childCode}"'
    $new = @'
                textDashboardMini.text = "$status — $mini\n${d.childCode}"
                textPermissionsMini.text = ParentPermissionsFormatter.summary(
                    this@ParentMainActivity,
                    d.permissionsOk,
                    d.permissions,
                )
'@
    if ($t.Contains($old)) {
        $t = $t.Replace($old, $new)
        [System.IO.File]::WriteAllText($pma2, $t, [System.Text.UTF8Encoding]::new($false))
        Write-Host "  OK ParentMainActivity permissions"
    }
}

Write-Host "Step 10: Patch permissions display (ParentScreenTimeActivity)..." -ForegroundColor Cyan
$pst = "$dst\app\src\parent\java\com\example\myrana\parent\ui\ParentScreenTimeActivity.kt"
if ((Test-Path $pst) -and -not (Select-String -Path $pst -Pattern "textChildPermissions" -Quiet)) {
    $t = Get-Content $pst -Raw -Encoding UTF8
    $t = $t.Replace(
        '    private lateinit var textLastUpdate: TextView',
        "    private lateinit var textLastUpdate: TextView`r`n    private lateinit var textChildPermissions: TextView"
    )
    $t = $t.Replace(
        '        textLastUpdate = findViewById(R.id.textLastUpdate)',
        "        textLastUpdate = findViewById(R.id.textLastUpdate)`r`n        textChildPermissions = findViewById(R.id.textChildPermissions)"
    )
    $old = @'
        textEducationalStats.text = getString(
            R.string.parent_educational_stats,
            d.educationalSeconds / 60,
            d.monitoredSeconds / 60,
        )

        if (d.topAppsToday.isNotEmpty()) {
'@
    $new = @'
        textEducationalStats.text = getString(
            R.string.parent_educational_stats,
            d.educationalSeconds / 60,
            d.monitoredSeconds / 60,
        )
        textChildPermissions.text = ParentPermissionsFormatter.summary(this, d.permissionsOk, d.permissions)

        if (d.topAppsToday.isNotEmpty()) {
'@
    if ($t.Contains($old)) {
        $t = $t.Replace($old, $new)
        [System.IO.File]::WriteAllText($pst, $t, [System.Text.UTF8Encoding]::new($false))
        Write-Host "  OK ParentScreenTimeActivity permissions"
    }
}

Write-Host "Step 11: Patch activity_parent_main.xml (permissions box)..." -ForegroundColor Cyan
$layoutMain = "$dst\app\src\parent\res\layout\activity_parent_main.xml"
if ((Test-Path $layoutMain) -and -not (Select-String -Path $layoutMain -Pattern "textPermissionsMini" -Quiet)) {
    $t = Get-Content $layoutMain -Raw -Encoding UTF8
    $old = @'
            <TextView
                android:id="@+id/textDashboardMini"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:background="#1A4CAF50"
                android:padding="10dp"
                android:textSize="13sp" />

            <Button
                android:id="@+id/btnAddAnotherChild"
'@
    $new = @'
            <TextView
                android:id="@+id/textDashboardMini"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:background="#1A4CAF50"
                android:padding="10dp"
                android:textSize="13sp" />

            <TextView
                android:id="@+id/textPermissionsMini"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:background="#1AFF9800"
                android:padding="10dp"
                android:textSize="12sp" />

            <Button
                android:id="@+id/btnAddAnotherChild"
'@
    if ($t.Contains($old)) {
        $t = $t.Replace($old, $new)
        [System.IO.File]::WriteAllText($layoutMain, $t, [System.Text.UTF8Encoding]::new($false))
        Write-Host "  OK activity_parent_main.xml"
    }
}

Write-Host "Step 12: Patch activity_parent_screen_time.xml (permissions line)..." -ForegroundColor Cyan
$layoutSt = "$dst\app\src\parent\res\layout\activity_parent_screen_time.xml"
if ((Test-Path $layoutSt) -and -not (Select-String -Path $layoutSt -Pattern "textChildPermissions" -Quiet)) {
    $t = Get-Content $layoutSt -Raw -Encoding UTF8
    $old = @'
                <TextView
                    android:id="@+id/textLastUpdate"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:textSize="12sp" />
            </LinearLayout>
        </LinearLayout>

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="@string/parent_dashboard_title"
'@
    $new = @'
                <TextView
                    android:id="@+id/textLastUpdate"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:textSize="12sp" />

                <TextView
                    android:id="@+id/textChildPermissions"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="6dp"
                    android:textSize="12sp" />
            </LinearLayout>
        </LinearLayout>

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="@string/parent_dashboard_title"
'@
    if ($t.Contains($old)) {
        $t = $t.Replace($old, $new)
        [System.IO.File]::WriteAllText($layoutSt, $t, [System.Text.UTF8Encoding]::new($false))
        Write-Host "  OK activity_parent_screen_time.xml"
    }
}

Write-Host ""
Write-Host "DONE. Project path:" -ForegroundColor Green
Write-Host "E:\parent_monitor_project\MYRana" -ForegroundColor Green
Write-Host "Open this folder in Android Studio." -ForegroundColor Green
exit 0

# تطبيق باتش Android — المرحلة 3

القرص كان ممتلئاً أثناء التعديل المباشر على `AndroidStudioProjects\MYRana`.
انسخي الملفات التالية يدوياً:

## 1) صلاحيات الطفل → السيرفر

```
patches/android/PermissionStatusReporter.kt
  → app/src/main/java/com/example/myrana/permissions/PermissionStatusReporter.kt
```

**NetworkModule.kt** — استبدلي `postChildHeartbeat`:
```kotlin
fun postChildHeartbeat(childCode: String, permissions: Map<String, Any?>? = null): Boolean {
    ...
    val payload = mutableMapOf<String, Any?>("child_code" to code, "ts_ms" to System.currentTimeMillis())
    if (!permissions.isNullOrEmpty()) payload["permissions"] = permissions
```

**ScreenTimeSyncHelper.kt**:
```kotlin
import com.example.myrana.permissions.PermissionStatusReporter
...
NetworkModule.postChildHeartbeat(childCode, PermissionStatusReporter.toPayload(context))
```

## 2) شاشة إعدادات ولي الأمر

```
patches/android/ParentSettingsActivity.kt
  → app/src/parent/java/com/example/myrana/parent/ui/ParentSettingsActivity.kt

patches/android/activity_parent_settings.xml
  → app/src/parent/res/layout/activity_parent_settings.xml
```

**AndroidManifest.xml** (parent):
```xml
<activity android:name=".parent.ui.ParentSettingsActivity" android:exported="false" />
```

**ParentMainActivity** — زر:
```kotlin
findViewById<Button>(R.id.btnOpenSettings).setOnClickListener {
    startActivity(Intent(this, ParentSettingsActivity::class.java))
}
```

**activity_parent_main.xml** — أضيفي زر `btnOpenSettings`.

## 3) GuardianApi.kt

راجعي `patches/android/GuardianApi_methods.txt` وأضيفي الدوال والحقول.

## 4) strings_parent.xml

```xml
<string name="parent_settings_title">إعدادات ولي الأمر</string>
<string name="parent_settings_retention_hint">حذف البيانات الأقدم من (أيام — 7 إلى 90)</string>
<string name="parent_settings_daily_email">تفعيل البريد اليومي</string>
<string name="parent_settings_weekly_email">تفعيل البريد الأسبوعي</string>
<string name="parent_settings_alert_sound">تفعيل صوت التنبيه القوي</string>
<string name="parent_btn_save_settings">حفظ الإعدادات</string>
<string name="parent_btn_send_daily_email">إرسال ملخص اليوم الآن</string>
<string name="parent_btn_send_weekly_email">إرسال ملخص الأسبوع الآن</string>
<string name="parent_btn_load_audit">عرض سجل التغييرات</string>
<string name="parent_btn_open_settings">الإعدادات وسجل التغييرات</string>
<string name="parent_audit_empty">لا تغييرات مسجّلة بعد</string>
<string name="parent_permissions_status">الصلاحيات: استخدام %1$s | وصول %2$s | إشعارات %3$s</string>
```

## 5) ParentScreenTimeActivity — عرض الصلاحيات

في `renderDashboard` أضيفي:
```kotlin
private lateinit var textPermissions: TextView
...
val perms = d.permissions
textPermissions.text = getString(
    R.string.parent_permissions_status,
    if (perms["usage"] == true) "✓" else "✗",
    if (perms["accessibility"] == true) "✓" else "✗",
    if (perms["notifications"] == true) "✓" else "✗",
)
```

## 6) رفع السيرفر

```powershell
cd E:\parent_monitor_project
git add server.py patches/
git commit -m "Audit log, guardian settings, permissions in heartbeat"
git push
```

Endpoints جديدة:
- `GET/POST /guardian-settings`
- `GET /audit-log`
- `POST /send-email-summary`

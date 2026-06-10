# MYRana — تقرير الفحص وعقد الربط (API)

## 1. خريطة المشروع

| المكوّن | المسار | التقنية |
|---------|--------|---------|
| السيرفر | `E:\parent_monitor_project\server.py` | Flask + **SQLite** (ليس SQLAlchemy) |
| تطبيق الطفل | `MYRana/app/src/child` + `main` | Kotlin, OkHttp, Room |
| تطبيق الأم | `MYRana/app/src/parent` | Kotlin, OkHttp |
| قاعدة محلية الطفل | `ScreenTimeRepository`, Room | `daily_app_usage`, `screen_time_events` |
| القوائم | `blocklists/catalog.json` | حظر/سماح |
| النشر | Render + GitHub `parental-server` | gunicorn |

## 2. Endpoints الربط والتحقق

| Endpoint | Method | الوظيفة |
|----------|--------|---------|
| `/send-email-code` | POST | OTP بريد ولي الأمر |
| `/verify-email-code` | POST | تحقق البريد |
| `/register-child-device` | POST | تسجيل جهاز الطفل |
| `/send-link-code` | POST | إرسال رمز الربط |
| `/verify-child-device-code` | POST | تحقق رمز الجهاز (اختياري) |
| **`/add-child`** | POST | **ربط نهائي** |
| **`/link-child`** | POST | نفس `/add-child` |
| `/child-link-status` | GET | هل اكتمل الربط؟ (يستعلم الطفل) |
| `/list-children` | GET | قائمة أطفال ولي الأمر |

## 3. Endpoint النهائي للربط

```
POST https://parental-server-4mms.onrender.com/add-child
Header: X-API-KEY: graduation-secret-key
Content-Type: application/json
```

## 4. JSON الذي يرسله Android

```json
{
  "parent_email": "parent.controll.app@gmail.com",
  "child_code": "1DF71288",
  "verification_code": "345195",
  "name": "طفل",
  "guardian_email": "parent.controll.app@gmail.com",
  "email": "parent.controll.app@gmail.com",
  "device_verify_code": "345195",
  "otp": "345195"
}
```

> `child_code` يُرسل **بدون** بادئة `CHILD-` أو معها — السيرفر ينظّفه إلى `1DF71288` للبحث.

## 5. JSON عند النجاح

```json
{
  "success": true,
  "status": "success",
  "message": "Child linked successfully",
  "parent_id": 1,
  "child_id": 5,
  "child_code": "CHILD-1DF71288",
  "child_code_clean": "1DF71288",
  "child_name": "طفل"
}
```

## 6. JSON عند الفشل

```json
{
  "success": false,
  "status": "error",
  "message": "Child not found",
  "error_code": "child_not_found",
  "child_code_input": "CHILD-1DF71288",
  "child_code_clean": "1DF71288"
}
```

```json
{
  "success": false,
  "status": "error",
  "message": "Invalid or expired verification code",
  "error_code": "invalid_verification_code"
}
```

## 7. تنظيف child_code (Flask)

```python
# FIX: normalize child_code to support codes with or without CHILD- prefix
# CHILD-1DF71288 → 1DF71288 في قاعدة البيانات
```

## 8. تعدد الأطفال

- جدول `guardians` → `parent_id`
- جدول `children` → `child_id` + `guardian_email` + `child_code` (فريد)
- `GET /list-children?parent_email=...`
- تطبيق الأم: `GuardianApi.fetchLinkedChildren()`

## 9. وقت الاستخدام

- **الطفل:** `ScreenTimeTracker` + Room → `ScreenTimeSyncHelper` → `POST /screen-time-events`
- **السيرفر:** `screen_time_policies`, `usage_daily`, `child_status`
- **الأم:** `ParentScreenTimeActivity` + `GET /child-dashboard`

## 10. التنبيهات

- `POST /add-alert` من الطفل
- `GET /alerts?child_code=...` للأم
- `POST /send-guardian-message`

## 11. صلاحيات Android (الطفل)

- Usage Access
- Accessibility (ContentFilterAccessibilityService)
- Notifications
- Battery optimization exemption
- شاشة واحدة: `ChildPermissionsActivity` → إعدادات النظام الحقيقية

## 12. تشغيل محلي

```powershell
cd E:\parent_monitor_project
pip install -r requirements.txt
python server.py

cd C:\Users\rannn\AndroidStudioProjects\MYRana
$env:GRADLE_USER_HOME="E:\gradle-home"
.\gradlew assembleChildDebug assembleParentDebug
```

## 13. Render

```powershell
cd E:\parental-server-deploy
git push origin main
```

Environment موصى به:
- `DATA_DIR=/var/data` + Persistent Disk
- `API_KEY`, `RESEND_API_KEY`, `SMTP_*`

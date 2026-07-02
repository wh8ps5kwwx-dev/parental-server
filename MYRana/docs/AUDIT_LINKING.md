# تقرير فحص وربط MYRana

## 1. خريطة المشروع

| المكوّن | المسار | التقنية |
|---------|--------|---------|
| السيرفر | `server.py` | Flask + **SQLite** (ليس SQLAlchemy) |
| تطبيق الطفل/الأم | `MYRana/app/` | Kotlin + OkHttp (ليس Retrofit) |
| تخزين محلي طفل | `data/local/` | Room |
| قوائم الحظر | `blocklists/` | JSON |
| النشر | Render + GitHub | `parental-server` |

## 2. Endpoints الربط (الأساسية)

| Method | Path | الوظيفة |
|--------|------|---------|
| POST | `/send-email-code` | رمز تحقق بريد ولي الأمر |
| POST | `/verify-email-code` | تأكيد البريد |
| POST | `/register-child-device` | تسجيل جهاز الطفل |
| POST | `/send-link-code` | إرسال رمز الربط |
| POST | `/verify-child-device-code` | تحقق اختياري من رمز الجهاز |
| **POST** | **`/add-child`** أو **`/link-child`** | **الربط النهائي** |
| GET | `/child-link-status` | هل اكتمل الربط؟ (يستعلم الطفل) |
| GET | `/list-children` | قائمة أطفال ولي الأمر |

## 3. JSON المطلوب من Android (الربط)

```json
{
  "parent_email": "parent.controll.app@gmail.com",
  "child_code": "1DF71288",
  "verification_code": "345195"
}
```

> يقبل Flask أيضاً: `email`, `guardian_email`, `otp`, `device_verify_code`, `childCode`.

## 4. JSON عند النجاح

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

## 5. JSON عند الفشل

```json
{
  "success": false,
  "status": "error",
  "message": "Child not found",
  "error_code": "child_not_found",
  "child_code_input": "CHILD-1DF71288",
  "child_code_clean": "1DF71288",
  "detail_ar": "الكود غير مسجّل على السيرفر — من جوال الطفل اضغطي «تسجيل الجهاز» ثم أعيدي الربط"
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

## 6. تطبيع child_code

```
# FIX: normalize child_code to support codes with or without CHILD- prefix
CHILD-1DF71288  →  1DF71288  (مفتاح قاعدة البيانات)
```

## 7. تعدد الأطفال

- جدول `guardians` (parent_id)
- جدول `children` (child_id لكل ربط)
- `GET /list-children?parent_email=...`
- تطبيق الأم: `GuardianApi.fetchLinkedChildren()`

## 8. وقت الاستخدام والمراقبة (موجود)

- `ScreenTimeTracker` / `ScreenTimeEnforcer` — عداد لكل تطبيق
- Room: `daily_app_usage`, `screen_time_events`
- `ScreenTimeSyncHelper` — مزامنة مع Flask
- `/screen-time-policy`, `/child-dashboard`, `/daily-report`

## 9. صلاحيات الطفل (موجود)

- `ChildPermissionsActivity` + `SystemPermissions` — Usage, Accessibility, إشعارات, بطارية

## 10. تشغيل محلي

```powershell
cd E:\parent_monitor_project
pip install -r requirements.txt
python server.py
```

```powershell
cd MYRana
$env:GRADLE_USER_HOME="E:\gradle-home"
.\gradlew assembleChildDebug assembleParentDebug
```

## 11. النشر على Render

```powershell
cd E:\parental-server-deploy
git add server.py MYRana/
git commit -m "Audit: child_code linking fixes and multi-child API"
git push origin main
```

متغيرات Render: `API_KEY`, `RESEND_API_KEY`, `DATA_DIR=/var/data` (قرص دائم).

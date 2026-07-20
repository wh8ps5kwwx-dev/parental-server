# MYRana Flutter — دليل التشغيل بالعربية

تحويل كامل لتطبيق **MYRana** من Kotlin إلى **Flutter/Dart** مع نفس سيرفر الرقابة الأبوية.

> مشروع Kotlin الأصلي **`MYRana/` لم يُحذف** — يبقى مرجعاً ونسخة احتياطية.

---

## الرقابة كيف تشتغل

### الفكرة باختصار

1. **ولي الأمر** يرسل أمر حظر/سماح عبر السيرفر (`/send-command`) ويُحدَّث سجل السياسة في قاعدة البيانات.
2. **جهاز الطفل** يسحب السياسة كل ~60 ثانية عبر الخدمة الأمامية، ويستقبل الأوامر كل ~20 ثانية عبر `/get-command`.
3. **إنفاذ أندرويد** (أصلي):
   - `ForegroundMonitorService` — حلقة مراقبة + مزامنة
   - `ContentFilterAccessibilityService` — حظر تطبيقات + فلترة مواقع/يوتيوب
   - `UsageStats` — رفع وقت الاستخدام للتقارير
   - `BootReceiver` — إعادة تشغيل المراقبة بعد إعادة التشغيل

### على هاتف الطفل (أندرويد) — خطوات إلزامية

1. ثبّتي التطبيق واختاري **أنا جهاز الطفل**.
2. سجّلي الجهاز → يظهر كود `CHILD-...`.
3. بعد ربط ولي الأمر: افتحي **الصلاحيات المطلوبة** وفعّلي بالترتيب:
   - **Usage Access** (اضغطي الصف → فعّلي MYRana Flutter)
   - **Accessibility Service** (اضغطي الصف → فعّلي الخدمة)
   - **تشغيل المراقبة** (Foreground Service — إشعار ثابت)
4. اتركي التطبيق في الخلفية؛ لا تفرضي إيقاف البطارية على التطبيق إن أمكن.

### على هاتف ولي الأمر

1. اختاري **أنا ولي الأمر** → دخول OTP بالبريد.
2. **ربط طفل** بكود `CHILD-...` من جهاز الطفل.
3. من **الحظر** أو **التطبيقات**: احظري حزمة مثل `com.instagram.android`.
4. خلال دقيقة تقريباً يُنفَّذ الحظر على جهاز الطفل (أو فوراً عند استلام الأمر).

### اختبار سريع على جهازين

| الخطوة | هاتف ولي الأمر | هاتف الطفل |
|--------|----------------|------------|
| 1 | دخول + ربط بالكود | تسجيل + انتظار الربط |
| 2 | — | تفعيل Usage + Accessibility + تشغيل المراقبة |
| 3 | حظر تطبيق معروف | حاول فتح التطبيق → يُغلق / شاشة تحذير |
| 4 | إلغاء الحظر | التطبيق يعود يعمل خلال ~دقيقة |
| 5 | طلب تحديث الاستخدام من التقارير | Usage Access مفعّل → تظهر بيانات |

---

## المتطلبات

- Flutter SDK 3.3 أو أحدث
- **أندرويد:** جهاز أو محاكي (API 21+)
- **iOS:** Mac مع Xcode (واجهة + API فقط — بلا حظر نظام)
- اتصال إنترنت للسيرفر

---

## التشغيل السريع

```bash
cd E:\parent_monitor_project\myrana_flutter
flutter pub get
flutter run
```

بناء APK:

```bash
flutter build apk
```

الملف الناتج غالباً: `build/app/outputs/flutter-apk/app-release.apk`

---

## اختيار الدور (بدل نكهات Gradle)

| في Kotlin | في Flutter |
|-----------|------------|
| `parent` flavor | شاشة البداية → **أنا ولي الأمر** |
| `child` flavor | شاشة البداية → **أنا جهاز الطفل** |

الدور يُحفظ في `shared_preferences` عبر `AppSession`.

---

## سير العمل — ولي الأمر

1. **دخول OTP:** `send-email-code` ثم `verify-email-code`
2. **لوحة التحكم:** مؤشرات من `child-dashboard`
3. **ربط طفل:** كود `CHILD-...` → `send-link-code` → `add-child`
4. **إدارة:** تطبيقات، حظر، وقت شاشة، تنبيهات، تقارير، رسائل، إعدادات

### الشاشات (`lib/screens/parent/`)

| الشاشة | الملف | API |
|--------|-------|-----|
| دخول OTP | `parent_login_screen.dart` | send/verify-email-code |
| لوحة التحكم | `parent_home_screen.dart` | child-dashboard |
| ربط طفل | `parent_link_screen.dart` | send-link-code, add-child, restore-link |
| الأطفال | `parent_children_screen.dart` | list-children |
| التطبيقات | `parent_apps_screen.dart` | child-installed-apps, send-command |
| الحظر | `parent_block_screen.dart` | send-command, apply-default-blocklist |
| وقت الشاشة | `parent_screen_time_screen.dart` | screen-time-policy |
| التنبيهات | `parent_alerts_screen.dart` | alerts |
| التقارير | `parent_reports_screen.dart` | daily-report, weekly-chart |
| رسالة | `parent_message_screen.dart` | send-guardian-message |
| الإعدادات | `parent_settings_screen.dart` | guardian-settings, audit-log |

---

## سير العمل — جهاز الطفل

1. **تسجيل:** `register-child-device` → كود `CHILD-...`
2. **انتظار:** `child-link-status` كل 5 ثوانٍ
3. **بعد الربط:** نبضة `child-heartbeat` + أوامر `get-command`
4. **صلاحيات أندرويد:** Usage Access + Accessibility + Foreground Service
5. **الأكاديمية:** تحديات ومدينة تعلم

### أوامر ولي الأمر التي ينفّذها الطفل (أندرويد)

| الأمر | التنفيذ |
|-------|---------|
| `block_app` / `freeze_app` | إضافة للحظر المحلي + إنفاذ فوري |
| `unblock_app` | إزالة من الحظر المحلي + تحديث سياسة السيرفر |
| `block_site` / `unblock_site` | حظر/إلغاء موقع في الكاش + Accessibility |
| `allow` | مسح كل الحظر المحلي والسيرفر |
| `request_usage` | رفع Usage Stats → `upload-usage` |
| `sync_installed_apps` | رفع قائمة التطبيقات → `sync-child-apps` |
| `guardian_message` | حوار على الشاشة |

---

## السيرفر

| المفتاح | القيمة |
|--------|--------|
| ROOT | `https://parental-server-4mms.onrender.com/` |
| API | `https://parental-server-4mms.onrender.com/api/` |
| Header | `X-API-KEY: graduation-secret-key` |

الملف: `lib/config/server_config.dart`

> ملاحظة: إصلاحات `unblock_app` / `unblock_site` موجودة في `server.py` المحلي — انشري التحديث على Render إن لم يكن منشوراً بعد.

---

## ما يعمل أين؟

| الميزة | Android | iOS | Web/Desktop |
|--------|---------|-----|-------------|
| واجهة ولي الأمر + REST | ✅ | ✅ | ⚠️ تجريبي |
| تسجيل/ربط الطفل | ✅ | ✅ | ⚠️ |
| حظر التطبيقات فعلياً | ✅ | ❌ | ❌ |
| فلترة مواقع/يوتيوب | ✅ | ❌ | ❌ |
| Usage Stats / تقارير | ✅ | ❌ | ❌ |
| Foreground monitoring | ✅ | ❌ | ❌ |
| إعادة تشغيل بعد Boot | ✅ | ❌ | ❌ |
| الأكاديمية | ✅ | ✅ | ✅ |

---

## الطبقة الأصلية (MethodChannel)

| القناة | Dart | Android (الحي) |
|--------|------|----------------|
| accessibility | `enforcement_channel.dart` | `com.example.myrana.enforcement.AccessibilityHelper` |
| usage_stats | `enforcement_channel.dart` | `UsageStatsCollectorLite` |
| enforcement | `enforcement_channel.dart` | `EnforcementEngine` + `ForegroundMonitorService` + `PolicyFilterCache` |

ملفات Kotlin الحية:

```
android/.../com/example/myrana/enforcement/
android/.../com/example/myrana/service/
android/.../com/example/myrana/worker/
android/.../com/example/myrana/network/
android/.../com/example/myrana/receiver/BootReceiver.kt
```

---

## هيكل المشروع

```
myrana_flutter/
├── lib/
│   ├── main.dart
│   ├── config/server_config.dart
│   ├── data/api/          # GuardianApi + ChildApi
│   ├── session/           # shared_preferences
│   ├── screens/parent/    # شاشات ولي أمر
│   ├── screens/child/     # شاشات طفل
│   └── native/            # MethodChannel Dart
├── android/               # تنفيذ أصلي كامل
├── ios/                   # stub + ملاحظات Screen Time
└── test/
```

---

## التحليل والاختبار

```bash
flutter analyze
flutter test
flutter build apk
```

---

## الفروق عن Kotlin الأصلي

1. **Room/SQLite** → كاش SharedPreferences (`PolicyFilterCache`) بدل Room كامل
2. **iOS** → واجهة + API؛ لا إنفاذ صلب
3. **MYRana/** → يبقى المرجع الكامل للميزات المتقدمة (Media scan، outbox، إلخ)

---

## استكشاف الأخطاء

| المشكلة | الحل |
|---------|------|
| الطفل غير موجود على السيرفر | سجّلي من جهاز الطفل أولاً |
| رمز الربط خاطئ | أرسلي رمزاً جديداً من «إرسال رمز الربط» |
| الحظر لا يعمل | فعّلي Usage Access + Accessibility + تشغيل المراقبة |
| الحظر يبقى بعد «إلغاء» | تأكدي أن السيرفر المحدَّث منشور (يدعم `unblock_app`) وانتظري مزامنة السياسة |
| لا تقارير استخدام | اطلبي «تحديث الاستخدام» + صلاحية Usage Access |
| بعد إعادة التشغيل توقفت المراقبة | تأكدي أن التطبيق ليس مقيداً بالبطارية؛ BootReceiver يعيد التشغيل إن وُجد child_code |

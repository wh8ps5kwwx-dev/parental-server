# MYRana Flutter — دليل التشغيل بالعربية

**التطبيقان كاملان فلاتر ✅**  
نكهتان أندرويد مثل Kotlin: **حماية الأطفال** (ولي الأمر) + **أكاديمية العباقرة** (طفل) — APKان منفصلان.

> مشروع Kotlin الأصلي **`MYRana/` لم يُحذف** — يبقى مرجعاً ونسخة احتياطية.

---

## حالة المشروع

| الطبقة | الحالة |
|--------|--------|
| نكهات Gradle `parent` / `child` | ✅ تطبيقان (applicationId + اسم مختلف) |
| واجهات ولي الأمر + الطفل | ✅ كاملة |
| REST API (Guardian + Child) | ✅ كاملة |
| MethodChannel + إنفاذ أندرويد | ✅ كامل (**نكهة الطفل فقط** في المانيفست) |
| Accessibility / Usage Stats / Foreground / Boot | ✅ كامل — مُعلَن في نكهة `child` فقط |
| حظر تطبيق + موقع + السماح + جدولة تجميد | ✅ كامل (يُنفَّذ على طفل Android) |
| تنبيهات فتح تطبيقات المراسلة | ✅ كامل (Android child) |
| نسبة البطارية + صلاحيات في النبضة | ✅ كامل |
| بناء APK parent + child | ✅ في `releases/` |
| ولي أمر على iPhone | ✅ كامل عبر السيرفر |
| طفل على iPhone | ✅ واجهة + ربط + أكاديمية + FamilyControls |
| GPS | ❌ غير موجود (متعمد — لا اختراع) |

---

## سيناريوهات التشغيل

### سيناريو كامل مدعوم (موصى به)

**ولي أمر على iPhone (أو Android) + طفل على Android = رقابة كاملة**

- ولي الأمر يرسل أوامر الحظر/السماح/الحدود من أي جهاز عبر REST.
- جهاز الطفل Android يستقبل الأوامر وينفّذها محلياً (Accessibility + Usage Stats + Foreground).
- هذا ليس حلاً جزئياً — هذا نموذج النشر الكامل للمنتج.

### طفل على iPhone

- يعمل دائماً: الواجهة، التسجيل، الربط، النبضة، الأكاديمية، رسائل ولي الأمر، قراءة البطارية.
- يعمل بعد تفويض FamilyControls + اختيار التطبيقات من منتقي آبل: درع ManagedSettings عند مزامنة سياسة الحظر من السيرفر.
- لا يعمل مثل أندرويد حرفياً: لا UsageStats/Accessibility؛ أسماء حزم أندرويد لا تُحوَّل تلقائياً إلى تطبيقات iOS — يجب اختيار التطبيقات عبر `FamilyActivityPicker`.
- حدود الوقت اليومية وتقارير الاستخدام التفصيلية تحتاج امتداد DeviceActivityMonitor على Mac (الشيفرة جاهزة في `ios/DeviceActivityMonitorExtension/`).
- بناء IPA يتطلب macOS + Xcode + حساب Apple Developer مع تفعيل صلاحية Family Controls.

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
   - `AppUsageAlertHelper` — تنبيه عند فتح تطبيقات المراسلة/التواصل

### على هاتف الطفل (أندرويد) — خطوات إلزامية

1. ثبّتي **`app-child-release.apk`** (أكاديمية العباقرة).
2. سجّلي الجهاز → يظهر كود `CHILD-...`.
3. بعد ربط ولي الأمر: افتحي **الصلاحيات المطلوبة** وفعّلي بالترتيب:
   - **Usage Access** (اضغطي الصف → فعّلي أكاديمية العباقرة)
   - **Accessibility Service** (اضغطي الصف → فعّلي الخدمة)
   - **تجاهل تحسين البطارية** (موصى به)
   - **صلاحية الكاميرا / الميكروفون** (اختيارية): طلب إذن النظام لتطبيق الطفل وتبليغ الحالة لولي الأمر — **ليست** تصويراً أو تنصتًا صامتاً في الخلفية، ولا تكشف متى تفتح تطبيقات أخرى الكاميرا/الميكروفون (قيود أندرويد)
   - **تشغيل المراقبة** (Foreground Service — إشعار ثابت)
4. اتركي التطبيق في الخلفية؛ لا تفرضي إيقاف البطارية على التطبيق إن أمكن.

> **ملاحظة:** حالة `camera` و`microphone` تُرسل مع نبضة الطفل (`permissions` JSON) وتظهر في لوحة ولي الأمر. لا تُحتسب ضمن الصلاحيات الإلزامية (`mandatory_ok`).

### على هاتف ولي الأمر (Android أو iPhone)

1. على أندرويد: ثبّتي **`app-parent-release.apk`** (حماية الأطفال) → دخول OTP بالبريد.
2. **ربط طفل** بكود `CHILD-...` من جهاز الطفل (تحقق من الجهاز + إتمام الربط).
3. من **الحظر** أو **التطبيقات**: احظري حزمة أو موقعاً، أو اضغطي «السماح» لإلغاء كل الحظر.
4. خلال دقيقة تقريباً يُنفَّذ الحظر على جهاز الطفل Android (أو فوراً عند استلام الأمر).

### اختبار سريع على جهازين

| الخطوة | هاتف ولي الأمر (أي منصة) | هاتف الطفل (Android) |
|--------|--------------------------|----------------------|
| 1 | دخول + ربط بالكود | تسجيل + انتظار الربط |
| 2 | — | تفعيل Usage + Accessibility + تشغيل المراقبة |
| 3 | حظر تطبيق معروف | حاول فتح التطبيق → يُغلق / شاشة تحذير |
| 4 | حظر موقع | فتح المتصفح للموقع → فلترة Accessibility |
| 5 | إلغاء الحظر / السماح | التطبيق يعود يعمل خلال ~دقيقة |
| 6 | طلب تحديث الاستخدام من التقارير | Usage Access مفعّل → تظهر بيانات |

---

## المتطلبات

- Flutter SDK 3.3 أو أحدث
- **أندرويد:** جهاز أو محاكي (API 21+) — compileSdk/targetSdk 35
- **iOS:** Mac مع Xcode 15+ — ولي أمر كامل؛ طفل: FamilyControls + ManagedSettings (iOS 15+)
- اتصال إنترنت للسيرفر

---

## التشغيل السريع

```bash
cd E:\parent_monitor_project\myrana_flutter
flutter pub get

# ولي الأمر
flutter run --flavor parent -t lib/main.dart

# جهاز الطفل
flutter run --flavor child -t lib/main.dart
```

### بناء APKان منفصلان (موصى به)

```bash
flutter build apk --flavor parent --release
flutter build apk --flavor child --release
```

انسخِ المخرجات محلياً إلى `releases/` (لا تُرفع إلى GitHub — حجمها كبير):

```bash
copy build\app\outputs\flutter-apk\app-parent-release.apk releases\
copy build\app\outputs\flutter-apk\app-child-release.apk releases\
```

| النكهة | applicationId | اسم التطبيق | مسار البناء | نسخة محلية |
|--------|---------------|-------------|-------------|------------|
| parent | `com.example.myrana.parent` | حماية الأطفال | `build/app/outputs/flutter-apk/app-parent-release.apk` | `releases/app-parent-release.apk` |
| child | `com.example.myrana.child` | أكاديمية العباقرة | `build/app/outputs/flutter-apk/app-child-release.apk` | `releases/app-child-release.apk` |

النكهة تُقرأ تلقائياً من `BuildConfig.FLAVOR` عبر MethodChannel (`getAppFlavor`).

---

## نكهات Gradle (مثل Kotlin)

| النكهة | applicationId | الاسم | المانيفست |
|--------|---------------|-------|-----------|
| `parent` | `com.example.myrana.parent` | حماية الأطفال | بدون Accessibility / Foreground / Boot |
| `child` | `com.example.myrana.child` | أكاديمية العباقرة | كامل الإنفاذ الأصلي |

الدور يُقفل حسب النكهة في `AppSession` + `AppFlavor`.  
شاشة اختيار الدور تبقى للتطوير/iOS فقط عندما لا توجد نكهة.

---

## سير العمل — ولي الأمر

1. **دخول OTP:** `send-email-code` ثم `verify-email-code`
2. **لوحة التحكم:** مؤشرات من `child-dashboard`
3. **ربط طفل:** كود `CHILD-...` → `send-link-code` → `verify-child-device-code` → `add-child`
4. **إدارة:** تطبيقات، حظر (تطبيق/موقع/سماح)، وقت شاشة، تنبيهات، تقارير، رسائل، إعدادات
5. **التحقق من المواقع:** من لوحة ولي الأمر → «التحقق من موقع» → أدخلي رابطاً أو نطاقاً → تظهر هل محظور في سياسة الطفل، وهل موجود في كتالوج الحظر الافتراضي، مع شرح قصير بالعربية

### الشاشات (`lib/screens/parent/`)

| الشاشة | الملف | API |
|--------|-------|-----|
| دخول OTP | `parent_login_screen.dart` | send/verify-email-code |
| لوحة التحكم | `parent_home_screen.dart` | child-dashboard |
| ربط طفل | `parent_link_screen.dart` | send-link-code, verify-child-device-code, add-child, restore-link |
| الأطفال | `parent_children_screen.dart` | list-children |
| التطبيقات | `parent_apps_screen.dart` | child-installed-apps, send-command |
| الحظر | `parent_block_screen.dart` | block/unblock app+site, allow, schedule, blocklist |
| التحقق من موقع | `parent_url_check_screen.dart` | POST `/api/check-url` (+ احتياطي: policy + catalog) |
| وقت الشاشة | `parent_screen_time_screen.dart` | screen-time-policy |
| التنبيهات | `parent_alerts_screen.dart` | alerts |
| التقارير | `parent_reports_screen.dart` | daily-report, weekly-chart |
| رسالة | `parent_message_screen.dart` | send-guardian-message |
| الإعدادات | `parent_settings_screen.dart` | guardian-settings, audit-log |

---

## سير العمل — جهاز الطفل

1. **تسجيل:** `register-child-device` → كود `CHILD-...`
2. **انتظار:** `child-link-status` كل 5 ثوانٍ
3. **بعد الربط:** نبضة `child-heartbeat` (بطارية + صلاحيات) + أوامر `get-command`
4. **صلاحيات أندرويد:** Usage Access + Accessibility + بطارية + Foreground Service
5. **الأكاديمية:** تحديات ومدينة تعلم (نجوم + مباني محفوظة)

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

| الميزة | طفل Android | ولي أمر iPhone | طفل iPhone |
|--------|-------------|----------------|------------|
| واجهة + REST | ✅ | ✅ كامل | ✅ |
| تسجيل/ربط | ✅ | ✅ | ✅ |
| إرسال أوامر حظر عبر السيرفر | — | ✅ | — |
| حظر التطبيقات فعلياً | ✅ | يُنفَّذ على الطفل | ✅ بعد FamilyControls + منتقي التطبيقات |
| فلترة مواقع/يوتيوب | ✅ | يُنفَّذ على الطفل | جزئي عبر webDomain tokens من المنتقي |
| Usage Stats / تقارير | ✅ | يعرض عبر API | يحتاج DeviceActivityReport (لاحقاً) |
| Foreground monitoring | ✅ | — | ✅ startMonitoring + درع (امتداد اختياري) |
| الأكاديمية | ✅ | — | ✅ |

---

## الطبقة الأصلية (MethodChannel)

| القناة | Dart | Android | iOS |
|--------|------|---------|-----|
| accessibility | `enforcement_channel.dart` | `AccessibilityHelper` | يعكس حالة FamilyControls |
| usage_stats | `enforcement_channel.dart` | `UsageStatsCollectorLite` | يعكس التفويض (تقارير Report لاحقاً) |
| enforcement | `enforcement_channel.dart` | `EnforcementEngine` | `FamilyControlsEnforcer` + ManagedSettings |

ملفات Kotlin الحية:

```
android/.../com/example/myrana/enforcement/
android/.../com/example/myrana/service/
android/.../com/example/myrana/worker/
android/.../com/example/myrana/network/
android/.../com/example/myrana/receiver/BootReceiver.kt
android/.../com/example/myrana/util/BatteryLevelHelper.kt
```

### FamilyControls (iOS — مسار حقيقي)

الملفات:

- `ios/Runner/AppDelegate.swift` — MethodChannels
- `ios/Runner/FamilyControlsEnforcer.swift` — `AuthorizationCenter` + `ManagedSettingsStore` + `syncPolicy` + درع عند وجود tokens
- `ios/Shared/MyranaAppGroupStore.swift` — App Group مشترك مع الامتداد
- `ios/Runner/Runner.entitlements` — Family Controls + App Groups
- `ios/DeviceActivityMonitorExtension/` — امتداد جاهز الشيفرة (يُضاف الهدف على Mac)

#### قائمة تحقق Mac / حساب آبل (مرقّمة)

1. انضمّي لـ **Apple Developer Program** (حساب مدفوع).
2. على [developer.apple.com](https://developer.apple.com) → Certificates, Identifiers & Profiles → Identifiers → App ID (`com.example.myranaFlutter`) → فعّلي **Family Controls**.
3. أنشئي App Group: `group.com.example.myranaFlutter` واربطيه بـ App ID الخاص بـ Runner (ولاحقاً بـ App ID الامتداد).
4. لتوزيع TestFlight/App Store: اطلبي موافقة Apple على صلاحية Family Controls.
5. على Mac افتحي `ios/Runner.xcworkspace` في Xcode 15+.
6. Runner target → **Signing & Capabilities** → ‎+ Capability → **Family Controls** و **App Groups** (يطابق `Runner.entitlements`).
7. اختاري Team + Bundle ID صحيحين؛ Signing Automatic.
8. (لحدود الوقت اليومية) أضيفي Target من نوع **Device Activity Monitor Extension** — اتبعي الخطوات المرقّمة في `ios/DeviceActivityMonitorExtension/README.md`.
9. ابنِي على جهاز iPhone حقيقي: `flutter build ios` أو `flutter build ipa` (لا يُبنى IPA من Windows).

#### على جهاز الطفل (iPhone)

1. الصلاحيات → اطلبي إذن FamilyControls.
2. اختاري التطبيقات من منتقي آبل (`FamilyActivityPicker`).
3. شغّلي المراقبة ثم «مزامنة السياسة» بعد أن يضع ولي الأمر حظراً من لوحة التحكم.

**صادق:** درع ManagedSettings يعمل على التطبيقات المختارة محلياً عندما تكون سياسة السيرفر غير فارغة والرموز محفوظة. امتداد DeviceActivity للعتبات اليومية جاهز كشيفرة ويُضمَّن الهدف على Mac فقط.

---

## هيكل المشروع

```
myrana_flutter/
├── lib/
│   ├── main.dart
│   ├── config/app_flavor.dart   # نكهة parent/child
│   ├── config/server_config.dart
│   ├── data/api/          # GuardianApi + ChildApi
│   ├── session/           # shared_preferences
│   ├── screens/parent/    # شاشات ولي أمر
│   ├── screens/child/     # شاشات طفل
│   └── native/            # MethodChannel Dart
├── android/
│   └── app/src/
│       ├── main/          # مشترك
│       ├── parent/        # مانيفست بلا Accessibility
│       └── child/         # Accessibility + FGS + Boot
├── ios/
├── releases/              # app-parent-release.apk + app-child-release.apk
└── test/
```

---

## التحليل والاختبار

```bash
flutter analyze
flutter test
flutter build apk --flavor parent --release
flutter build apk --flavor child --release
```

---

## الفروق عن Kotlin الأصلي

1. **Room/SQLite** → كاش SharedPreferences (`PolicyFilterCache`) بدل Room كامل
2. **iOS** → ولي أمر كامل عبر السيرفر؛ طفل مع مسار Screen Time الحقيقي
3. **MYRana/** → يبقى المرجع الكامل للميزات المتقدمة الاختيارية (Media scan، outbox Room)
4. **GPS** → غير منفّذ في المشروعين — لا يُذكر كميزة
5. **نكهات Gradle** → نفس فكرة Kotlin (`parent` / `child`) بأسماء وأرقام حزم مطابقة

---

## استكشاف الأخطاء

| المشكلة | الحل |
|---------|------|
| الطفل غير موجود على السيرفر | سجّلي من جهاز الطفل أولاً |
| رمز الربط خاطئ | أرسلي رمزاً جديداً من «إرسال رمز الربط» |
| الحظر لا يعمل | ثبّتي APK الطفل + Usage Access + Accessibility + تشغيل المراقبة |
| الحظر يبقى بعد «إلغاء» | تأكدي أن السيرفر المحدَّث منشور (يدعم `unblock_app`) وانتظري مزامنة السياسة |
| لا تقارير استخدام | اطلبي «تحديث الاستخدام» + صلاحية Usage Access على Android |
| بعد إعادة التشغيل توقفت المراقبة | فعّلي تجاهل تحسين البطارية؛ BootReceiver يعيد التشغيل إن وُجد child_code |
| طفل على iPhone بلا درع | افتحي الصلاحيات → FamilyControls → منتقي التطبيقات → مزامنة |
| `flutter run` يطلب flavor | أضيفي `--flavor parent` أو `--flavor child` |

---

## الحكم النهائي

**التطبيقان كاملان فلاتر ✅** — نكهتان أندرويد (`app-parent-release.apk` + `app-child-release.apk`) بنفس فكرة Kotlin، مع إنفاذ أصلي على جهاز الطفل فقط.

**طفل على iPhone:** واجهة وربط وأكاديمية + Screen Time. Android لم يُكسر؛ `MYRana/` لم يُحذف؛ لا GPS.

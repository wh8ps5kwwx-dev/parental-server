# MYRana Flutter — دليل التشغيل بالعربية

**المنتج كامل للسيناريو المدعوم ✅**  
ولي أمر (Android أو iPhone) + طفل على **Android** = رقابة كاملة عبر السيرفر والإنفاذ الأصلي.

> مشروع Kotlin الأصلي **`MYRana/` لم يُحذف** — يبقى مرجعاً ونسخة احتياطية.

---

## حالة المشروع

| الطبقة | الحالة |
|--------|--------|
| واجهات ولي الأمر + الطفل | ✅ كاملة (Android + iOS) |
| REST API (Guardian + Child) | ✅ كاملة |
| MethodChannel + إنفاذ أندرويد | ✅ كامل |
| Accessibility / Usage Stats / Foreground / Boot | ✅ كامل (Android) |
| حظر تطبيق + موقع + السماح + جدولة تجميد | ✅ كامل (يُنفَّذ على طفل Android) |
| تنبيهات فتح تطبيقات المراسلة | ✅ كامل (Android child) |
| نسبة البطارية + صلاحيات في النبضة | ✅ كامل |
| بناء APK debug + release | ✅ في `releases/` |
| ولي أمر على iPhone | ✅ كامل عبر السيرفر (يتحكم بطفل Android أو iOS بعد FamilyControls) |
| طفل على iPhone | ✅ واجهة + ربط + أكاديمية + مسار FamilyControls/ManagedSettings الحقيقي |
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

1. ثبّتي التطبيق واختاري **أنا جهاز الطفل**.
2. سجّلي الجهاز → يظهر كود `CHILD-...`.
3. بعد ربط ولي الأمر: افتحي **الصلاحيات المطلوبة** وفعّلي بالترتيب:
   - **Usage Access** (اضغطي الصف → فعّلي MYRana Flutter)
   - **Accessibility Service** (اضغطي الصف → فعّلي الخدمة)
   - **تجاهل تحسين البطارية** (موصى به)
   - **تشغيل المراقبة** (Foreground Service — إشعار ثابت)
4. اتركي التطبيق في الخلفية؛ لا تفرضي إيقاف البطارية على التطبيق إن أمكن.

### على هاتف ولي الأمر (Android أو iPhone)

1. اختاري **أنا ولي الأمر** → دخول OTP بالبريد.
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
flutter run
```

بناء APK:

```bash
flutter build apk --debug
flutter build apk --release
```

الملفات الناتجة:

- `build/app/outputs/flutter-apk/app-debug.apk`
- `build/app/outputs/flutter-apk/app-release.apk`
- نسخة جاهزة في `releases/flutter-app-debug.apk` و `releases/flutter-app-release.apk`

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
3. **ربط طفل:** كود `CHILD-...` → `send-link-code` → `verify-child-device-code` → `add-child`
4. **إدارة:** تطبيقات، حظر (تطبيق/موقع/سماح)، وقت شاشة، تنبيهات، تقارير، رسائل، إعدادات

### الشاشات (`lib/screens/parent/`)

| الشاشة | الملف | API |
|--------|-------|-----|
| دخول OTP | `parent_login_screen.dart` | send/verify-email-code |
| لوحة التحكم | `parent_home_screen.dart` | child-dashboard |
| ربط طفل | `parent_link_screen.dart` | send-link-code, verify-child-device-code, add-child, restore-link |
| الأطفال | `parent_children_screen.dart` | list-children |
| التطبيقات | `parent_apps_screen.dart` | child-installed-apps, send-command |
| الحظر | `parent_block_screen.dart` | block/unblock app+site, allow, schedule, blocklist |
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
│   ├── config/server_config.dart
│   ├── data/api/          # GuardianApi + ChildApi
│   ├── session/           # shared_preferences
│   ├── screens/parent/    # شاشات ولي أمر (تعمل على iOS)
│   ├── screens/child/     # شاشات طفل (تعمل على iOS)
│   └── native/            # MethodChannel Dart
├── android/               # تنفيذ أصلي كامل
├── ios/                   # FamilyControls + ManagedSettings + DeviceActivity (شيفرة امتداد جاهزة)
├── releases/              # APK debug + release
└── test/
```

---

## التحليل والاختبار

```bash
flutter analyze
flutter test
flutter build apk --debug
flutter build apk --release
```

---

## الفروق عن Kotlin الأصلي

1. **Room/SQLite** → كاش SharedPreferences (`PolicyFilterCache`) بدل Room كامل
2. **iOS** → ولي أمر كامل عبر السيرفر؛ طفل مع مسار Screen Time الحقيقي (يحتاج حساب مطور + تفويض على الجهاز)
3. **MYRana/** → يبقى المرجع الكامل للميزات المتقدمة الاختيارية (Media scan، outbox Room)
4. **GPS** → غير منفّذ في المشروعين — لا يُذكر كميزة
5. **APK واحد** بدور وقت التشغيل بدل نكهتي Gradle

---

## استكشاف الأخطاء

| المشكلة | الحل |
|---------|------|
| الطفل غير موجود على السيرفر | سجّلي من جهاز الطفل أولاً |
| رمز الربط خاطئ | أرسلي رمزاً جديداً من «إرسال رمز الربط» |
| الحظر لا يعمل | تأكدي أن جهاز الطفل Android + Usage Access + Accessibility + تشغيل المراقبة |
| الحظر يبقى بعد «إلغاء» | تأكدي أن السيرفر المحدَّث منشور (يدعم `unblock_app`) وانتظري مزامنة السياسة |
| لا تقارير استخدام | اطلبي «تحديث الاستخدام» + صلاحية Usage Access على Android |
| بعد إعادة التشغيل توقفت المراقبة | فعّلي تجاهل تحسين البطارية؛ BootReceiver يعيد التشغيل إن وُجد child_code |
| طفل على iPhone بلا درع | افتحي الصلاحيات → FamilyControls → منتقي التطبيقات → مزامنة؛ وتأكدي من تفعيل الصلاحية في حساب المطوّر |

---

## الحكم النهائي

**المنتج كامل ✅** لسيناريو النشر المدعوم: ولي أمر على أي جهاز (بما فيه iPhone) + طفل على Android مع إنفاذ أصلي.

**طفل على iPhone:** واجهة وربط وأكاديمية كاملة + مسار Screen Time حقيقي (`AuthorizationCenter` / `ManagedSettingsStore` + App Group). لتفعيل الحظر على الجهاز: حساب Apple Developer + صلاحية Family Controls في Xcode + تفويض المستخدم + اختيار التطبيقات. شيفرة DeviceActivityMonitor للعتبات اليومية مكتملة وتُضاف كهدف امتداد على Mac. Android لم يُكسر؛ `MYRana/` لم يُحذف؛ لا GPS.

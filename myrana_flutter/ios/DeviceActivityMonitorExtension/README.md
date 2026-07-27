# Device Activity Monitor Extension

شيفرة الامتداد **مكتملة وظيفياً** (إعادة تطبيق الدرع + عتبة يومية).  
هدف Xcode للإضافة يجب إنشاؤه على **Mac** — تعديل `project.pbxproj` لامتداد كامل من Windows سهل الكسر.

## ماذا يعمل بدون الامتداد؟

1. `AuthorizationCenter.requestAuthorization(for: .individual)`
2. `ManagedSettingsStore` درع التطبيقات المختارة من `FamilyActivityPicker`
3. `syncPolicy` يسحب السياسة من السيرفر ويُفعّل/يلغي الدرع عبر App Group

## ماذا يضيف الامتداد بعد تضمينه؟

1. `intervalDidStart` — إعادة تطبيق الدرع من App Group
2. `eventDidReachThreshold` — تفعيل الدرع عند بلوغ الحد اليومي
3. مشاركة الحالة عبر `group.com.example.myranaFlutter`

## قائمة تحقق على Mac (مرقّمة)

1. افتحي `ios/Runner.xcworkspace` في Xcode.
2. File → New → Target → **Device Activity Monitor Extension**.
3. Product Name: `DeviceActivityMonitorExtension`.
4. Bundle ID مثال: `com.example.myranaFlutter.DeviceActivityMonitorExtension`.
5. احذفي الشيفرة الافتراضية واستبدليها بـ `MyranaDeviceActivityMonitor.swift` من هذا المجلد.
6. أضيفي أيضاً `ios/Shared/MyranaAppGroupStore.swift` لهدف الامتداد (Target Membership) — اختياري إن بقيت الشيفرة ذاتية الاكتفاء في الملف أعلاه.
7. Signing & Capabilities للامتداد: **Family Controls** + **App Groups** (`group.com.example.myranaFlutter`).
8. اربطي `DeviceActivityMonitorExtension.entitlements`.
9. على Runner: نفس App Group + Family Controls (موجود في `Runner.entitlements`).
10. تأكدي أن Runner يضم الامتداد في **Embed App Extensions**.
11. على [developer.apple.com](https://developer.apple.com): فعّلي Family Controls + App Groups لنفس App IDs، واطلبي موافقة App Store إن لزم.
12. ابنِي على جهاز حقيقي: `flutter build ios` أو `flutter build ipa` (macOS فقط).

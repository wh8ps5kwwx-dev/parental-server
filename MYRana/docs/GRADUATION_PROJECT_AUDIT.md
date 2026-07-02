# Graduation Project Audit — MYRana (Parental Control)

**تاريخ الفحص:** 2026-06-07  
**المصدر:** الكود الفعلي فقط — Android `MYRana/` + سيرفر `server.py`  
**لا يُفترض وجود ميزة إلا إذا وُجدت في الكود.**

---

## ملخص تنفيذي

| الفئة | العدد |
|--------|-------|
| ميزات مراقبة مُنفَّذة | 12 |
| ميزات تحكم مُنفَّذة | 10 |
| ميزات تعليمية (أكاديمية الطفل) | 4 |
| تقنيات مطلوبة في المواصفات **غير موجودة** | 5 |
| ميزات سيرفر بدون واجهة Android كاملة | 1 |

**البنية:** تطبيقان من نكهتين Gradle (`child` / `parent`) + سيرفر Flask على Render.

---

## تقنيات Android — نتيجة البحث

| التقنية | موجود؟ | الملفات |
|---------|--------|---------|
| `UsageStatsManager` | ✅ نعم | `UsageStatsCollector.kt`, `ForegroundAppDetector.kt` |
| `AccessibilityService` | ✅ نعم | `ContentFilterAccessibilityService.kt` |
| `DevicePolicyManager` | ❌ لا | — |
| `NotificationListenerService` | ❌ لا | — |
| `LocationManager` | ❌ لا | — |
| `FusedLocationProviderClient` | ❌ لا | — |
| Foreground Service | ✅ نعم | `ParentSyncService.kt` |
| Background (WorkManager) | ✅ نعم | `MonitoringWorker.kt`, `BackgroundLoopWorker.kt` |
| Boot Receiver | ✅ نعم | `BootReceiver.kt` |

---

## ميزات المراقبة (Monitoring)

### 1. مراقبة استخدام التطبيقات (App Usage Monitoring)

**Feature:** App Usage Monitoring  
**Description:** جمع ثواني الاستخدام الأمامي لكل حزمة عبر `UsageStatsManager`، تخزين محلي في Room، رفع دوري (24 ساعة) أو عند أمر الأم.  
**Files:**
- `app/src/main/java/com/example/myrana/enforcement/UsageStatsCollector.kt`
- `app/src/main/java/com/example/myrana/enforcement/ForegroundAppDetector.kt`
- `app/src/main/java/com/example/myrana/enforcement/UsageAccessHelper.kt`
- `app/src/main/java/com/example/myrana/sync/UsageUploadHelper.kt`
- `app/src/main/java/com/example/myrana/data/local/DailyAppUsageEntity.kt`
- `app/src/main/java/com/example/myrana/data/local/DailyAppUsageDao.kt`
- `app/src/main/java/com/example/myrana/data/repo/OutboxRepository.kt`
- `E:/parent_monitor_project/server.py` → `POST /upload-usage`, `GET /weekly-report`  
**Status:** ✅ مكتملة (تتطلب صلاحية Usage Access)

---

### 2. وقت الشاشة (Screen Time)

**Feature:** Screen Time Tracking & Enforcement  
**Description:** تتبع وقت الشاشة اليومي، تحذيرات متدرجة، إغلاق تلقائي عند تجاوز الحد، مزامنة مع السيرفر.  
**Files:**
- `app/src/main/java/com/example/myrana/screentime/ScreenTimeTracker.kt`
- `app/src/main/java/com/example/myrana/screentime/ScreenTimeEnforcer.kt`
- `app/src/main/java/com/example/myrana/screentime/ScreenTimeRepository.kt`
- `app/src/main/java/com/example/myrana/screentime/ScreenTimePolicy.kt`
- `app/src/main/java/com/example/myrana/screentime/ScreenTimePolicyStore.kt`
- `app/src/main/java/com/example/myrana/ui/ScreenTimeWarningActivity.kt`
- `app/src/main/java/com/example/myrana/ui/ScreenTimeLimitActivity.kt`
- `app/src/main/java/com/example/myrana/sync/ScreenTimeSyncHelper.kt`
- `server.py` → `GET/POST /screen-time-policy`, `POST /screen-time-events`  
**Status:** ✅ مكتملة على جهاز الطفل

---

### 3. فلترة المواقع (Website Blocking — Accessibility)

**Feature:** Website Blocking  
**Description:** عبر `AccessibilityService` في Chrome/المتصفحات المراقبة — مطابقة النطاقات المحظورة وإغلاق الصفحة + تنبيه ولي الأمر.  
**Files:**
- `app/src/main/java/com/example/myrana/enforcement/ContentFilterAccessibilityService.kt`
- `app/src/main/java/com/example/myrana/enforcement/PolicyFilterCache.kt`
- `app/src/main/java/com/example/myrana/enforcement/BlocklistCatalogLoader.kt`
- `app/src/main/res/xml/accessibility_content_filter.xml`
- `app/src/child/AndroidManifest.xml` (تسجيل الخدمة)
- `server.py` → policy hosts, `GET /blocklist/catalog`, `POST /apply-default-blocklist`  
**Status:** ✅ مكتملة (تعتمد على تفعيل Accessibility يدوياً)

---

### 4. كلمات بحث خطرة (Search Safety Alerts)

**Feature:** Dangerous Search Keyword Alerts  
**Description:** عند كتابة كلمات من `SafetyKeywordCatalog` في شريط بحث Chrome — إرسال تنبيه للسيرفر.  
**Files:**
- `app/src/main/java/com/example/myrana/enforcement/ContentFilterAccessibilityService.kt`
- `app/src/main/java/com/example/myrana/enforcement/SafetyKeywordCatalog.kt`
- `server.py` → `POST /add-alert`, `GET /alerts`  
**Status:** ✅ مكتملة

---

### 5. مراقبة YouTube (ضمن Accessibility)

**Feature:** YouTube Content Monitoring  
**Description:** فحص نصوص واجهة YouTube للحظر والكلمات الخطرة.  
**Files:**
- `ContentFilterAccessibilityService.kt`
- `AccessibilityHelper.kt` (حزمة YouTube)  
**Status:** ✅ مكتملة جزئياً (يعتمد على بنية واجهة YouTube — قد يتغير مع تحديثات التطبيق)

---

### 6. كشف التطبيق الأمامي (Foreground App Detection)

**Feature:** Real-time Foreground App Detection  
**Description:** تحديد الحزمة النشطة كل ~2 ثانية عبر Usage Stats لفرض الحظر.  
**Files:**
- `ForegroundAppDetector.kt`
- `EnforcementEngine.kt`
- `ParentSyncService.kt`  
**Status:** ✅ مكتملة

---

### 7. نبضات الحياة (Child Heartbeat / Online Status)

**Feature:** Child Online Status  
**Description:** إرسال heartbeat من جهاز الطفل؛ السيرفر يحدّث `last_seen`؛ الأم ترى «متصل/غير متصل».  
**Files:**
- `NetworkModule.kt` (postHeartbeat)
- `ParentSyncService.kt` / `MonitoringWorker.kt`
- `server.py` → `POST /child-heartbeat`
- `ParentScreenTimeActivity.kt` (مؤشر online)
- `ParentMainActivity.kt` (refreshLinkedChildrenSummary, `spinnerChildren`)  
**Status:** ✅ مكتملة

---

### 8. تنبيهات ولي الأمر (Alerts Feed)

**Feature:** Parent Alerts  
**Description:** تنبيهات محاولات حظر، بحث خطر، إلخ — عرض في لوحة الأم مع polling.  
**Files:**
- `NetworkModule.kt` → postAlert, fetchAlerts
- `ParentMainActivity.kt` (viewAlerts, textAlertsPreview)
- `server.py` → `/add-alert`, `/alerts`  
**Status:** ✅ مكتملة

---

### 9. تقارير يومية/أسبوعية (Usage Reports)

**Feature:** Daily & Weekly Usage Reports  
**Description:** تقارير نصية من السيرفر؛ عرض في `ParentScreenTimeActivity` و RecyclerView في `ParentMainActivity`.  
**Files:**
- `GuardianApi.kt`, `NetworkModule.kt`
- `ParentScreenTimeActivity.kt`
- `ParentMainActivity.kt`, `UsageReportAdapter.kt`
- `server.py` → `/daily-report`, `/weekly-report`, `/reports`  
**Status:** ⚠️ مكتملة كنص — **لا توجد رسوم بيانية (Charts)**

---

### 10. لوحة ولي الأمر (Parent Dashboard)

**Feature:** Parent Dashboard  
**Description:** `ParentMainActivity` (ربط + أوامر) + `ParentScreenTimeActivity` (مؤشرات وقت الشاشة، تقارير، سياسة).  
**Files:**
- `app/src/parent/java/com/example/myrana/parent/ui/ParentMainActivity.kt`
- `app/src/parent/java/com/example/myrana/parent/ui/ParentScreenTimeActivity.kt`
- `app/src/parent/res/layout/activity_parent_main.xml`
- `app/src/parent/res/layout/activity_parent_screen_time.xml`
- `server.py` → `GET /child-dashboard`  
**Status:** ✅ مكتملة (ProgressBar فقط — ليس مكتبة Charts)

---

### 11. خدمات الخلفية (Background Monitoring)

**Feature:** Background Services  
**Description:** WorkManager دوري + حلقة كل 1–2 دقيقة + Foreground Service عند توفر إذن الإشعارات + إعادة تشغيل بعد Boot.  
**Files:**
- `MonitoringWorker.kt`, `BackgroundLoopWorker.kt`, `MonitoringScheduler.kt`
- `ParentSyncService.kt`
- `BackgroundMonitoring.kt`, `SyncStarter.kt`
- `BootReceiver.kt`
- `MyRanaApp.kt`, `ChildProjectRuntime.kt`  
**Status:** ✅ مكتملة

---

### 12. تخزين محلي وسياسة (Room DB)

**Feature:** Local Policy Cache & Outbox  
**Description:** Room DB `myrana_policy.db` — مواقع/تطبيقات محظورة، outbox للرفع، أحداث screen time.  
**Files:**
- `app/src/main/java/com/example/myrana/data/local/AppDatabase.kt`
- `BlockedSiteEntity.kt`, `BlockedAppEntity.kt`, `PendingOutboxEntity.kt`, إلخ
- `PolicyRepository.kt`  
**Status:** ✅ مكتملة

---

## ميزات التحكم (Control)

### 13. حظر التطبيقات (App Blocking)

**Feature:** App Blocking  
**Description:** أمّر `block_app` → سيرفر → مزامنة → `EnforcementEngine` يقتل العملية ويعيد Home ويعرض `BlockWarningActivity`.  
**Files:**
- `EnforcementEngine.kt`
- `PolicyRepository.kt`
- `BlockWarningActivity.kt`
- `ParentMainActivity.kt` (btnBlockApp)
- `server.py` → `POST /send-command`, policy packages  
**Status:** ✅ مكتملة

---

### 14. تجميد التطبيق (Freeze App)

**Feature:** App Freeze  
**Description:** أمر `freeze_app` — نفس آلية الحظر (إضافة للحزم المحظورة).  
**Files:** نفس App Blocking + `ParentMainActivity.kt` (btnFreezeApp)  
**Status:** ✅ مكتملة

---

### 15. حظر موقع يدوي (Block Site Command)

**Feature:** Manual Site Block  
**Description:** أمّر `block_site` من تطبيق الأم → يُضاف للسياسة على السيرفر ويُزامَن للطفل.  
**Files:**
- `ParentMainActivity.kt` (btnBlockSite)
- `PolicyRepository.kt`
- `server.py` → policy hosts  
**Status:** ✅ مكتملة

---

### 16. السماح / إلغاء الحظر (Allow)

**Feature:** Allow / Unblock  
**Description:** أمر `allow` لمسح قيود مؤقتة.  
**Files:** `PolicyRepository.kt`, `ParentMainActivity.kt` (btnAllow)  
**Status:** ✅ مكتملة

---

### 17. جدولة الحظر (Scheduled Freeze)

**Feature:** Scheduled App Block  
**Description:** جدولة `freeze_app` بين وقت بداية ونهاية؛ الطفل يجلب `active-schedules`.  
**Files:**
- `ParentMainActivity.kt` (scheduleFreeze)
- `GuardianApi.kt` (addSchedule)
- `EnforcementEngine.kt` (scheduleBlocked)
- `server.py` → `/add-schedule`, `/active-schedules`  
**Status:** ✅ مكتملة

---

### 18. قائمة حظر افتراضية (Default Blocklist)

**Feature:** Default Blocklist  
**Description:** تطبيق كتالوج حظر جاهز من السيرفر على جهاز الطفل.  
**Files:**
- `BlocklistCatalogLoader.kt`
- `ParentMainActivity.kt` (btnApplyDefaultBlocklist)
- `server.py` → `/blocklist/catalog`, `/apply-default-blocklist`  
**Status:** ✅ مكتملة

---

### 19. سياسة وقت الشاشة من الأم (Screen Time Policy Control)

**Feature:** Parent Screen Time Policy  
**Description:** ضبط دقائق التحذير والإغلاق وحد التطبيقات المفتوحة وحفظها على السيرفر.  
**Files:**
- `ParentScreenTimeActivity.kt`
- `GuardianApi.kt` (fetch/save screen time policy)
- `ScreenTimePolicyStore.kt` (على الطفل)  
**Status:** ✅ مكتملة

---

### 20. طلب تقرير استخدام فوري

**Feature:** Request Usage Report  
**Description:** أمّر `request_usage` → الطفل يرفع فوراً.  
**Files:**
- `ParentMainActivity.kt` (btnRequestUsageReport)
- `UsageUploadHelper.kt`
- `server.py` → commands  
**Status:** ✅ مكتملة

---

### 21. رسائل ولي الأمر للطفل

**Feature:** Guardian Messages  
**Description:** إرسال رسالة عبر السيرفر؛ تُدرَج في `alerts` وتُصفَّ كأمر `guardian_message` ليستقبلها الطفل كإشعار.  
**Files:**
- `ParentMainActivity.kt` (btnSendMessage)
- `GuardianApi.kt`
- `PolicyRepository.kt` (معالجة `guardian_message`)
- `sync/GuardianMessageNotifier.kt`
- `server.py` → `POST /send-guardian-message`  
**Status:** ✅ مكتملة (إشعار على جهاز الطفل)

---

### 22. ربط ولي الأمر بالطفل (Linking & Multi-child API)

**Feature:** Parent-Child Linking  
**Description:** تسجيل طفل، تحقق بريد، ربط بكود، ربط تلقائي، قائمة أطفال من السيرفر.  
**Files:**
- `ChildRegistrationActivity.kt`, `ChildCodeNormalizer.kt`
- `ParentMainActivity.kt`, `ParentSession.kt`, `GuardianApi.kt`
- `server.py` → register, link, list-children, child-link-status  
**Status:** ✅ مكتملة — ربط يدوي/تلقائي + `Spinner` لاختيار الطفل النشط (`spinnerChildren` في `activity_parent_main.xml`)

---

## ما هو غير موجود في الكود (مُثبت بالبحث)

| Feature | Status |
|---------|--------|
| `DevicePolicyManager` (إدارة جهاز كـ Device Admin) | ❌ غير مُنفَّذ |
| `NotificationListenerService` (قراءة الإشعارات) | ❌ غير مُنفَّذ |
| تتبع الموقع GPS | ❌ غير مُنفَّذ |
| Child Dashboard (لوحة للطفل) | ❌ الطفل يرى الأكاديمية فقط؛ `MonitoringStatusActivity` محوّل قديم |
| شاشة Settings مخصصة للأم | ❌ الإعدادات مدمجة في `ParentScreenTimeActivity` |
| رسوم بيانية Charts | ❌ غير موجودة |
| سجل تدقيق تغييرات (Audit Log) | ❌ غير موجود في Android أو server.py |
| حذف بيانات تلقائي | ❌ غير موجود |
| إرسال تقارير بالبريد تلقائياً | ❌ البريد للتحقق/OTP فقط (Resend) |

---

## Android Manifest — Services & Receivers

### نكهة الطفل (`app/src/child/AndroidManifest.xml`)

| المكوّن | النوع | الحالة |
|---------|-------|--------|
| `ParentSyncService` | Foreground Service (`dataSync`) | ✅ |
| `ContentFilterAccessibilityService` | Accessibility Service | ✅ |
| `BootReceiver` | BroadcastReceiver | ✅ |

### نكهة ولي الأمر (`app/src/parent/AndroidManifest.xml`)

| المكوّن | النوع | الحالة |
|---------|-------|--------|
| `ParentMainActivity` | Launcher Activity | ✅ |
| `ParentScreenTimeActivity` | Activity | ✅ |

**لا توجد Services أو Receivers في نكهة parent** — كل المراقبة على جهاز الطفل.

---

## السيرفر Flask — نقاط النهاية الفعلية

`E:/parent_monitor_project/server.py` (منشور: `https://parental-server-4mms.onrender.com`)

| Endpoint | الوظيفة |
|----------|---------|
| `GET/POST /api/v1/devices/<id>/policy` | سياسة الحظر |
| `POST /register-child-device` | تسجيل الطفل |
| `POST /add-child`, `/link-child` | الربط |
| `GET /list-children` | قائمة الأطفال |
| `POST /send-command`, `GET /get-command` | أوامر عن بُعد |
| `POST /add-schedule`, `GET /active-schedules` | جدولة |
| `POST /upload-usage`, `GET /weekly-report` | استخدام |
| `GET /daily-report`, `GET /child-dashboard` | تقارير ولوحة |
| `GET/POST /screen-time-policy` | سياسة وقت الشاشة |
| `POST /child-heartbeat`, `POST /screen-time-events` | مراقبة حية |
| `POST /add-alert`, `GET /alerts` | تنبيهات |
| `POST /send-guardian-message` | رسائل |
| `POST /send-email-code`, `/verify-email-code` | تحقق البريد |

**قاعدة البيانات:** SQLite مباشرة (`sqlite3`) — ليس SQLAlchemy.

---

## طبقة التعليم (أكاديمية العباقرة) — ليست مراقبة لكنها جزء من المشروع

| Feature | Files | Status |
|---------|-------|--------|
| قائمة الأكاديمية | `AcademyMenuActivity.kt` | ✅ |
| تحديات Python | `AcademyChallengeActivity.kt`, `AcademyPythonBridge.kt` | ✅ |
| مدينة وتقدم | `AcademyCityActivity.kt`, `AcademyProgressStore.kt` | ✅ |
| مكافآت | `AcademyRewardsActivity.kt` | ✅ |

---

## خلاصة Graduation Project Audit

### ما يعمل فعلياً (End-to-End)

1. تسجيل جهاز الطفل والحصول على `CHILD-XXXX`
2. تحقق بريد ولي الأمر وربط (يدوي أو تلقائي)
3. منح صلاحيات Usage + Accessibility + Battery على جهاز الطفل
4. مراقبة خلفية مستمرة (WorkManager + Foreground Service + Boot)
5. حظر تطبيقات ومواقع وجدولة تجميد
6. تنبيهات بحث خطر ومحاولات حظر
7. وقت شاشة مع تحذيرات وإغلاق
8. تقارير استخدام يومية/أسبوعية (نص)
9. لوحة أم بمؤشرات وتقارير وسياسة
10. أكاديمية تعليمية للطفل بعد الإعداد

### فجوات للتقرير الأكاديمي

1. لا تتبع موقع — Feature مطلوبة في المواصفات غير موجودة
2. لا Device Admin / Notification Listener
3. لا Charts — تقارير نصية فقط
4. بيانات Render قد تُفقد عند إعادة النشر بدون قرص دائم (`DATA_DIR`)
5. APK على GitHub قد يكون أقدم من آخر تعديلات — يُنصح بإعادة بناء `parentDebug` بعد إضافة منتقي الأطفال

---

*تم إنشاء هذا التقرير من فحص 86 ملف Kotlin + Manifests + server.py — بدون افتراض ميزات غير موجودة.*

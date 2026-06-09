# دليل الأكواد — MYRana (تعليقات وشرح الملفات)

> مشروع الرقابة الأبوية: تطبيق طفل + تطبيق أم + سيرفر `E:\parent_monitor_project`

---

## 1) هيكل المشروع Android

```
app/src/main/          ← كود مشترك (طفل + أم)
app/src/child/         ← نكهة الطفل (Launcher = اللعبة)
app/src/parent/        ← نكهة ولي الأمر
app/build.gradle       ← عنوان السيرفر + API_KEY
```

---

## 2) واجهة الطفل (UI)

| الملف | الوظيفة |
|-------|---------|
| `ui/GameActivity.kt` | اللعبة «تحدي الألوان» — واجهة الجهاز الظاهرة للطفل |
| `ui/ChildRegistrationActivity.kt` | تسجيل مرة واحدة: بريد + رمز + عرض الأكواد لولي الأمر |
| `ui/ChildPermissionsActivity.kt` | صلاحيات: استخدام + إشعارات + خدمة الوصول |
| `ui/BlockWarningActivity.kt` | شاشة تحذير عند حظر تطبيق/موقع |
| `ui/MainActivity.kt` | شاشة مطورين فقط (ليست للطفل) |

---

## 3) الحظر والمراقبة (Enforcement)

| الملف | الوظيفة |
|-------|---------|
| `enforcement/EnforcementEngine.kt` | حظر التطبيقات فعلياً (Usage Stats) + تنبيه للأم |
| `enforcement/ContentFilterAccessibilityService.kt` | حظر مواقع المتصفح + كلمات YouTube |
| `enforcement/PolicyFilterCache.kt` | ذاكرة سريعة لقوائم المواقع وكلمات الفيديو |
| `enforcement/AccessibilityHelper.kt` | فتح إعدادات الوصول + قائمة المتصفحات |
| `enforcement/ForegroundAppDetector.kt` | معرفة التطبيق الأمامي حالياً |
| `enforcement/UsageAccessHelper.kt` | فتح إعدادات «بيانات الاستخدام» |

---

## 4) الخلفية (Background)

| الملف | الوظيفة |
|-------|---------|
| `service/ParentSyncService.kt` | خدمة أمامية: مزامنة كل 60ث + حظر كل 2ث |
| `worker/MonitoringWorker.kt` | WorkManager: مزامنة دورية (~15 دقيقة) |
| `worker/MonitoringScheduler.kt` | جدولة Worker |
| `receiver/BootReceiver.kt` | إعادة تشغيل المراقبة بعد إقلاع الجهاز |
| `sync/SyncStarter.kt` | نقطة تشغيل الخدمة + Worker |
| `sync/UsageUploadHelper.kt` | رفع استخدام التطبيقات للسيرفر |
| `sync/UsageReportScheduler.kt` | رفع تلقائي كل 24 ساعة |
| `network/NetworkMonitor.kt` | عند عودة النت: رفع الطابور |
| `MyRanaApp.kt` | Application: تشغيل الخلفية لنكهة الطفل |

---

## 5) البيانات المحلية (Room)

| الملف | الوظيفة |
|-------|---------|
| `data/local/AppDatabase.kt` | قاعدة `myrana_policy.db` |
| `data/local/BlockedAppEntity.kt` | جدول التطبيقات المحظورة (package) |
| `data/local/BlockedSiteEntity.kt` | جدول المواقع المحظورة |
| `data/local/BlockedAppDao.kt` | استعلامات التطبيقات |
| `data/local/BlockedSiteDao.kt` | استعلامات المواقع |
| `data/local/PendingOutboxEntity.kt` | طابور رفع الاستخدام عند انقطاع النت |
| `data/local/SyncStateEntity.kt` | آخر وقت مزامنة مع السيرفر |

---

## 6) الشبكة والسيرفر

| الملف | الوظيفة |
|-------|---------|
| `data/remote/NetworkModule.kt` | OkHttp + Retrofit — كل طلبات REST |
| `data/remote/GuardianApi.kt` | مسارات الأم: تسجيل، ربط، رسائل، تقارير |
| `data/remote/ParentPolicyApi.kt` | GET/POST سياسة الحظر |
| `data/repo/PolicyRepository.kt` | مزامنة السياسة: سحب + رفع + أوامر الأم |
| `data/repo/OutboxRepository.kt` | رفع الاستخدام مع طابور offline |

**عنوان السيرفر:** `app/build.gradle` → `SERVER_ROOT_URL`

---

## 7) تطبيق الأم (parent)

| الملف | الوظيفة |
|-------|---------|
| `parent/ui/ParentMainActivity.kt` | تسجيل + ربط طفل + أوامر حظر + رسائل + تقارير |
| `parent/ui/UsageReportAdapter.kt` | قائمة استخدام التطبيقات |
| `parent/ParentSession.kt` | حفظ بريد الأم، كود الطفل، الصفة (أم/أب) |

---

## 8) الجلسة والهوية

| الملف | الوظيفة |
|-------|---------|
| `session/ChildSession.kt` | تسجيل الطفل محلياً (مرة واحدة) |
| `device/DeviceIdentity.kt` | `child_code` للسيرفر |
| `permissions/PermissionCoordinator.kt` | إذن الإشعارات |

---

## 9) السيرفر Python (`E:\parent_monitor_project`)

| الملف | الوظيفة |
|-------|---------|
| `server.py` | Flask: تسجيل، ربط، حظر، تنبيهات، استخدام |
| `blocklists/catalog.json` | قائمة الحظر الافتراضية |
| `common/config.py` | `SERVER_URL` + `API_KEY` |
| `common/api_client.py` | دوال REST من Python |
| `mother-app/main.py` | تطبيق أم Kivy |
| `RA/server.py` | نسخة للنشر على Render |

### أهم مسارات API

| المسار | من يستدعيه |
|--------|------------|
| `POST /register-child-device` | تطبيق الطفل |
| `POST /add-child` | تطبيق الأم — ربط |
| `POST /send-command` | أوامر حظر/تجميد |
| `POST /send-guardian-message` | رسالة من أم لولي الأمر |
| `POST /add-alert` | جهاز الطفل — تنبيه محاولة حظر |
| `GET /alerts` | الأم — عرض التنبيهات |
| `POST /upload-usage` | الطفل — تقرير الاستخدام |
| `GET /weekly-report` | الأم — تقرير أسبوعي |
| `GET /api/v1/devices/{id}/policy` | مزامنة قوائم الحظر |

---

## 10) تدفق البيانات (مختصر)

```
الأم → send-command / send-guardian-message → السيرفر
الطفل → ParentSyncService → GET policy + GET command
الطفل → EnforcementEngine → حظر + POST add-alert
الأم → GET alerts / weekly-report
```

---

## 11) الاختبارات

| الملف | ملاحظة |
|-------|--------|
| `test/ExampleUnitTest.kt` | اختبار وحدة بسيط |
| `androidTest/ExampleInstrumentedTest.kt` | اختبار على الجهاز — تحقق من package |

---

*آخر تحديث: يعكس بنية المشروع الحالية مع اللعبة، الرسائل، والحظر عبر السيرفر.*

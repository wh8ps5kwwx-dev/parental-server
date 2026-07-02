# Python داخل APK — أكاديمية العباقرة

## كيف يعمل

- **Chaquopy** يضمّن مفسّر Python 3.8 داخل APK نكهة الطفل.
- الملف: `app/src/main/python/academy_game.py`
- Android يعرض الواجهة؛ **كل منطق اللعب من Python** عبر `AcademyPythonBridge.kt`.

## البناء

```powershell
cd c:\Users\rannn\AndroidStudioProjects\MYRana
.\gradlew assembleChildDebug
```

APK: `app\build\outputs\apk\child\debug\app-child-debug.apk`

**متطلبات:** Python 3.8+ على جهاز التطوير (للبناء فقط — ليس على جوال الطفل).

## الخلفية (WorkManager كامل)

| آلية | التكرار | الوظيفة |
|------|---------|---------|
| `BackgroundLoopWorker` | كل **3 دقائق** | مزامنة + حظر + أوامر + رفع استخدام |
| `MonitoringWorker` دوري | كل **15 دقيقة** | نفس المزامنة |
| `ParentSyncService` | كل **2 ث** حظر + **60 ث** مزامنة | عند إذن الإشعارات |
| `BootReceiver` | بعد الإقلاع | إعادة تشغيل كل ما سبق |

## تعديل اللعبة

عدّلي `app/src/main/python/academy_game.py` ثم أعيدي البناء — لا حاجة لتعديل Kotlin إلا للواجهة.

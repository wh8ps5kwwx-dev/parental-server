# مشروع Android على القرص E

بسبب امتلاء القرص **C:**، يُخزَّن مشروع MYRana على:

```
E:\parent_monitor_project\MYRana
```

## خطوة واحدة — مزامنة وتطبيق الباتشات

```powershell
powershell -ExecutionPolicy Bypass -File E:\parent_monitor_project\scripts\sync_myrana_to_E.ps1
```

يقوم السكربت بـ:
1. نسخ المصدر من `C:\Users\rannn\AndroidStudioProjects\MYRana` إلى `E:\...` (بدون `build` و `.gradle`)
2. نسخ ملفات الباتش الجديدة
3. تعديل `NetworkModule`, `GuardianApi`, `ScreenTimeSyncHelper`, `AndroidManifest`, إلخ.

## فتح المشروع في Android Studio

**File → Open** → `E:\parent_monitor_project\MYRana`

## البناء (من E — لا يستخدم مساحة C للمخرجات إذا Gradle cache على E)

```powershell
cd E:\parent_monitor_project\MYRana

# اختياري: توجيه Gradle cache إلى E
$env:GRADLE_USER_HOME = "E:\parent_monitor_project\.gradle"

.\gradlew assembleParentDebug
.\gradlew assembleChildDebug
```

## تثبيت APK

```powershell
adb install app\build\outputs\apk\parent\debug\app-parent-debug.apk
adb install app\build\outputs\apk\child\debug\app-child-debug.apk
```

## السيرفر Flask

```powershell
cd E:\parent_monitor_project
python server.py
```

أو ارفعي `server.py` على Render كما سبق.

## تحرير مساحة C (موصى به)

```powershell
# حذف build القديم على C
Remove-Item -Recurse -Force "C:\Users\rannn\AndroidStudioProjects\MYRana\app\build" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "C:\Users\rannn\AndroidStudioProjects\MYRana\.gradle" -ErrorAction SilentlyContinue
```

بعدها اعملي من **E** فقط ولا تفتحي المشروع من C.

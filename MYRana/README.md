# MYRana — مشروع التخرج (رقابة أبوية)

**كل الروابط:** [LINKS.md](LINKS.md)

---

## تحميل APK — اضغطي من الجوال (Chrome)

| الجهاز | الرابط |
|--------|--------|
| **جوال الطفل** | [تحميل app-child-debug.apk](https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/releases/app-child-debug.apk) |
| **جوال الأم** | [تحميل app-parent-debug.apk](https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/releases/app-parent-debug.apk) |

> اسمحي بتثبيت من **مصادر غير معروفة** في إعدادات الجوال.

---

## روابط أخرى

| ماذا | الرابط |
|------|--------|
| السيرفر | [parental-server-4mms.onrender.com](https://parental-server-4mms.onrender.com) |
| GitHub | [parental-server](https://github.com/wh8ps5kwwx-dev/parental-server) |
| Python zip | [myrana_mother_phone.zip](https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/mother-app/myrana_mother_phone.zip) |
| دليل الربط | [تشغيل_جوالين.md](تشغيل_جوالين.md) |

---

## خطوات الربط

1. **الطفل:** حمّلي APK الطفل → تسجيل جهاز → `CHILD-...`
2. **الأم:** حمّلي APK الأم → بريد → رمز التحقق
3. **الأم:** كود الطفل → رمز الربط → **ربط الطفل**
4. **الطفل:** صلاحيات أندرويد
5. **الأم:** جرّبي حظر تطبيق

---

## بناء محلي

```powershell
cd c:\Users\rannn\AndroidStudioProjects\MYRana
.\gradlew assembleChildDebug assembleParentDebug
```

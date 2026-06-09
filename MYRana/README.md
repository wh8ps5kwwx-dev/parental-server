# MYRana — مشروع التخرج (رقابة أبوية)

## روابط مباشرة — افتحي من الجوال (Chrome)

### تحميل APK (ثبّتي مباشرة)

| الجهاز | الرابط |
|--------|--------|
| **جوال الطفل** — أكاديمية العباقرة | https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/releases/app-child-debug.apk |
| **جوال الأم** — MY Rana ولي الأمر | https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/releases/app-parent-debug.apk |

> اسمحي بتثبيت تطبيقات من **مصادر غير معروفة** في إعدادات الجوال.

### تطبيق الأم Python (Pydroid 3 — اختياري)

| الملف | الرابط |
|-------|--------|
| حزمة Pydroid كاملة | https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/mother-app/myrana_mother_phone.zip |

### السيرفر

| الخدمة | الرابط |
|--------|--------|
| حالة السيرفر (يفتح في المتصفح) | https://parental-server-4mms.onrender.com |
| لوحة Render | https://dashboard.render.com |

---

## المشروع على GitHub

| الجزء | الرابط |
|-------|--------|
| **المستودع الرئيسي** | https://github.com/wh8ps5kwwx-dev/parental-server |
| مشروع Android (MYRana) | https://github.com/wh8ps5kwwx-dev/parental-server/tree/main/MYRana |
| تطبيق الأم Python | https://github.com/wh8ps5kwwx-dev/parental-server/tree/main/mother-app |
| السيرفر server.py | https://github.com/wh8ps5kwwx-dev/parental-server/blob/main/server.py |
| أكاديمية Python | https://github.com/wh8ps5kwwx-dev/parental-server/tree/main/child-academy |

---

## خطوات الربط السريعة

1. **الطفل:** ثبّتي APK الطفل → تسجيل جهاز → انسخي `CHILD-...`
2. **الأم:** ثبّتي APK الأم → بريد Gmail → رمز التحقق
3. **الأم:** اسم + عمر → لصق كود الطفل → رمز الربط من Gmail → **ربط الطفل**
4. **الطفل:** فعّلي الصلاحيات (استخدام + وصول + إشعارات)
5. **الأم:** جرّبي حظر تطبيق من لوحة التحكم

تفاصيل كاملة: [تشغيل_جوالين.md](تشغيل_جوالين.md)

---

## البناء المحلي (Android Studio)

```powershell
cd c:\Users\rannn\AndroidStudioProjects\MYRana
.\gradlew assembleChildDebug assembleParentDebug
```

APK محلياً:
- `releases\myrana-child-debug.apk`
- `releases\myrana-parent-debug.apk`

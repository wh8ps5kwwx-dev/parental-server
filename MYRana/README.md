# MYRana — مشروع التخرج (رقابة أبوية)

**كل الروابط:** [LINKS.md](LINKS.md) — اضغطي لفتح من GitHub أو Chrome.

---

## روابط سريعة (اضغطي)

| ماذا | الرابط |
|------|--------|
| **السيرفر شغّال؟** | [parental-server-4mms.onrender.com](https://parental-server-4mms.onrender.com) |
| **GitHub — المشروع كامل** | [parental-server](https://github.com/wh8ps5kwwx-dev/parental-server) |
| **كود Android** | [MYRana](https://github.com/wh8ps5kwwx-dev/parental-server/tree/main/MYRana) |
| **Python للأم (zip)** | [myrana_mother_phone.zip](https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/mother-app/myrana_mother_phone.zip) |
| **دليل الربط** | [تشغيل_جوالين.md](تشغيل_جوالين.md) · [على GitHub](https://github.com/wh8ps5kwwx-dev/parental-server/blob/main/MYRana/%D8%AA%D8%B4%D8%BA%D9%8A%D9%84_%D8%AC%D9%88%D8%A7%D9%84%D9%8A%D9%86.md) |

---

## APK — تثبيت على الجوال

> **APK غير مرفوعة على GitHub** (حجم كبير). انقلي الملفات من جهازك:

| الجهاز | الملف المحلي |
|--------|--------------|
| **جوال الطفل** — أكاديمية العباقرة | `releases\myrana-child-debug.apk` |
| **جوال الأم** — MY Rana ولي الأمر | `releases\myrana-parent-debug.apk` |

1. انسخي APK لواتساب / Drive / USB
2. على الجوال: فعّلي **مصادر غير معروفة** → ثبّتي
3. اتبعي [تشغيل_جوالين.md](تشغيل_جوالين.md)

---

## خطوات الربط (ملخص)

1. **الطفل:** APK الطفل → تسجيل جهاز → انسخي `CHILD-...`
2. **الأم:** APK الأم → بريد Gmail → رمز التحقق
3. **الأم:** اسم + عمر → كود الطفل → رمز الربط → **ربط الطفل**
4. **الطفل:** صلاحيات أندرويد (استخدام + وصول + إشعارات)
5. **الأم:** جرّبي حظر تطبيق

---

## البناء من Android Studio

```powershell
cd c:\Users\rannn\AndroidStudioProjects\MYRana
.\gradlew assembleChildDebug assembleParentDebug
```

النتيجة: `releases\myrana-child-debug.apk` و `releases\myrana-parent-debug.apk`

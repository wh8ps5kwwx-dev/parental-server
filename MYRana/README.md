# MYRana — مشروع التخرج (رقابة أبوية)

## تحميل التطبيقات على الجوال (روابط مباشرة)

بعد الرفع على GitHub، افتحي هذه الروابط **في Chrome على الجوال** → حمّلي → ثبّتي:

| الجهاز | الرابط |
|--------|--------|
| **جوال الطفل** | https://media.githubusercontent.com/media/wh8ps5kwwx-dev/parental-server/main/releases/app-child-debug.apk |
| **جوال الأم** | https://media.githubusercontent.com/media/wh8ps5kwwx-dev/parental-server/main/releases/app-parent-debug.apk |

> اسمحي بتثبيت تطبيقات من مصادر غير معروفة في إعدادات الجوال.

## المشروع الكامل على GitHub

- **المستودع:** https://github.com/wh8ps5kwwx-dev/parental-server
- **أندرويد (هذا المجلد):** [MYRana/](https://github.com/wh8ps5kwwx-dev/parental-server/tree/main/MYRana)
- **تطبيق الأم Python (Kivy):** [mother-app/](https://github.com/wh8ps5kwwx-dev/parental-server/tree/main/mother-app)
- **السيرفر:** [server.py](https://github.com/wh8ps5kwwx-dev/parental-server/blob/main/server.py)
- **أكاديمية Python:** [child-academy/](https://github.com/wh8ps5kwwx-dev/parental-server/tree/main/child-academy)

## السيرفر (Render)

- https://parental-server-4mms.onrender.com

## خطوات الربط (جوالين)

1. **الطفل:** افتحي «أكاديمية العباقرة» → تسجيل جهاز → **نسخ الكود** → أرسليه للأم
2. **الأم:** افتحي «MY Rana - ولي الأمر» → بريد `parent.controll.app@gmail.com` → رمز التحقق من Gmail
3. **الأم:** لصق كود الطفل → إرسال رمز الربط → رمز الربط من Gmail → ربط الطفل
4. **اختبار:** حظر تطبيق `Granny` من جوال الأم

تفاصيل أكثر: [تشغيل_جوالين.md](تشغيل_جوالين.md)

## البناء من Android Studio

```powershell
cd MYRana
.\gradlew assembleChildDebug assembleParentDebug
```

APK الناتج في `app/build/outputs/apk/` — انسخيه إلى `releases/` عند التحديث.

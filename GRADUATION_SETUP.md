# إعداد التخرج — خطوات التشغيل النهائية

## 1) Render — متغيرات البيئة (مهم)

في [Render Dashboard](https://dashboard.render.com) → خدمتك → **Environment**:

| المتغير | القيمة |
|---------|--------|
| `SMTP_USER` | `parent.controll.app@gmail.com` |
| `SMTP_PASS` | **App Password** من Google (انظري أدناه) |
| `SMTP_HOST` | `smtp.gmail.com` |
| `SMTP_PORT` | `465` |
| `API_KEY` | `graduation-secret-key` |

### Gmail App Password (مطلوب للبريد الحقيقي)

1. [Google Account](https://myaccount.google.com) → **Security**
2. فعّلي **2-Step Verification**
3. **App passwords** → Mail → انسخي الرمز 16 حرفاً
4. ضعيه في `SMTP_PASS` على Render (ليس كلمة مرور الحساب العادية)

بعد التعديل: **Manual Deploy** → Redeploy.

---

## 2) رفع السيرفر المحدّث

```powershell
cd E:\parent_monitor_project\RA
powershell -File sync_for_render.ps1
git add server.py blocklists
git commit -m "resolve app names to packages + SMTP"
git push
```

---

## 3) حظر باسم التطبيق (تم الإصلاح)

الأم تكتب مثلاً: `Granny` أو `تيك توك` أو `TikTok`  
السيرفر يحوّل تلقائياً إلى `com.dvloper.granny` أو `com.zhiliaoapp.musically` عبر `blocklists/package_resolver.py`.

---

## 4) جوال الطفل

```powershell
cd c:\Users\rannn\AndroidStudioProjects\MYRana
.\gradlew assembleChildDebug
```

ثبّتي APK → تسجيل → صلاحيات → أكاديمية العباقرة.

---

## 5) الأم (Kivy)

```powershell
cd E:\parent_monitor_project\mother-app
pip install -r requirements.txt
python main.py
```

1. تحقق من البريد  
2. ربط طفل (`child_code` + `device_verify_code`)  
3. حظر تطبيق بالاسم (مثل Granny)  
4. التنبيهات والتقارير  

---

## 6) اختبار سريع للتخرج

| # | الاختبار | النتيجة المتوقعة |
|---|----------|------------------|
| 1 | `send-email-code` لبريد الأم | رسالة في البريد |
| 2 | ربط طفل | نجاح `add-child` |
| 3 | حظر `Granny` من Kivy | التطبيق يُغلق على جوال الطفل |
| 4 | فتح تطبيق محظور | تنبيه في `GET /alerts` |
| 5 | اللعب في الأكاديمية | تقرير في `GET /reports` |

---

**مبروك التخرج.**

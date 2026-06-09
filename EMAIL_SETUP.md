# إعداد البريد الحقيقي (Gmail) على Render

## 1) إنشاء App Password

1. [Google Account](https://myaccount.google.com) → **Security**
2. فعّلي **2-Step Verification**
3. **App passwords** → Mail → انسخي الرمز (16 حرفاً)

## 2) متغيرات Render

في لوحة خدمتك على Render → **Environment**:

```
SMTP_USER=your-gmail@gmail.com
SMTP_PASS=abcdefghijklmnop
SMTP_HOST=smtp.gmail.com
SMTP_PORT=465
API_KEY=graduation-secret-key
```

## 3) اختبار

```bash
curl -X POST https://parental-server-4mms.onrender.com/send-email-code \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: graduation-secret-key" \
  -d "{\"email\":\"your@gmail.com\"}"
```

- إذا `email_sent: true` → تحققي من صندوق الوارد.
- إذا `dev_fallback: true` → SMTP غير مضبوط؛ الرمز يظهر في الاستجابة للتطوير فقط.

## 4) ربط فعلي بين Kivy والطفل

### الأم (Kivy)
```bash
cd E:\parent_monitor_project\mother-app
pip install -r requirements.txt
python main.py
```
1. أدخلي البريد وكلمة السر المحلية (6+ أحرف)
2. **إرسال رمز التحقق** → أدخلي الرمز من البريد
3. **إضافة طفل** → `child_code` + `device_verify_code` من جهاز الطفل

### الطفل (Android)
1. ثبّتي APK نكهة `child`
2. سجّلي بريد الطفل → رمز يصل للبريد
3. أدخلي الرمز → الصلاحيات → المراقبة

يعمل على **شبكتين مختلفتين** عبر Render.

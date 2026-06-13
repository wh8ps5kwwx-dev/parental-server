# إعداد البريد الحقيقي — الربط عبر Gmail/Resend

## لماذا البريد مطلوب؟

الربط الحقيقي يمر بـ **3 رسائل Gmail** إلى بريد ولي الأمر:

1. **رمز تحقق البريد** — من تطبيق الأم
2. **كود CHILD-XXXXXXXX** — عند تسجيل جهاز الطفل
3. **رمز ربط الطفل** (6 أرقام) — بعد لصق كود CHILD في تطبيق الأم

> بدون إعداد البريد على السيرفر، التطبيق **لن يملأ الرمز تلقائياً** ويرفض الربط.

---

## الطريقة الموصى بها: Resend (Render مجاني)

Gmail SMTP **محظور** على Render المجاني. استخدمي [Resend](https://resend.com):

```
RESEND_API_KEY=re_xxxxxxxx
RESEND_FROM=MYRana <onboarding@resend.dev>
ALLOW_DEV_FALLBACK=0
SIMPLE_FAMILY_LINK=0
API_KEY=graduation-secret-key
```

---

## الطريقة البديلة: Gmail SMTP (سيرفر مدفوع)

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

## 3) البريد التلقائي (ملخص يومي/أسبوعي)

في تطبيق الأم (MYRana Parent): **إعدادات وسجل التغييرات** → فعّلي «ملخص يومي بالبريد» أو «ملخص أسبوعي بالبريد».

على Render أضيفي **Cron Job** يضرب السيرفر مرة يومياً (مثلاً 08:00 UTC):

```
GET https://parental-server-4mms.onrender.com/cron/email-summaries?secret=YOUR_API_KEY
```

أو Header: `X-CRON-SECRET: YOUR_API_KEY`

اختياري — للتطوير المحلي فقط:

```
EMAIL_CRON_ENABLED=1
EMAIL_CRON_INTERVAL_SEC=3600
```

## 4) اختبار

```bash
curl -X POST https://parental-server-4mms.onrender.com/send-email-code \
  -H "Content-Type: application/json" \
  -H "X-API-KEY: graduation-secret-key" \
  -d "{\"email\":\"your@gmail.com\"}"
```

- إذا `email_sent: true` → تحققي من صندوق الوارد.
- إذا `dev_fallback: true` → SMTP غير مضبوط؛ الرمز يظهر في الاستجابة للتطوير فقط.

## 5) ربط فعلي بين Kivy والطفل

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

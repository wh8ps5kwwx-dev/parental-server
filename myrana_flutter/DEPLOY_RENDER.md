# نشر السيرفر على Render يدوياً (Manual Deploy)

الخادم الحي حالياً: https://parental-server-4mms.onrender.com/

الكوميت المطلوب على GitHub: `7cecbf0` — *Fix unblock_app/unblock_site...*

> ملاحظة: قيمة `deploy_version` في الكود ما زالت `2026-07-20-unblock-fix` حتى بعد هذا الكوميت، لذلك **لا تعتمد عليها** وحدها. تأكد من أن Deploy في لوحة Render يشير إلى commit `7cecbf0`.

**بعد Manual Deploy:** افتحي https://parental-server-4mms.onrender.com/ وتأكدي أن `deploy_version` يظهر `2026-07-20-unblock-fix`.

---

## هل تم Deploy تلقائياً؟

- لا يوجد `RENDER_API_KEY` ولا Deploy Hook في `.env` المحلي → **لا يمكن تشغيل Redeploy من هنا**.
- يلزمك الضغط على **Manual Deploy** من لوحة Render (أو تفعيل Auto-Deploy إن لم يكن مفعّلاً).

---

## خطوات Manual Deploy (اضغط بالترتيب)

1. افتح: https://dashboard.render.com/
2. سجّل الدخول إن لزم.
3. من قائمة **Services** اختر الخدمة: **`parental-server`**  
   (أو الرابط المباشر إن ظهر: الخدمة المرتبطة بـ `parental-server-4mms.onrender.com`)
4. افتح تبويب **Events** أو **Deploys**.
5. اضغط **Manual Deploy**.
6. اختر **Deploy latest commit** (أو اختر الفرع `main` / الكوميت `7cecbf0`).
7. انتظر حتى تصبح الحالة **Live** (قد يستغرق دقائق على الخطة المجانية؛ أول طلب بعد السكون قد يتأخر).
8. تحقق:
   - في Events: آخر Deploy = commit يبدأ بـ `7cecbf0`
   - في المتصفح: افتح https://parental-server-4mms.onrender.com/  
     يجب أن ترى `"status":"running"` وتأكدي أن deploy_version = `2026-07-20-unblock-fix``

---

## ملفات APK الجاهزة للتثبيت

المجلد السهل:

`E:\parent_monitor_project\myrana_flutter\releases\`

| الملف | الاستخدام |
|--------|-----------|
| `app-release.apk` | نسخة Release (موصى بها للتجربة النهائية) |
| `app-debug.apk` | نسخة Debug |
| `app-child-debug.apk` | تطبيق الطفل (قديم من MYRana إن احتجت) |
| `app-parent-debug.apk` | تطبيق ولي الأمر (قديم من MYRana إن احتجت) |

للتطبيق الحالي (Flutter موحّد): استخدم **`app-release.apk`** أو **`app-debug.apk`**.

انسخ الـ APK إلى الهاتف وثبّته (فعّل «مصادر غير معروفة» إن طلب النظام).

---

## بعد نجاح الـ Deploy

جرّب من لوحة ولي الأمر: **إلغاء حظر تطبيق/موقع** وتأكد أن جهاز الطفل يتوقف عن فرض الحظر القديم (هذا هو إصلاح `7cecbf0`).

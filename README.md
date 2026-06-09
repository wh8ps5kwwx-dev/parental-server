# MYRana — مشروع التخرج الكامل

مشروع رقابة أبوية: سيرفر Python + تطبيقات أندرويد + تطبيق أم Python (Kivy).

## هيكل المشروع

| المجلد | الوصف |
|--------|--------|
| [server.py](server.py) | السيرفر الرئيسي (Flask) — Render |
| [mother-app/](mother-app/) | تطبيق الأم Python (Kivy) |
| [common/](common/) | مكتبات مشتركة (API، تسجيل الطفل، محاكيات) |
| [child-academy/](child-academy/) | لعبة الأكاديمية Python |
| [MYRana/](MYRana/) | تطبيقات أندرويد (طفل + أم) |
| [blocklists/](blocklists/) | قوائم الحظر والكلمات |
| [releases/](releases/) | APK جاهزة للتثبيت |

## تشغيل سريع

### السيرفر (محلي)
```powershell
pip install -r requirements.txt
python server.py
```

### تطبيق الأم (Kivy)
```powershell
cd mother-app
pip install -r requirements.txt
python main.py
```

### واجهة أم بديلة
```powershell
python run_parent_ui.py
```

### أندرويد
```powershell
cd MYRana
.\gradlew assembleChildDebug assembleParentDebug
```

## السيرفر على Render

- https://parental-server-4mms.onrender.com
- إعداد البريد: [EMAIL_SETUP.md](EMAIL_SETUP.md)
- خطوات التخرج: [GRADUATION_SETUP.md](GRADUATION_SETUP.md)

## APK على الجوال

| الجهاز | الرابط |
|--------|--------|
| الطفل | [releases/app-child-debug.apk](releases/app-child-debug.apk) |
| الأم | [releases/app-parent-debug.apk](releases/app-parent-debug.apk) |

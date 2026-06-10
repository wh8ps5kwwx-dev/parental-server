# MYRana — مشروع التخرج الكامل

مشروع رقابة أبوية: سيرفر Python + تطبيقات أندرويد + تطبيق أم Python (Kivy).

**روابط قابلة للفتح:** [MYRana/LINKS.md](MYRana/LINKS.md)

---

## روابط سريعة

| ماذا | الرابط |
|------|--------|
| **السيرفر** | [parental-server-4mms.onrender.com](https://parental-server-4mms.onrender.com) |
| **GitHub** | [wh8ps5kwwx-dev/parental-server](https://github.com/wh8ps5kwwx-dev/parental-server) |
| **Android MYRana** | [MYRana/](MYRana/) |
| **Python zip للأم** | [myrana_mother_phone.zip](https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/mother-app/myrana_mother_phone.zip) |
| **دليل الربط** | [تشغيل_جوالين.md](MYRana/%D8%AA%D8%B4%D8%BA%D9%8A%D9%84_%D8%AC%D9%88%D8%A7%D9%84%D9%8A%D9%86.md) |

---

## هيكل المشروع

| المجلد | الوصف |
|--------|--------|
| [server.py](server.py) | السيرفر الرئيسي (Flask) — Render |
| [mother-app/](mother-app/) | تطبيق الأم Python (Kivy) |
| [common/](common/) | مكتبات مشتركة |
| [child-academy/](child-academy/) | لعبة الأكاديمية Python |
| [MYRana/](MYRana/) | تطبيقات أندرويد (طفل + أم) |
| [blocklists/](blocklists/) | قوائم الحظر |

---

## APK — تحميل مباشر (اضغطي على الجوال)

| الجهاز | الرابط |
|--------|--------|
| **جوال الطفل** | [app-child-debug.apk](https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/releases/app-child-debug.apk) |
| **جوال الأم** | [app-parent-debug.apk](https://github.com/wh8ps5kwwx-dev/parental-server/raw/main/releases/app-parent-debug.apk) |

> بعد التحميل: فعّلي **مصادر غير معروفة** ثم ثبّتي APK.  
> المزيد: [MYRana/LINKS.md](MYRana/LINKS.md)

```powershell
cd MYRana
.\gradlew assembleChildDebug assembleParentDebug
```

---

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

---

## السيرفر على Render

- [parental-server-4mms.onrender.com](https://parental-server-4mms.onrender.com)
- [EMAIL_SETUP.md](EMAIL_SETUP.md)
- [GRADUATION_SETUP.md](GRADUATION_SETUP.md)

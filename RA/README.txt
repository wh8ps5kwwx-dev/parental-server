مجلد RA — للرفع إلى Render
============================

ارفعي إلى مستودع السيرفر على GitHub (أو Render مباشرة):
  - server.py
  - blocklists/   (مجلد كامل مع catalog.json)
  - Procfile
  - requirements.txt

قبل الرفع:  powershell -File RA\sync_for_render.ps1
بعد الرفع:  python RA\test_after_deploy.py

افتحي الملف: تعليمات_الرفع_والتشغيل.txt  (كل الخطوات بالتفصيل)

بعد رفع السيرفر: شغّلي محاكيين ثم تثبيت_على_المحاكيين.bat

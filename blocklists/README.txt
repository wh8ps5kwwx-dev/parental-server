قائمة الحظر الافتراضية — MYRana / parent_monitor_project
========================================================

الملفات:
  catalog.json          — القائمة الكاملة (تطبيقات + مواقع + كلمات فيديو)
  app_package_map.json  — ربط أسماء التطبيقات بحزم Android
  generate_catalog.py   — إعادة توليد catalog.json بعد تعديل القوائم

توليد القائمة:
  cd E:\parent_monitor_project
  python blocklists\generate_catalog.py

السيرفر:
  GET  /blocklist/catalog
  POST /apply-default-blocklist   { "child_code": "...", "merge": true }

يُطبَّق تلقائياً عند ربط الطفل (POST /add-child).
تنبيه الأم: POST /add-alert من جهاز الطفل عند كل محاولة فتح تطبيق محظور.

رفع Render: انسخي RA\server.py و blocklists\ إلى المستودع ثم Deploy.

# -*- coding: utf-8 -*-
"""الفصل الخامس — التنفيذ والاختبار (عربي)."""

from __future__ import annotations


def dev_environment_ar(doc, heading, para, table):
    heading(doc, "5.1 بيئة التطوير", 2)
    para(
        doc,
        "اعتمد التنفيذ بنية Client-Server: تطبيقان Android (نكهتان من مستودع Kotlin "
        "واحد)، وسيرفر Flask على Render، وخدمة بريد Resend لتوصيل رسائل Gmail. "
        "جميع ما ورد في هذا الفصل يعكس الكود الفعلي في "
        "parent_monitor_project/MYRana و parental-server-deploy وليس تصوراً نظرياً فقط.",
        indent=0.75,
    )
    table(
        doc,
        ["العنصر", "الإصدار / القيمة", "الملاحظات"],
        [
            ("Kotlin", "1.x", "لغة تطبيق Android"),
            ("compileSdk / targetSdk", "33", "app/build.gradle"),
            ("minSdk", "21", "Android 5.0+"),
            ("versionName", "1.3 (versionCode 4)", "APK المختبر على جوالين"),
            ("Python", "3.x", "سيرفر Flask"),
            ("Flask + Gunicorn", "أحدث مستقر", "parental-server-deploy"),
            ("SQLite", "3", "parent_control.db على السيرفر"),
            ("Room ORM", "v3", "myrana_policy.db على جهاز الطفل"),
            ("Android Studio", "2024+", "بناء وتصحيح"),
            ("Render URL", "parental-server-4mms.onrender.com", "استضافة سحابية"),
            ("Resend", "API", "إرسال OTP و CHILD عبر Gmail"),
            ("Git / GitHub", "wh8ps5kwwx-dev/parental-server", "إصدارات و APK"),
        ],
        title="جدول 5.1 — بيئة التطوير والأدوات",
    )
    table(
        doc,
        ["المكوّن", "applicationId", "اسم العرض"],
        [
            ("تطبيق الطفل", "com.example.myrana.child", "أكاديمية العباقرة"),
            ("تطبيق ولي الأمر", "com.example.myrana.parent", "MY Rana - ولي الأمر"),
        ],
        title="جدول 5.2 — حزم التطبيق المنشورة",
    )
    para(
        doc,
        "تُضمَّن ثوابت الاتصال في BuildConfig لكل APK: SERVER_ROOT_URL، "
        "SERVER_BASE_URL، و API_KEY. الاتصال عبر HTTPS مع رأس X-API-KEY في كل طلب REST.",
        indent=0.75,
    )


def android_structure_ar(doc, heading, para, bullets):
    heading(doc, "5.2.1 هيكل مشروع Android", 3)
    para(
        doc,
        "يستخدم المشروع Gradle Product Flavors لبناء تطبيقين منفصلين دون تكرار "
        "المنطق المشترك. يمنع اختلاف applicationId ولي الأمر من الوصول السهل "
        "لواجهة الرقابة على جهاز الطفل.",
        indent=0.75,
    )
    bullets(
        doc,
        [
            "src/main/: شبكة، مراقبة، Room، أكاديمية، خدمات خلفية.",
            "src/parent/: ParentMainActivity، ParentScreenTimeActivity، ParentSettingsActivity.",
            "src/child/: موارد وإعدادات نكهة الطفل.",
            "أوامر البناء: assembleChildDebug و assembleParentDebug.",
        ],
    )


def server_implementation_ar(doc, heading, para, table, bullets):
    heading(doc, "5.2.2 قاعدة البيانات والسيرفر", 3)
    para(
        doc,
        "ملف server.py يوفّر واجهات REST أحادية (monolithic Flask). يُنشأ المخطط "
        "عبر init_db() ويُخزَّن في parent_control.db. يدعم الربط، السياسات، "
        "التنبيهات، التقارير، سجل التدقيق، وتنظيف البيانات القديمة حسب retention.",
        indent=0.75,
    )
    table(
        doc,
        ["المسار", "الوظيفة"],
        [
            ("POST /send-email-code", "إرسال OTP تحقق بريد ولي الأمر"),
            ("POST /verify-email-code", "التحقق من OTP البريد"),
            ("POST /register-child-device", "تسجيل جهاز الطفل وإرسال CHILD"),
            ("POST /send-link-code", "إرسال OTP الربط"),
            ("POST /link-child", "إتمام الربط وحفظ سجل الطفل"),
            ("GET /child-link-status", "استعلام حالة الربط من جهاز الطفل"),
            ("GET /child-dashboard", "مؤشرات لوحة ولي الأمر"),
            ("POST /child-heartbeat", "تحديث last_seen_ms"),
            ("POST /add-alert", "تسجيل تنبيه من جهاز الطفل"),
            ("GET /alerts", "جلب تنبيهات لولي الأمر"),
            ("POST /send-command", "أوامر حظر/تجميد/سماح"),
            ("GET /get-command", "سحب الأوامر من جهاز الطفل"),
            ("GET/POST /screen-time-policy", "سياسة وقت الشاشة"),
            ("POST /upload-usage", "رفع إحصائيات الاستخدام"),
            ("GET /weekly-chart", "بيانات رسم 7 أيام"),
            ("GET/POST /guardian-settings", "إعدادات retention والبريد"),
            ("GET /audit-log", "سجل التدقيق"),
            ("GET /health", "فحص جاهزية السيرفر (عام)"),
        ],
        title="جدول 5.3 — أهم واجهات API المنفّذة",
    )
    bullets(
        doc,
        [
            "catalog.json: قائمة حظر افتراضية 101+ package ومواقع.",
            "_cleanup_old_data(): حذف تلقائي بعد retention_days (7–90).",
            "إرجاع 200 مع child_code حتى عند فشل إرسال البريد (إصلاح v1.3).",
            "/health معفى من API key لإيقاظ السيرفر قبل الربط.",
        ],
    )


def child_app_ar(doc, heading, para, table, bullets):
    heading(doc, "5.2.3 تطبيق الطفل (أكاديمية العباقرة)", 3)
    para(
        doc,
        "يبدأ المسار من PermissionsLauncherActivity ثم ChildUiRouter الذي يوجّه "
        "إلى التسجيل أو الصلاحيات أو واجهة الأكاديمية. بعد الربط يرى الطفل "
        "تطبيقاً تعليمياً/ترفيهياً بينما تعمل المراقبة في الخلفية.",
        indent=0.75,
    )
    bullets(
        doc,
        [
            "ChildRegistrationActivity — POST /register-child-device، polling /child-link-status كل 3ث.",
            "ChildPermissionsActivity — Usage Access، Accessibility، إشعارات، بطارية، تخزين.",
            "AcademyMenuActivity + أنشطة الألعاب — واجهة مموّهة؛ Chaquopy academy_game.py (نكهة child).",
            "ParentSyncService — Foreground Service: heartbeat، مزامنة سياسات، EnforcementEngine.",
            "ContentFilterAccessibilityService — فحص نصوص عبر MonitoredAppRegistry و SafetyKeywordCatalog.",
            "EnforcementEngine — حظر/تجميد التطبيقات الأمامية؛ BlockWarningActivity.",
            "MediaLibraryScanner — فحص MediaStore كل 6 ساعات (حد 8 تنبيهات، 14 يوماً).",
            "ScreenTimeTracker / ScreenTimeEnforcer — حدود يومية ووقت نوم.",
            "OutboxRepository + UsageUploadHelper — تخزين مؤقت عند انقطاع الشبكة.",
            "MonitoringWorker + BootReceiver — استمرار المراقبة بعد إعادة التشغيل.",
        ],
    )
    table(
        doc,
        ["المعامل", "القيمة", "المصدر"],
        [
            ("فئات الكلمات الخطرة", "11 فئة", "SafetyKeywordCatalog.kt"),
            ("إجمالي الكلمات", "100+", "SafetyKeywordCatalog + catalog.json"),
            ("تطبيقات مراسلة مراقبة", "18 package", "MonitoredAppRegistry.kt"),
            ("متصفحات مراقبة", "10+", "MonitoredAppRegistry.kt"),
            ("فترة فحص الوسائط", "6 ساعات", "MediaLibraryScanner.kt"),
            ("polling حالة الربط", "3 ثوانٍ", "ChildRegistrationActivity.kt"),
        ],
        title="جدول 5.4 — معاملات المراقبة على جهاز الطفل",
    )
    table(
        doc,
        ["الصلاحية", "الغرض", "الإلزام"],
        [
            ("Usage Access", "إحصائيات وقت التطبيقات", "إلزامي"),
            ("Accessibility Service", "قراءة النصوص والروابط", "إلزامي"),
            ("Notifications", "تنبيهات محلية للحدود", "مستحسن"),
            ("Battery optimization", "استمرار الخدمة بالخلفية", "مستحسن"),
            ("READ_MEDIA_*", "فحص ملفات الوسائط", "مستحسن"),
        ],
        title="جدول 5.5 — صلاحيات جهاز الطفل",
    )


def parent_app_ar(doc, heading, para, bullets):
    heading(doc, "5.2.4 تطبيق ولي الأمر (MY Rana)", 3)
    para(
        doc,
        "ParentMainActivity يدير معالجاً من أربع خطوات: (1) بريد Gmail ودور ولي الأمر، "
        "(2) تحقق OTP من الرسالة الأولى، (3) اسم الطفل وكود CHILD ورمز الربط من "
        "الرسالتين 2 و 3، (4) لوحة التحكم. يستخدم ChildCodeNormalizer للتحقق من "
        "صيغة CHILD ورفض لصق البريد بالخطأ.",
        indent=0.75,
    )
    bullets(
        doc,
        [
            "GuardianApi + NetworkModule — كل استدعاءات REST مع X-API-KEY وإعادة محاولة.",
            "ServerConnectionHelper — رسائل عربية لأخطاء DNS والشبكة.",
            "ParentScreenTimeActivity — SimpleBarChartView لرسم 7 أيام؛ نموذج سياسة الوقت.",
            "ParentSettingsActivity — retention 7–90 يوماً، ملخص بريد، عرض audit log.",
            "polling التنبيهات كل ~15 ثانية في onResume.",
            "أوامر فورية: حظر موقع/تطبيق، تجميد، جدولة، قائمة حظر افتراضية.",
            "ParentSession — حفظ البريد الموثّق و child_code النشط.",
        ],
    )


def gmail_linking_impl_ar(doc, heading, para, table, bullets):
    heading(doc, "5.2.5 تنفيذ الربط عبر Gmail", 3)
    para(
        doc,
        "يحقق النظام ربطاً حقيقياً عبر Gmail بثلاث رسائل مستقلة. أُجري الاختبار "
        "الميداني على جوالين بإصدار 1.3 واكتمل الربط دون أخطاء بعد معالجة مشكلات "
        "الشبكة وإصلاح استجابة السيرفر عند فشل البريد.",
        indent=0.75,
    )
    table(
        doc,
        ["الخطوة", "الرسالة", "المسار", "الجدول"],
        [
            ("1", "OTP تحقق (6 أرقام)", "/send-email-code", "email_codes"),
            ("2", "CHILD-XXXXXXXX", "/register-child-device", "child_devices"),
            ("3", "OTP ربط (6 أرقام)", "/send-link-code ثم /link-child", "children"),
        ],
        title="جدول 5.6 — تسلسل الربط المنفّذ",
    )
    bullets(
        doc,
        [
            "مثال اختبار ناجح: CHILD-88278A25.",
            "ChildCodeNormalizer.isValid() يرفض لصق البريد بدل كود CHILD.",
            "إيقاظ السيرفر عبر GET /health قبل أول طلب من التطبيق.",
            "روابط APK v1.3 على GitHub: releases/app-child-debug.apk و app-parent-debug.apk.",
        ],
    )


def security_deploy_ar(doc, heading, para, table, bullets):
    heading(doc, "5.2.6 الأمان والنشر", 3)
    bullets(
        doc,
        [
            "HTTPS إلزامي بين التطبيقات و Render.",
            "X-API-KEY على الطلبات المحمية؛ /health عام للإيقاظ.",
            "OTP محدود زمنياً في قاعدة البيانات.",
            "فصل applicationId بين الطفل وولي الأمر.",
            "Audit Log لعمليات الحظر وتغيير الإعدادات.",
            "حذف تلقائي للسجلات بعد retention_days (افتراضي 30).",
        ],
    )
    table(
        doc,
        ["العنصر", "القيمة"],
        [
            ("رابط السيرفر", "https://parental-server-4mms.onrender.com"),
            ("مستودع GitHub", "github.com/wh8ps5kwwx-dev/parental-server"),
            ("أمر بناء الطفل", "./gradlew assembleChildDebug"),
            ("أمر بناء ولي الأمر", "./gradlew assembleParentDebug"),
        ],
        title="جدول 5.7 — النشر والتوزيع",
    )


def implementation_section_ar(doc, heading, para, bullets, table):
    heading(doc, "5.2 التنفيذ", 2)
    para(
        doc,
        "يوضح هذا القسم التنفيذ الفعلي على ثلاثة محاور: تطبيق Android (نكهتان)، "
        "السيرفر السحابي، وآلية الربط. لم يُستخدم Flutter أو XAMPP أو MySQL؛ "
        "المنفّذ هو Kotlin + Flask + SQLite حسب الكود الحالي.",
        indent=0.75,
    )
    android_structure_ar(doc, heading, para, bullets)
    server_implementation_ar(doc, heading, para, table, bullets)
    child_app_ar(doc, heading, para, table, bullets)
    parent_app_ar(doc, heading, para, bullets)
    gmail_linking_impl_ar(doc, heading, para, table, bullets)
    security_deploy_ar(doc, heading, para, table, bullets)

    heading(doc, "5.2.7 تحديات التنفيذ وحلولها", 3)
    table(
        doc,
        ["التحدي", "الحل المنفّذ"],
        [
            ("نوم السيرفر على Render", "GET /health لإيقاظ مسبق؛ زيادة مهلة الاتصال"),
            ("DNS: Unable to resolve host", "ServerConnectionHelper + تجربة 4G"),
            ("فشل بريد مع تسجيل ناجح", "إرجاع 200 + child_code من السيرفر"),
            ("لصق بريد بدل CHILD", "ChildCodeNormalizer + AlertDialog"),
            ("استمرار المراقبة", "Foreground Service + BootReceiver + Workers"),
            ("انقطاع الشبكة", "Room OutboxRepository"),
            ("الخصوصية مقابل الحماية", "واجهة أكاديمية + تنبيهات لولي الأمر فقط"),
        ],
        title="جدول 5.8 — تحديات التنفيذ وحلولها",
    )


def screenshots_ar(doc, heading, para, figure):
    heading(doc, "5.3 صور النظام", 2)
    para(
        doc,
        "توثّق لقطات الشاشة التالية التنفيذ الفعلي على أجهزة Android. "
        "تُدرج الصور من الاختبار الميداني (إصدار 1.3) في الأماكن المخصّصة أدناه.",
        indent=0.75,
    )
    shots = [
        ("شكل 5.1", "شاشة تسجيل الطفل وإرسال طلب CHILD"),
        ("شكل 5.2", "رسالة Gmail تحتوي CHILD-88278A25"),
        ("شكل 5.3", "شاشة ربط ولي الأمر — إدخال CHILD و OTP"),
        ("شكل 5.4", "لوحة التحكم بعد اكتمال الربط"),
        ("شكل 5.5", "لوحة وقت الاستخدام والمؤشرات الملونة"),
        ("شكل 5.6", "شاشة الصلاحيات على جوال الطفل"),
        ("شكل 5.7", "تنبيه عند اكتشاف كلمة خطرة (مثال: قمار)"),
        ("شكل 5.8", "استجابة السيرفر — status: running"),
    ]
    for label, cap in shots:
        figure(doc, label, cap)


def testing_ar(doc, heading, para, table, bullets):
    heading(doc, "5.4 الاختبارات", 2)
    heading(doc, "5.4.1 أنواع الاختبار", 3)
    para(
        doc,
        "اتبعت منهجية اختبار متعددة المستويات متوافقة مع هندسة البرمجيات: "
        "وحدة، تكامل، نظام، وقبول مستخدم.",
        indent=0.75,
    )
    table(
        doc,
        ["النوع", "النطاق", "الأداة / السيناريو"],
        [
            ("Unit", "SafetyKeywordCatalog، ChildCodeNormalizer", "JUnit / منطق Kotlin"),
            ("Integration", "مسارات REST بعد النشر", "test_after_deploy.py"),
            ("System", "ربط جوالين + مزامنة", "APK v1.3 على شبكة حقيقية"),
            ("UAT", "سيناريو Gmail كامل", "ولي أمر + طفل فعليان"),
            ("Regression", "إصلاحات DNS و OTP", "إعادة TC-03 بعد v1.3"),
        ],
        title="جدول 5.9 — أنواع الاختبار",
    )

    heading(doc, "5.4.2 حالات الاختبار", 3)
    tests = [
        ("TC-01", "send-email-code", "OTP في Gmail", "OTP وصل", "Pass", ""),
        ("TC-02", "register-child-device", "CHILD في Gmail", "CHILD-88278A25", "Pass", ""),
        ("TC-03", "link-child", "linked=1", "اكتمل الربط", "Pass", "v1.3 جوالين"),
        ("TC-04", "child-dashboard", "مؤشرات usage", "تظهر بعد الربط", "Pass", ""),
        ("TC-05", "keyword alert", "تنبيه قمار", "يظهر في لوحة الأم", "Pending", "يحتاج اختبار محتوى"),
        ("TC-06", "blocklist catalog", "101+ package", "HTTP 200", "Pass", "API"),
        ("TC-07", "weekly-report", "رسم 7 أيام", "HTTP 200", "Pass", ""),
        ("TC-08", "Render cold start", "إيقاظ /health", "Chrome أو تطبيق", "Workaround", ""),
        ("TC-09", "ChildCodeNormalizer", "رفض بريد كـ CHILD", "AlertDialog", "Pass", "v1.3"),
        ("TC-10", "register + email fail", "200 + child_code", "حفظ محلي", "Pass", "إصلاح سيرفر"),
    ]
    table(
        doc,
        ["ID", "السيناريو", "المتوقع", "الفعلي", "الحالة", "ملاحظات"],
        tests,
        title="جدول 5.10 — حالات الاختبار",
    )

    heading(doc, "5.4.3 معايير القبول", 3)
    bullets(
        doc,
        [
            "نجاح الربط الثلاثي عبر Gmail دون إدخال يدوي لبيانات حساسة على شاشة الطفل.",
            "ظهور الطفل «متصل» في لوحة ولي الأمر خلال دقيقة من heartbeat.",
            "تسجيل تنبيه في alerts خلال 15 ثانية من polling عند اكتشاف كلمة خطرة.",
            "تطبيق حظر تطبيق خلال دورة مزامنة السياسات (~60 ثانية).",
        ],
    )


def results_ar(doc, heading, para, table, figure):
    heading(doc, "5.5 النتائج والمقارنة", 2)
    para(
        doc,
        "أظهرت الاختبارات تحقق الأهداف الرئيسية للمشروع ضمن النطاق المحدد "
        "(Android، طفل واحد في المرحلة الأولى، استضافة Render المجانية).",
        indent=0.75,
    )
    table(
        doc,
        ["المعيار", "قبل (تقليدي / يدوي)", "بعد (MY Rana v1.3)"],
        [
            ("الربط الآمن", "حساب Google أو إدخال يدوي", "Gmail — 3 رسائل OTP"),
            ("واجهة الطفل", "تطبيق رقابة ظاهر", "أكاديمية العباقرة"),
            ("تنبيهات المحتوى", "محدودة / إنجليزية", "100+ كلمة عربية/ثنائية اللغة"),
            ("التقارير", "غير متوفرة", "يومي + أسبوعي + رسم"),
            ("سجل التدقيق", "غير موجود", "audit_log على السيرفر"),
            ("العمل دون شبكة", "فقدان بيانات", "Outbox + إعادة إرسال"),
        ],
        title="جدول 5.11 — مقارنة النتائج",
    )
    figure(doc, "شكل 5.9", "رسم بياني — توزيع وقت الاستخدام على التطبيقات")
    figure(doc, "شكل 5.10", "مقارنة مؤشرات قبل وبعد التطبيق")

    heading(doc, "5.5.1 القيود المؤكدة من الكود", 3)
    table(
        doc,
        ["القيد", "الدليل في الكود"],
        [
            ("لا Device Admin", "غياب DevicePolicyManager"),
            ("لا FCM push", "polling HTTP كل 15 ثانية"),
            ("لا تشفير SQLite", "Room بدون SQLCipher"),
            ("لا iOS", "نطاق المشروع Android فقط"),
            ("إقلاع بارد Render", "مهلة انتظار أطول على الطبقة المجانية"),
            ("طفل نشط واحد في التطبيق", "ParentSession يخزّن child واحد"),
        ],
        title="جدول 5.12 — قيود التنفيذ الحالية",
    )


def conclusions_ar(doc, heading, para, bullets):
    heading(doc, "5.6 الاستنتاجات", 2)
    para(
        doc,
        "تحقق الهدف الرئيسي للبحث: تصميم وتنفيذ نظام رقابة أبوية متكامل يوازن "
        "بين الأمن الرقمي وراحة الطفل النفسية. نجح الربط عبر Gmail على أجهزة "
        "حقيقية، وعملت طبقة المراقبة (Usage، Accessibility، وقت الشاشة، قائمة "
        "الحظر) كما هو مصمم في الفصول السابقة.",
        indent=0.75,
    )
    bullets(
        doc,
        [
            "إثبات جدوى الواجهة المموّهة مع مراقبة خلفية على Android.",
            "إثبات نموذج ربط ثلاثي المراحل عبر Gmail في مشروع تخرج مفتوح المصدر.",
            "توفير بنية قابلة للتوسع (عدة أطفال على السيرفر، سياسات لكل جهاز).",
        ],
    )

    heading(doc, "5.7 التوصيات", 2)
    bullets(
        doc,
        [
            "إضافة Firebase Cloud Messaging لتنبيهات فورية بدل polling.",
            "دعم iOS أو واجهة ويب لولي الأمر.",
            "تقارير PDF شهرية جاهزة للطباعة.",
            "واجهة لتخصيص كلمات خطرة من لوحة ولي الأمر.",
            "قرص دائم على Render أو استضافة مدفوعة لتقليل الإقلاع البارد.",
            "تشفير SQLite at-rest للبيانات الحساسة.",
        ],
    )

    heading(doc, "5.8 الخاتمة", 2)
    para(
        doc,
        "أُنجز إطار رقابة أبوية ذكي (MY Rana / أكاديمية العباقرة) قابل للتطبيق "
        "والتوسع والتوثيق الأكاديمي. يجمع المشروع بين Kotlin و Flask وربط Gmail "
        "ومراقبة Android متعددة الطبقات، ويفتح مجالاً لبحوث مقارنة مع الحلول "
        "التجارية ودراسات تأثير الواجهة المموّهة على سلوك الأطفال الرقمي.",
        indent=0.75,
    )

    heading(doc, "5.9 ملخص الفصل", 2)
    para(
        doc,
        "عرض الفصل الخامس بيئة التطوير، التنفيذ التفصيلي للتطبيقين والسيرفر، "
        "لقطات النظام، الاختبارات ونتائجها، والاستنتاجات والتوصيات. مع إدراج "
        "المخططات والصور من الاختبار الميداني، يكتمل المستند ضمن النطاق المتوقع "
        "لمشاريع نظم المعلومات (70–90 صفحة).",
        indent=0.75,
    )


def write_chapter5_ar(doc, heading, para, bullets, table, figure):
    """كتابة الفصل الخامس كاملاً."""
    heading(doc, "الفصل الخامس: التنفيذ والاختبار", 1)
    para(
        doc,
        "يتناول هذا الفصل الجوانب العملية لتنفيذ نظام الرقابة الأبوية المقترح "
        "واختباره على أجهزة حقيقية. يصف بيئة التطوير، هيكل المشروع، تنفيذ "
        "تطبيقي Android والسيرفر، آلية الربط عبر Gmail، محركات المراقبة، "
        "وحالات الاختبار ونتائجها.",
        indent=0.75,
    )

    dev_environment_ar(doc, heading, para, table)
    implementation_section_ar(doc, heading, para, bullets, table)
    screenshots_ar(doc, heading, para, figure)
    testing_ar(doc, heading, para, table, bullets)
    results_ar(doc, heading, para, table, figure)
    conclusions_ar(doc, heading, para, bullets)
    doc.add_page_break()

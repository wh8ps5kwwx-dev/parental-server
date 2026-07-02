# -*- coding: utf-8 -*-
"""الفصل الرابع — تصميم النظام (عربي)."""

from __future__ import annotations


def architecture_ar(doc, heading, para, table, figure):
    heading(doc, "4.1 التصميم المعماري", 2)
    para(
        doc,
        "يُصمَّم نظام MY Rana وفق بنية ثلاثية الطبقات (Three-Tier Architecture): "
        "طبقة العرض (Presentation) على جهازي Android، طبقة الأعمال (Business Logic) "
        "على سيرفر Flask، وطبقة البيانات (Data) عبر SQLite على السيرفر و Room على "
        "جهاز الطفل. يفصل Product Flavors بين تطبيق الطفل (child) وتطبيق ولي الأمر (parent) "
        "من مستودع Kotlin واحد.",
        indent=0.75,
    )
    figure(doc, "شكل 4.1", "التصميم المعماري للنظام — System Architecture")
    table(
        doc,
        ["الطبقة", "المكوّن", "التقنية", "الوظيفة"],
        [
            ("العرض — طفل", "أكاديمية العباقرة", "Kotlin / child flavor", "واجهة مموّهة + مراقبة خلفية"),
            ("العرض — ولي الأمر", "MY Rana", "Kotlin / parent flavor", "لوحة تحكم وتقارير وتنبيهات"),
            ("الأعمال", "server.py", "Python Flask", "REST API، ربط Gmail، سياسات، تنبيهات"),
            ("البيانات — سحابة", "SQLite", "parent_control.db", "أطفال، سياسات، تنبيهات، usage"),
            ("البيانات — محلي", "Room", "AppDatabase", "Outbox، حظر مؤقت، أحداث وقت الشاشة"),
            ("خارجي", "Resend → Gmail", "SMTP/API", "OTP تحقق، CHILD، OTP ربط"),
        ],
        title="جدول 4.1 — طبقات النظام المعماري",
    )
    para(
        doc,
        "يتم الاتصال بين الطبقات عبر HTTPS مع رأس X-API-KEY. يستخدم تطبيق الطفل "
        "ParentSyncService كخدمة أمامية (Foreground Service) للمزامنة الدورية؛ "
        "ويستخدم تطبيق ولي الأمر GuardianApi مع polling كل نحو 15 ثانية لتحديث المؤشرات.",
        indent=0.75,
    )


def database_design_ar(doc, heading, para, table):
    heading(doc, "4.2 تصميم قاعدة البيانات", 2)
    para(
        doc,
        "تُخزَّن البيانات المركزية في SQLite على السيرفر (ملف parent_control.db). "
        "يُنشأ المخطط عبر init_db() في server.py. الجداول التالية تمثّل التصميم المنطقي "
        "النهائي بعد دمج جداول التسجيل والربط.",
        indent=0.75,
    )

    db_tables = [
        (
            "email_codes",
            "رموز تحقق بريد ولي الأمر",
            [
                ("id", "INTEGER", "PK", "AUTOINCREMENT"),
                ("email", "TEXT", "", "بريد Gmail"),
                ("code", "TEXT", "", "OTP 6 أرقام"),
                ("verified", "INTEGER", "", "0/1"),
                ("created_at", "TEXT", "", "طابع زمني"),
            ],
        ),
        (
            "child_devices",
            "أجهزة الأطفال قبل إتمام الربط",
            [
                ("child_code", "TEXT", "UNIQUE", "CHILD-XXXXXXXX"),
                ("child_email", "TEXT", "", "بريد ولي الأمر"),
                ("device_verify_code", "TEXT", "", "OTP الربط"),
                ("linked", "INTEGER", "", "حالة الربط"),
                ("created_at", "TEXT", "", ""),
            ],
        ),
        (
            "children",
            "الأطفال المرتبطون بولي الأمر",
            [
                ("child_code", "TEXT", "UNIQUE", "معرف الطفل"),
                ("name", "TEXT", "", "اسم الطفل"),
                ("guardian_email", "TEXT", "", "FK → guardians"),
                ("device", "TEXT", "", "اسم الجهاز"),
                ("linked_at", "TEXT", "", "تاريخ الربط"),
            ],
        ),
        (
            "device_policies",
            "سياسات الحظر لكل جهاز",
            [
                ("device_id", "TEXT", "PK", "= child_code"),
                ("blocked_packages", "TEXT", "", "JSON قائمة packages"),
                ("blocked_hosts", "TEXT", "", "JSON نطاقات"),
                ("video_keywords", "TEXT", "", "كلمات فيديو"),
                ("revision", "INTEGER", "", "رقم مراجعة للمزامنة"),
            ],
        ),
        (
            "alerts",
            "تنبيهات المحتوى والأحداث",
            [
                ("id", "INTEGER", "PK", "AUTOINCREMENT"),
                ("child_code", "TEXT", "FK", "الطفل"),
                ("message", "TEXT", "", "نص التنبيه"),
                ("time", "TEXT", "", "وقت الإنشاء"),
            ],
        ),
        (
            "usage_daily",
            "تجميع استخدام التطبيقات يومياً",
            [
                ("child_code", "TEXT", "PK*", "مع الطفل واليوم"),
                ("day", "TEXT", "PK*", "YYYY-MM-DD"),
                ("package_name", "TEXT", "PK*", "اسم التطبيق"),
                ("total_seconds", "INTEGER", "", "مدة الاستخدام"),
            ],
        ),
        (
            "screen_time_policies",
            "حدود وقت الشاشة والنوم",
            [
                ("child_code", "TEXT", "PK", ""),
                ("policy_json", "TEXT", "", "حدود وألوان تحذير"),
                ("updated_at", "TEXT", "", ""),
            ],
        ),
        (
            "audit_log",
            "سجل تدقيق ولي الأمر",
            [
                ("id", "INTEGER", "PK", ""),
                ("guardian_email", "TEXT", "", ""),
                ("child_code", "TEXT", "", ""),
                ("action", "TEXT", "", "نوع الحدث"),
                ("detail", "TEXT", "", "تفاصيل"),
            ],
        ),
        (
            "guardian_settings",
            "إعدادات ولي الأمر",
            [
                ("guardian_email", "TEXT", "PK", ""),
                ("settings_json", "TEXT", "", "retention، تنبيهات"),
                ("updated_at", "TEXT", "", ""),
            ],
        ),
    ]

    for idx, (tname, desc, fields) in enumerate(db_tables, start=1):
        heading(doc, f"4.2.{idx} جدول {tname}", 3)
        para(doc, desc + ".", indent=0.5)
        table(doc, ["الحقل", "النوع", "مفتاح", "الوصف"], fields)


def logical_design_ar(doc, heading, para, figure, table):
    heading(doc, "4.3 التصميم المنطقي", 2)
    para(
        doc,
        "يربط التصميم المنطقي بين كيانات السيرفر وكيانات Room على جهاز الطفل. "
        "عند انقطاع الشبكة، يُخزَّن الحدث في PendingOutboxEntity ثم يُرسل عند عودة "
        "الاتصال. تُزامَن سياسات device_policies مع BlockedAppEntity و BlockedSiteEntity محلياً.",
        indent=0.75,
    )
    figure(doc, "شكل 4.2", "مخطط قاعدة البيانات المنطقي — سيرفر + Room")
    table(
        doc,
        ["الكيان المحلي (Room)", "الجدول السحابي", "الاتجاه"],
        [
            ("PendingOutboxEntity", "alerts / reports", "طفل → سيرفر"),
            ("BlockedAppEntity", "device_policies.blocked_packages", "سيرفر → طفل"),
            ("BlockedSiteEntity", "device_policies.blocked_hosts", "سيرفر → طفل"),
            ("DailyAppUsageEntity", "usage_daily", "طفل → سيرفر"),
            ("ScreenTimeEventEntity", "screen_time_events", "طفل → سيرفر"),
            ("SyncStateEntity", "revision في policies", "مزامنة ثنائية"),
        ],
        title="جدول 4.2 — مطابقة Room مع SQLite",
    )


def uml_diagrams_ar(doc, heading, para, figure):
    heading(doc, "4.4 مخططات UML", 2)
    para(
        doc,
        "تُكمّل مخططات UML التحليل في الفصل الثالث بعرض التصميم الساكن (Class) "
        "والتفاعلي (Sequence) والسلوكي (Activity). تُستمد أسماء الفئات من الكود "
        "الفعلي في مشروع MYRana.",
        indent=0.75,
    )

    heading(doc, "4.4.1 مخطط الفئات Class Diagram", 3)
    para(
        doc,
        "الفئات الرئيسية: GuardianApi و NetworkModule (ولي الأمر)، ParentSyncService "
        "و EnforcementEngine و ContentFilterAccessibilityService (طفل)، SafetyKeywordCatalog "
        "و ChildCodeNormalizer (مشترك)، و server.py كوحدة خدمة سحابية.",
        indent=0.75,
    )
    figure(doc, "شكل 4.3", "مخطط الفئات — GuardianApi, NetworkModule, Services")

    heading(doc, "4.4.2 مخطط التسلسل Sequence — الربط", 3)
    para(
        doc,
        "يُوضح تسلسل الرسائل بين تطبيق ولي الأمر، السيرفر، Gmail، وتطبيق الطفل "
        "خلال عملية الربط الثلاثية (تحقق بريد → CHILD → OTP ربط).",
        indent=0.75,
    )
    figure(doc, "شكل 4.4", "مخطط تسلسل ربط Gmail — Sequence Diagram")

    heading(doc, "4.4.3 مخطط التسلسل Sequence — التنبيه", 3)
    figure(doc, "شكل 4.5", "مخطط تسلسل عند اكتشاف كلمة خطرة")

    heading(doc, "4.4.4 مخطط النشاط Activity — وقت الشاشة", 3)
    para(
        doc,
        "يُنفَّذ فرض حدود وقت الشاشة عبر ScreenTimeEnforcer و EnforcementEngine: "
        "قراءة السياسة، مقارنة الاستخدام اليومي، إظهار تحذير ملون، ثم حظر عند التجاوز.",
        indent=0.75,
    )
    figure(doc, "شكل 4.6", "مخطط نشاط فرض وقت الشاشة — Activity Diagram")

    heading(doc, "4.4.5 مخطط النشاط Activity — تسجيل الطفل", 3)
    figure(doc, "شكل 4.7", "مخطط نشاط تسجيل جهاز الطفل وانتظار الربط")


def ui_design_ar(doc, heading, para, table, figure):
    heading(doc, "4.5 تصميم الواجهات", 2)
    para(
        doc,
        "تُصمَّم واجهات المستخدم وفق مبدأ البساطة لولي الأمر والإخفاء للطفل. "
        "تطبيق ولي الأمر يمر بأربع خطوات: تحقق Gmail، إدخال CHILD، إدخال OTP الربط، "
        "لوحة التحكم. تطبيق الطفل يبدأ بالتسجيل ثم شاشة انتظار الربط ثم الصلاحيات "
        "ثم واجهة الأكاديمية.",
        indent=0.75,
    )
    screens = [
        (
            "تسجيل الطفل",
            "ChildRegistrationActivity",
            "إدخال بريد الأم، إرسال الطلب، عرض CHILD، حفظ محلي",
            "شكل 4.8",
        ),
        (
            "انتظار الربط",
            "LinkWaitingActivity",
            "استعلام child-link-status، رسالة انتظار",
            "شكل 4.9",
        ),
        (
            "الصلاحيات",
            "PermissionsActivity",
            "Usage Access، Accessibility، إشعارات",
            "شكل 4.10",
        ),
        (
            "الأكاديمية",
            "AcademyMainActivity",
            "قائمة ألعاب/دروس — واجهة مموّهة",
            "شكل 4.11",
        ),
        (
            "تسجيل ولي الأمر",
            "ParentMainActivity — خطوة 1",
            "Gmail + OTP من الرسالة الأولى",
            "شكل 4.12",
        ),
        (
            "ربط الطفل",
            "ParentMainActivity — خطوة 2–3",
            "CHILD + OTP الربط، التحقق من الصيغة",
            "شكل 4.13",
        ),
        (
            "لوحة التحكم",
            "ParentMainActivity — لوحة",
            "حظر تطبيقات، تنبيهات، آخر اتصال",
            "شكل 4.14",
        ),
        (
            "لوحة وقت الاستخدام",
            "ParentScreenTimeActivity",
            "مؤشرات، ألوان، رسم 7 أيام",
            "شكل 4.15",
        ),
    ]
    table(
        doc,
        ["الشاشة", "النشاط / المكوّن", "الوظائف", "مرجع الشكل"],
        screens,
        title="جدول 4.3 — شاشات النظام",
    )
    for name, _, desc, fig in screens:
        heading(doc, f"واجهة: {name}", 3)
        para(doc, f"الوصف: {desc}.", indent=0.5)
        figure(doc, fig, f"لقطة شاشة — {name}")


def messages_reports_ar(doc, heading, para, table, bullets):
    heading(doc, "4.6 تصميم الرسائل والتقارير", 2)
    para(
        doc,
        "يُصمَّم نظام الرسائل ليوفّر تغذية راجعة فورية لولي الأمر دون إزعاج الطفل. "
        "تُعرض الأخطاء بصياغة عربية واضحة (ServerConnectionHelper) وتُرسل التقارير "
        "عبر الواجهة والبريد.",
        indent=0.75,
    )
    table(
        doc,
        ["النوع", "مثال", "القناة"],
        [
            ("نجاح", "تم الربط بنجاح", "Toast / شاشة ولي الأمر"),
            ("نجاح", "تم إرسال رمز التحقق", "واجهة + Gmail"),
            ("خطأ", "رمز غير صحيح", "AlertDialog"),
            ("خطأ", "تعذّر الاتصال بالسيرفر — تحققي من الإنترنت", "عربي — ServerConnectionHelper"),
            ("تنبيه", "كلمة خطرة: قمار", "لوحة ولي الأمر + alerts"),
            ("تقرير يومي", "ملخص الاستخدام", "ParentScreenTime + بريد"),
            ("تقرير أسبوعي", "رسم 7 أيام", "GET /weekly-report"),
        ],
        title="جدول 4.4 — تصميم الرسائل والتقارير",
    )
    bullets(
        doc,
        [
            "رسائل Gmail الثلاث موثّقة في دليل المستخدم (ملحق و).",
            "ألوان مؤشر وقت الشاشة: أخضر (آمن)، أصفر (تحذير)، أحمر (تجاوز).",
            "Audit Log يسجّل: ربط، حظر، تغيير إعدادات retention.",
        ],
    )


def chapter4_summary_ar(doc, heading, para):
    heading(doc, "4.7 ملخص الفصل", 2)
    para(
        doc,
        "عرض الفصل الرابع التصميم المعماري ثلاثي الطبقات، وجداول قاعدة البيانات على "
        "السيرفر ومطابقتها مع Room، ومخططات UML (Class، Sequence، Activity)، وتصميم "
        "الواجهات والرسائل. يمهّد الفصل الخامس لتنفيذ هذا التصميم واختباره على أجهزة حقيقية.",
        indent=0.75,
    )


def write_chapter4_ar(doc, heading, para, bullets, table, figure):
    """كتابة الفصل الرابع كاملاً."""
    heading(doc, "الفصل الرابع: تصميم النظام", 1)
    para(
        doc,
        "بعد تحليل المتطلبات في الفصل الثالث، يقدّم هذا الفصل التصميم التفصيلي لنظام "
        "MY Rana: البنية المعمارية، قاعدة البيانات، المخططات UML، وواجهات المستخدم "
        "ونظام الرسائل والتقارير.",
        indent=0.75,
    )

    architecture_ar(doc, heading, para, table, figure)
    database_design_ar(doc, heading, para, table)
    logical_design_ar(doc, heading, para, figure, table)
    uml_diagrams_ar(doc, heading, para, figure)
    ui_design_ar(doc, heading, para, table, figure)
    messages_reports_ar(doc, heading, para, table, bullets)
    chapter4_summary_ar(doc, heading, para)
    doc.add_page_break()

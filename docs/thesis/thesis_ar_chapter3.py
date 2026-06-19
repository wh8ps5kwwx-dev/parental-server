# -*- coding: utf-8 -*-
"""الفصل الثالث — تحليل النظام (عربي)."""

from __future__ import annotations


def current_systems_ar(doc, heading, para, table, figure):
    heading(doc, "3.1 دراسة الأنظمة الحالية", 2)
    para(
        doc,
        "قبل تحديد متطلبات نظام MY Rana، أُجريت دراسة مقارنة للأنظمة السائدة في "
        "الرقابة الأبوية الرقمية. تُصنَّف هذه الأنظمة إلى: حلول مدمجة في النظام "
        "(Google Family Link، Apple Screen Time)، تطبيقات تجارية (Qustodio، Norton Family، "
        "Bark)، وحلول مؤسسية (MDM). تختلف كل فئة في نموذج الربط، عمق المراقبة، "
        "والتأثير النفسي على الطفل.",
        indent=0.75,
    )
    table(
        doc,
        ["النظام", "الفئة", "المميزات", "القيود"],
        [
            ("Google Family Link", "مدمج", "مجاني، ربط حساب Google", "Android فقط جزئياً؛ واجهة رقابة واضحة"),
            ("Apple Screen Time", "مدمج", "تكامل iOS/macOS", "لا يغطي Android في مشروعنا"),
            ("Qustodio", "تجاري", "تقارير، فلترة، حدود زمنية", "اشتراك مدفوع؛ مراقبة ظاهرة"),
            ("Bark", "تجاري", "تحليل نصوص ووسائط", "موجّه للمراهقين؛ خصوصية محل الجدل"),
            ("Norton Family", "تجاري", "حظر مواقع وتطبيقات", "تكلفة؛ اعتماد على سحابة مغلقة"),
            ("MY Rana (مقترح)", "مخصص", "واجهة مموّهة، Gmail، عربي", "Android فقط؛ لا iOS في النطاق"),
        ],
        title="جدول 3.1 — مقارنة الأنظمة الحالية",
    )
    para(
        doc,
        "تبيّن أن الأنظمة التجارية والمدمجة تعتمد غالباً على واجهة رقابة صريحة عند الطفل، "
        "ما يزيد احتمال المقاومة السلوكية (Ghosh et al., 2018). كما أن الربط عبر حساب "
        "Google وحده لا يكفي لإثبات ملكية ولي الأمر في سياق أكاديمي مفتوح المصدر. "
        "لذلك يُقترح نموذج ربط Gmail ثلاثي المراحل مع واجهة طفل مموّهة.",
        indent=0.75,
    )
    figure(doc, "شكل 3.1", "مخطط سير العمل للأنظمة الحالية (Flowchart)")


def stakeholders_ar(doc, heading, para, table, bullets):
    heading(doc, "3.2 تحليل أصحاب المصلحة", 2)
    para(
        doc,
        "يُعرَّف أصحاب المصلحة بكل من يتأثر بالنظام أو يتفاعل معه. تحليلهم يحدد "
        "المتطلبات الوظيفية وغير الوظيفية ويضمن قبول النظام عند التشغيل الفعلي.",
        indent=0.75,
    )
    table(
        doc,
        ["الفئة", "الدور", "الاحتياجات", "التفاعل مع النظام"],
        [
            ("ولي الأمر", "مشرف رئيسي", "رؤية الاستخدام، حظر، تنبيهات، تقارير", "تطبيق MY Rana"),
            ("الطفل (5–13)", "مستخدم نهائي", "لعب/تعلم دون إحساس مراقبة مباشرة", "أكاديمية العباقرة"),
            ("السيرفر السحابي", "وسيط بيانات", "تخزين، مزامنة، بريد OTP", "Flask على Render"),
            ("Gmail / Resend", "خدمة خارجية", "توصيل رموز التحقق والربط", "3 رسائل لكل عملية ربط"),
            ("المشرف الأكاديمي", "مقيّم", "توثيق UML، اختبار، أخلاقيات", "مراجعة التقرير والعرض"),
        ],
        title="جدول 3.2 — أصحاب المصلحة واحتياجاتهم",
    )
    bullets(
        doc,
        [
            "ولي الأمر: يحتاج واجهة عربية بسيطة، مؤشرات لحظية، وسجل تدقيق.",
            "الطفل: يحتاج تجربة ترفيهية/تعليمية دون إزعاج بتنبيهات رقابية على شاشته.",
            "النظام: يحتاج استمرارية عند انقطاع الشبكة (Outbox على جهاز الطفل).",
        ],
    )


def functional_requirements_ar(doc, heading, para, table):
    heading(doc, "3.3 المتطلبات الوظيفية", 2)
    para(
        doc,
        "وُثّقت المتطلبات الوظيفية (Functional Requirements) وفق معرّفات FR-xx "
        "لربطها بحالات الاستخدام واختبارات الفصل الخامس.",
        indent=0.75,
    )
    fr_rows = [
        ("FR-01", "تحقق بريد ولي الأمر عبر OTP", "UC-01", "عالي"),
        ("FR-02", "تسجيل جهاز الطفل وإرسال كود CHILD", "UC-02", "عالي"),
        ("FR-03", "ربط الطفل برمز OTP ثالث", "UC-03", "عالي"),
        ("FR-04", "لوحة مؤشرات: وقت، تطبيقات، اتصال", "UC-04", "عالي"),
        ("FR-05", "حدود زمنية يومية وألوان تحذير", "UC-08", "عالي"),
        ("FR-06", "جدول نوم وتنبيه خارج الوقت", "UC-08", "متوسط"),
        ("FR-07", "حظر مواقع وتطبيقات", "UC-05", "عالي"),
        ("FR-08", "اكتشاف كلمات خطرة (100+ فئة)", "UC-06", "عالي"),
        ("FR-09", "إرسال تنبيهات إلى السيرفر", "UC-06", "عالي"),
        ("FR-10", "تقارير يومية وأسبوعية", "UC-07", "متوسط"),
        ("FR-11", "سجل تدقيق Audit Log", "—", "متوسط"),
        ("FR-12", "حذف تلقائي للبيانات (retention)", "—", "متوسط"),
        ("FR-13", "ملخص بريد يومي/أسبوعي", "UC-07", "منخفض"),
        ("FR-14", "قائمة حظر مدمجة 101+ package", "UC-05", "عالي"),
        ("FR-15", "فحص ملفات وسائط محلية", "UC-06", "متوسط"),
        ("FR-16", "مزامنة سياسات الحظر من السيرفر", "UC-05", "عالي"),
        ("FR-17", "استعلام حالة الربط من جهاز الطفل", "UC-03", "عالي"),
    ]
    table(
        doc,
        ["المعرّف", "الوصف", "Use Case", "الأولوية"],
        fr_rows,
        title="جدول 3.3 — المتطلبات الوظيفية",
    )


def nonfunctional_requirements_ar(doc, heading, para, table, bullets):
    heading(doc, "3.4 المتطلبات غير الوظيفية", 2)
    table(
        doc,
        ["النوع", "المتطلب", "المعيار / القيمة"],
        [
            ("الأداء", "استجابة API بعد إيقاظ السيرفر", "< 3 ثوانٍ في الظروف الطبيعية"),
            ("الأمان", "نقل البيانات", "HTTPS + رأس X-API-KEY"),
            ("الأمان", "التحقق من الهوية", "OTP محدود الزمن عبر Gmail"),
            ("سهولة الاستخدام", "واجهة ولي الأمر", "عربية، 4 خطوات ربط موثّقة"),
            ("الاعتمادية", "انقطاع الشبكة", "Room Outbox + إعادة محاولة"),
            ("القابلية للتوسع", "عدة أطفال", "دعم في بنية قاعدة البيانات"),
            ("الخصوصية", "الاحتفاظ بالبيانات", "حذف تلقائي 7–90 يوماً"),
            ("الصيانة", "فصل النكهات", "Product Flavors: child / parent"),
            ("التوافق", "إصدارات Android", "minSdk 21، targetSdk 33"),
        ],
        title="جدول 3.4 — المتطلبات غير الوظيفية",
    )
    para(
        doc,
        "تُقيَّم المتطلبات غير الوظيفية أثناء الاختبار في الفصل الخامس؛ ويُوثَّق "
        "أي انحراف (مثل بطء Render عند الإقلاع البارد) كقيد تشغيلي مع حل بديل (إيقاظ "
        "السيرفر عبر /health قبل الربط).",
        indent=0.75,
    )


def use_case_diagram_ar(doc, heading, para, figure):
    heading(doc, "3.5 مخطط حالات الاستخدام Use Case", 2)
    para(
        doc,
        "يُظهر مخطط حالات الاستخدام (UML Use Case) التفاعل بين الفاعلين: ولي الأمر، "
        "الطفل، والنظام (كفاعل ثانوي للتنبيهات التلقائية). الفاعل الرئيسي هو ولي "
        "الأمر الذي يدير الربط والسياسات والتقارير؛ بينما يقوم الطفل بتسجيل الجهاز "
        "واللعب عبر الواجهة المموّهة.",
        indent=0.75,
    )
    figure(doc, "شكل 3.2", "مخطط حالات الاستخدام — ولي الأمر / الطفل / النظام")
    para(
        doc,
        "ملاحظة: يمكن توليد هذا المخطط من ملف docs/مخططات_وجداول_البحث.html "
        "(قسم شكل 3.2) ولصق لقطة الشاشة في Word تحت العنوان أعلاه.",
        indent=0.75,
    )


def use_case_descriptions_ar(doc, heading, para, table):
    heading(doc, "3.6 وصف حالات الاستخدام", 2)
    ucs = [
        (
            "UC-01",
            "تحقق Gmail",
            "ولي الأمر",
            "بريد Gmail + OTP من الرسالة الأولى",
            "جلسة موثقة في ParentSession",
            "1) إدخال البريد 2) POST /send-email-code 3) إدخال OTP 4) تحقق",
        ),
        (
            "UC-02",
            "تسجيل جهاز الطفل",
            "الطفل",
            "بريد ولي الأمر المسجّل مسبقاً",
            "كود CHILD-XXXXXXXX في Gmail",
            "1) إدخال البريد 2) POST /register-child-device 3) حفظ الكود محلياً",
        ),
        (
            "UC-03",
            "ربط الطفل",
            "ولي الأمر",
            "CHILD + OTP من الرسالة الثالثة",
            "linked=1 في جدول children",
            "1) إدخال CHILD 2) POST /send-link-code 3) OTP 4) POST /add-child",
        ),
        (
            "UC-04",
            "لوحة المؤشرات",
            "ولي الأمر",
            "child_code مربوط",
            "وقت الاستخدام، التطبيقات، آخر اتصال",
            "GET dashboard + polling كل ~15 ثانية",
        ),
        (
            "UC-05",
            "حظر تطبيق/موقع",
            "ولي الأمر",
            "package name أو host",
            "سياسة محدّثة على السيرفر والطفل",
            "POST block command → مزامنة device_policies",
        ),
        (
            "UC-06",
            "تنبيه محتوى خطير",
            "النظام",
            "نص من Accessibility أو بحث",
            "سجل alert لولي الأمر",
            "SafetyKeywordCatalog → POST /add-alert",
        ),
        (
            "UC-07",
            "تقارير يومية/أسبوعية",
            "ولي الأمر",
            "نطاق تاريخ",
            "رسم 7 أيام + جداول usage_daily",
            "GET /weekly-report وواجهة ParentScreenTime",
        ),
        (
            "UC-08",
            "حدود وقت ونوم",
            "ولي الأمر",
            "دقائق يومية، أوقات نوم",
            "تطبيق ScreenTimeEnforcer على الطفل",
            "screen_time_policies + schedules",
        ),
    ]
    table(
        doc,
        ["المعرف", "الحالة", "الفاعل", "المدخلات", "المخرجات", "التدفق الرئيسي"],
        ucs,
        title="جدول 3.5 — وصف حالات الاستخدام",
    )


def dfd_ar(doc, heading, para, figure, bullets):
    heading(doc, "3.7 مخططات تدفق البيانات DFD", 2)
    para(
        doc,
        "تُمثّل مخططات تدفق البيانات (Data Flow Diagram) حركة المعلومات بين الكيانات "
        "الخارجية والعمليات دون التركيز على تفاصيل التنفيذ. يُستخدم المستوى السياقي "
        "(Context) ثم المستوى 0 والمستوى 1 لتفصيل الربط والمراقبة.",
        indent=0.75,
    )
    figure(doc, "شكل 3.3", "مخطط السياق Context Diagram")
    figure(doc, "شكل 3.4", "DFD المستوى 0 — العمليات الرئيسية")
    figure(doc, "شكل 3.5", "DFD المستوى 1 — الربط والمراقبة")
    figure(doc, "شكل 3.6", "DFD المستوى 2 — معالجة التنبيهات والكلمات الخطرة")
    bullets(
        doc,
        [
            "الكيانات الخارجية: ولي الأمر، الطفل، Gmail.",
            "عملية مركزية: نظام MY Rana (تطبيقان + سيرفر).",
            "مخازن البيانات: SQLite على السيرفر، Room على جهاز الطفل.",
            "تدفقات: أوامر حظر، تقارير، تنبيهات، رموز OTP، بيانات usage.",
        ],
    )


def data_dictionary_ar(doc, heading, para, table):
    heading(doc, "3.8 قاموس البيانات", 2)
    para(
        doc,
        "يُعرّف قاموس البيانات (Data Dictionary) عناصر البيانات الرئيسية المستخدمة "
        "في الجداول والواجهات البرمجية، مع نوعها ومصدرها.",
        indent=0.75,
    )
    table(
        doc,
        ["العنصر", "النوع", "الجدول / المصدر", "الوصف"],
        [
            ("child_code", "TEXT PK", "children / child_devices", "معرف الطفل بصيغة CHILD-XXXXXXXX"),
            ("guardian_email", "TEXT", "children / guardians", "بريد Gmail لولي الأمر"),
            ("parent_email", "TEXT", "email_codes", "بريد التحقق الأولي"),
            ("linked", "INT", "child_devices", "0 غير مربوط، 1 مربوط"),
            ("device_verify_code", "TEXT", "child_devices", "OTP الربط (6 أرقام)"),
            ("last_seen_ms", "INTEGER", "child_status", "آخر اتصال بالسيرفر"),
            ("blocked_packages", "JSON/TEXT", "device_policies", "قائمة package names محظورة"),
            ("blocked_hosts", "JSON/TEXT", "device_policies", "قائمة نطاقات محظورة"),
            ("video_keywords", "JSON/TEXT", "device_policies", "كلمات فيديو إضافية"),
            ("message", "TEXT", "alerts", "نص التنبيه المرسل لولي الأمر"),
            ("policy_json", "TEXT", "screen_time_policies", "حدود الوقت والنوم"),
            ("total_seconds", "INTEGER", "usage_daily", "ثواني استخدام تطبيق في يوم"),
            ("settings_json", "TEXT", "guardian_settings", "إعدادات retention والتنبيهات"),
            ("action / detail", "TEXT", "audit_log", "حدث تدقيق وتفاصيله"),
        ],
        title="جدول 3.6 — قاموس البيانات",
    )


def erd_ar(doc, heading, para, figure, table):
    heading(doc, "3.9 نموذج الكيانات ERD", 2)
    para(
        doc,
        "يُعبّر نموذج الكيانات والعلاقات (Entity-Relationship Diagram) عن بنية "
        "قاعدة البيانات على السيرفر. العلاقة الأساسية: طفل واحد (child_code) له "
        "سياسة جهاز (device_policies) وتنبيهات متعددة (alerts) وسجلات استخدام يومية.",
        indent=0.75,
    )
    figure(doc, "شكل 3.7", "ERD — children, device_policies, alerts, audit_log, guardian_settings")
    table(
        doc,
        ["الكيان", "المفتاح", "العلاقة"],
        [
            ("children", "child_code", "1 — N مع alerts و usage_daily"),
            ("child_devices", "child_code", "تسجيل قبل الربط؛ يندمج في children"),
            ("device_policies", "device_id (=child_code)", "1 — 1 مع الطفل المربوط"),
            ("email_codes", "id", "رموز تحقق بريد ولي الأمر"),
            ("guardians", "email", "ولي أمر مسجّل"),
            ("screen_time_policies", "child_code", "سياسة وقت شاشة لكل طفل"),
            ("audit_log", "id", "سجل أحداث ولي الأمر"),
        ],
        title="جدول 3.7 — ملخص كيانات ERD",
    )


def chapter3_summary_ar(doc, heading, para):
    heading(doc, "3.10 ملخص الفصل", 2)
    para(
        doc,
        "قدّم الفصل الثالث دراسة للأنظمة الحالية وتحليل أصحاب المصلحة، ووثّق المتطلبات "
        "الوظيفية وغير الوظيفية، ومخططات Use Case و DFD وقاموس البيانات و ERD. "
        "يمهّد هذا التحليل للفصل الرابع الذي يترجم المتطلبات إلى تصميم معماري "
        "وقاعدة بيانات ومخططات UML وواجهات مستخدم.",
        indent=0.75,
    )


def write_chapter3_ar(doc, heading, para, bullets, table, figure):
    """كتابة الفصل الثالث كاملاً."""
    heading(doc, "الفصل الثالث: تحليل النظام", 1)
    para(
        doc,
        "يهدف هذا الفصل إلى تحليل نظام الرقابة الأبوية المقترح من منظور هندسة البرمجيات: "
        "دراسة البدائل، تحديد أصحاب المصلحة، استخراج المتطلبات، ونمذجة البيانات والعمليات "
        "بمخططات UML و DFD و ERD استعداداً للتصميم في الفصل التالي.",
        indent=0.75,
    )

    current_systems_ar(doc, heading, para, table, figure)
    stakeholders_ar(doc, heading, para, table, bullets)
    functional_requirements_ar(doc, heading, para, table)
    nonfunctional_requirements_ar(doc, heading, para, table, bullets)
    use_case_diagram_ar(doc, heading, para, figure)
    use_case_descriptions_ar(doc, heading, para, table)
    dfd_ar(doc, heading, para, figure, bullets)
    data_dictionary_ar(doc, heading, para, table)
    erd_ar(doc, heading, para, figure, table)
    chapter3_summary_ar(doc, heading, para)
    doc.add_page_break()

# -*- coding: utf-8 -*-
"""Generate Word document with thesis tables (diagrams in HTML file)."""

import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(__file__))
from docx import Document

from thesis_helpers import cover, font, heading, para, set_rtl, setup_page, table

OUT = [
    os.path.join(os.path.dirname(__file__), "مخططات_وجداول_البحث.docx"),
    r"E:\المشروع النظري\مخططات_وجداول_البحث.docx",
]
HTML_SRC = os.path.join(os.path.dirname(__file__), "مخططات_وجداول_البحث.html")


def build(doc):
    heading(doc, "مخططات وجداول — نظام الرقابة الأبوية", 1)
    para(
        doc,
        "هذا الملف يحتوي الجداول الجاهزة للبحث. المخططات (Architecture، Sequence، "
        "Use Case، DFD، ERD) في الملف المرافق: مخططات_وجداول_البحث.html — "
        "افتحيه في Chrome والتقطي لقطة لكل شكل وألصقيها في Word.",
        indent=0,
    )

    heading(doc, "جدول 4.1 — طبقات النظام المعماري", 2)
    table(doc, ["الطبقة", "المكوّن", "التقنية", "الوظيفة"], [
        ("العرض — طفل", "أكاديمية العباقرة", "Kotlin child", "واجهة مموّهة"),
        ("العرض — أم", "MY Rana", "Kotlin parent", "لوحة تحكم"),
        ("الأعمال", "server.py", "Flask", "REST API"),
        ("البيانات", "SQLite + Room", "سيرفر/جهاز", "تخزين"),
        ("خارجي", "Resend", "Gmail", "ربط OTP"),
    ])

    heading(doc, "جدول 3.1 — حالات الاستخدام", 2)
    table(doc, ["المعرف", "الحالة", "الفاعل", "المدخلات", "المخرجات"], [
        ("UC-01", "تحقق Gmail", "ولي الأمر", "OTP", "جلسة"),
        ("UC-02", "تسجيل الطفل", "الطفل", "بريد", "CHILD"),
        ("UC-03", "ربط", "ولي الأمر", "CHILD+OTP", "linked"),
        ("UC-04", "لوحة", "ولي الأمر", "child_code", "مؤشرات"),
        ("UC-05", "حظر", "ولي الأمر", "host/pkg", "سياسة"),
        ("UC-06", "تنبيه", "النظام", "كلمة خطرة", "alert"),
    ])

    heading(doc, "جدول 3.2 — قاموس البيانات", 2)
    table(doc, ["العنصر", "النوع", "الجدول", "الوصف"], [
        ("child_code", "TEXT PK", "children", "CHILD-XXX"),
        ("parent_email", "TEXT", "children", "Gmail"),
        ("linked", "INT", "children", "حالة الربط"),
        ("blocked_packages", "TEXT", "device_policies", "تطبيقات"),
        ("blocked_hosts", "TEXT", "device_policies", "مواقع"),
        ("message", "TEXT", "alerts", "تنبيه"),
    ])

    heading(doc, "جدول 1.1 — مقارنة الأنظمة", 2)
    table(doc, ["المعيار", "تقليدي", "MY Rana"], [
        ("واجهة الطفل", "رقابة ظاهرة", "أكاديمية"),
        ("الربط", "Google", "Gmail 3 رسائل"),
        ("كلمات خطرة", "محدود", "100+"),
        ("Audit", "لا", "نعم"),
    ])

    heading(doc, "جدول 5.2 — اختبارات", 2)
    table(doc, ["ID", "السيناريو", "المتوقع", "الفعلي", "الحالة"], [
        ("TC-01", "OTP بريد", "نجاح", "نجح", "Pass"),
        ("TC-02", "CHILD", "بريد", "88278A25", "Pass"),
        ("TC-03", "ربط", "linked", "—", "Pending"),
        ("TC-06", "blocklist", "101", "نجح", "Pass"),
    ])

    heading(doc, "قائمة الأشكال المطلوبة (من HTML)", 2)
    for fig in [
        "شكل 4.1 — التصميم المعماري",
        "شكل 4.4 — تسلسل ربط Gmail",
        "شكل 3.2 — Use Case",
        "شكل 3.3 — DFD Context",
        "شكل 3.7 — ERD",
    ]:
        para(doc, f"[{fig}] — من ملف HTML", indent=0)


def main():
    doc = Document()
    setup_page(doc)
    build(doc)
    for path in OUT:
        try:
            os.makedirs(os.path.dirname(path), exist_ok=True)
            doc.save(path)
            print(f"Saved: {path}")
        except OSError as e:
            print(f"Skip {path}: {e}")
    for dest_dir in [os.path.dirname(OUT[1]), os.path.dirname(OUT[0])]:
        dest_html = os.path.join(dest_dir, "مخططات_وجداول_البحث.html")
        try:
            shutil.copy2(HTML_SRC, dest_html)
            print(f"Copied HTML: {dest_html}")
        except OSError as e:
            print(f"Skip HTML {dest_html}: {e}")


if __name__ == "__main__":
    main()

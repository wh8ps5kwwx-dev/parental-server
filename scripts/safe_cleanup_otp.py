#!/usr/bin/env python3
"""
تنظيف آمن لرموز OTP والسجلات التالفة — لا يحذف أطفالاً مرتبطين أو آباء حقيقيين.

الاستخدام:
  python scripts/safe_cleanup_otp.py              # عرض SQL فقط (dry-run)
  python scripts/safe_cleanup_otp.py --execute    # تنفيذ بعد المراجعة
"""
from __future__ import annotations

import argparse
import os
import sqlite3
from datetime import datetime, timedelta

DB = os.environ.get("DB_PATH", "parent_control.db")


def preview_and_run(cur, title: str, count_sql: str, delete_sql: str, params: tuple, execute: bool) -> int:
    print(f"-- {title}")
    print(delete_sql.strip())
    if params:
        print(f"-- params: {params}")
    cur.execute(count_sql, params)
    count = int(cur.fetchone()[0])
    print(f"-- rows to delete: {count}\n")
    if execute and count > 0:
        cur.execute(delete_sql, params)
        print(f"   deleted: {cur.rowcount} rows\n")
    return count


def main() -> None:
    parser = argparse.ArgumentParser(description="Safe OTP cleanup for MYRana server DB")
    parser.add_argument("--execute", action="store_true", help="تنفيذ الحذف")
    parser.add_argument("--db", default=DB, help="مسار parent_control.db")
    args = parser.parse_args()
    execute = args.execute

    if not os.path.isfile(args.db):
        print(f"DB not found: {args.db}")
        return

    conn = sqlite3.connect(args.db)
    cur = conn.cursor()
    cutoff_email = (datetime.now() - timedelta(days=7)).strftime("%Y-%m-%d %H:%M:%S")
    cutoff_devices = (datetime.now() - timedelta(days=30)).strftime("%Y-%m-%d %H:%M:%S")

    print("=== MYRana safe cleanup ===")
    print(f"DB: {args.db}")
    print(f"Mode: {'EXECUTE' if execute else 'DRY-RUN (preview only)'}\n")

    total = 0
    total += preview_and_run(
        cur,
        "حذف رموز بريد قديمة غير مُحققة (>7 أيام)",
        "SELECT COUNT(*) FROM email_codes WHERE verified = 0 AND created_at < ?",
        "DELETE FROM email_codes WHERE verified = 0 AND created_at < ?",
        (cutoff_email,),
        execute,
    )
    total += preview_and_run(
        cur,
        "حذف أجهزة بدون child_code أو بدون رمز ربط",
        """
        SELECT COUNT(*) FROM child_devices
        WHERE child_code IS NULL OR TRIM(child_code) = ''
           OR device_verify_code IS NULL OR TRIM(device_verify_code) = ''
        """,
        """
        DELETE FROM child_devices
        WHERE child_code IS NULL OR TRIM(child_code) = ''
           OR device_verify_code IS NULL OR TRIM(device_verify_code) = ''
        """,
        (),
        execute,
    )
    total += preview_and_run(
        cur,
        "حذف أجهزة غير مرتبطة قديمة (>30 يوم) — تجريبية فقط",
        "SELECT COUNT(*) FROM child_devices WHERE linked = 0 AND created_at < ?",
        "DELETE FROM child_devices WHERE linked = 0 AND created_at < ?",
        (cutoff_devices,),
        execute,
    )

    if execute:
        conn.commit()
        print(f"تم التنفيذ — إجمالي المحذوف: {total}")
    else:
        conn.rollback()
        print(f"معاينة فقط — سيُحذف {total} سجل. للتنفيذ: --execute")
    conn.close()


if __name__ == "__main__":
    main()

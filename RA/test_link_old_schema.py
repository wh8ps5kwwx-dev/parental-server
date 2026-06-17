# -*- coding: utf-8 -*-
"""Simulate add-child against legacy children table schema."""
import os
import sqlite3
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
os.chdir(ROOT)

import server as s

db_path = tempfile.mktemp(suffix=".db")
conn = sqlite3.connect(db_path)
conn.row_factory = sqlite3.Row
cur = conn.cursor()
cur.execute(
    "CREATE TABLE email_codes (id INTEGER PRIMARY KEY, email TEXT, code TEXT, verified INTEGER, created_at TEXT)"
)
cur.execute(
    "INSERT INTO email_codes VALUES (1,'parent.controll.app@gmail.com','111111',1,'2026-01-01')"
)
cur.execute(
    """
    CREATE TABLE child_devices (
        id INTEGER PRIMARY KEY, child_code TEXT UNIQUE, child_email TEXT, device_name TEXT,
        android_version TEXT, device_verify_code TEXT, device_verified INTEGER DEFAULT 0,
        linked INTEGER DEFAULT 0, created_at TEXT
    )
    """
)
cur.execute(
    """
    INSERT INTO child_devices VALUES
    (1,'88278A25','parent.controll.app@gmail.com','Phone','14','662543',1,0,'2026-06-16 20:00:00')
    """
)
cur.execute(
    "CREATE TABLE children (id INTEGER PRIMARY KEY, name TEXT, child_code TEXT, parent_email TEXT)"
)
cur.execute(
    "CREATE TABLE reports (id INTEGER PRIMARY KEY, event TEXT, value TEXT, child_code TEXT, time TEXT)"
)
cur.execute(
    """
    CREATE TABLE device_policies (
        device_id TEXT PRIMARY KEY, revision INTEGER DEFAULT 0,
        blocked_hosts TEXT DEFAULT '[]', blocked_packages TEXT DEFAULT '[]',
        video_keywords TEXT DEFAULT '[]', updated_at TEXT
    )
    """
)
conn.commit()
conn.close()

s.DB = db_path
s.init_db()

conn = s.db()
cur = conn.cursor()
data = {
    "parent_email": "parent.controll.app@gmail.com",
    "child_code": "CHILD-88278A25",
    "verification_code": "662543",
    "name": "Rana",
    "age": 10,
    "guardian_role": "ولي أمر",
}
try:
    result = s._link_child_transaction(cur, conn, data)
    print("OK", result)
except Exception:
    import traceback

    traceback.print_exc()
    raise SystemExit(1)

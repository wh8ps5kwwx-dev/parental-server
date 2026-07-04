# -*- coding: utf-8 -*-
"""Reproduce link-child transaction locally."""
import importlib.util
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("server", ROOT / "server.py")
server = importlib.util.module_from_spec(spec)
sys.modules["server"] = server
spec.loader.exec_module(server)

conn = server.db()
server.init_db()
cur = conn.cursor()

# simulate child register
cur.execute(
    """
    INSERT OR REPLACE INTO child_devices
    (child_code, child_email, device_name, android_version, device_verify_code, linked, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """,
    ("D250B50E", "", "TestPhone", "Android 14", "803478", 0, server.now()),
)
cur.execute(
    """
    INSERT INTO email_codes (email, code, verified, created_at)
    VALUES (?, ?, ?, ?)
    """,
    ("parent.controll.app@gmail.com", "111111", 1, server.now()),
)
conn.commit()

data = {
    "parent_email": "parent.controll.app@gmail.com",
    "child_code": "CHILD-D250B50E",
    "verification_code": "803478",
    "name": "Test",
    "age": 10,
    "guardian_role": "ولي أمر",
}
try:
    result = server._link_child_transaction(cur, conn, data)
    print("RESULT:", result)
except Exception as e:
    import traceback
    print("EXCEPTION:", e)
    traceback.print_exc()
finally:
    conn.close()

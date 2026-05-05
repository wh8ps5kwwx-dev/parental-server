# ==============================
# سيرفر مشروع الرقابة الأبوية
# Parental Control Server
#
# وظائف السيرفر:
# 1) إرسال رمز تحقق لبريد ولي الأمر
# 2) التحقق من رمز البريد
# 3) تسجيل جهاز الطفل
# 4) ربط جهاز الطفل بحساب ولي الأمر
# 5) إرسال أوامر التحكم للطفل
# 6) استقبال التقارير والتنبيهات
# 7) حفظ التحكم الزمني
# ==============================

from flask import Flask, request, jsonify
from datetime import datetime
import sqlite3
import os
import random
import smtplib
from email.message import EmailMessage

# إنشاء تطبيق Flask
app = Flask(__name__)

# اسم قاعدة البيانات
DB = "parent_control.db"

# مفتاح حماية الطلبات بين التطبيق والسيرفر
API_KEY = os.environ.get("API_KEY", "graduation-secret-key")

# بيانات البريد لإرسال رموز التحقق
# إذا لم تضعي SMTP_USER و SMTP_PASS في Render
# سيظهر الرمز في Logs فقط
SMTP_USER = os.environ.get("SMTP_USER", "")
SMTP_PASS = os.environ.get("SMTP_PASS", "")
SMTP_HOST = os.environ.get("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.environ.get("SMTP_PORT", "587"))


# دالة ترجع الوقت الحالي
def now():
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


# دالة الاتصال بقاعدة البيانات
def db():
    conn = sqlite3.connect(DB)
    conn.row_factory = sqlite3.Row
    return conn


# دالة إرسال البريد
def send_email(to_email, subject, body):
    # إذا لم يتم إعداد البريد الحقيقي، نطبع الكود في Render Logs
    if not SMTP_USER or not SMTP_PASS:
        print("EMAIL MESSAGE:", body)
        return True

    msg = EmailMessage()
    msg["From"] = SMTP_USER
    msg["To"] = to_email
    msg["Subject"] = subject
    msg.set_content(body)

    with smtplib.SMTP(SMTP_HOST, SMTP_PORT) as smtp:
        smtp.starttls()
        smtp.login(SMTP_USER, SMTP_PASS)
        smtp.send_message(msg)

    return True


# إنشاء الجداول إذا لم تكن موجودة
def init_db():
    conn = db()
    cur = conn.cursor()

    # جدول رموز تحقق البريد
    cur.execute("""
    CREATE TABLE IF NOT EXISTS email_codes (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        email TEXT,
        code TEXT,
        verified INTEGER DEFAULT 0,
        created_at TEXT
    )
    """)

    # جدول أجهزة الأطفال قبل الربط
    cur.execute("""
    CREATE TABLE IF NOT EXISTS child_devices (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        child_code TEXT UNIQUE,
        child_email TEXT,
        device_name TEXT,
        android_version TEXT,
        device_verify_code TEXT,
        linked INTEGER DEFAULT 0,
        created_at TEXT
    )
    """)

    # جدول الأطفال المرتبطين بولي الأمر
    cur.execute("""
    CREATE TABLE IF NOT EXISTS children (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        name TEXT,
        age INTEGER,
        child_email TEXT,
        device TEXT,
        android_version TEXT,
        child_code TEXT UNIQUE,
        guardian_email TEXT,
        guardian_role TEXT,
        linked_at TEXT
    )
    """)

    # جدول أوامر التحكم
    cur.execute("""
    CREATE TABLE IF NOT EXISTS commands (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        action TEXT,
        value TEXT,
        child_code TEXT,
        guardian_email TEXT,
        executed INTEGER DEFAULT 0,
        time TEXT
    )
    """)

    # جدول التقارير
    cur.execute("""
    CREATE TABLE IF NOT EXISTS reports (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        event TEXT,
        value TEXT,
        child_code TEXT,
        time TEXT
    )
    """)

    # جدول التنبيهات
    cur.execute("""
    CREATE TABLE IF NOT EXISTS alerts (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        message TEXT,
        child_code TEXT,
        time TEXT
    )
    """)

    # جدول التحكم الزمني
    cur.execute("""
    CREATE TABLE IF NOT EXISTS schedules (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        child_code TEXT,
        action TEXT,
        value TEXT,
        start_time TEXT,
        end_time TEXT,
        active INTEGER DEFAULT 1,
        created_at TEXT
    )
    """)

    conn.commit()
    conn.close()


# حماية كل الروابط باستخدام API_KEY
@app.before_request
def protect():
    # الصفحة الرئيسية لا تحتاج حماية
    if request.path == "/":
        return

    # التحقق من مفتاح الحماية
    if request.headers.get("X-API-KEY") != API_KEY:
        return jsonify({"status": "error", "message": "Unauthorized"}), 401


# اختبار أن السيرفر يعمل
@app.route("/")
def home():
    return jsonify({
        "status": "running",
        "message": "Parental Control Server is running"
    })


# إرسال رمز تحقق لبريد ولي الأمر
@app.route("/send-email-code", methods=["POST"])
def send_email_code():
    data = request.get_json() or {}
    email = data.get("email", "").strip()

    if not email:
        return jsonify({"status": "error", "message": "email required"}), 400

    code = str(random.randint(100000, 999999))

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    INSERT INTO email_codes (email, code, verified, created_at)
    VALUES (?, ?, ?, ?)
    """, (email, code, 0, now()))

    conn.commit()
    conn.close()

    send_email(
        email,
        "Parental Control Verification Code",
        f"Your verification code is: {code}"
    )

    return jsonify({"status": "success", "message": "Verification code sent"})


# التحقق من رمز البريد
@app.route("/verify-email-code", methods=["POST"])
def verify_email_code():
    data = request.get_json() or {}
    email = data.get("email", "").strip()
    code = data.get("code", "").strip()

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    SELECT * FROM email_codes
    WHERE email = ? AND code = ?
    ORDER BY id DESC
    LIMIT 1
    """, (email, code))

    row = cur.fetchone()

    if not row:
        conn.close()
        return jsonify({"status": "error", "message": "Invalid code"}), 400

    cur.execute("UPDATE email_codes SET verified = 1 WHERE id = ?", (row["id"],))
    conn.commit()
    conn.close()

    return jsonify({"status": "success", "message": "Email verified"})


# تسجيل جهاز الطفل من تطبيق الطفل
@app.route("/register-child-device", methods=["POST"])
def register_child_device():
    data = request.get_json() or {}

    child_code = data.get("child_code", "").strip()
    child_email = data.get("child_email", "").strip()
    device_name = data.get("device_name", "").strip()
    android_version = data.get("android_version", "").strip()

    if not child_code or not child_email or not device_name:
        return jsonify({"status": "error", "message": "missing data"}), 400

    device_code = str(random.randint(100000, 999999))

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    INSERT OR REPLACE INTO child_devices
    (child_code, child_email, device_name, android_version, device_verify_code, linked, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """, (
        child_code,
        child_email,
        device_name,
        android_version,
        device_code,
        0,
        now()
    ))

    conn.commit()
    conn.close()

    send_email(
        child_email,
        "Child Device Verification Code",
        f"Child device verification code is: {device_code}"
    )

    return jsonify({
        "status": "success",
        "message": "Child device registered",
        "device_verify_code": device_code
    })


# إضافة وربط الطفل بحساب ولي الأمر
@app.route("/add-child", methods=["POST"])
def add_child():
    data = request.get_json() or {}

    name = data.get("name", "").strip()
    age = int(data.get("age", 0))
    child_email = data.get("child_email", "").strip()
    device = data.get("device", "").strip()
    android_version = data.get("android_version", "").strip()
    child_code = data.get("child_code", "").strip()
    device_verify_code = data.get("device_verify_code", "").strip()
    guardian_email = data.get("guardian_email", "").strip()
    guardian_role = data.get("guardian_role", "").strip()

    conn = db()
    cur = conn.cursor()

    # التحقق أن جهاز الطفل مسجل فعلاً وأن الرمز صحيح
    cur.execute("""
    SELECT * FROM child_devices
    WHERE child_code = ?
    AND child_email = ?
    AND device_verify_code = ?
    LIMIT 1
    """, (child_code, child_email, device_verify_code))

    device_row = cur.fetchone()

    if not device_row:
        conn.close()
        return jsonify({
            "status": "error",
            "message": "Child device verification failed"
        }), 400

    # حفظ بيانات الطفل وربطه بولي الأمر
    cur.execute("""
    INSERT OR REPLACE INTO children
    (name, age, child_email, device, android_version, child_code, guardian_email, guardian_role, linked_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (
        name,
        age,
        child_email,
        device,
        android_version,
        child_code,
        guardian_email,
        guardian_role,
        now()
    ))

    # تحديث حالة جهاز الطفل إلى مرتبط
    cur.execute("UPDATE child_devices SET linked = 1 WHERE child_code = ?", (child_code,))

    # إضافة تقرير عملية الربط
    cur.execute("""
    INSERT INTO reports (event, value, child_code, time)
    VALUES (?, ?, ?, ?)
    """, (
        "child_linked",
        f"{name} - {device}",
        child_code,
        now()
    ))

    conn.commit()
    conn.close()

    return jsonify({"status": "success", "message": "Child linked successfully"})


# إرسال أمر من تطبيق الأم إلى جهاز الطفل
@app.route("/send-command", methods=["POST"])
def send_command():
    data = request.get_json() or {}

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    INSERT INTO commands (action, value, child_code, guardian_email, executed, time)
    VALUES (?, ?, ?, ?, ?, ?)
    """, (
        data.get("action", ""),
        data.get("value", ""),
        data.get("child_code", ""),
        data.get("guardian_email", ""),
        0,
        now()
    ))

    # حفظ الأمر كتقرير
    cur.execute("""
    INSERT INTO reports (event, value, child_code, time)
    VALUES (?, ?, ?, ?)
    """, (
        "command_sent",
        f"{data.get('action', '')}: {data.get('value', '')}",
        data.get("child_code", ""),
        now()
    ))

    conn.commit()
    conn.close()

    return jsonify({"status": "success", "message": "Command sent"})


# جهاز الطفل يسحب آخر أمر غير منفذ
@app.route("/get-command", methods=["GET"])
def get_command():
    child_code = request.args.get("child_code", "")

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    SELECT * FROM commands
    WHERE child_code = ? AND executed = 0
    ORDER BY id DESC
    LIMIT 1
    """, (child_code,))

    cmd = cur.fetchone()

    if not cmd:
        conn.close()
        return jsonify({"action": "none", "value": "", "child_code": child_code})

    # بعد إرسال الأمر للطفل نعتبره منفذًا حتى لا يتكرر
    cur.execute("UPDATE commands SET executed = 1 WHERE id = ?", (cmd["id"],))
    conn.commit()
    conn.close()

    return jsonify(dict(cmd))


# إضافة جدول تحكم زمني
@app.route("/add-schedule", methods=["POST"])
def add_schedule():
    data = request.get_json() or {}

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    INSERT INTO schedules (child_code, action, value, start_time, end_time, active, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?)
    """, (
        data.get("child_code", ""),
        data.get("action", ""),
        data.get("value", ""),
        data.get("start_time", ""),
        data.get("end_time", ""),
        1,
        now()
    ))

    cur.execute("""
    INSERT INTO reports (event, value, child_code, time)
    VALUES (?, ?, ?, ?)
    """, (
        "schedule_added",
        f"{data.get('action', '')}: {data.get('value', '')}",
        data.get("child_code", ""),
        now()
    ))

    conn.commit()
    conn.close()

    return jsonify({"status": "success", "message": "Schedule added"})


# إرسال تقرير من جهاز الطفل
@app.route("/add-report", methods=["POST"])
def add_report():
    data = request.get_json() or {}

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    INSERT INTO reports (event, value, child_code, time)
    VALUES (?, ?, ?, ?)
    """, (
        data.get("event", ""),
        data.get("value", ""),
        data.get("child_code", ""),
        now()
    ))

    conn.commit()
    conn.close()

    return jsonify({"status": "success", "message": "Report added"})


# عرض التقارير للأم
@app.route("/reports", methods=["GET"])
def reports():
    child_code = request.args.get("child_code", "")

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    SELECT * FROM reports
    WHERE child_code = ?
    ORDER BY id DESC
    LIMIT 50
    """, (child_code,))

    rows = cur.fetchall()
    conn.close()

    return jsonify([dict(r) for r in rows])


# إضافة تنبيه من جهاز الطفل
@app.route("/add-alert", methods=["POST"])
def add_alert():
    data = request.get_json() or {}

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    INSERT INTO alerts (message, child_code, time)
    VALUES (?, ?, ?)
    """, (
        data.get("message", ""),
        data.get("child_code", ""),
        now()
    ))

    conn.commit()
    conn.close()

    return jsonify({"status": "success"})


# عرض التنبيهات للأم
@app.route("/alerts", methods=["GET"])
def alerts():
    child_code = request.args.get("child_code", "")

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    SELECT * FROM alerts
    WHERE child_code = ?
    ORDER BY id DESC
    LIMIT 50
    """, (child_code,))

    rows = cur.fetchall()
    conn.close()

    return jsonify([dict(r) for r in rows])


# إنشاء الجداول عند تشغيل السيرفر
init_db()

# تشغيل محلي فقط، أما Render يستخدم gunicorn
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)

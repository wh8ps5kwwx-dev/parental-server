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
from datetime import datetime, timedelta
import json
import sqlite3
import os
import random
import smtplib
import urllib.error
import urllib.request
from email.message import EmailMessage

# إنشاء تطبيق Flask
app = Flask(__name__)

# اسم قاعدة البيانات
DB = "parent_control.db"

# مفتاح حماية الطلبات بين التطبيق والسيرفر
API_KEY = os.environ.get("API_KEY", "graduation-secret-key")

# بيانات البريد لإرسال رموز التحقق (Gmail App Password على Render)
# SMTP_USER=your@gmail.com  SMTP_PASS=16-char-app-password  SMTP_PORT=465
SMTP_USER = os.environ.get("SMTP_USER", "").strip()
# App Password: أحياناً يُلصق مع مسافات — نزيلها
SMTP_PASS = os.environ.get("SMTP_PASS", "").replace(" ", "").strip()
SMTP_HOST = os.environ.get("SMTP_HOST", "smtp.gmail.com").strip()
SMTP_PORT = int(os.environ.get("SMTP_PORT", "465"))
SMTP_LAST_ERROR = ""
# Render المجاني يحظر SMTP — استخدمي Resend API (HTTPS) بدلاً من Gmail SMTP
RESEND_API_KEY = os.environ.get("RESEND_API_KEY", "").strip()
RESEND_FROM = os.environ.get(
    "RESEND_FROM", "MYRana <onboarding@resend.dev>"
).strip()


# دالة ترجع الوقت الحالي
def now():
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


# دالة الاتصال بقاعدة البيانات
def db():
    conn = sqlite3.connect(DB)
    conn.row_factory = sqlite3.Row
    return conn

def smtp_configured():
    """هل بيانات SMTP مضبوطة؟ بدونها لا يُرسل بريد حقيقي."""
    return bool(SMTP_USER and SMTP_PASS)


def email_configured():
    """SMTP أو Resend API — Render المجاني يحتاج Resend."""
    return bool(RESEND_API_KEY) or smtp_configured()


def verification_payload(code, email_sent, success_message, dev_message):
    """استجابة API: الرمز يُعاد في JSON فقط عند فشل SMTP (وضع تطوير)."""
    global SMTP_LAST_ERROR
    if not email_sent and email_configured() and SMTP_LAST_ERROR:
        dev_message = f"فشل إرسال البريد — تحققي من App Password على Render ({SMTP_LAST_ERROR})"
    payload = {
        "status": "success",
        "message": success_message if email_sent else dev_message,
        "email_sent": email_sent,
    }
    if not email_sent:
        payload["verification_code"] = code
        payload["dev_fallback"] = True
        print("EMAIL DEV FALLBACK — code for", code[:2] + "****")
    return payload


def send_email_resend(to_email, subject, body):
    """إرسال عبر Resend HTTPS — يعمل على Render المجاني."""
    global SMTP_LAST_ERROR
    if not RESEND_API_KEY:
        return False
    payload = json.dumps({
        "from": RESEND_FROM,
        "to": [to_email],
        "subject": subject,
        "text": body,
    }).encode("utf-8")
    req = urllib.request.Request(
        "https://api.resend.com/emails",
        data=payload,
        headers={
            "Authorization": f"Bearer {RESEND_API_KEY}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            if resp.status in (200, 201):
                SMTP_LAST_ERROR = ""
                print("EMAIL SENT (Resend) to", to_email)
                return True
            SMTP_LAST_ERROR = f"Resend HTTP {resp.status}"
            return False
    except urllib.error.HTTPError as e:
        SMTP_LAST_ERROR = f"Resend HTTP {e.code}: {e.read().decode()[:120]}"
        print("EMAIL ERROR (Resend):", SMTP_LAST_ERROR)
        return False
    except Exception as e:
        SMTP_LAST_ERROR = f"{type(e).__name__}: {str(e)[:120]}"
        print("EMAIL ERROR (Resend):", SMTP_LAST_ERROR)
        return False


# دالة إرسال البريد — Resend (Render مجاني) أو SMTP (سيرفر مدفوع)
def send_email(to_email, subject, body):
    global SMTP_LAST_ERROR
    if RESEND_API_KEY:
        return send_email_resend(to_email, subject, body)
    if not smtp_configured():
        SMTP_LAST_ERROR = "missing SMTP_USER or SMTP_PASS (or set RESEND_API_KEY)"
        print("EMAIL NOT SENT (no email config):", body)
        return False

    msg = EmailMessage()
    msg["From"] = SMTP_USER
    msg["To"] = to_email
    msg["Subject"] = subject
    msg.set_content(body)

    try:
        if SMTP_PORT == 465:
            with smtplib.SMTP_SSL(SMTP_HOST, SMTP_PORT, timeout=15) as smtp:
                smtp.login(SMTP_USER, SMTP_PASS)
                smtp.send_message(msg)
        else:
            with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15) as smtp:
                smtp.ehlo()
                smtp.starttls()
                smtp.ehlo()
                smtp.login(SMTP_USER, SMTP_PASS)
                smtp.send_message(msg)
        SMTP_LAST_ERROR = ""
        print("EMAIL SENT SUCCESS to", to_email)
        return True
    except Exception as e:
        SMTP_LAST_ERROR = f"{type(e).__name__}: {str(e)[:120]}"
        print("EMAIL ERROR:", SMTP_LAST_ERROR)
        return False
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
        device_verified INTEGER DEFAULT 0,
        linked INTEGER DEFAULT 0,
        created_at TEXT
    )
    """)
    try:
        cur.execute("ALTER TABLE child_devices ADD COLUMN device_verified INTEGER DEFAULT 0")
    except sqlite3.OperationalError:
        pass

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

    # سياسة الحظر لكل جهاز (تطبيق MYRana الأندرويد + مزامنة القوائم)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS device_policies (
        device_id TEXT PRIMARY KEY,
        revision INTEGER NOT NULL DEFAULT 0,
        blocked_hosts TEXT NOT NULL DEFAULT '[]',
        blocked_packages TEXT NOT NULL DEFAULT '[]',
        video_keywords TEXT NOT NULL DEFAULT '[]',
        updated_at TEXT
    )
    """)

    _ensure_policy_columns(cur)

    # استخدام التطبيقات (تجميع يومي → تقرير أسبوعي)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS usage_daily (
        child_code TEXT NOT NULL,
        day TEXT NOT NULL,
        package_name TEXT NOT NULL,
        total_seconds INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (child_code, day, package_name)
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


def _norm_host(host: str) -> str:
    return (host or "").strip().lower()


def _norm_pkg(package: str) -> str:
    from blocklists.package_resolver import resolve_app_package

    return resolve_app_package(package)


def _ensure_policy_columns(cur) -> None:
    cur.execute("PRAGMA table_info(device_policies)")
    cols = {row[1] for row in cur.fetchall()}
    if "video_keywords" not in cols:
        cur.execute(
            "ALTER TABLE device_policies ADD COLUMN video_keywords TEXT NOT NULL DEFAULT '[]'"
        )


def _policy_get(conn, device_id: str):
    cur = conn.cursor()
    cur.execute(
        """
        SELECT revision, blocked_hosts, blocked_packages, video_keywords
        FROM device_policies WHERE device_id = ?
        """,
        (device_id,),
    )
    row = cur.fetchone()
    if not row:
        return 0, [], [], []
    hosts = json.loads(row["blocked_hosts"] or "[]")
    packages = json.loads(row["blocked_packages"] or "[]")
    keywords = json.loads(row["video_keywords"] or "[]") if row["video_keywords"] is not None else []
    return int(row["revision"]), hosts, packages, keywords


def _policy_save(
    conn,
    device_id: str,
    hosts: list,
    packages: list,
    bump_revision: bool = True,
    video_keywords: list | None = None,
) -> None:
    cur = conn.cursor()
    cur.execute("SELECT revision, video_keywords FROM device_policies WHERE device_id = ?", (device_id,))
    row = cur.fetchone()
    if row:
        revision = int(row["revision"]) + (1 if bump_revision else 0)
        kw_json = row["video_keywords"]
    else:
        revision = 1 if bump_revision else 0
        kw_json = "[]"
    if video_keywords is None:
        keywords = json.loads(kw_json or "[]")
    else:
        keywords = video_keywords

    cur.execute(
        """
        INSERT INTO device_policies (
            device_id, revision, blocked_hosts, blocked_packages, video_keywords, updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(device_id) DO UPDATE SET
            revision = excluded.revision,
            blocked_hosts = excluded.blocked_hosts,
            blocked_packages = excluded.blocked_packages,
            video_keywords = excluded.video_keywords,
            updated_at = excluded.updated_at
        """,
        (device_id, revision, json.dumps(hosts), json.dumps(packages), json.dumps(keywords), now()),
    )


def load_blocklist_catalog() -> dict:
    """تحميل catalog.json — بجانب server.py (Render/RA) أو من جذر المشروع."""
    from pathlib import Path

    here = Path(__file__).resolve().parent
    for catalog_path in (
        here / "blocklists" / "catalog.json",
        here.parent / "blocklists" / "catalog.json",
    ):
        if catalog_path.is_file():
            with open(catalog_path, encoding="utf-8") as f:
                return json.load(f)
    return {"packages": [], "sites": [], "video_keywords": [], "app_labels": []}


def blocklist_catalog_counts(cat: dict | None = None) -> dict:
    c = cat if cat is not None else load_blocklist_catalog()
    return {
        "packages": len(c.get("packages") or []),
        "sites": len(c.get("sites") or []),
        "video_keywords": len(c.get("video_keywords") or []),
        "app_labels": len(c.get("app_labels") or []),
    }


def apply_default_blocklist(conn, device_id: str, merge: bool = True) -> dict:
    """دمج catalog.json في سياسة جهاز الطفل (child_code = device_id)."""
    device_id = (device_id or "").strip()
    if not device_id:
        return {"status": "error", "message": "child_code required"}

    cat = load_blocklist_catalog()
    new_pkgs = [_norm_pkg(p) for p in (cat.get("packages") or []) if _norm_pkg(p)]
    new_hosts = [_norm_host(s) for s in (cat.get("sites") or []) if _norm_host(s)]
    new_kw = [k.strip() for k in (cat.get("video_keywords") or []) if k and str(k).strip()]

    _, hosts, packages, keywords = _policy_get(conn, device_id)
    if merge:
        pkg_set = set(packages)
        pkg_set.update(new_pkgs)
        host_set = set(hosts)
        host_set.update(new_hosts)
        kw_set = list(dict.fromkeys(keywords + new_kw))
        packages = sorted(pkg_set)
        hosts = sorted(host_set)
        keywords = kw_set
    else:
        packages = sorted(set(new_pkgs))
        hosts = sorted(set(new_hosts))
        keywords = new_kw

    _policy_save(conn, device_id, hosts, packages, video_keywords=keywords)
    revision, hosts, packages, keywords = _policy_get(conn, device_id)
    return {
        "status": "success",
        "revision": revision,
        "blockedHosts": hosts,
        "blockedPackages": packages,
        "videoKeywords": keywords,
        "counts": {
            "packages": len(packages),
            "sites": len(hosts),
            "video_keywords": len(keywords),
        },
    }


def policy_add_host(conn, device_id: str, host: str) -> None:
    host = _norm_host(host)
    if not host:
        return
    _, hosts, packages, keywords = _policy_get(conn, device_id)
    if host not in hosts:
        hosts.append(host)
    _policy_save(conn, device_id, hosts, packages, video_keywords=keywords)


def policy_add_package(conn, device_id: str, package: str) -> None:
    package = _norm_pkg(package)
    if not package:
        return
    _, hosts, packages, keywords = _policy_get(conn, device_id)
    if package not in packages:
        packages.append(package)
    _policy_save(conn, device_id, hosts, packages, video_keywords=keywords)


def policy_clear(conn, device_id: str) -> None:
    _policy_save(conn, device_id, [], [], video_keywords=[])


def _time_hm() -> str:
    return datetime.now().strftime("%H:%M")


def _time_in_window(now_hm: str, start: str, end: str) -> bool:
    start = (start or "").strip()[:5]
    end = (end or "").strip()[:5]
    if not start or not end:
        return False
    if start <= end:
        return start <= now_hm < end
    return now_hm >= start or now_hm < end


def usage_add_seconds(conn, child_code: str, day: str, package_name: str, seconds: int) -> None:
    pkg = _norm_pkg(package_name)
    if not child_code or not pkg or seconds <= 0:
        return
    cur = conn.cursor()
    cur.execute(
        """
        INSERT INTO usage_daily (child_code, day, package_name, total_seconds)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(child_code, day, package_name)
        DO UPDATE SET total_seconds = total_seconds + excluded.total_seconds
        """,
        (child_code, day, pkg, int(seconds)),
    )


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
        "message": "Parental Control Server is running",
        "smtp_ready": email_configured(),
        "email_via": "resend" if RESEND_API_KEY else ("smtp" if smtp_configured() else "none"),
        "smtp_user_set": bool(SMTP_USER),
        "smtp_pass_set": bool(SMTP_PASS),
        "smtp_last_error": SMTP_LAST_ERROR or None,
        "smtp_host": SMTP_HOST,
        "smtp_port": SMTP_PORT,
    })


# ==============================
# سياسة الحظر — عقد MYRana (أندرويد)
# device_id = child_code من تطبيق الأم/الطفل
# ==============================
@app.route("/api/v1/devices/<device_id>/policy", methods=["GET"])
def api_get_policy(device_id):
    device_id = device_id.strip()
    conn = db()
    revision, hosts, packages, keywords = _policy_get(conn, device_id)
    conn.close()
    return jsonify({
        "revision": revision,
        "blockedHosts": hosts,
        "blockedPackages": packages,
        "videoKeywords": keywords,
    })


@app.route("/api/v1/devices/<device_id>/policy/push", methods=["POST"])
def api_push_policy(device_id):
    device_id = device_id.strip()
    data = request.get_json() or {}
    new_hosts = [_norm_host(h) for h in (data.get("blockedHosts") or []) if _norm_host(h)]
    new_packages = [_norm_pkg(p) for p in (data.get("blockedPackages") or []) if _norm_pkg(p)]

    conn = db()
    _, hosts, packages, keywords = _policy_get(conn, device_id)
    for host in new_hosts:
        if host not in hosts:
            hosts.append(host)
    for package in new_packages:
        if package not in packages:
            packages.append(package)
    _policy_save(conn, device_id, hosts, packages, video_keywords=keywords)
    revision, hosts, packages, keywords = _policy_get(conn, device_id)
    conn.commit()
    conn.close()

    return jsonify({
        "revision": revision,
        "blockedHosts": hosts,
        "blockedPackages": packages,
        "videoKeywords": keywords,
    })


@app.route("/blocklist/catalog", methods=["GET"])
def blocklist_catalog():
    cat = load_blocklist_catalog()
    return jsonify({
        "status": "success",
        "catalog": cat,
        "counts": blocklist_catalog_counts(cat),
    })


@app.route("/apply-default-blocklist", methods=["POST"])
def api_apply_default_blocklist():
    data = request.get_json() or {}
    child_code = (data.get("child_code") or "").strip()
    merge = data.get("merge", True)
    if not child_code:
        return jsonify({"status": "error", "message": "child_code required"}), 400
    conn = db()
    result = apply_default_blocklist(conn, child_code, merge=bool(merge))
    conn.commit()
    conn.close()
    return jsonify(result)


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

    email_sent = send_email(
        email,
        "Parental Control Verification Code",
        f"Your verification code is: {code}"
    )

    return jsonify(verification_payload(
        code,
        email_sent,
        "Verification code sent to your email",
        "SMTP not configured — code returned for development only",
    ))


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


# تسجيل جهاز الطفل — بدون بريد (التحقق مرة واحدة عند الربط من تطبيق الأم)
@app.route("/register-child-device", methods=["POST"])
def register_child_device():
    data = request.get_json() or {}

    child_code = data.get("child_code", "").strip()
    child_email = data.get("child_email", "").strip()
    device_name = data.get("device_name", "").strip()
    android_version = data.get("android_version", "").strip()

    if not child_code or not device_name:
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

    return jsonify({
        "status": "success",
        "message": "تم تسجيل الجهاز — انتظر ربط ولي الأمر",
        "child_code": child_code,
    })


# إرسال رمز الربط لبريد ولي الأمر — مرة واحدة أثناء الربط
@app.route("/send-link-code", methods=["POST"])
def send_link_code():
    data = request.get_json() or {}
    guardian_email = data.get("guardian_email", "").strip()
    child_code = data.get("child_code", "").strip()

    if not guardian_email or not child_code:
        return jsonify({"status": "error", "message": "guardian_email and child_code required"}), 400

    conn = db()
    cur = conn.cursor()
    cur.execute(
        "SELECT * FROM child_devices WHERE child_code = ? LIMIT 1",
        (child_code,),
    )
    row = cur.fetchone()
    conn.close()

    if not row:
        return jsonify({"status": "error", "message": "Child device not found"}), 404
    if row["linked"]:
        return jsonify({"status": "error", "message": "Device already linked"}), 400

    device_code = row["device_verify_code"]
    email_sent = send_email(
        guardian_email,
        "MYRana — رمز ربط الطفل",
        f"رمز ربط الطفل ({child_code}):\n\n{device_code}\n\n"
        f"أدخليه في تطبيق الأم لإتمام الربط.",
    )

    return jsonify(verification_payload(
        device_code,
        email_sent,
        "تم إرسال رمز الربط إلى بريدك",
        "SMTP غير مضبوط — الرمز للتطوير فقط",
    ))


# هل اكتمل ربط جهاز الطفل؟ (يستعلم عنه تطبيق الطفل)
@app.route("/child-link-status", methods=["GET"])
def child_link_status():
    child_code = request.args.get("child_code", "").strip()
    if not child_code:
        return jsonify({"status": "error", "message": "child_code required"}), 400

    conn = db()
    cur = conn.cursor()
    cur.execute(
        "SELECT linked FROM child_devices WHERE child_code = ? LIMIT 1",
        (child_code,),
    )
    row = cur.fetchone()
    conn.close()

    if not row:
        return jsonify({"status": "error", "message": "Child device not found"}), 404

    return jsonify({
        "status": "success",
        "child_code": child_code,
        "linked": bool(row["linked"]),
    })


# التحقق من رمز جهاز الطفل — نفس آلية verify-email-code
@app.route("/verify-child-device-code", methods=["POST"])
def verify_child_device_code():
    data = request.get_json() or {}
    child_code = data.get("child_code", "").strip()
    code = (data.get("code") or data.get("device_verify_code") or "").strip()

    if not child_code or not code:
        return jsonify({"status": "error", "message": "child_code and code required"}), 400

    conn = db()
    cur = conn.cursor()
    cur.execute("""
    SELECT * FROM child_devices
    WHERE child_code = ? AND device_verify_code = ?
    LIMIT 1
    """, (child_code, code))
    row = cur.fetchone()

    if not row:
        conn.close()
        return jsonify({"status": "error", "message": "Invalid verification code"}), 400

    cur.execute(
        "UPDATE child_devices SET device_verified = 1 WHERE child_code = ?",
        (child_code,),
    )
    conn.commit()
    conn.close()

    return jsonify({
        "status": "success",
        "message": "Device verified",
        "child_code": child_code,
        "child_email": row["child_email"],
        "device_name": row["device_name"],
        "android_version": row["android_version"],
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

    if not name or not child_code or not device_verify_code or not guardian_email:
        return jsonify({"status": "error", "message": "missing data"}), 400

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    SELECT * FROM child_devices
    WHERE child_code = ? AND device_verify_code = ?
    LIMIT 1
    """, (child_code, device_verify_code))

    device_row = cur.fetchone()

    if not device_row:
        conn.close()
        return jsonify({
            "status": "error",
            "message": "Child device verification failed"
        }), 400

    if device_row["linked"]:
        conn.close()
        return jsonify({"status": "error", "message": "Device already linked"}), 400

    child_email = device_row["child_email"]
    device = device_row["device_name"] or device
    android_version = device_row["android_version"] or android_version

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

    cur.execute("""
    INSERT INTO email_codes (email, code, verified, created_at)
    VALUES (?, ?, ?, ?)
    """, (guardian_email, device_verify_code, 1, now()))

    apply_default_blocklist(conn, child_code, merge=True)

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

    action = (data.get("action") or "").strip()
    value = (data.get("value") or "").strip()
    if action in ("block_app", "freeze_app") and value:
        value = _norm_pkg(value)
        data["value"] = value

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    INSERT INTO commands (action, value, child_code, guardian_email, executed, time)
    VALUES (?, ?, ?, ?, ?, ?)
    """, (
        action,
        value,
        data.get("child_code", ""),
        data.get("guardian_email", ""),
        0,
        now()
    ))

    cur.execute("""
    INSERT INTO reports (event, value, child_code, time)
    VALUES (?, ?, ?, ?)
    """, (
        "command_sent",
        f"{action}: {value}",
        data.get("child_code", ""),
        now()
    ))

    child_code = (data.get("child_code") or "").strip()
    if child_code:
        if action == "block_site" and value:
            policy_add_host(conn, child_code, value)
        elif action in ("block_app", "freeze_app") and value:
            policy_add_package(conn, child_code, value)
        elif action == "allow":
            policy_clear(conn, child_code)
        elif action == "apply_default_blocklist":
            apply_default_blocklist(conn, child_code, merge=True)

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


# جداول زمنية نشطة الآن (حظر/تجميد مؤقت)
@app.route("/active-schedules", methods=["GET"])
def active_schedules():
    child_code = (request.args.get("child_code") or "").strip()
    now_hm = _time_hm()
    conn = db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT action, value FROM schedules
        WHERE child_code = ? AND active = 1
        """,
        (child_code,),
    )
    packages = []
    for row in cur.fetchall():
        action = (row["action"] or "").strip()
        value = (row["value"] or "").strip()
        if action not in ("block_app", "freeze_app") or not value:
            continue
        if _time_in_window(now_hm, row["start_time"], row["end_time"]):
            packages.append(_norm_pkg(value))
    conn.close()
    return jsonify({"packages": list(dict.fromkeys(packages))})


# رفع استخدام التطبيقات من جهاز الطفل
@app.route("/upload-usage", methods=["POST"])
def upload_usage():
    data = request.get_json() or {}
    child_code = (data.get("child_code") or "").strip()
    entries = data.get("entries") or []
    conn = db()
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        day = (entry.get("day") or datetime.now().strftime("%Y-%m-%d")).strip()
        pkg = entry.get("package") or entry.get("package_name") or ""
        sec = int(entry.get("seconds") or 0)
        usage_add_seconds(conn, child_code, day, pkg, sec)
    conn.commit()
    conn.close()
    return jsonify({"status": "success", "message": "Usage saved"})


# تقرير استخدام أسبوعي لولي الأمر
@app.route("/weekly-report", methods=["GET"])
def weekly_report():
    child_code = (request.args.get("child_code") or "").strip()
    since = (datetime.now() - timedelta(days=7)).strftime("%Y-%m-%d")
    conn = db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT package_name, SUM(total_seconds) AS total_seconds
        FROM usage_daily
        WHERE child_code = ? AND day >= ?
        GROUP BY package_name
        ORDER BY total_seconds DESC
        LIMIT 30
        """,
        (child_code, since),
    )
    apps = [dict(r) for r in cur.fetchall()]
    conn.close()
    return jsonify({"child_code": child_code, "since": since, "apps": apps})


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
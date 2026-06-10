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
import logging
import sqlite3
import os
import random
import smtplib
import traceback
import urllib.error
import urllib.request
from email.message import EmailMessage

# سجلات ربط الطفل — تظهر في log السيرفر (Render → Logs)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("myrana.link")

# صلاحية رموز البريد (دقائق)
OTP_EMAIL_EXPIRY_MINUTES = int(os.environ.get("OTP_EMAIL_EXPIRY_MINUTES", "60"))

# إنشاء تطبيق Flask
app = Flask(__name__)

# قاعدة البيانات — على Render أضيفي قرصاً دائماً عند /var/data (Environment: DATA_DIR=/var/data)
def _resolve_db_path() -> str:
    data_dir = os.environ.get("DATA_DIR", "").strip()
    if not data_dir and os.path.isdir("/var/data") and os.access("/var/data", os.W_OK):
        data_dir = "/var/data"
    if data_dir:
        try:
            os.makedirs(data_dir, exist_ok=True)
            if os.access(data_dir, os.W_OK):
                return os.path.join(data_dir, "parent_control.db")
        except OSError:
            pass
    explicit = os.environ.get("DATABASE_PATH", "").strip()
    if explicit:
        return explicit
    return "parent_control.db"


DB = _resolve_db_path()

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


# FIX: normalize child_code to support codes with or without CHILD- prefix
def clean_child_code(raw):
    """
    تنظيف كود الطفل للبحث في قاعدة البيانات:
    trim → uppercase → إزالة CHILD- → أحرف وأرقام فقط.
    مثال: CHILD-1DF71288 → 1DF71288
    """
    code = (raw or "").strip().upper()
    if code.startswith("CHILD-"):
        code = code[6:]
    suffix = "".join(ch for ch in code if ch.isalnum())
    return suffix


def normalize_child_code(raw):
    """الصيغة القياسية للتخزين والاستجابة: CHILD-XXXXXXXX"""
    suffix = clean_child_code(raw)
    if not suffix:
        return ""
    return f"CHILD-{suffix}"


def find_child_device(cur, child_code_raw, log_on_miss=True):
    """يبحث بـ CHILD-1DF71288 أو 1DF71288 أو أي صيغة مخزّنة."""
    original = child_code_raw
    suffix = clean_child_code(child_code_raw)
    if not suffix:
        return None
    canonical = f"CHILD-{suffix}"

    for candidate in (suffix, canonical):
        cur.execute(
            "SELECT * FROM child_devices WHERE child_code = ? COLLATE NOCASE LIMIT 1",
            (candidate,),
        )
        row = cur.fetchone()
        if row:
            return row

    cur.execute(
        """
        SELECT * FROM child_devices
        WHERE UPPER(REPLACE(REPLACE(TRIM(child_code), 'CHILD-', ''), 'child-', '')) = ?
        ORDER BY id DESC
        LIMIT 1
        """,
        (suffix,),
    )
    row = cur.fetchone()
    if not row and log_on_miss:
        logger.warning(
            "child_not_found original=%r cleaned=%r",
            original,
            suffix,
        )
    return row


def _child_not_found_response(raw, message):
    """JSON واضح عند عدم وجود الطفل — مع الكود الأصلي والمنظّف في السجلات."""
    cleaned = clean_child_code(raw)
    logger.warning("child_not_found original=%r cleaned=%r", raw, cleaned)
    return _json_error(
        message,
        404,
        error_code="child_not_found",
        child_code_input=(raw or "").strip(),
        child_code_clean=cleaned,
    )


def _migrate_child_codes_in_db(cur):
    """FIX: normalize child_code to support codes with or without CHILD- prefix — ترحيل DB."""
    for table, col in (
        ("child_devices", "child_code"),
        ("children", "child_code"),
        ("usage_daily", "child_code"),
        ("screen_time_policies", "child_code"),
        ("child_status", "child_code"),
        ("commands", "child_code"),
        ("reports", "child_code"),
        ("alerts", "child_code"),
        ("schedules", "child_code"),
    ):
        try:
            cur.execute(f"SELECT rowid AS _rid, {col} FROM {table} WHERE {col} IS NOT NULL")
            for row in cur.fetchall():
                suffix = clean_child_code(row[col])
                if suffix and suffix != row[col]:
                    cur.execute(
                        f"UPDATE {table} SET {col} = ? WHERE rowid = ?",
                        (suffix, row["_rid"]),
                    )
        except sqlite3.OperationalError:
            pass
    try:
        cur.execute("SELECT device_id FROM device_policies WHERE device_id IS NOT NULL")
        for row in cur.fetchall():
            suffix = clean_child_code(row["device_id"])
            if suffix and suffix != row["device_id"]:
                cur.execute(
                    "UPDATE device_policies SET device_id = ? WHERE device_id = ?",
                    (suffix, row["device_id"]),
                )
    except sqlite3.OperationalError:
        pass


def now():
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def _parse_db_time(value: str | None) -> datetime | None:
    if not value:
        return None
    for fmt in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d %H:%M"):
        try:
            return datetime.strptime(str(value).strip(), fmt)
        except ValueError:
            continue
    return None


def _otp_expired(created_at: str | None, minutes: int) -> bool:
    created = _parse_db_time(created_at)
    if not created:
        return False
    return datetime.now() - created > timedelta(minutes=minutes)


def _json_error(message: str, code: int = 400, **extra):
    """استجابة JSON موحّدة — لا HTML."""
    payload = {"success": False, "status": "error", "message": message}
    payload.update(extra)
    return jsonify(payload), code


def _json_success(message: str, code: int = 200, **extra):
    """نجاح — JSON فقط مع success: true."""
    payload = {"success": True, "status": "success", "message": message}
    payload.update(extra)
    return jsonify(payload), code


def _ensure_guardian(cur, email: str) -> int:
    """إنشاء/جلب parent_id من بريد ولي الأمر."""
    email = (email or "").strip()
    cur.execute("SELECT id FROM guardians WHERE email = ? LIMIT 1", (email,))
    row = cur.fetchone()
    if row:
        return int(row["id"])
    cur.execute(
        "INSERT INTO guardians (email, created_at) VALUES (?, ?)",
        (email, now()),
    )
    return int(cur.lastrowid)


def db_child_code(raw) -> str:
    # FIX: normalize child_code to support codes with or without CHILD- prefix
    """مفتاح قاعدة البيانات — CHILD-1DF71288 → 1DF71288"""
    return clean_child_code(raw)


def _extract_parent_email(data: dict) -> str:
    return (
        data.get("guardian_email")
        or data.get("parent_email")
        or data.get("email")
        or ""
    ).strip()


def _extract_verification_code(data: dict) -> str:
    """رمز الربط — أسماء موحّدة بين Android و Flask."""
    raw = (
        data.get("device_verify_code")
        or data.get("verification_code")
        or data.get("otp")
        or data.get("code")
        or ""
    )
    return str(raw).strip()


def _extract_child_code(data: dict) -> str:
    """FIX: normalize child_code to support codes with or without CHILD- prefix"""
    raw = data.get("child_code") or data.get("childCode") or ""
    return db_child_code(raw)


def _safe_age(data: dict, default: int = 10) -> int:
    try:
        raw = data.get("age")
        if raw is None or raw == "":
            return default
        age = int(raw)
        return max(3, min(18, age))
    except (TypeError, ValueError):
        return default


def _child_display_name(data: dict) -> str:
    name = (data.get("name") or data.get("child_name") or "").strip()
    return name or "طفل"


def _guardian_verified(cur, email: str) -> bool:
    cur.execute(
        """
        SELECT id FROM email_codes
        WHERE email = ? AND verified = 1
        ORDER BY id DESC LIMIT 1
        """,
        (email,),
    )
    return cur.fetchone() is not None


def _log_link_context(step: str, parent_email: str, child_code: str, verify_code: str, stored_code: str | None, reason: str = ""):
    masked = f"{verify_code[:2]}****" if verify_code else "(empty)"
    stored_masked = f"{stored_code[:2]}****" if stored_code else "(none)"
    logger.info(
        "[%s] parent_email=%s child_code=%s verify=%s stored=%s %s",
        step,
        parent_email or "(empty)",
        child_code or "(empty)",
        masked,
        stored_masked,
        reason,
    )


def _link_child_transaction(cur, conn, data: dict):
    """
    ربط الطفل — يعتمد على:
      parent_email / guardian_email / email
      child_code (CHILD-1DF71288 أو 1DF71288)
      verification_code / device_verify_code / otp / code
    الاسم اختياري (افتراضي: طفل) — لا يُستخدم في التحقق.
    """
    parent_email = _extract_parent_email(data)
    raw_input = str(data.get("child_code") or data.get("childCode") or "").strip()
    child_code = _extract_child_code(data)
    verify_code = _extract_verification_code(data)
    logger.info(
        "[link-child] step=receive parent_email=%s child_code_raw=%r child_code_db=%r verify=%s",
        parent_email or "(empty)",
        raw_input,
        child_code or "(empty)",
        f"{verify_code[:2]}****" if verify_code else "(empty)",
    )
    name = _child_display_name(data)
    age = _safe_age(data)
    guardian_role = (data.get("guardian_role") or "ولي أمر").strip() or "ولي أمر"
    device = (data.get("device") or "").strip()
    android_version = (data.get("android_version") or "").strip()
    child_email = (data.get("child_email") or parent_email).strip()

    def _fail(message, code=400, **extra):
        conn.rollback()
        return _json_error(message, code, **extra)

    if not parent_email:
        _log_link_context("add-child", parent_email, child_code, verify_code, None, "missing parent_email")
        return _fail("parent_email مطلوب", error_code="missing_parent_email")

    if not child_code:
        _log_link_context("add-child", parent_email, child_code, verify_code, None, "missing child_code")
        return _fail("child_code مطلوب", error_code="missing_child_code")

    if not verify_code:
        _log_link_context("add-child", parent_email, child_code, verify_code, None, "missing verification_code")
        return _fail("verification_code مطلوب", error_code="missing_verification_code")

    if not _guardian_verified(cur, parent_email):
        _log_link_context("add-child", parent_email, child_code, verify_code, None, "parent not verified")
        return _fail(
            "يجب التحقق من بريد ولي الأمر أولاً — أرسلي رمز التحقق من Gmail",
            error_code="parent_email_not_verified",
        )

    raw_child = raw_input or child_code
    device_row = find_child_device(cur, raw_child)
    stored_code = str(device_row["device_verify_code"] or "").strip() if device_row else None
    _log_link_context("add-child", parent_email, child_code, verify_code, stored_code, "checking device")

    if not device_row:
        conn.rollback()
        logger.warning(
            "[link-child] step=child_lookup FAIL original=%r cleaned=%r",
            raw_child,
            db_child_code(raw_child),
        )
        return _child_not_found_response(
            raw_child,
            "Child not found",
        )

    child_code = db_child_code(device_row["child_code"])
    logger.info("[link-child] step=child_lookup OK child_code_db=%r", child_code)

    if device_row["linked"]:
        cur.execute(
            "SELECT id, guardian_email FROM children WHERE child_code = ? LIMIT 1",
            (child_code,),
        )
        existing = cur.fetchone()
        if existing and str(existing["guardian_email"] or "").strip() == parent_email:
            parent_id = _ensure_guardian(cur, parent_email)
            _log_link_context("add-child", parent_email, child_code, verify_code, stored_code, "already linked same parent")
            conn.commit()
            return _json_success(
                "Child linked successfully",
                409,
                already_linked=True,
                parent_id=parent_id,
                child_id=int(existing["id"]),
                child_code=normalize_child_code(child_code),
                child_code_clean=child_code,
            )
        return _fail(
            "الجهاز مربوط بحساب أم آخر",
            409,
            error_code="already_linked_other_parent",
        )

    if not stored_code or stored_code != verify_code:
        _log_link_context("add-child", parent_email, child_code, verify_code, stored_code, "wrong verification_code")
        logger.warning(
            "[link-child] step=otp_verify FAIL expected=%s got=%s",
            f"{stored_code[:2]}****" if stored_code else "(none)",
            f"{verify_code[:2]}****" if verify_code else "(empty)",
        )
        return _fail(
            "Invalid or expired verification code",
            400,
            error_code="invalid_verification_code",
        )

    logger.info("[link-child] step=otp_verify OK")

    child_email = (device_row["child_email"] or child_email or parent_email).strip()
    device = (device_row["device_name"] or device or "Android").strip()
    android_version = (device_row["android_version"] or android_version or "Android").strip()

    cur.execute(
        """
        INSERT OR REPLACE INTO children
        (name, age, child_email, device, android_version, child_code, guardian_email, guardian_role, linked_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        (name, age, child_email, device, android_version, child_code, parent_email, guardian_role, now()),
    )
    cur.execute(
        "UPDATE child_devices SET linked = 1, device_verified = 1 WHERE child_code = ?",
        (child_code,),
    )

    try:
        apply_default_blocklist(conn, child_code, merge=True)
    except Exception as block_err:
        logger.warning("apply_default_blocklist after link failed: %s", block_err)

    cur.execute(
        """
        INSERT INTO reports (event, value, child_code, time)
        VALUES (?, ?, ?, ?)
        """,
        ("child_linked", f"{name} - {device}", child_code, now()),
    )

    parent_id = _ensure_guardian(cur, parent_email)
    cur.execute("SELECT id FROM children WHERE child_code = ? ORDER BY id DESC LIMIT 1", (child_code,))
    child_row = cur.fetchone()
    child_id = int(child_row["id"]) if child_row else None
    conn.commit()
    _log_link_context("add-child", parent_email, child_code, verify_code, stored_code, "success")
    logger.info(
        "[link-child] step=done parent_id=%s child_id=%s child_code=%r",
        parent_id,
        child_id,
        child_code,
    )
    return _json_success(
        "Child linked successfully",
        parent_id=parent_id,
        child_id=child_id,
        child_code=normalize_child_code(child_code),
        child_code_clean=child_code,
        child_name=name,
    )


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
            "User-Agent": "MYRana/1.0",
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

    # جدول أولياء الأمور (parent_id)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS guardians (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        email TEXT UNIQUE NOT NULL,
        created_at TEXT
    )
    """)

    # FIX: normalize child_code to support codes with or without CHILD- prefix
    _migrate_child_codes_in_db(cur)

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

    # سياسة وقت الشاشة لكل طفل
    cur.execute("""
    CREATE TABLE IF NOT EXISTS screen_time_policies (
        child_code TEXT PRIMARY KEY,
        policy_json TEXT NOT NULL,
        updated_at TEXT
    )
    """)

    # آخر اتصال لجهاز الطفل
    cur.execute("""
    CREATE TABLE IF NOT EXISTS child_status (
        child_code TEXT PRIMARY KEY,
        last_seen_ms INTEGER DEFAULT 0,
        device_name TEXT
    )
    """)

    # أحداث وقت الشاشة (تحذيرات / إغلاق)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS screen_time_events (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        child_code TEXT,
        event_type TEXT,
        package_name TEXT,
        message TEXT,
        seconds_used INTEGER DEFAULT 0,
        created_at_ms INTEGER,
        time TEXT
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
    # FIX: normalize child_code to support codes with or without CHILD- prefix
    device_id = db_child_code(device_id) or (device_id or "").strip()
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
    device_id = db_child_code(device_id) or device_id.strip()
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
    device_id = db_child_code(device_id) or device_id.strip()
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
    raw = str(data.get("child_code") or data.get("childCode") or "").strip()
    suffix = clean_child_code(raw)
    merge = data.get("merge", True)
    if not suffix:
        return _json_error("child_code required", 400, error_code="missing_child_code")
    conn = db()
    cur = conn.cursor()
    row = find_child_device(cur, raw, log_on_miss=False)
    child_code = row["child_code"] if row else suffix
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
        "MYRana — رمز التحقق من البريد",
        f"رمز التحقق لبريدك ({email}):\n\n{code}\n\n"
        f"أدخليه في تطبيق الأم لتأكيد أن البريد ملكك.",
    )

    payload = verification_payload(
        code,
        email_sent,
        f"تم إرسال رمز التحقق إلى {email}",
        "لم يُرسل البريد — الرمز للتطوير فقط",
    )
    payload["email_verify_code"] = code
    return jsonify(payload)


# التحقق من رمز البريد
@app.route("/verify-email-code", methods=["POST"])
def verify_email_code():
    try:
        data = request.get_json(silent=True) or {}
        email = _extract_parent_email(data)
        code = _extract_verification_code(data)

        if not email or not code:
            return _json_error("email و verification_code مطلوبان", 400)

        conn = db()
        cur = conn.cursor()
        cur.execute(
            """
            SELECT * FROM email_codes
            WHERE email = ? AND code = ?
            ORDER BY id DESC
            LIMIT 1
            """,
            (email, code),
        )
        row = cur.fetchone()

        if not row:
            conn.close()
            logger.info("[verify-email] failed email=%s code=%s reason=not_found", email, code[:2] + "****")
            return _json_error("كود التحقق غير صحيح", 400, error_code="invalid_code")

        if _otp_expired(row["created_at"], OTP_EMAIL_EXPIRY_MINUTES):
            conn.close()
            logger.info("[verify-email] expired email=%s", email)
            return _json_error("كود التحقق منتهي الصلاحية — أرسلي رمزاً جديداً", 400, error_code="expired_code")

        cur.execute("UPDATE email_codes SET verified = 1 WHERE id = ?", (row["id"],))
        conn.commit()
        conn.close()
        return jsonify({"status": "success", "message": "تم التحقق من البريد"})
    except Exception as exc:
        logger.exception("verify-email-code failed: %s", exc)
        return _json_error("خطأ داخلي أثناء التحقق من البريد", 500, error_code="server_error")


# تسجيل جهاز الطفل — بدون بريد (التحقق مرة واحدة عند الربط من تطبيق الأم)
@app.route("/register-child-device", methods=["POST"])
def register_child_device():
    try:
        data = request.get_json(silent=True) or {}
        raw_child = str(data.get("child_code") or data.get("childCode") or "").strip()
        suffix = clean_child_code(raw_child)
        child_email = (data.get("child_email") or "").strip()
        device_name = (data.get("device_name") or data.get("device") or "").strip()
        android_version = (data.get("android_version") or "").strip()

        if not suffix or not device_name:
            return _json_error("child_code و device_name مطلوبان", 400)

        conn = db()
        cur = conn.cursor()
        existing = find_child_device(cur, raw_child)

        if existing and existing["linked"]:
            conn.close()
            return _json_error("الجهاز مربوط مسبقاً", 400, error_code="already_linked")

        if existing:
            # لا نغيّر device_verify_code عند إعادة التسجيل — حتى يبقى رمز الربط صالحاً
            cur.execute(
                """
                UPDATE child_devices
                SET child_email = ?, device_name = ?, android_version = ?
                WHERE child_code = ?
                """,
                (child_email, device_name, android_version, existing["child_code"]),
            )
            stored = existing["child_code"]
        else:
            device_code = str(random.randint(100000, 999999))
            # FIX: normalize child_code — يُخزَّن بدون بادئة: 1DF71288
            cur.execute(
                """
                INSERT INTO child_devices
                (child_code, child_email, device_name, android_version, device_verify_code, linked, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (suffix, child_email, device_name, android_version, device_code, 0, now()),
            )
            stored = suffix

        conn.commit()
        conn.close()
        return jsonify({
            "status": "success",
            "message": "تم تسجيل الجهاز — انتظر ربط ولي الأمر",
            "child_code": normalize_child_code(stored),
            "child_code_clean": clean_child_code(stored),
        })
    except Exception as exc:
        logger.exception("register-child-device failed: %s", exc)
        return _json_error("خطأ داخلي أثناء تسجيل الطفل", 500, error_code="server_error")


# إرسال رمز الربط لبريد ولي الأمر — مرة واحدة أثناء الربط
@app.route("/send-link-code", methods=["POST"])
def send_link_code():
    try:
        data = request.get_json(silent=True) or {}
        parent_email = _extract_parent_email(data)
        child_code = _extract_child_code(data)

        if not parent_email or not child_code:
            return _json_error("parent_email و child_code مطلوبان", 400)

        conn = db()
        cur = conn.cursor()
        if not _guardian_verified(cur, parent_email):
            conn.close()
            return _json_error(
                "يجب التحقق من بريد ولي الأمر أولاً (رمز التحقق)",
                400,
                error_code="parent_email_not_verified",
            )

        raw_child = data.get("child_code") or data.get("childCode") or child_code
        row = find_child_device(cur, raw_child, log_on_miss=False)
        if not row:
            conn.close()
            return _child_not_found_response(
                raw_child,
                "لم يُعثر على جهاز الطفل — سجّلي من جوال الطفل أولاً (CHILD-...)",
            )

        child_code = row["child_code"]
        if row["linked"]:
            conn.close()
            return _json_error("الجهاز مربوط مسبقاً", 400, error_code="already_linked")

        # رمز جديد في كل إرسال — يطابق ما في Gmail وما في قاعدة البيانات
        device_code = str(random.randint(100000, 999999))
        cur.execute(
            """
            UPDATE child_devices
            SET device_verify_code = ?, device_verified = 0
            WHERE child_code = ?
            """,
            (device_code, child_code),
        )
        conn.commit()
        conn.close()

        _log_link_context("send-link-code", parent_email, child_code, device_code, device_code, "sent")

        email_sent = send_email(
            parent_email,
            "MYRana — رمز ربط الطفل",
            f"رمز ربط الطفل ({child_code}):\n\n{device_code}\n\n"
            f"أدخليه في تطبيق الأم لإتمام الربط.\n"
            f"(هذا ليس رمز تحقق البريد الأول)",
        )

        payload = verification_payload(
            device_code,
            email_sent,
            "تم إرسال رمز الربط إلى بريدك",
            "SMTP غير مضبوط — الرمز للتطوير فقط",
        )
        # تطبيق الأم يستخدمه للربط التلقائي (بعد تحقق البريد + API_KEY)
        payload["link_code"] = device_code
        return jsonify(payload)
    except Exception as exc:
        logger.exception("send-link-code failed: %s", exc)
        return _json_error("خطأ داخلي أثناء إرسال رمز الربط", 500, error_code="server_error")


# هل اكتمل ربط جهاز الطفل؟ (يستعلم عنه تطبيق الطفل)
@app.route("/child-link-status", methods=["GET"])
def child_link_status():
    raw = request.args.get("child_code", "")
    if not clean_child_code(raw):
        return _json_error("child_code required", 400, error_code="missing_child_code")

    conn = db()
    cur = conn.cursor()
    row = find_child_device(cur, raw, log_on_miss=False)
    conn.close()

    if not row:
        return _child_not_found_response(
            raw,
            "لم يُعثر على جهاز الطفل — سجّلي من جوال الطفل أولاً",
        )

    return jsonify({
        "status": "success",
        "child_code": normalize_child_code(row["child_code"]),
        "child_code_clean": clean_child_code(row["child_code"]),
        "linked": bool(row["linked"]),
    })


# التحقق من رمز جهاز الطفل — اختياري قبل الربط النهائي
@app.route("/verify-child-device-code", methods=["POST"])
def verify_child_device_code():
    try:
        data = request.get_json(silent=True) or {}
        child_code = _extract_child_code(data)
        code = _extract_verification_code(data)
        parent_email = _extract_parent_email(data)

        if not child_code or not code:
            return _json_error("child_code و verification_code مطلوبان", 400)

        conn = db()
        cur = conn.cursor()
        raw_child = data.get("child_code") or data.get("childCode") or child_code
        device_row = find_child_device(cur, raw_child, log_on_miss=False)
        stored = str(device_row["device_verify_code"] or "").strip() if device_row else None
        _log_link_context("verify-device", parent_email, child_code, code, stored, "check")

        if not device_row:
            conn.close()
            return _child_not_found_response(
                raw_child,
                "لم يُعثر على جهاز الطفل — من جوال الطفل اضغطي «تسجيل الجهاز» مرة أخرى ثم أعيدي الربط",
            )

        if not stored or stored != code:
            conn.close()
            return _json_error(
                "كود التحقق غير صحيح — استخدمي رمز الربط من Gmail (الرسالة الثانية)",
                400,
                error_code="invalid_verification_code",
            )

        cur.execute(
            "UPDATE child_devices SET device_verified = 1 WHERE child_code = ?",
            (device_row["child_code"],),
        )
        conn.commit()
        conn.close()

        return jsonify({
            "status": "success",
            "message": "تم التحقق من رمز الربط",
            "child_code": device_row["child_code"],
            "child_email": device_row["child_email"],
            "device_name": device_row["device_name"],
            "android_version": device_row["android_version"],
        })
    except Exception as exc:
        logger.exception("verify-child-device-code failed: %s", exc)
        return _json_error("خطأ داخلي أثناء التحقق من رمز الربط", 500, error_code="server_error")


# إضافة وربط الطفل — الاسم اختياري؛ الربط بـ parent_email + child_code + verification_code
@app.route("/add-child", methods=["POST"])
@app.route("/link-child", methods=["POST"])
def add_child():
    conn = None
    try:
        data = request.get_json(silent=True) or {}
        conn = db()
        cur = conn.cursor()
        result = _link_child_transaction(cur, conn, data)
        return result
    except Exception as exc:
        if conn:
            try:
                conn.rollback()
            except Exception:
                pass
        logger.exception("add-child failed: %s\n%s", exc, traceback.format_exc())
        return _json_error(
            "خطأ داخلي أثناء ربط الطفل — راجعي سجلات السيرفر",
            500,
            error_code="server_error",
        )
    finally:
        if conn:
            try:
                conn.close()
            except Exception:
                pass


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
    child_code = db_child_code(data.get("child_code") or data.get("childCode") or "")
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
    child_code = db_child_code(request.args.get("child_code", ""))
    if not child_code:
        return _json_error("child_code required", 400, error_code="missing_child_code")
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
    child_code = normalize_child_code(data.get("child_code", ""))
    message = (data.get("message") or "").strip()
    if not child_code or not message:
        return jsonify({"status": "error", "message": "child_code and message required"}), 400

    conn = db()
    cur = conn.cursor()

    cur.execute("""
    INSERT INTO alerts (message, child_code, time)
    VALUES (?, ?, ?)
    """, (
        message,
        child_code,
        now()
    ))

    conn.commit()
    conn.close()

    return jsonify({"status": "success"})


# رسالة من ولي الأمر — تُحفظ في التنبيهات ليراها في لوحة الأم
@app.route("/send-guardian-message", methods=["POST"])
def send_guardian_message():
    data = request.get_json() or {}
    child_code = normalize_child_code(data.get("child_code", ""))
    message = (data.get("message") or "").strip()
    role = (data.get("guardian_role") or "ولي الأمر").strip()
    if not child_code or not message:
        return jsonify({"status": "error", "message": "child_code and message required"}), 400

    full_message = f"[{role}] {message}"
    conn = db()
    cur = conn.cursor()
    cur.execute(
        "INSERT INTO alerts (message, child_code, time) VALUES (?, ?, ?)",
        (full_message, child_code, now()),
    )
    conn.commit()
    conn.close()
    return jsonify({"status": "success", "message": "تم إرسال الرسالة"})


# عرض التنبيهات للأم
@app.route("/list-children", methods=["GET"])
def list_children():
    """قائمة الأطفال المرتبطين بولي أمر — دعم تعدد الأطفال."""
    parent_email = _extract_parent_email(dict(request.args))
    if not parent_email:
        return _json_error("parent_email is required", 400, error_code="missing_parent_email")
    conn = db()
    cur = conn.cursor()
    parent_id = None
    cur.execute("SELECT id FROM guardians WHERE email = ? LIMIT 1", (parent_email,))
    g = cur.fetchone()
    if g:
        parent_id = int(g["id"])
    cur.execute(
        """
        SELECT c.id AS child_id, c.name, c.age, c.child_code, c.device, c.android_version,
               c.linked_at, cs.last_seen_ms, cs.device_name AS status_device
        FROM children c
        LEFT JOIN child_status cs ON cs.child_code = c.child_code
        WHERE c.guardian_email = ?
        ORDER BY c.id DESC
        """,
        (parent_email,),
    )
    rows = []
    now_ms = int(datetime.now().timestamp() * 1000)
    for r in cur.fetchall():
        last_ms = int(r["last_seen_ms"] or 0)
        online = last_ms > 0 and (now_ms - last_ms) < 180_000
        code_db = r["child_code"]
        rows.append({
            "child_id": int(r["child_id"]),
            "name": r["name"] or "طفل",
            "age": r["age"],
            "child_code": normalize_child_code(code_db),
            "child_code_clean": clean_child_code(code_db),
            "device": r["device"],
            "android_version": r["android_version"],
            "linked_at": r["linked_at"],
            "online": online,
            "last_seen_ms": last_ms,
            "device_name": r["status_device"] or r["device"],
        })
    conn.close()
    return _json_success(
        "Children list",
        parent_id=parent_id,
        parent_email=parent_email,
        children=rows,
        count=len(rows),
    )


@app.route("/alerts", methods=["GET"])
def alerts():
    suffix = db_child_code(request.args.get("child_code", ""))
    if not suffix:
        return _json_error("child_code required", 400, error_code="missing_child_code")
    child_code = suffix

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


DEFAULT_SCREEN_TIME_POLICY = {
    "monitored_packages": [],
    "unlimited_packages": [],
    "warn_minutes": 60,
    "strong_warn_minutes": 90,
    "block_minutes": 120,
    "max_open_apps": 8,
    "max_open_sites": 8,
    "sleep_start": "22:00",
    "sleep_end": "07:00",
    "allow_during_sleep": False,
    "vacation_mode": False,
    "vacation_same_rules": True,
}


def _screen_time_policy_get(conn, child_code: str) -> dict:
    cur = conn.cursor()
    cur.execute(
        "SELECT policy_json FROM screen_time_policies WHERE child_code = ?",
        (child_code,),
    )
    row = cur.fetchone()
    if not row:
        return dict(DEFAULT_SCREEN_TIME_POLICY)
    try:
        data = json.loads(row["policy_json"] or "{}")
        merged = dict(DEFAULT_SCREEN_TIME_POLICY)
        merged.update(data)
        return merged
    except Exception:
        return dict(DEFAULT_SCREEN_TIME_POLICY)


def _screen_time_policy_save(conn, child_code: str, policy: dict) -> None:
    merged = dict(DEFAULT_SCREEN_TIME_POLICY)
    merged.update(policy or {})
    cur = conn.cursor()
    cur.execute(
        """
        INSERT OR REPLACE INTO screen_time_policies (child_code, policy_json, updated_at)
        VALUES (?, ?, ?)
        """,
        (child_code, json.dumps(merged, ensure_ascii=False), now()),
    )


@app.route("/screen-time-policy", methods=["GET", "POST"])
def screen_time_policy():
    if request.method == "GET":
        suffix = db_child_code(request.args.get("child_code", ""))
        if not suffix:
            return _json_error("child_code required", 400, error_code="missing_child_code")
        conn = db()
        policy = _screen_time_policy_get(conn, suffix)
        conn.close()
        return jsonify({
            "success": True,
            "child_code": normalize_child_code(suffix),
            "child_code_clean": suffix,
            "policy": policy,
        })

    data = request.get_json() or {}
    suffix = db_child_code(data.get("child_code") or data.get("childCode") or "")
    if not suffix:
        return _json_error("child_code required", 400, error_code="missing_child_code")
    child_code = suffix
    policy = data.get("policy") or {}
    conn = db()
    _screen_time_policy_save(conn, child_code, policy)
    conn.commit()
    conn.close()
    return jsonify({"status": "success", "message": "Screen time policy saved"})


@app.route("/child-heartbeat", methods=["POST"])
def child_heartbeat():
    data = request.get_json() or {}
    child_code = normalize_child_code(data.get("child_code", ""))
    ts_ms = int(data.get("ts_ms") or 0)
    if not child_code:
        return jsonify({"status": "error", "message": "child_code required"}), 400
    conn = db()
    cur = conn.cursor()
    device_name = ""
    cur.execute(
        "SELECT device_name FROM child_devices WHERE child_code = ? LIMIT 1",
        (child_code,),
    )
    row = cur.fetchone()
    if row:
        device_name = row["device_name"] or ""
    cur.execute(
        """
        INSERT OR REPLACE INTO child_status (child_code, last_seen_ms, device_name)
        VALUES (?, ?, ?)
        """,
        (child_code, ts_ms or int(datetime.now().timestamp() * 1000), device_name),
    )
    conn.commit()
    conn.close()
    return jsonify({"status": "success"})


@app.route("/screen-time-events", methods=["POST"])
def screen_time_events():
    data = request.get_json() or {}
    child_code = normalize_child_code(data.get("child_code", ""))
    events = data.get("events") or []
    if not child_code:
        return jsonify({"status": "error", "message": "child_code required"}), 400
    conn = db()
    cur = conn.cursor()
    for ev in events:
        if not isinstance(ev, dict):
            continue
        cur.execute(
            """
            INSERT INTO screen_time_events
            (child_code, event_type, package_name, message, seconds_used, created_at_ms, time)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """,
            (
                child_code,
                ev.get("event_type", ""),
                ev.get("package_name", ""),
                ev.get("message", ""),
                int(ev.get("seconds_used") or 0),
                int(ev.get("created_at_ms") or 0),
                now(),
            ),
        )
        msg = (ev.get("message") or "").strip()
        if msg:
            cur.execute(
                "INSERT INTO alerts (message, child_code, time) VALUES (?, ?, ?)",
                (msg, child_code, now()),
            )
    conn.commit()
    conn.close()
    return jsonify({"status": "success"})


@app.route("/child-dashboard", methods=["GET"])
def child_dashboard():
    raw = request.args.get("child_code", "")
    suffix = db_child_code(raw)
    if not suffix:
        return _json_error("child_code required", 400, error_code="missing_child_code")
    child_code = suffix
    today = datetime.now().strftime("%Y-%m-%d")
    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT name FROM children WHERE child_code = ? LIMIT 1", (child_code,))
    child_row = cur.fetchone()
    child_name = child_row["name"] if child_row else child_code

    cur.execute(
        "SELECT last_seen_ms, device_name FROM child_status WHERE child_code = ?",
        (child_code,),
    )
    status_row = cur.fetchone()
    last_seen_ms = int(status_row["last_seen_ms"]) if status_row else 0
    device_name = status_row["device_name"] if status_row else ""

    online = False
    if last_seen_ms > 0:
        online = (int(datetime.now().timestamp() * 1000) - last_seen_ms) < 180_000

    cur.execute(
        """
        SELECT COALESCE(SUM(total_seconds), 0) AS total
        FROM usage_daily WHERE child_code = ? AND day = ?
        """,
        (child_code, today),
    )
    today_seconds = int(cur.fetchone()["total"] or 0)

    cur.execute(
        """
        SELECT COUNT(DISTINCT package_name) AS cnt
        FROM usage_daily WHERE child_code = ? AND day = ? AND total_seconds > 0
        """,
        (child_code, today),
    )
    apps_opened = int(cur.fetchone()["cnt"] or 0)

    policy = _screen_time_policy_get(conn, child_code)
    conn.close()

    return jsonify({
        "success": True,
        "child_code": normalize_child_code(child_code),
        "child_code_clean": child_code,
        "child_name": child_name,
        "device_name": device_name,
        "online": online,
        "last_seen_ms": last_seen_ms,
        "today_seconds": today_seconds,
        "apps_opened": apps_opened,
        "policy": policy,
    })


@app.route("/daily-report", methods=["GET"])
def daily_report():
    suffix = db_child_code(request.args.get("child_code", ""))
    if not suffix:
        return _json_error("child_code required", 400, error_code="missing_child_code")
    child_code = suffix
    today = datetime.now().strftime("%Y-%m-%d")
    conn = db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT package_name, total_seconds
        FROM usage_daily
        WHERE child_code = ? AND day = ?
        ORDER BY total_seconds DESC
        LIMIT 30
        """,
        (child_code, today),
    )
    apps = [dict(r) for r in cur.fetchall()]
    conn.close()
    return jsonify({"child_code": child_code, "day": today, "apps": apps})


@app.errorhandler(404)
def not_found_json(error):
    return _json_error("المسار غير موجود", 404, error_code="not_found")


@app.errorhandler(500)
def server_error_json(error):
    logger.exception("unhandled 500: %s", error)
    return _json_error("خطأ داخلي في السيرفر", 500, error_code="server_error")


@app.errorhandler(Exception)
def unhandled_exception(error):
    logger.exception("unhandled exception: %s", error)
    return _json_error("خطأ غير متوقع", 500, error_code="server_error")


# إنشاء الجداول عند تشغيل السيرفر
init_db()

# تشغيل محلي فقط، أما Render يستخدم gunicorn
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
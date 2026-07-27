# ==============================
# ط³ظٹط±ظپط± ظ…ط´ط±ظˆط¹ ط§ظ„ط±ظ‚ط§ط¨ط© ط§ظ„ط£ط¨ظˆظٹط©
# Parental Control Server
#
# ظˆط¸ط§ط¦ظپ ط§ظ„ط³ظٹط±ظپط±:
# 1) ط¥ط±ط³ط§ظ„ ط±ظ…ط² طھط­ظ‚ظ‚ ظ„ط¨ط±ظٹط¯ ظˆظ„ظٹ ط§ظ„ط£ظ…ط±
# 2) ط§ظ„طھط­ظ‚ظ‚ ظ…ظ† ط±ظ…ط² ط§ظ„ط¨ط±ظٹط¯
# 3) طھط³ط¬ظٹظ„ ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„
# 4) ط±ط¨ط· ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„ ط¨ط­ط³ط§ط¨ ظˆظ„ظٹ ط§ظ„ط£ظ…ط±
# 5) ط¥ط±ط³ط§ظ„ ط£ظˆط§ظ…ط± ط§ظ„طھط­ظƒظ… ظ„ظ„ط·ظپظ„
# 6) ط§ط³طھظ‚ط¨ط§ظ„ ط§ظ„طھظ‚ط§ط±ظٹط± ظˆط§ظ„طھظ†ط¨ظٹظ‡ط§طھ
# 7) ط­ظپط¸ ط§ظ„طھط­ظƒظ… ط§ظ„ط²ظ…ظ†ظٹ
# ==============================

from flask import Flask, request, jsonify
from datetime import datetime, timedelta
import json
import logging
import sqlite3
import os
import random
import smtplib
import threading
import traceback
import urllib.error
import urllib.request
import hmac
import hashlib
from email.message import EmailMessage
from typing import Tuple

# ط³ط¬ظ„ط§طھ ط±ط¨ط· ط§ظ„ط·ظپظ„ â€” طھط¸ظ‡ط± ظپظٹ log ط§ظ„ط³ظٹط±ظپط± (Render â†’ Logs)
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("myrana.link")

# طµظ„ط§ط­ظٹط© ط±ظ…ظˆط² ط§ظ„ط¨ط±ظٹط¯ (ط¯ظ‚ط§ط¦ظ‚)
OTP_EMAIL_EXPIRY_MINUTES = int(os.environ.get("OTP_EMAIL_EXPIRY_MINUTES", "60"))
# طµظ„ط§ط­ظٹط© ط±ظ…ط² ط±ط¨ط· ط§ظ„ط¬ظ‡ط§ط² (ط¯ظ‚ط§ط¦ظ‚) â€” 0 = ط¨ط¯ظˆظ† ط§ظ†طھظ‡ط§ط،
DEVICE_OTP_EXPIRY_MINUTES = int(os.environ.get("DEVICE_OTP_EXPIRY_MINUTES", "60"))

# ط¥ظ†ط´ط§ط، طھط·ط¨ظٹظ‚ Flask
app = Flask(__name__)

# ظ‚ط§ط¹ط¯ط© ط§ظ„ط¨ظٹط§ظ†ط§طھ â€” Render ط§ظ„ظ…ط¬ط§ظ†ظٹ ظٹظ…ط³ط­ ط§ظ„ظ…ظ„ظپط§طھ ط¹ظ†ط¯ ط¥ط¹ط§ط¯ط© ط§ظ„طھط´ط؛ظٹظ„.
# ط§ظ„ط­ظ„: ط£ط¶ظٹظپظٹ ط¹ظ„ظ‰ Render:
#   TURSO_DATABASE_URL=libsql://....turso.io
#   TURSO_AUTH_TOKEN=...
# ط£ظˆ ظ‚ط±طµط§ظ‹ ط¯ط§ط¦ظ…ط§ظ‹: DATA_DIR=/var/data
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


def _turso_credentials() -> Tuple[str, str]:
    url = (
        os.environ.get("TURSO_DATABASE_URL", "").strip()
        or os.environ.get("LIBSQL_URL", "").strip()
    )
    token = (
        os.environ.get("TURSO_AUTH_TOKEN", "").strip()
        or os.environ.get("LIBSQL_AUTH_TOKEN", "").strip()
    )
    return url, token


def _db_mode() -> str:
    url, token = _turso_credentials()
    if url and token:
        return "turso"
    path = _resolve_db_path()
    if path.startswith("/var/data"):
        return "local_persistent"
    return "local_ephemeral"


DB = _resolve_db_path()
DB_MODE = _db_mode()

# ظ…ظپطھط§ط­ ط­ظ…ط§ظٹط© ط§ظ„ط·ظ„ط¨ط§طھ ط¨ظٹظ† ط§ظ„طھط·ط¨ظٹظ‚ ظˆط§ظ„ط³ظٹط±ظپط±
API_KEY = os.environ.get("API_KEY", "graduation-secret-key")

# ط¨ظٹط§ظ†ط§طھ ط§ظ„ط¨ط±ظٹط¯ ظ„ط¥ط±ط³ط§ظ„ ط±ظ…ظˆط² ط§ظ„طھط­ظ‚ظ‚ (Gmail App Password ط¹ظ„ظ‰ Render)
# SMTP_USER=your@gmail.com  SMTP_PASS=16-char-app-password  SMTP_PORT=465
SMTP_USER = os.environ.get("SMTP_USER", "").strip()
# App Password: ط£ط­ظٹط§ظ†ط§ظ‹ ظٹظڈظ„طµظ‚ ظ…ط¹ ظ…ط³ط§ظپط§طھ â€” ظ†ط²ظٹظ„ظ‡ط§
SMTP_PASS = os.environ.get("SMTP_PASS", "").replace(" ", "").strip()
SMTP_HOST = os.environ.get("SMTP_HOST", "smtp.gmail.com").strip()
SMTP_PORT = int(os.environ.get("SMTP_PORT", "465"))
SMTP_LAST_ERROR = ""
# Render ط§ظ„ظ…ط¬ط§ظ†ظٹ ظٹط­ط¸ط± SMTP â€” ط§ط³طھط®ط¯ظ…ظٹ Resend API (HTTPS) ط¨ط¯ظ„ط§ظ‹ ظ…ظ† Gmail SMTP
RESEND_API_KEY = os.environ.get("RESEND_API_KEY", "").strip()
RESEND_FROM = os.environ.get(
    "RESEND_FROM", "MYRana <onboarding@resend.dev>"
).strip()


# ط¯ط§ظ„ط© طھط±ط¬ط¹ ط§ظ„ظˆظ‚طھ ط§ظ„ط­ط§ظ„ظٹ


# FIX: normalize child_code to support codes with or without CHILD- prefix
def clean_child_code(raw):
    """
    طھظ†ط¸ظٹظپ ظƒظˆط¯ ط§ظ„ط·ظپظ„ ظ„ظ„ط¨ط­ط« ظپظٹ ظ‚ط§ط¹ط¯ط© ط§ظ„ط¨ظٹط§ظ†ط§طھ:
    trim â†’ uppercase â†’ ط¥ط²ط§ظ„ط© CHILD- â†’ ط£ط­ط±ظپ ظˆط£ط±ظ‚ط§ظ… ظپظ‚ط·.
    ظ…ط«ط§ظ„: CHILD-1DF71288 â†’ 1DF71288
    """
    code = (raw or "").strip().upper()
    if code.startswith("CHILD-"):
        code = code[6:]
    suffix = "".join(ch for ch in code if ch.isalnum())
    return suffix


def normalize_child_code(raw):
    """ط§ظ„طµظٹط؛ط© ط§ظ„ظ‚ظٹط§ط³ظٹط© ظ„ظ„طھط®ط²ظٹظ† ظˆط§ظ„ط§ط³طھط¬ط§ط¨ط©: CHILD-XXXXXXXX"""
    suffix = clean_child_code(raw)
    if not suffix:
        return ""
    return f"CHILD-{suffix}"


def find_child_device(cur, child_code_raw, log_on_miss=True):
    """ظٹط¨ط­ط« ط¨ظ€ CHILD-1DF71288 ط£ظˆ 1DF71288 ط£ظˆ ط£ظٹ طµظٹط؛ط© ظ…ط®ط²ظ‘ظ†ط©."""
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


def _child_not_found_response(raw, detail_ar: str = ""):
    """JSON ظ…ظˆط­ظ‘ط¯ â€” Child not found + ط§ظ„ظƒظˆط¯ ط§ظ„ط£طµظ„ظٹ ظˆط§ظ„ظ…ظ†ط¸ظ‘ظپ ظپظٹ ط§ظ„ط³ط¬ظ„ط§طھ."""
    cleaned = clean_child_code(raw)
    logger.warning("child_not_found original=%r cleaned=%r", raw, cleaned)
    extra = {
        "error_code": "child_not_found",
        "child_code_input": (raw or "").strip(),
        "child_code_clean": cleaned,
    }
    if detail_ar:
        extra["detail_ar"] = detail_ar
    return _json_error("Child not found", 404, **extra)


def _migrate_child_codes_in_db(cur):
    """FIX: normalize child_code to support codes with or without CHILD- prefix â€” طھط±ط­ظٹظ„ DB."""
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
    """ط§ط³طھط¬ط§ط¨ط© JSON ظ…ظˆط­ظ‘ط¯ط© â€” ظ„ط§ HTML."""
    payload = {"success": False, "status": "error", "message": message}
    payload.update(extra)
    return jsonify(payload), code


def _json_success(message: str, code: int = 200, **extra):
    """ظ†ط¬ط§ط­ â€” JSON ظپظ‚ط· ظ…ط¹ success: true."""
    payload = {"success": True, "status": "success", "message": message}
    payload.update(extra)
    return jsonify(payload), code


def _usage_period_days(raw, default: int = 7, max_days: int = 30) -> int:
    try:
        days = int(raw if raw is not None else default)
    except (TypeError, ValueError):
        days = default
    return max(1, min(days, max_days))


def _attach_avg_seconds_per_day(apps, days: int):
    span = max(1, days)
    enriched = []
    for row in apps:
        item = dict(row)
        total = int(item.get("total_seconds") or 0)
        item["avg_seconds_per_day"] = total // span
        enriched.append(item)
    return enriched


def _avg_daily_screen_seconds(usage_by_day, days: int) -> int:
    total = sum(int(r.get("total_seconds") or 0) for r in usage_by_day)
    return total // max(1, days)


def _load_app_meta_map(cur, child_code: str, packages) -> dict:
    pkgs = [str(p).strip().lower() for p in packages if str(p).strip()]
    if not pkgs:
        return {}
    placeholders = ",".join("?" * len(pkgs))
    cur.execute(
        f"""
        SELECT package_name, app_label, icon_b64
        FROM child_app_meta
        WHERE child_code = ? AND package_name IN ({placeholders})
        """,
        [child_code] + pkgs,
    )
    return {
        str(r["package_name"]).lower(): dict(r)
        for r in cur.fetchall()
    }


def _enrich_app_rows(cur, child_code: str, rows: list) -> list:
    meta = _load_app_meta_map(cur, child_code, [r.get("package_name") for r in rows])
    enriched = []
    for row in rows:
        item = dict(row)
        pkg = str(item.get("package_name") or "").lower()
        m = meta.get(pkg, {})
        label = (m.get("app_label") or "").strip()
        if not label:
            label = pkg.split(".")[-1] if pkg else "?"
        item["app_label"] = label
        icon = (m.get("icon_b64") or "").strip()
        if icon:
            item["icon_b64"] = icon
        enriched.append(item)
    return enriched


def _upsert_child_app_meta(conn, child_code: str, apps: list) -> int:
    if not apps:
        return 0
    cur = conn.cursor()
    saved = 0
    ts = now()
    for app in apps:
        if not isinstance(app, dict):
            continue
        pkg = _norm_pkg(app.get("package") or app.get("package_name") or "")
        if not pkg:
            continue
        label = (app.get("app_label") or app.get("label") or "").strip()
        icon = (app.get("icon_b64") or app.get("icon") or "").strip()
        cur.execute(
            """
            INSERT INTO child_app_meta (child_code, package_name, app_label, icon_b64, updated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(child_code, package_name) DO UPDATE SET
                app_label = excluded.app_label,
                icon_b64 = CASE
                    WHEN excluded.icon_b64 != '' THEN excluded.icon_b64
                    ELSE child_app_meta.icon_b64
                END,
                updated_at = excluded.updated_at
            """,
            (child_code, pkg, label, icon, ts),
        )
        saved += 1
    return saved


def _ensure_guardian(cur, email: str) -> int:
    """ط¥ظ†ط´ط§ط،/ط¬ظ„ط¨ parent_id ظ…ظ† ط¨ط±ظٹط¯ ظˆظ„ظٹ ط§ظ„ط£ظ…ط±."""
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
    """ظ…ظپطھط§ط­ ظ‚ط§ط¹ط¯ط© ط§ظ„ط¨ظٹط§ظ†ط§طھ â€” CHILD-1DF71288 â†’ 1DF71288"""
    return clean_child_code(raw)


def _extract_parent_email(data: dict) -> str:
    return (
        data.get("guardian_email")
        or data.get("parent_email")
        or data.get("email")
        or ""
    ).strip()


def _extract_verification_code(data: dict) -> str:
    """ط±ظ…ط² ط§ظ„ط±ط¨ط· â€” ط£ط³ظ…ط§ط، ظ…ظˆط­ظ‘ط¯ط© ط¨ظٹظ† Android ظˆ Flask."""
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


def _child_code_from_request_args() -> str:
    """ظ…ظپطھط§ط­ DB ظ…ظ† query string â€” CHILD-1DF71288 â†’ 1DF71288"""
    return db_child_code(request.args.get("child_code", ""))


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
    return name or "ط·ظپظ„"


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


def _make_restore_token(parent_email: str, child_code: str) -> str:
    """ط±ظ…ط² ط§ط³طھط¹ط§ط¯ط© ط§ظ„ط±ط¨ط· â€” ظٹظڈط­ظپط¸ ط¹ظ„ظ‰ ط¬ظˆط§ظ„ ط§ظ„ط£ظ… ط¨ط¹ط¯ ط£ظˆظ„ ط±ط¨ط· ظ†ط§ط¬ط­."""
    email = (parent_email or "").strip().lower()
    code = db_child_code(child_code) or clean_child_code(child_code)
    if not email or not code:
        return ""
    key = (API_KEY or "graduation-secret-key").encode("utf-8")
    msg = f"restore|{email}|{code}".encode("utf-8")
    return hmac.new(key, msg, hashlib.sha256).hexdigest()


def _restore_link_transaction(cur, conn, data: dict):
    """ط¥ط¹ط§ط¯ط© ط±ط¨ط· ط§ظ„ط£ظ… ط¨ط§ظ„ط·ظپظ„ ط¨ط¹ط¯ ظپظ‚ط¯ط§ظ† ط¨ظٹط§ظ†ط§طھ Render â€” ط¨ط¯ظˆظ† Turso."""
    parent_email = _extract_parent_email(data)
    raw_child = str(data.get("child_code") or data.get("childCode") or "").strip()
    child_code = db_child_code(raw_child) or clean_child_code(raw_child)
    token = (data.get("restore_token") or "").strip()
    name = (data.get("name") or data.get("child_name") or "ط·ظپظ„").strip() or "ط·ظپظ„"
    try:
        age = int(data.get("age") or 10)
    except (TypeError, ValueError):
        age = 10
    guardian_role = (data.get("guardian_role") or "ظˆظ„ظٹ ط£ظ…ط±").strip() or "ظˆظ„ظٹ ط£ظ…ط±"

    if not parent_email or not child_code or not token:
        return _json_error(
            "parent_email ظˆ child_code ظˆ restore_token ظ…ط·ظ„ظˆط¨ط§ظ†",
            400,
            error_code="missing_fields",
        )

    expected = _make_restore_token(parent_email, child_code)
    if not expected or not hmac.compare_digest(expected, token):
        return _json_error(
            "ط±ظ…ط² ط§ط³طھط¹ط§ط¯ط© ط§ظ„ط±ط¨ط· ط؛ظٹط± طµط§ظ„ط­",
            403,
            error_code="invalid_restore_token",
        )

    device_row = find_child_device(cur, raw_child, log_on_miss=False)
    if not device_row:
        return _child_not_found_response(
            raw_child,
            "ظ…ظ† ط¬ظˆط§ظ„ ط§ظ„ط·ظپظ„: ط§ظپطھط­ظٹ ط§ظ„طھط·ط¨ظٹظ‚ ظ„ظٹظڈط¹ط§ط¯ ط§ظ„طھط³ط¬ظٹظ„ ط«ظ… ط£ط¹ظٹط¯ظٹ ط§ظ„ظ…ط­ط§ظˆظ„ط©",
        )

    device_db_key = device_row["child_code"]
    child_email = (device_row["child_email"] or parent_email).strip()
    device = (device_row["device_name"] or "Android").strip()
    android_version = (device_row["android_version"] or "Android").strip()

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
        (device_db_key,),
    )
    try:
        apply_default_blocklist(conn, child_code, merge=True)
    except Exception as block_err:
        logger.warning("apply_default_blocklist after restore-link failed: %s", block_err)

    _ensure_guardian(cur, parent_email)
    conn.commit()
    logger.info("[restore-link] OK parent=%s child=%r", parent_email, child_code)
    return _json_success(
        "طھظ… ط§ط³طھط¹ط§ط¯ط© ط§ظ„ط±ط¨ط· ط¨ط¹ط¯ ط¥ط¹ط§ط¯ط© طھط´ط؛ظٹظ„ ط§ظ„ط³ظٹط±ظپط±",
        child_code=normalize_child_code(child_code),
        child_code_clean=child_code,
        child_name=name,
        restore_token=expected,
    )


def _link_child_transaction(cur, conn, data: dict):
    """
    ط±ط¨ط· ط§ظ„ط·ظپظ„ â€” ظٹط¹طھظ…ط¯ ط¹ظ„ظ‰:
      parent_email / guardian_email / email
      child_code (CHILD-1DF71288 ط£ظˆ 1DF71288)
      verification_code / device_verify_code / otp / code
    ط§ظ„ط§ط³ظ… ط§ط®طھظٹط§ط±ظٹ (ط§ظپطھط±ط§ط¶ظٹ: ط·ظپظ„) â€” ظ„ط§ ظٹظڈط³طھط®ط¯ظ… ظپظٹ ط§ظ„طھط­ظ‚ظ‚.
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
    guardian_role = (data.get("guardian_role") or "ظˆظ„ظٹ ط£ظ…ط±").strip() or "ظˆظ„ظٹ ط£ظ…ط±"
    device = (data.get("device") or "").strip()
    android_version = (data.get("android_version") or "").strip()
    child_email = (data.get("child_email") or parent_email).strip()

    def _fail(message, code=400, **extra):
        conn.rollback()
        return _json_error(message, code, **extra)

    if not parent_email:
        _log_link_context("add-child", parent_email, child_code, verify_code, None, "missing parent_email")
        return _fail("parent_email ظ…ط·ظ„ظˆط¨", error_code="missing_parent_email")

    if not child_code:
        _log_link_context("add-child", parent_email, child_code, verify_code, None, "missing child_code")
        return _fail("child_code ظ…ط·ظ„ظˆط¨", error_code="missing_child_code")

    if not verify_code:
        _log_link_context("add-child", parent_email, child_code, verify_code, None, "missing verification_code")
        return _fail("verification_code ظ…ط·ظ„ظˆط¨", error_code="missing_verification_code")

    if not _guardian_verified(cur, parent_email):
        _log_link_context("add-child", parent_email, child_code, verify_code, None, "parent not verified")
        return _fail(
            "ظٹط¬ط¨ ط§ظ„طھط­ظ‚ظ‚ ظ…ظ† ط¨ط±ظٹط¯ ظˆظ„ظٹ ط§ظ„ط£ظ…ط± ط£ظˆظ„ط§ظ‹ â€” ط£ط±ط³ظ„ظٹ ط±ظ…ط² ط§ظ„طھط­ظ‚ظ‚ ظ…ظ† Gmail",
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
            "ط³ط¬ظ‘ظ„ظٹ ط§ظ„ط¬ظ‡ط§ط² ظ…ظ† طھط·ط¨ظٹظ‚ ط§ظ„ط·ظپظ„ ط£ظˆظ„ط§ظ‹ (CHILD-...)",
        )

    # ظ…ظپطھط§ط­ ط§ظ„طµظپ ط§ظ„ظپط¹ظ„ظٹ ظپظٹ child_devices (ظ‚ط¯ ظٹظƒظˆظ† 1DF71288 ط£ظˆ CHILD-1DF71288 ظ‚ط¨ظ„ ط§ظ„طھط±ط­ظٹظ„)
    device_db_key = str(device_row["child_code"] or "").strip()
    child_code = db_child_code(device_db_key)
    logger.info(
        "[link-child] step=child_lookup OK device_db_key=%r child_code_db=%r",
        device_db_key,
        child_code,
    )

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
            # 200 ظˆظ„ظٹط³ 409 â€” Android ظٹظ‚ط±ط£ JSON ظپظ‚ط· ط¹ظ†ط¯ isSuccessful
            return _json_success(
                "Child linked successfully",
                200,
                already_linked=True,
                parent_id=parent_id,
                child_id=int(existing["id"]),
                child_code=normalize_child_code(child_code),
                child_code_clean=child_code,
                restore_token=_make_restore_token(parent_email, child_code),
            )
        return _fail(
            "ط§ظ„ط¬ظ‡ط§ط² ظ…ط±ط¨ظˆط· ط¨ط­ط³ط§ط¨ ط£ظ… ط¢ط®ط±",
            409,
            error_code="already_linked_other_parent",
        )

    device_created = device_row["created_at"] if "created_at" in device_row.keys() else None
    if DEVICE_OTP_EXPIRY_MINUTES > 0 and _otp_expired(device_created, DEVICE_OTP_EXPIRY_MINUTES):
        _log_link_context("add-child", parent_email, child_code, verify_code, stored_code, "expired verification_code")
        return _fail(
            "Invalid or expired verification code",
            400,
            error_code="expired_code",
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
        (device_db_key,),
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
        restore_token=_make_restore_token(parent_email, child_code),
    )


# ط¯ط§ظ„ط© ط§ظ„ط§طھطµط§ظ„ ط¨ظ‚ط§ط¹ط¯ط© ط§ظ„ط¨ظٹط§ظ†ط§طھ
def db():
    turso_url, turso_token = _turso_credentials()
    if turso_url and turso_token:
        try:
            import libsql
        except ImportError as exc:
            raise RuntimeError(
                "Turso configured but libsql not installed â€” add libsql to requirements.txt"
            ) from exc
        conn = libsql.connect(database=turso_url, auth_token=turso_token)
    else:
        conn = sqlite3.connect(DB, timeout=30.0, check_same_thread=False)
        conn.execute("PRAGMA journal_mode=WAL")
        conn.execute("PRAGMA synchronous=NORMAL")
    conn.row_factory = sqlite3.Row
    return conn


def _storage_status() -> dict:
    """ط­ط§ظ„ط© ط§ظ„طھط®ط²ظٹظ† â€” ظ„ظ„طھط´ط®ظٹطµ ظ…ظ† GET /"""
    url, _ = _turso_credentials()
    info = {
        "mode": DB_MODE,
        "persistent": DB_MODE in ("turso", "local_persistent"),
        "db_path": DB if DB_MODE != "turso" else url,
        "warning": None,
    }
    if DB_MODE == "local_ephemeral":
        info["warning"] = (
            "ط§ظ„ط¨ظٹط§ظ†ط§طھ طھظڈظ…ط³ط­ ط¹ظ†ط¯ ط¥ط¹ط§ط¯ط© طھط´ط؛ظٹظ„ Render â€” ط£ط¶ظٹظپظٹ TURSO_DATABASE_URL "
            "ظˆ TURSO_AUTH_TOKEN ط£ظˆ DATA_DIR=/var/data ظ…ط¹ ظ‚ط±طµ ط¯ط§ط¦ظ…"
        )
    try:
        conn = db()
        cur = conn.cursor()
        for table, key in (
            ("child_devices", "child_devices"),
            ("children", "linked_children"),
            ("guardians", "guardians"),
            ("alerts", "alerts"),
        ):
            try:
                cur.execute(f"SELECT COUNT(*) AS c FROM {table}")
                info[key] = int(cur.fetchone()["c"])
            except sqlite3.OperationalError:
                info[key] = 0
        conn.close()
    except Exception as exc:
        info["error"] = str(exc)[:200]
    return info


def _log_db_startup():
    status = _storage_status()
    logger.info(
        "DB startup mode=%s persistent=%s path=%s child_devices=%s",
        status.get("mode"),
        status.get("persistent"),
        status.get("db_path"),
        status.get("child_devices", "?"),
    )
    if status.get("warning"):
        logger.warning(status["warning"])

def smtp_configured():
    """ظ‡ظ„ ط¨ظٹط§ظ†ط§طھ SMTP ظ…ط¶ط¨ظˆط·ط©طں ط¨ط¯ظˆظ†ظ‡ط§ ظ„ط§ ظٹظڈط±ط³ظ„ ط¨ط±ظٹط¯ ط­ظ‚ظٹظ‚ظٹ."""
    return bool(SMTP_USER and SMTP_PASS)


def email_configured():
    """SMTP ط£ظˆ Resend API â€” Render ط§ظ„ظ…ط¬ط§ظ†ظٹ ظٹط­طھط§ط¬ Resend."""
    return bool(RESEND_API_KEY) or smtp_configured()


def verification_payload(code, email_sent, success_message, dev_message):
    """ط§ط³طھط¬ط§ط¨ط© API: ط§ظ„ط±ظ…ط² ظٹظڈط¹ط§ط¯ ظپظٹ JSON ظپظ‚ط· ط¹ظ†ط¯ ظپط´ظ„ SMTP (ظˆط¶ط¹ طھط·ظˆظٹط±)."""
    global SMTP_LAST_ERROR
    if not email_sent and email_configured() and SMTP_LAST_ERROR:
        dev_message = f"ظپط´ظ„ ط¥ط±ط³ط§ظ„ ط§ظ„ط¨ط±ظٹط¯ â€” طھط­ظ‚ظ‚ظٹ ظ…ظ† App Password ط¹ظ„ظ‰ Render ({SMTP_LAST_ERROR})"
    payload = {
        "success": True,
        "status": "success",
        "message": success_message if email_sent else dev_message,
        "email_sent": email_sent,
    }
    if not email_sent:
        payload["verification_code"] = code
        payload["dev_fallback"] = True
        print("EMAIL DEV FALLBACK â€” code for", code[:2] + "****")
    return payload


def send_email_resend(to_email, subject, body):
    """ط¥ط±ط³ط§ظ„ ط¹ط¨ط± Resend HTTPS â€” ظٹط¹ظ…ظ„ ط¹ظ„ظ‰ Render ط§ظ„ظ…ط¬ط§ظ†ظٹ."""
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


# ط¯ط§ظ„ط© ط¥ط±ط³ط§ظ„ ط§ظ„ط¨ط±ظٹط¯ â€” Resend (Render ظ…ط¬ط§ظ†ظٹ) ط£ظˆ SMTP (ط³ظٹط±ظپط± ظ…ط¯ظپظˆط¹)
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
# ط¥ظ†ط´ط§ط، ط§ظ„ط¬ط¯ط§ظˆظ„ ط¥ط°ط§ ظ„ظ… طھظƒظ† ظ…ظˆط¬ظˆط¯ط©
def init_db():
    conn = db()
    cur = conn.cursor()

    # ط¬ط¯ظˆظ„ ط±ظ…ظˆط² طھط­ظ‚ظ‚ ط§ظ„ط¨ط±ظٹط¯
    cur.execute("""
    CREATE TABLE IF NOT EXISTS email_codes (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        email TEXT,
        code TEXT,
        verified INTEGER DEFAULT 0,
        created_at TEXT
    )
    """)

    # ط¬ط¯ظˆظ„ ط£ط¬ظ‡ط²ط© ط§ظ„ط£ط·ظپط§ظ„ ظ‚ط¨ظ„ ط§ظ„ط±ط¨ط·
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

    # ط¬ط¯ظˆظ„ ط£ظˆظ„ظٹط§ط، ط§ظ„ط£ظ…ظˆط± (parent_id)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS guardians (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        email TEXT UNIQUE NOT NULL,
        created_at TEXT
    )
    """)

    # FIX: normalize child_code to support codes with or without CHILD- prefix
    _migrate_child_codes_in_db(cur)

    # ط¬ط¯ظˆظ„ ط§ظ„ط£ط·ظپط§ظ„ ط§ظ„ظ…ط±طھط¨ط·ظٹظ† ط¨ظˆظ„ظٹ ط§ظ„ط£ظ…ط±
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

    _ensure_children_columns(cur)

    # ط¬ط¯ظˆظ„ ط£ظˆط§ظ…ط± ط§ظ„طھط­ظƒظ…
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

    # ط¬ط¯ظˆظ„ ط§ظ„طھظ‚ط§ط±ظٹط±
    cur.execute("""
    CREATE TABLE IF NOT EXISTS reports (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        event TEXT,
        value TEXT,
        child_code TEXT,
        time TEXT
    )
    """)

    # ط¬ط¯ظˆظ„ ط§ظ„طھظ†ط¨ظٹظ‡ط§طھ
    cur.execute("""
    CREATE TABLE IF NOT EXISTS alerts (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        message TEXT,
        child_code TEXT,
        time TEXT
    )
    """)

    # ط³ظٹط§ط³ط© ط§ظ„ط­ط¸ط± ظ„ظƒظ„ ط¬ظ‡ط§ط² (طھط·ط¨ظٹظ‚ MYRana ط§ظ„ط£ظ†ط¯ط±ظˆظٹط¯ + ظ…ط²ط§ظ…ظ†ط© ط§ظ„ظ‚ظˆط§ط¦ظ…)
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

    # ط§ط³طھط®ط¯ط§ظ… ط§ظ„طھط·ط¨ظٹظ‚ط§طھ (طھط¬ظ…ظٹط¹ ظٹظˆظ…ظٹ â†’ طھظ‚ط±ظٹط± ط£ط³ط¨ظˆط¹ظٹ)
    cur.execute("""
    CREATE TABLE IF NOT EXISTS usage_daily (
        child_code TEXT NOT NULL,
        day TEXT NOT NULL,
        package_name TEXT NOT NULL,
        total_seconds INTEGER NOT NULL DEFAULT 0,
        PRIMARY KEY (child_code, day, package_name)
    )
    """)

    # ط¬ط¯ظˆظ„ ط§ظ„طھط­ظƒظ… ط§ظ„ط²ظ…ظ†ظٹ
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

    # ط³ظٹط§ط³ط© ظˆظ‚طھ ط§ظ„ط´ط§ط´ط© ظ„ظƒظ„ ط·ظپظ„
    cur.execute("""
    CREATE TABLE IF NOT EXISTS screen_time_policies (
        child_code TEXT PRIMARY KEY,
        policy_json TEXT NOT NULL,
        updated_at TEXT
    )
    """)

    # ط¢ط®ط± ط§طھطµط§ظ„ ظ„ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„
    cur.execute("""
    CREATE TABLE IF NOT EXISTS child_status (
        child_code TEXT PRIMARY KEY,
        last_seen_ms INTEGER DEFAULT 0,
        device_name TEXT
    )
    """)

    # ط£ط­ط¯ط§ط« ظˆظ‚طھ ط§ظ„ط´ط§ط´ط© (طھط­ط°ظٹط±ط§طھ / ط¥ط؛ظ„ط§ظ‚)
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

    cur.execute("""
    CREATE TABLE IF NOT EXISTS audit_log (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        guardian_email TEXT,
        child_code TEXT,
        action TEXT,
        detail TEXT,
        created_at TEXT
    )
    """)

    cur.execute("""
    CREATE TABLE IF NOT EXISTS guardian_settings (
        guardian_email TEXT PRIMARY KEY,
        settings_json TEXT NOT NULL,
        updated_at TEXT
    )
    """)

    cur.execute("""
    CREATE TABLE IF NOT EXISTS email_summary_sent (
        guardian_email TEXT NOT NULL,
        child_code TEXT NOT NULL,
        period TEXT NOT NULL,
        sent_key TEXT NOT NULL,
        sent_at TEXT,
        PRIMARY KEY (guardian_email, child_code, period, sent_key)
    )
    """)

    cur.execute("""
    CREATE TABLE IF NOT EXISTS child_app_meta (
        child_code TEXT NOT NULL,
        package_name TEXT NOT NULL,
        app_label TEXT,
        icon_b64 TEXT,
        updated_at TEXT,
        PRIMARY KEY (child_code, package_name)
    )
    """)

    for col, typedef in (
        ("permissions_json", "TEXT"),
        ("permissions_ok", "INTEGER DEFAULT 0"),
    ):
        try:
            cur.execute(f"ALTER TABLE child_status ADD COLUMN {col} {typedef}")
        except sqlite3.OperationalError:
            pass

    conn.commit()
    conn.close()
    _run_startup_cleanup()


def _norm_host(host: str) -> str:
    return (host or "").strip().lower()


def _extract_url_host(raw: str) -> str:
    """ط§ط³طھط®ط±ط§ط¬ ط§ظ„ظ†ط·ط§ظ‚ ظ…ظ† ط±ط§ط¨ط· ط£ظˆ ط§ط³ظ… ظ…ظˆظ‚ط¹ â€” ظ„ظ€ /api/check-url."""
    from urllib.parse import urlparse

    s = (raw or "").strip().lower()
    if not s:
        return ""
    # ط¥ط²ط§ظ„ط© ظ…ط³ط§ظپط§طھ ظˆظ…ط®ط·ط· ظ†ط§ظ‚طµ
    s = s.split()[0]
    if "://" not in s and not s.startswith("//"):
        s = "http://" + s
    try:
        host = (urlparse(s).hostname or "").strip().lower()
    except Exception:
        host = ""
    if not host:
        host = _norm_host(raw.split("/")[0].split("?")[0].split("#")[0])
    if host.startswith("www."):
        host = host[4:]
    return host


def _host_matches_pattern(host: str, pattern: str) -> bool:
    """ظ…ط·ط§ط¨ظ‚ط© ظ†ط·ط§ظ‚ ظ…ط¹ ظ†ظ…ط· ط­ط¸ط± (ظ…ط³ط§ظˆظٹط© / ظ†ط·ط§ظ‚ ظپط±ط¹ظٹ / ط§ط­طھظˆط§ط، ظƒظ†طµ Accessibility)."""
    h = (host or "").strip().lower().lstrip(".")
    p = (pattern or "").strip().lower().lstrip(".")
    if not h or not p:
        return False
    if h == p or h.endswith("." + p):
        return True
    # ظ†ظپط³ ط£ط³ظ„ظˆط¨ PolicyFilterCache.matchBlockedHost ط¹ظ„ظ‰ ظ†طµ ط§ظ„ط±ط§ط¨ط·
    return p in h


def _find_matching_host(host: str, patterns: list) -> str | None:
    for item in patterns or []:
        p = _norm_host(str(item))
        if p and _host_matches_pattern(host, p):
            return p
    return None


def _norm_pkg(package: str) -> str:
    from blocklists.package_resolver import resolve_app_package

    return resolve_app_package(package)


def _ensure_children_columns(cur) -> None:
    """طھط±ظ‚ظٹط© ط¬ط¯ظˆظ„ children ط§ظ„ظ‚ط¯ظٹظ… â€” ظٹظ…ظ†ط¹ 500 ط¹ظ†ط¯ ط§ظ„ط±ط¨ط· (child_email / linked_at)."""
    cur.execute("PRAGMA table_info(children)")
    cols = {row[1] for row in cur.fetchall()}
    if not cols:
        return
    if "child_email" not in cols:
        cur.execute("ALTER TABLE children ADD COLUMN child_email TEXT")
    if "linked_at" not in cols:
        cur.execute("ALTER TABLE children ADD COLUMN linked_at TEXT")


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
    """طھط­ظ…ظٹظ„ catalog.json â€” ط¨ط¬ط§ظ†ط¨ server.py (Render/RA) ط£ظˆ ظ…ظ† ط¬ط°ط± ط§ظ„ظ…ط´ط±ظˆط¹."""
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
    """ط¯ظ…ط¬ catalog.json ظپظٹ ط³ظٹط§ط³ط© ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„ (child_code = device_id)."""
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


def policy_remove_package(conn, device_id: str, package: str) -> None:
    package = _norm_pkg(package)
    if not package:
        return
    _, hosts, packages, keywords = _policy_get(conn, device_id)
    packages = [p for p in packages if _norm_pkg(p) != package]
    _policy_save(conn, device_id, hosts, packages, video_keywords=keywords)


def policy_remove_host(conn, device_id: str, host: str) -> None:
    host = _norm_host(host)
    if not host:
        return
    _, hosts, packages, keywords = _policy_get(conn, device_id)
    hosts = [h for h in hosts if _norm_host(h) != host]
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


# ط­ظ…ط§ظٹط© ظƒظ„ ط§ظ„ط±ظˆط§ط¨ط· ط¨ط§ط³طھط®ط¯ط§ظ… API_KEY
@app.before_request
def protect():
    # ط§ظ„طµظپط­ط© ط§ظ„ط±ط¦ظٹط³ظٹط© ظ„ط§ طھط­طھط§ط¬ ط­ظ…ط§ظٹط©
    if request.path == "/":
        return

    # ط§ظ„طھط­ظ‚ظ‚ ظ…ظ† ظ…ظپطھط§ط­ ط§ظ„ط­ظ…ط§ظٹط©
    if request.headers.get("X-API-KEY") != API_KEY:
        return jsonify({"status": "error", "message": "Unauthorized"}), 401


# ط§ط®طھط¨ط§ط± ط£ظ† ط§ظ„ط³ظٹط±ظپط± ظٹط¹ظ…ظ„
@app.route("/")
def home():
    return jsonify({
        "status": "running",
        "deploy_version": "2026-07-27-check-url",
        "message": "Parental Control Server is running",
        "storage": _storage_status(),
        "smtp_ready": email_configured(),
        "email_via": "resend" if RESEND_API_KEY else ("smtp" if smtp_configured() else "none"),
        "smtp_user_set": bool(SMTP_USER),
        "smtp_pass_set": bool(SMTP_PASS),
        "smtp_last_error": SMTP_LAST_ERROR or None,
        "smtp_host": SMTP_HOST,
        "smtp_port": SMTP_PORT,
    })


# ==============================
# ط³ظٹط§ط³ط© ط§ظ„ط­ط¸ط± â€” ط¹ظ‚ط¯ MYRana (ط£ظ†ط¯ط±ظˆظٹط¯)
# device_id = child_code ظ…ظ† طھط·ط¨ظٹظ‚ ط§ظ„ط£ظ…/ط§ظ„ط·ظپظ„
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


@app.route("/api/check-url", methods=["POST"])
def api_check_url():
    """طھط­ظ‚ظ‚ ظˆظ„ظٹ ط§ظ„ط£ظ…ط± ظ…ظ† ظ…ظˆظ‚ط¹: ط³ظٹط§ط³ط© ط§ظ„ط·ظپظ„ + ظƒطھط§ظ„ظˆط¬ ط§ظ„ط­ط¸ط± ط§ظ„ط§ظپطھط±ط§ط¶ظٹ."""
    data = request.get_json(silent=True) or {}
    raw_url = str(
        data.get("url") or data.get("host") or data.get("domain") or ""
    ).strip()
    host = _extract_url_host(raw_url)
    if not host:
        return _json_error(
            "ط£ط¯ط®ظ„ظٹ ط±ط§ط¨ط·ط§ظ‹ ط£ظˆ ظ†ط·ط§ظ‚ط§ظ‹ طµط§ظ„ط­ط§ظ‹",
            400,
            error_code="missing_url",
        )

    raw_child = str(data.get("child_code") or data.get("childCode") or "").strip()
    device_id = db_child_code(raw_child) if raw_child else ""
    policy_hosts: list = []
    policy_match = None
    if device_id:
        conn = db()
        _, policy_hosts, _, _ = _policy_get(conn, device_id)
        conn.close()
        policy_match = _find_matching_host(host, policy_hosts)

    cat = load_blocklist_catalog()
    catalog_sites = [_norm_host(s) for s in (cat.get("sites") or []) if _norm_host(s)]
    catalog_match = _find_matching_host(host, catalog_sites)

    in_policy = policy_match is not None
    in_catalog = catalog_match is not None
    blocked = in_policy  # ط§ظ„ط­ط¸ط± ط§ظ„ظپط¹ظ„ظٹ ط¹ظ„ظ‰ ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„ = ط³ظٹط§ط³ط© ط§ظ„ط·ظپظ„

    if blocked and in_catalog:
        explanation = (
            f"ط§ظ„ظ…ظˆظ‚ط¹ آ«{host}آ» ظ…ط­ط¸ظˆط± ظپظٹ ط³ظٹط§ط³ط© ط§ظ„ط·ظپظ„ "
            f"(ظ…ط·ط§ط¨ظ‚ط©: {policy_match}) ظˆظ…ظˆط¬ظˆط¯ ط£ظٹط¶ط§ظ‹ ظپظٹ ظƒطھط§ظ„ظˆط¬ ط§ظ„ط­ط¸ط± ط§ظ„ط§ظپطھط±ط§ط¶ظٹ."
        )
    elif blocked:
        explanation = (
            f"ط§ظ„ظ…ظˆظ‚ط¹ آ«{host}آ» ظ…ط­ط¸ظˆط± ظپظٹ ط³ظٹط§ط³ط© ط§ظ„ط·ظپظ„ ط§ظ„ط­ط§ظ„ظٹط© "
            f"(ظ…ط·ط§ط¨ظ‚ط©: {policy_match})."
        )
    elif in_catalog:
        explanation = (
            f"ط§ظ„ظ…ظˆظ‚ط¹ آ«{host}آ» ظ…ظˆط¬ظˆط¯ ظپظٹ ظƒطھط§ظ„ظˆط¬ ط§ظ„ط­ط¸ط± ط§ظ„ط§ظپطھط±ط§ط¶ظٹ "
            f"(ظ…ط·ط§ط¨ظ‚ط©: {catalog_match}) ظ„ظƒظ†ظ‡ ط؛ظٹط± ظ…ط¶ط§ظپ ظ„ط³ظٹط§ط³ط© ظ‡ط°ط§ ط§ظ„ط·ظپظ„ ط¨ط¹ط¯. "
            "ظٹظ…ظƒظ†ظƒظگ طھط·ط¨ظٹظ‚ ط§ظ„ظ‚ط§ط¦ظ…ط© ط§ظ„ط§ظپطھط±ط§ط¶ظٹط© ط£ظˆ ط­ط¸ط±ظ‡ ظٹط¯ظˆظٹط§ظ‹ ظ…ظ† ط´ط§ط´ط© ط§ظ„ط­ط¸ط±."
        )
    elif not device_id:
        explanation = (
            f"ط§ظ„ظ…ظˆظ‚ط¹ آ«{host}آ» ط؛ظٹط± ظ…ظˆط¬ظˆط¯ ظپظٹ ظƒطھط§ظ„ظˆط¬ ط§ظ„ط­ط¸ط±. "
            "ظ„ظ… ظٹظڈط­ط¯ط¯ ط·ظپظ„ ظ„ظ„طھط­ظ‚ظ‚ ظ…ظ† ط³ظٹط§ط³طھظ‡."
        )
    else:
        explanation = (
            f"ط§ظ„ظ…ظˆظ‚ط¹ آ«{host}آ» ط؛ظٹط± ظ…ط­ط¸ظˆط± ظپظٹ ط³ظٹط§ط³ط© ط§ظ„ط·ظپظ„ "
            "ظˆط؛ظٹط± ظ…ظˆط¬ظˆط¯ ظپظٹ ظƒطھط§ظ„ظˆط¬ ط§ظ„ط­ط¸ط± ط§ظ„ط§ظپطھط±ط§ط¶ظٹ."
        )

    return jsonify({
        "status": "success",
        "success": True,
        "host": host,
        "url": raw_url,
        "blocked": blocked,
        "in_policy": in_policy,
        "in_catalog": in_catalog,
        "policy_match": policy_match,
        "catalog_match": catalog_match,
        "child_code": device_id or None,
        "explanation": explanation,
        "message": explanation,
    })


# ط¥ط±ط³ط§ظ„ ط±ظ…ط² طھط­ظ‚ظ‚ ظ„ط¨ط±ظٹط¯ ظˆظ„ظٹ ط§ظ„ط£ظ…ط±
@app.route("/send-email-code", methods=["POST"])
def send_email_code():
    try:
        data = request.get_json(silent=True) or {}
        email = _extract_parent_email(data)

        if not email:
            return _json_error("parent_email is required", 400, error_code="missing_parent_email")

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
            "MYRana â€” ط±ظ…ط² ط§ظ„طھط­ظ‚ظ‚ ظ…ظ† ط§ظ„ط¨ط±ظٹط¯",
            f"ط±ظ…ط² ط§ظ„طھط­ظ‚ظ‚ ظ„ط¨ط±ظٹط¯ظƒ ({email}):\n\n{code}\n\n"
            f"ط£ط¯ط®ظ„ظٹظ‡ ظپظٹ طھط·ط¨ظٹظ‚ ط§ظ„ط£ظ… ظ„طھط£ظƒظٹط¯ ط£ظ† ط§ظ„ط¨ط±ظٹط¯ ظ…ظ„ظƒظƒ.",
        )

        payload = verification_payload(
            code,
            email_sent,
            f"طھظ… ط¥ط±ط³ط§ظ„ ط±ظ…ط² ط§ظ„طھط­ظ‚ظ‚ ط¥ظ„ظ‰ {email}",
            "ظ„ظ… ظٹظڈط±ط³ظ„ ط§ظ„ط¨ط±ظٹط¯ â€” ط§ظ„ط±ظ…ط² ظ„ظ„طھط·ظˆظٹط± ظپظ‚ط·",
        )
        if not email_sent:
            payload["email_verify_code"] = code
        return jsonify(payload)
    except Exception as exc:
        logger.exception("send-email-code failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط¥ط±ط³ط§ظ„ ط±ظ…ط² ط§ظ„ط¨ط±ظٹط¯", 500, error_code="server_error")


# ط§ظ„طھط­ظ‚ظ‚ ظ…ظ† ط±ظ…ط² ط§ظ„ط¨ط±ظٹط¯
@app.route("/verify-email-code", methods=["POST"])
def verify_email_code():
    try:
        data = request.get_json(silent=True) or {}
        email = _extract_parent_email(data)
        code = _extract_verification_code(data)

        if not email or not code:
            return _json_error("email ظˆ verification_code ظ…ط·ظ„ظˆط¨ط§ظ†", 400)

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
            return _json_error("ظƒظˆط¯ ط§ظ„طھط­ظ‚ظ‚ ط؛ظٹط± طµط­ظٹط­", 400, error_code="invalid_code")

        if _otp_expired(row["created_at"], OTP_EMAIL_EXPIRY_MINUTES):
            conn.close()
            logger.info("[verify-email] expired email=%s", email)
            return _json_error("ظƒظˆط¯ ط§ظ„طھط­ظ‚ظ‚ ظ…ظ†طھظ‡ظٹ ط§ظ„طµظ„ط§ط­ظٹط© â€” ط£ط±ط³ظ„ظٹ ط±ظ…ط²ط§ظ‹ ط¬ط¯ظٹط¯ط§ظ‹", 400, error_code="expired_code")

        cur.execute("UPDATE email_codes SET verified = 1 WHERE id = ?", (row["id"],))
        conn.commit()
        conn.close()
        return _json_success("Email verified successfully")
    except Exception as exc:
        logger.exception("verify-email-code failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط§ظ„طھط­ظ‚ظ‚ ظ…ظ† ط§ظ„ط¨ط±ظٹط¯", 500, error_code="server_error")


# طھط³ط¬ظٹظ„ ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„ â€” ط¨ط¯ظˆظ† ط¨ط±ظٹط¯ (ط§ظ„طھط­ظ‚ظ‚ ظ…ط±ط© ظˆط§ط­ط¯ط© ط¹ظ†ط¯ ط§ظ„ط±ط¨ط· ظ…ظ† طھط·ط¨ظٹظ‚ ط§ظ„ط£ظ…)
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
            return _json_error("child_code ظˆ device_name ظ…ط·ظ„ظˆط¨ط§ظ†", 400)

        conn = db()
        cur = conn.cursor()
        existing = find_child_device(cur, raw_child)

        if existing and existing["linked"]:
            conn.close()
            return _json_error("ط§ظ„ط¬ظ‡ط§ط² ظ…ط±ط¨ظˆط· ظ…ط³ط¨ظ‚ط§ظ‹", 400, error_code="already_linked")

        if existing:
            # ظ„ط§ ظ†ط؛ظٹظ‘ط± device_verify_code ط¹ظ†ط¯ ط¥ط¹ط§ط¯ط© ط§ظ„طھط³ط¬ظٹظ„ â€” ط­طھظ‰ ظٹط¨ظ‚ظ‰ ط±ظ…ط² ط§ظ„ط±ط¨ط· طµط§ظ„ط­ط§ظ‹
            cur.execute(
                """
                UPDATE child_devices
                SET child_email = ?, device_name = ?, android_version = ?
                WHERE child_code = ?
                """,
                (child_email, device_name, android_version, existing["child_code"]),
            )
            stored = existing["child_code"]
            verify_out = str(existing["device_verify_code"] or "").strip() or None
        else:
            client_verify = str(
                data.get("device_verify_code") or data.get("deviceVerifyCode") or ""
            ).strip()
            if client_verify.isdigit() and len(client_verify) == 6:
                device_code = client_verify
            else:
                device_code = str(random.randint(100000, 999999))
            verify_out = device_code
            # FIX: normalize child_code â€” ظٹظڈط®ط²ظ‘ظژظ† ط¨ط¯ظˆظ† ط¨ط§ط¯ط¦ط©: 1DF71288
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
        return _json_success(
            "Child device registered â€” waiting for parent link",
            child_code=normalize_child_code(stored),
            child_code_clean=clean_child_code(stored),
            device_verify_code=verify_out,
        )
    except Exception as exc:
        logger.exception("register-child-device failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، طھط³ط¬ظٹظ„ ط§ظ„ط·ظپظ„", 500, error_code="server_error")


# ط¥ط±ط³ط§ظ„ ط±ظ…ط² ط§ظ„ط±ط¨ط· ظ„ط¨ط±ظٹط¯ ظˆظ„ظٹ ط§ظ„ط£ظ…ط± â€” ظ…ط±ط© ظˆط§ط­ط¯ط© ط£ط«ظ†ط§ط، ط§ظ„ط±ط¨ط·
@app.route("/send-link-code", methods=["POST"])
def send_link_code():
    try:
        data = request.get_json(silent=True) or {}
        parent_email = _extract_parent_email(data)
        child_code = _extract_child_code(data)

        if not parent_email or not child_code:
            return _json_error("parent_email ظˆ child_code ظ…ط·ظ„ظˆط¨ط§ظ†", 400)

        conn = db()
        cur = conn.cursor()
        if not _guardian_verified(cur, parent_email):
            conn.close()
            return _json_error(
                "ظٹط¬ط¨ ط§ظ„طھط­ظ‚ظ‚ ظ…ظ† ط¨ط±ظٹط¯ ظˆظ„ظٹ ط§ظ„ط£ظ…ط± ط£ظˆظ„ط§ظ‹ (ط±ظ…ط² ط§ظ„طھط­ظ‚ظ‚)",
                400,
                error_code="parent_email_not_verified",
            )

        raw_child = data.get("child_code") or data.get("childCode") or child_code
        row = find_child_device(cur, raw_child, log_on_miss=False)
        if not row:
            conn.close()
            return _child_not_found_response(
                raw_child,
                "ظ„ظ… ظٹظڈط¹ط«ط± ط¹ظ„ظ‰ ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„ â€” ط³ط¬ظ‘ظ„ظٹ ظ…ظ† ط¬ظˆط§ظ„ ط§ظ„ط·ظپظ„ ط£ظˆظ„ط§ظ‹ (طھط³ط¬ظٹظ„ ط§ظ„ط¬ظ‡ط§ط²)",
            )

        child_code = row["child_code"]
        if row["linked"]:
            conn.close()
            return _json_error("ط§ظ„ط¬ظ‡ط§ط² ظ…ط±ط¨ظˆط· ظ…ط³ط¨ظ‚ط§ظ‹", 400, error_code="already_linked")

        force_resend = bool(data.get("force_resend"))
        existing_code = str(row["device_verify_code"] or "").strip()
        device_created = row["created_at"] if "created_at" in row.keys() else None
        otp_still_valid = (
            existing_code
            and not force_resend
            and (
                DEVICE_OTP_EXPIRY_MINUTES <= 0
                or not _otp_expired(device_created, DEVICE_OTP_EXPIRY_MINUTES)
            )
        )
        if otp_still_valid:
            device_code = existing_code
            logger.info(
                "[send-link-code] reusing existing OTP child_code=%s force_resend=%s",
                child_code,
                force_resend,
            )
        else:
            device_code = str(random.randint(100000, 999999))
            cur.execute(
                """
                UPDATE child_devices
                SET device_verify_code = ?, device_verified = 0, created_at = ?
                WHERE child_code = ?
                """,
                (device_code, now(), child_code),
            )
            conn.commit()
        conn.close()

        _log_link_context("send-link-code", parent_email, child_code, device_code, device_code, "sent")

        email_sent = send_email(
            parent_email,
            "MYRana â€” ط±ظ…ط² ط±ط¨ط· ط§ظ„ط·ظپظ„",
            f"ط±ظ…ط² ط±ط¨ط· ط§ظ„ط·ظپظ„ ({normalize_child_code(child_code)}):\n\n{device_code}\n\n"
            f"ط£ط¯ط®ظ„ظٹظ‡ ظپظٹ طھط·ط¨ظٹظ‚ ط§ظ„ط£ظ… ظ„ط¥طھظ…ط§ظ… ط§ظ„ط±ط¨ط·.\n"
            f"(ظ‡ط°ط§ ظ„ظٹط³ ط±ظ…ط² طھط­ظ‚ظ‚ ط§ظ„ط¨ط±ظٹط¯ ط§ظ„ط£ظˆظ„)",
        )

        payload = verification_payload(
            device_code,
            email_sent,
            "طھظ… ط¥ط±ط³ط§ظ„ ط±ظ…ط² ط§ظ„ط±ط¨ط· ط¥ظ„ظ‰ ط¨ط±ظٹط¯ظƒ",
            "SMTP ط؛ظٹط± ظ…ط¶ط¨ظˆط· â€” ط§ظ„ط±ظ…ط² ظ„ظ„طھط·ظˆظٹط± ظپظ‚ط·",
        )
        if not email_sent:
            payload["link_code"] = device_code
        return jsonify(payload)
    except Exception as exc:
        logger.exception("send-link-code failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط¥ط±ط³ط§ظ„ ط±ظ…ط² ط§ظ„ط±ط¨ط·", 500, error_code="server_error")


# ظ‡ظ„ ط§ظƒطھظ…ظ„ ط±ط¨ط· ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„طں (ظٹط³طھط¹ظ„ظ… ط¹ظ†ظ‡ طھط·ط¨ظٹظ‚ ط§ظ„ط·ظپظ„)
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
            "ظ„ظ… ظٹظڈط¹ط«ط± ط¹ظ„ظ‰ ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„ â€” ط§ظپطھط­ظٹ طھط·ط¨ظٹظ‚ ط§ظ„ط·ظپظ„ ظˆط§ط¶ط؛ط·ظٹ طھط³ط¬ظٹظ„ ط§ظ„ط¬ظ‡ط§ط²",
        )

    return _json_success(
        "Child link status",
        child_code=normalize_child_code(row["child_code"]),
        child_code_clean=clean_child_code(row["child_code"]),
        linked=bool(row["linked"]),
    )


# ط§ظ„طھط­ظ‚ظ‚ ظ…ظ† ط±ظ…ط² ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„ â€” ط§ط®طھظٹط§ط±ظٹ ظ‚ط¨ظ„ ط§ظ„ط±ط¨ط· ط§ظ„ظ†ظ‡ط§ط¦ظٹ
@app.route("/verify-child-device-code", methods=["POST"])
def verify_child_device_code():
    try:
        data = request.get_json(silent=True) or {}
        child_code = _extract_child_code(data)
        code = _extract_verification_code(data)
        parent_email = _extract_parent_email(data)

        if not child_code or not code:
            return _json_error("child_code ظˆ verification_code ظ…ط·ظ„ظˆط¨ط§ظ†", 400)

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
                "ظ…ظ† ط¬ظˆط§ظ„ ط§ظ„ط·ظپظ„ ط§ط¶ط؛ط·ظٹ آ«طھط³ط¬ظٹظ„ ط§ظ„ط¬ظ‡ط§ط²آ» ط«ظ… ط£ط¹ظٹط¯ظٹ ط§ظ„ط±ط¨ط·",
            )

        if not stored or stored != code:
            conn.close()
            return _json_error(
                "ظƒظˆط¯ ط§ظ„طھط­ظ‚ظ‚ ط؛ظٹط± طµط­ظٹط­ â€” ط§ط³طھط®ط¯ظ…ظٹ ط±ظ…ط² ط§ظ„ط±ط¨ط· ظ…ظ† Gmail (ط§ظ„ط±ط³ط§ظ„ط© ط§ظ„ط«ط§ظ†ظٹط©)",
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
            "message": "طھظ… ط§ظ„طھط­ظ‚ظ‚ ظ…ظ† ط±ظ…ط² ط§ظ„ط±ط¨ط·",
            "child_code": device_row["child_code"],
            "child_email": device_row["child_email"],
            "device_name": device_row["device_name"],
            "android_version": device_row["android_version"],
        })
    except Exception as exc:
        logger.exception("verify-child-device-code failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط§ظ„طھط­ظ‚ظ‚ ظ…ظ† ط±ظ…ط² ط§ظ„ط±ط¨ط·", 500, error_code="server_error")


# ط¥ط¶ط§ظپط© ظˆط±ط¨ط· ط§ظ„ط·ظپظ„ â€” ط§ظ„ط§ط³ظ… ط§ط®طھظٹط§ط±ظٹط› ط§ظ„ط±ط¨ط· ط¨ظ€ parent_email + child_code + verification_code
@app.route("/add-child", methods=["POST"])
@app.route("/link-child", methods=["POST"])
def add_child():
    conn = None
    try:
        data = request.get_json(silent=True) or {}
        raw_cc = str(data.get("child_code") or data.get("childCode") or "").strip()
        logger.info(
            "[link-child] step=receive endpoint=%s parent_email=%s child_code_raw=%r cleaned=%r",
            request.path,
            _extract_parent_email(data) or "(empty)",
            raw_cc,
            clean_child_code(raw_cc),
        )
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
            "ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط±ط¨ط· ط§ظ„ط·ظپظ„ â€” ط±ط§ط¬ط¹ظٹ ط³ط¬ظ„ط§طھ ط§ظ„ط³ظٹط±ظپط±",
            500,
            error_code="server_error",
        )
    finally:
        if conn:
            try:
                conn.close()
            except Exception:
                pass


# ط§ط³طھط¹ط§ط¯ط© ط§ظ„ط±ط¨ط· ط¨ط¹ط¯ ط¥ط¹ط§ط¯ط© طھط´ط؛ظٹظ„ Render â€” ط¨ط¯ظˆظ† Turso (ط±ظ…ط² ظ…ظ† ط£ظˆظ„ ط±ط¨ط· ظ†ط§ط¬ط­)
@app.route("/restore-link", methods=["POST"])
def restore_link():
    conn = None
    try:
        data = request.get_json(silent=True) or {}
        conn = db()
        cur = conn.cursor()
        return _restore_link_transaction(cur, conn, data)
    except Exception as exc:
        if conn:
            try:
                conn.rollback()
            except Exception:
                pass
        logger.exception("restore-link failed: %s", exc)
        return _json_error("ط®ط·ط£ ط£ط«ظ†ط§ط، ط§ط³طھط¹ط§ط¯ط© ط§ظ„ط±ط¨ط·", 500, error_code="server_error")
    finally:
        if conn:
            try:
                conn.close()
            except Exception:
                pass


# ط¥ط±ط³ط§ظ„ ط£ظ…ط± ظ…ظ† طھط·ط¨ظٹظ‚ ط§ظ„ط£ظ… ط¥ظ„ظ‰ ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„
@app.route("/send-command", methods=["POST"])
def send_command():
    try:
        data = request.get_json(silent=True) or {}

        action = (data.get("action") or "").strip()
        value = (data.get("value") or "").strip()
        if action in ("block_app", "freeze_app") and value:
            value = _norm_pkg(value)
            data["value"] = value

        child_code = db_child_code(data.get("child_code", ""))
        if not child_code:
            return _json_error("child_code required", 400, error_code="missing_child_code")

        conn = db()
        cur = conn.cursor()

        cur.execute("""
        INSERT INTO commands (action, value, child_code, guardian_email, executed, time)
        VALUES (?, ?, ?, ?, ?, ?)
        """, (
            action,
            value,
            child_code,
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
            child_code,
            now()
        ))

        if action == "block_site" and value:
            policy_add_host(conn, child_code, value)
        elif action in ("block_app", "freeze_app") and value:
            policy_add_package(conn, child_code, value)
        elif action == "unblock_app" and value:
            policy_remove_package(conn, child_code, value)
        elif action == "unblock_site" and value:
            policy_remove_host(conn, child_code, value)
        elif action == "allow":
            policy_clear(conn, child_code)
        elif action == "apply_default_blocklist":
            apply_default_blocklist(conn, child_code, merge=True)

        _audit_log(
            cur,
            data.get("guardian_email", ""),
            child_code,
            f"command_{action}",
            value or action,
        )

        conn.commit()
        conn.close()

        return jsonify({"status": "success", "message": "Command sent"})
    except Exception as exc:
        logger.exception("send-command failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط¥ط±ط³ط§ظ„ ط§ظ„ط£ظ…ط±", 500, error_code="server_error")


# ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„ ظٹط³ط­ط¨ ط¢ط®ط± ط£ظ…ط± ط؛ظٹط± ظ…ظ†ظپط°
@app.route("/get-command", methods=["GET"])
def get_command():
    try:
        child_code = _child_code_from_request_args()
        if not child_code:
            return _json_error("child_code required", 400, error_code="missing_child_code")

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
            return jsonify({
                "action": "none",
                "value": "",
                "child_code": normalize_child_code(child_code),
                "child_code_clean": child_code,
            })

        cur.execute("UPDATE commands SET executed = 1 WHERE id = ?", (cmd["id"],))
        conn.commit()
        conn.close()

        result = dict(cmd)
        result["child_code"] = normalize_child_code(child_code)
        result["child_code_clean"] = child_code
        return jsonify(result)
    except Exception as exc:
        logger.exception("get-command failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط¬ظ„ط¨ ط§ظ„ط£ظ…ط±", 500, error_code="server_error")


# ط¥ط¶ط§ظپط© ط¬ط¯ظˆظ„ طھط­ظƒظ… ط²ظ…ظ†ظٹ
@app.route("/add-schedule", methods=["POST"])
def add_schedule():
    try:
        data = request.get_json(silent=True) or {}
        child_code = db_child_code(data.get("child_code", ""))
        if not child_code:
            return _json_error("child_code required", 400, error_code="missing_child_code")

        conn = db()
        cur = conn.cursor()

        cur.execute("""
        INSERT INTO schedules (child_code, action, value, start_time, end_time, active, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """, (
            child_code,
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
            child_code,
            now()
        ))

        _audit_log(
            cur,
            data.get("guardian_email", ""),
            child_code,
            "schedule_added",
            f"{data.get('action', '')} {data.get('value', '')} {data.get('start_time', '')}-{data.get('end_time', '')}",
        )

        conn.commit()
        conn.close()

        return jsonify({"status": "success", "message": "Schedule added"})
    except Exception as exc:
        logger.exception("add-schedule failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط¥ط¶ط§ظپط© ط§ظ„ط¬ط¯ظˆظ„", 500, error_code="server_error")


# ط¬ط¯ط§ظˆظ„ ط²ظ…ظ†ظٹط© ظ†ط´ط·ط© ط§ظ„ط¢ظ† (ط­ط¸ط±/طھط¬ظ…ظٹط¯ ظ…ط¤ظ‚طھ)
@app.route("/active-schedules", methods=["GET"])
def active_schedules():
    child_code = _child_code_from_request_args()
    if not child_code:
        return _json_error("child_code required", 400, error_code="missing_child_code")
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


# ط±ظپط¹ ط§ط³طھط®ط¯ط§ظ… ط§ظ„طھط·ط¨ظٹظ‚ط§طھ ظ…ظ† ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„
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


# طھظ‚ط±ظٹط± ط§ط³طھط®ط¯ط§ظ… ط£ط³ط¨ظˆط¹ظٹ ظ„ظˆظ„ظٹ ط§ظ„ط£ظ…ط±
@app.route("/weekly-report", methods=["GET"])
def weekly_report():
    child_code = db_child_code(request.args.get("child_code", ""))
    if not child_code:
        return _json_error("child_code required", 400, error_code="missing_child_code")
    days = _usage_period_days(request.args.get("days"), default=7)
    since = (datetime.now() - timedelta(days=days - 1)).strftime("%Y-%m-%d")
    conn = db()
    cur = conn.cursor()
    cur.execute(
        """
        SELECT package_name, SUM(total_seconds) AS total_seconds
        FROM usage_daily
        WHERE child_code = ? AND day >= ?
        GROUP BY package_name
        ORDER BY total_seconds DESC
        LIMIT 100
        """,
        (child_code, since),
    )
    apps = _attach_avg_seconds_per_day([dict(r) for r in cur.fetchall()], days)
    apps = _enrich_app_rows(cur, child_code, apps)
    cur.execute(
        """
        SELECT COALESCE(SUM(total_seconds), 0) AS total
        FROM usage_daily
        WHERE child_code = ? AND day >= ?
        """,
        (child_code, since),
    )
    total_sec = int(cur.fetchone()["total"] or 0)
    conn.close()
    return jsonify({
        "child_code": child_code,
        "since": since,
        "days": days,
        "avg_daily_screen_seconds": total_sec // max(1, days),
        "apps": apps,
    })


# ط¥ط±ط³ط§ظ„ طھظ‚ط±ظٹط± ظ…ظ† ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„
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


# ط¹ط±ط¶ ط§ظ„طھظ‚ط§ط±ظٹط± ظ„ظ„ط£ظ…
@app.route("/reports", methods=["GET"])
def reports():
    child_code = _child_code_from_request_args()
    if not child_code:
        return _json_error("child_code required", 400, error_code="missing_child_code")

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


# ط¥ط¶ط§ظپط© طھظ†ط¨ظٹظ‡ ظ…ظ† ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„
@app.route("/add-alert", methods=["POST"])
def add_alert():
    try:
        data = request.get_json(silent=True) or {}
        child_code = db_child_code(data.get("child_code", ""))
        message = (data.get("message") or "").strip()
        if not child_code or not message:
            return _json_error("child_code and message required", 400, error_code="missing_child_code")

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

        return jsonify({"success": True, "status": "success"})
    except Exception as exc:
        logger.exception("add-alert failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط¥ط¶ط§ظپط© ط§ظ„طھظ†ط¨ظٹظ‡", 500, error_code="server_error")


# ط±ط³ط§ظ„ط© ظ…ظ† ظˆظ„ظٹ ط§ظ„ط£ظ…ط± â€” طھظڈط­ظپط¸ ظپظٹ ط§ظ„طھظ†ط¨ظٹظ‡ط§طھ ظ„ظٹط±ط§ظ‡ط§ ظپظٹ ظ„ظˆط­ط© ط§ظ„ط£ظ…
@app.route("/send-guardian-message", methods=["POST"])
def send_guardian_message():
    try:
        data = request.get_json(silent=True) or {}
        child_code = db_child_code(data.get("child_code", ""))
        message = (data.get("message") or "").strip()
        role = (data.get("guardian_role") or "ظˆظ„ظٹ ط§ظ„ط£ظ…ط±").strip()
        if not child_code or not message:
            return _json_error("child_code and message required", 400, error_code="missing_child_code")

        full_message = f"[{role}] {message}"
        conn = db()
        cur = conn.cursor()
        cur.execute(
            "INSERT INTO alerts (message, child_code, time) VALUES (?, ?, ?)",
            (full_message, child_code, now()),
        )
        cur.execute(
            """
            INSERT INTO commands (action, value, child_code, guardian_email, executed, time)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            ("guardian_message", full_message, child_code, "", 0, now()),
        )
        conn.commit()
        conn.close()
        return jsonify({"status": "success", "message": "طھظ… ط¥ط±ط³ط§ظ„ ط§ظ„ط±ط³ط§ظ„ط©"})
    except Exception as exc:
        logger.exception("send-guardian-message failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط¥ط±ط³ط§ظ„ ط§ظ„ط±ط³ط§ظ„ط©", 500, error_code="server_error")


# ط¹ط±ط¶ ط§ظ„طھظ†ط¨ظٹظ‡ط§طھ ظ„ظ„ط£ظ…
@app.route("/list-children", methods=["GET"])
def list_children():
    """ظ‚ط§ط¦ظ…ط© ط§ظ„ط£ط·ظپط§ظ„ ط§ظ„ظ…ط±طھط¨ط·ظٹظ† ط¨ظˆظ„ظٹ ط£ظ…ط± â€” ط¯ط¹ظ… طھط¹ط¯ط¯ ط§ظ„ط£ط·ظپط§ظ„."""
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
               c.linked_at, cs.last_seen_ms, cs.device_name AS status_device,
               cs.permissions_json, cs.permissions_ok
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
        perms = {}
        if r["permissions_json"]:
            try:
                perms = json.loads(r["permissions_json"] or "{}")
            except Exception:
                perms = {}
        rows.append({
            "child_id": int(r["child_id"]),
            "name": r["name"] or "ط·ظپظ„",
            "age": r["age"],
            "child_code": normalize_child_code(code_db),
            "child_code_clean": clean_child_code(code_db),
            "device": r["device"],
            "android_version": r["android_version"],
            "linked_at": r["linked_at"],
            "online": online,
            "last_seen_ms": last_ms,
            "device_name": r["status_device"] or r["device"],
            "permissions_ok": bool(r["permissions_ok"]),
            "permissions": perms,
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

DEFAULT_GUARDIAN_SETTINGS = {
    "retention_days": 30,
    "email_daily_enabled": False,
    "email_weekly_enabled": False,
    "alert_sound_enabled": True,
}


def _audit_log(cur, guardian_email: str, child_code: str, action: str, detail: str = ""):
    """ط³ط¬ظ„ طھط؛ظٹظٹط±ط§طھ ظˆظ„ظٹ ط§ظ„ط£ظ…ط±."""
    try:
        cur.execute(
            """
            INSERT INTO audit_log (guardian_email, child_code, action, detail, created_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            (
                (guardian_email or "").strip(),
                db_child_code(child_code),
                (action or "").strip(),
                (detail or "").strip()[:500],
                now(),
            ),
        )
        logger.info("[audit] %s %s %s %s", guardian_email, child_code, action, detail[:80])
    except Exception as exc:
        logger.warning("audit_log failed: %s", exc)


def _guardian_settings_get(conn, guardian_email: str) -> dict:
    cur = conn.cursor()
    cur.execute(
        "SELECT settings_json FROM guardian_settings WHERE guardian_email = ? LIMIT 1",
        (guardian_email.strip(),),
    )
    row = cur.fetchone()
    if not row:
        return dict(DEFAULT_GUARDIAN_SETTINGS)
    try:
        data = json.loads(row["settings_json"] or "{}")
        merged = dict(DEFAULT_GUARDIAN_SETTINGS)
        merged.update(data)
        return merged
    except Exception:
        return dict(DEFAULT_GUARDIAN_SETTINGS)


def _guardian_settings_save(conn, guardian_email: str, settings: dict) -> None:
    merged = dict(DEFAULT_GUARDIAN_SETTINGS)
    merged.update(settings or {})
    retention = int(merged.get("retention_days") or 30)
    merged["retention_days"] = max(7, min(90, retention))
    cur = conn.cursor()
    cur.execute(
        """
        INSERT OR REPLACE INTO guardian_settings (guardian_email, settings_json, updated_at)
        VALUES (?, ?, ?)
        """,
        (guardian_email.strip(), json.dumps(merged, ensure_ascii=False), now()),
    )


def _cleanup_old_data(conn, retention_days: int) -> dict:
    """ط­ط°ظپ ط¨ظٹط§ظ†ط§طھ ط£ظ‚ط¯ظ… ظ…ظ† retention_days."""
    days = max(7, min(90, int(retention_days or 30)))
    cutoff = (datetime.now() - timedelta(days=days)).strftime("%Y-%m-%d 00:00:00")
    cur = conn.cursor()
    counts = {}
    for table, col in (
        ("alerts", "time"),
        ("reports", "time"),
        ("screen_time_events", "time"),
        ("audit_log", "created_at"),
    ):
        cur.execute(f"DELETE FROM {table} WHERE {col} < ?", (cutoff,))
        counts[table] = cur.rowcount
    cutoff_day = (datetime.now() - timedelta(days=days)).strftime("%Y-%m-%d")
    cur.execute("DELETE FROM usage_daily WHERE day < ?", (cutoff_day,))
    counts["usage_daily"] = cur.rowcount
    cur.execute(
        "DELETE FROM email_codes WHERE verified = 0 AND created_at < ?",
        (cutoff,),
    )
    counts["email_codes"] = cur.rowcount
    logger.info("[cleanup] retention=%sd deleted=%s", days, counts)
    return {"retention_days": days, "deleted": counts}


def _run_startup_cleanup():
    """طھظ†ط¸ظٹظپ طھظ„ظ‚ط§ط¦ظٹ ط¹ظ†ط¯ طھط´ط؛ظٹظ„ ط§ظ„ط³ظٹط±ظپط± â€” ظ„ظƒظ„ ظˆظ„ظٹ ط£ظ…ط± ط­ط³ط¨ ط¥ط¹ط¯ط§ط¯ط§طھظ‡."""
    try:
        conn = db()
        cur = conn.cursor()
        cur.execute("SELECT guardian_email, settings_json FROM guardian_settings")
        rows = cur.fetchall()
        if not rows:
            conn.close()
            return
        for row in rows:
            try:
                settings = json.loads(row["settings_json"] or "{}")
            except Exception:
                settings = dict(DEFAULT_GUARDIAN_SETTINGS)
            _cleanup_old_data(conn, int(settings.get("retention_days") or 30))
        conn.commit()
        conn.close()
    except Exception as exc:
        logger.warning("startup cleanup skipped: %s", exc)


def _email_summary_sent_key(period: str) -> str:
    """ظ…ظپطھط§ط­ ظٹظˆظ…ظٹ ط£ظˆ ط£ط³ط¨ظˆط¹ظٹ ظ„ظ…ظ†ط¹ ط¥ط±ط³ط§ظ„ ظ…ظƒط±ط±."""
    if period == "weekly":
        return datetime.now().strftime("%Y-W%W")
    return datetime.now().strftime("%Y-%m-%d")


def _email_summary_already_sent(cur, guardian_email: str, child_code: str, period: str) -> bool:
    sent_key = _email_summary_sent_key(period)
    cur.execute(
        """
        SELECT 1 FROM email_summary_sent
        WHERE guardian_email = ? AND child_code = ? AND period = ? AND sent_key = ?
        LIMIT 1
        """,
        (guardian_email.strip(), db_child_code(child_code), period, sent_key),
    )
    return cur.fetchone() is not None


def _mark_email_summary_sent(cur, guardian_email: str, child_code: str, period: str) -> None:
    sent_key = _email_summary_sent_key(period)
    cur.execute(
        """
        INSERT OR REPLACE INTO email_summary_sent
        (guardian_email, child_code, period, sent_key, sent_at)
        VALUES (?, ?, ?, ?, ?)
        """,
        (
            guardian_email.strip(),
            db_child_code(child_code),
            period,
            sent_key,
            now(),
        ),
    )


def _send_guardian_summary_email(
    conn,
    guardian_email: str,
    child_code: str,
    period: str,
) -> bool:
    """ط¥ط±ط³ط§ظ„ ظ…ظ„ط®طµ ظٹظˆظ…ظٹ/ط£ط³ط¨ظˆط¹ظٹ ظ„ط·ظپظ„ ظˆط§ط­ط¯ â€” ظٹظڈط³طھط®ط¯ظ… ظ…ظ† ط§ظ„ط²ط± ظˆط§ظ„ظ€ cron."""
    days = 7 if period == "weekly" else 1
    code = db_child_code(child_code)
    body = _build_usage_summary(conn, code, days=days)
    subject = f"MYRana â€” ظ…ظ„ط®طµ {'ط§ظ„ط£ط³ط¨ظˆط¹' if days > 1 else 'ط§ظ„ظٹظˆظ…'}"
    sent = send_email(guardian_email.strip(), subject, body)
    cur = conn.cursor()
    _audit_log(
        cur,
        guardian_email,
        code,
        f"email_summary_{period}",
        "sent" if sent else "failed",
    )
    if sent:
        _mark_email_summary_sent(cur, guardian_email, code, period)
    return sent


def _run_scheduled_email_summaries() -> dict:
    """
    ط¥ط±ط³ط§ظ„ ط§ظ„ظ…ظ„ط®طµط§طھ ط§ظ„ظ…ط¬ط¯ظˆظ„ط© ط­ط³ط¨ ط¥ط¹ط¯ط§ط¯ط§طھ ظˆظ„ظٹ ط§ظ„ط£ظ…ط±.
    ظٹظڈط³طھط¯ط¹ظ‰ ظ…ظ† /cron/email-summaries ط¹ظ„ظ‰ Render ط£ظˆ ط®ظٹط· ط®ظ„ظپظٹ ظ…ط­ظ„ظٹط§ظ‹.
    """
    stats = {"daily_sent": 0, "weekly_sent": 0, "skipped": 0, "failed": 0}
    if not email_configured():
        stats["skipped"] = -1
        logger.info("[email-cron] SMTP ط؛ظٹط± ظ…ط¶ط¨ظˆط· â€” طھط®ط·ظٹ")
        return stats

    conn = db()
    cur = conn.cursor()
    cur.execute("SELECT guardian_email, settings_json FROM guardian_settings")
    guardian_rows = cur.fetchall()

    for grow in guardian_rows:
        guardian_email = (grow["guardian_email"] or "").strip()
        if not guardian_email:
            continue
        try:
            settings = json.loads(grow["settings_json"] or "{}")
        except Exception:
            settings = dict(DEFAULT_GUARDIAN_SETTINGS)

        daily_on = settings.get("email_daily_enabled") is True
        weekly_on = settings.get("email_weekly_enabled") is True
        if not daily_on and not weekly_on:
            continue

        cur.execute(
            "SELECT child_code FROM children WHERE guardian_email = ?",
            (guardian_email,),
        )
        children = [r["child_code"] for r in cur.fetchall() if r["child_code"]]

        for child_code in children:
            if daily_on and not _email_summary_already_sent(cur, guardian_email, child_code, "daily"):
                if _send_guardian_summary_email(conn, guardian_email, child_code, "daily"):
                    stats["daily_sent"] += 1
                else:
                    stats["failed"] += 1
            else:
                stats["skipped"] += 1

            if weekly_on and not _email_summary_already_sent(cur, guardian_email, child_code, "weekly"):
                if _send_guardian_summary_email(conn, guardian_email, child_code, "weekly"):
                    stats["weekly_sent"] += 1
                else:
                    stats["failed"] += 1
            else:
                stats["skipped"] += 1

    conn.commit()
    conn.close()
    logger.info("[email-cron] done stats=%s", stats)
    return stats


_email_cron_started = False


def _start_email_cron_thread():
    """ط®ظٹط· ط®ظ„ظپظٹ ظ„ظ„طھط·ظˆظٹط± ط§ظ„ظ…ط­ظ„ظٹ â€” ط¹ظ„ظ‰ Render ط§ط³طھط®ط¯ظ…ظٹ Cron Job ظٹط¶ط±ط¨ /cron/email-summaries."""
    global _email_cron_started
    if _email_cron_started:
        return
    if os.environ.get("EMAIL_CRON_ENABLED", "0") != "1":
        return
    _email_cron_started = True
    interval = max(300, int(os.environ.get("EMAIL_CRON_INTERVAL_SEC", "3600")))

    def _loop():
        while True:
            try:
                _run_scheduled_email_summaries()
            except Exception as exc:
                logger.warning("email cron loop error: %s", exc)
            threading.Event().wait(interval)

    t = threading.Thread(target=_loop, name="email-cron", daemon=True)
    t.start()
    logger.info("[email-cron] background thread every %ss", interval)


def _build_usage_summary(conn, child_code: str, days: int = 1) -> str:
    since = (datetime.now() - timedelta(days=days - 1)).strftime("%Y-%m-%d")
    cur = conn.cursor()
    cur.execute(
        """
        SELECT COALESCE(SUM(total_seconds), 0) AS total
        FROM usage_daily WHERE child_code = ? AND day >= ?
        """,
        (child_code, since),
    )
    total_sec = int(cur.fetchone()["total"] or 0)
    cur.execute(
        """
        SELECT COUNT(*) AS cnt FROM alerts
        WHERE child_code = ? AND time >= ?
        """,
        (child_code, f"{since} 00:00:00"),
    )
    alerts = int(cur.fetchone()["cnt"] or 0)
    cur.execute(
        """
        SELECT package_name, SUM(total_seconds) AS total_seconds
        FROM usage_daily WHERE child_code = ? AND day >= ?
        GROUP BY package_name ORDER BY total_seconds DESC LIMIT 5
        """,
        (child_code, since),
    )
    top = cur.fetchall()
    lines = [
        f"MYRana â€” ظ…ظ„ط®طµ {'ط§ظ„ظٹظˆظ…' if days <= 1 else f'{days} ط£ظٹط§ظ…'}",
        f"ظƒظˆط¯ ط§ظ„ط·ظپظ„: {normalize_child_code(child_code)}",
        f"ظˆظ‚طھ ط§ظ„ط§ط³طھط®ط¯ط§ظ…: {total_sec // 60} ط¯ظ‚ظٹظ‚ط©",
        f"ط§ظ„طھظ†ط¨ظٹظ‡ط§طھ: {alerts}",
        "",
        "ط£ظƒط«ط± ط§ظ„طھط·ط¨ظٹظ‚ط§طھ:",
    ]
    for i, r in enumerate(top, 1):
        lines.append(f"  {i}. {r['package_name']} â€” {int(r['total_seconds'] or 0) // 60} ط¯")
    if not top:
        lines.append("  (ظ„ط§ ط¨ظٹط§ظ†ط§طھ ط¨ط¹ط¯)")
    return "\n".join(lines)


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

    try:
        data = request.get_json(silent=True) or {}
        suffix = db_child_code(data.get("child_code") or data.get("childCode") or "")
        if not suffix:
            return _json_error("child_code required", 400, error_code="missing_child_code")
        child_code = suffix
        policy = data.get("policy") or {}
        parent_email = _extract_parent_email(data)
        conn = db()
        cur = conn.cursor()
        _screen_time_policy_save(conn, child_code, policy)
        _audit_log(
            cur,
            parent_email,
            child_code,
            "screen_time_policy_saved",
            f"warn={policy.get('warn_minutes')} block={policy.get('block_minutes')}",
        )
        if parent_email:
            settings = _guardian_settings_get(conn, parent_email)
            _cleanup_old_data(conn, int(settings.get("retention_days") or 30))
        conn.commit()
        conn.close()
        return jsonify({"success": True, "status": "success", "message": "Screen time policy saved"})
    except Exception as exc:
        logger.exception("screen-time-policy POST failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط­ظپط¸ ط§ظ„ط³ظٹط§ط³ط©", 500, error_code="server_error")


@app.route("/sync-child-apps", methods=["POST"])
def sync_child_apps():
    """ط±ظپط¹ ظ‚ط§ط¦ظ…ط© طھط·ط¨ظٹظ‚ط§طھ ط§ظ„ط·ظپظ„ ظ…ط¹ ط§ظ„ط£ط³ظ…ط§ط، ظˆط§ظ„ط£ظٹظ‚ظˆظ†ط§طھ ظ„ط¹ط±ط¶ظ‡ط§ ط¹ظ†ط¯ ط§ظ„ط£ظ…."""
    try:
        data = request.get_json(silent=True) or {}
        child_code = db_child_code(data.get("child_code", ""))
        if not child_code:
            return _json_error("child_code required", 400, error_code="missing_child_code")
        apps = data.get("apps") or []
        conn = db()
        saved = _upsert_child_app_meta(conn, child_code, apps)
        conn.commit()
        conn.close()
        return jsonify({"success": True, "status": "success", "saved": saved})
    except Exception as exc:
        logger.exception("sync-child-apps failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ظ…ط²ط§ظ…ظ†ط© ط§ظ„طھط·ط¨ظٹظ‚ط§طھ", 500, error_code="server_error")


@app.route("/child-installed-apps", methods=["GET"])
def child_installed_apps():
    """ظ‚ط§ط¦ظ…ط© ط§ظ„طھط·ط¨ظٹظ‚ط§طھ ط§ظ„ظ…ط«ط¨طھط© ط¹ظ„ظ‰ ط¬ظ‡ط§ط² ط§ظ„ط·ظپظ„ (ط§ط³ظ… + ط£ظٹظ‚ظˆظ†ط©)."""
    try:
        child_code = db_child_code(request.args.get("child_code", ""))
        if not child_code:
            return _json_error("child_code required", 400, error_code="missing_child_code")
        conn = db()
        cur = conn.cursor()
        cur.execute(
            """
            SELECT package_name, app_label, icon_b64, updated_at
            FROM child_app_meta
            WHERE child_code = ?
            ORDER BY COALESCE(NULLIF(app_label, ''), package_name) ASC
            """,
            (child_code,),
        )
        apps = []
        for row in cur.fetchall():
            pkg = str(row["package_name"] or "").strip()
            if not pkg:
                continue
            apps.append(
                {
                    "package_name": pkg,
                    "app_label": (row["app_label"] or "").strip() or pkg.split(".")[-1],
                    "icon_b64": (row["icon_b64"] or "").strip() or None,
                    "updated_at": row["updated_at"],
                }
            )
        conn.close()
        return jsonify(
            {
                "success": True,
                "status": "success",
                "count": len(apps),
                "apps": apps,
            }
        )
    except Exception as exc:
        logger.exception("child-installed-apps failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط¬ظ„ط¨ ط§ظ„طھط·ط¨ظٹظ‚ط§طھ", 500, error_code="server_error")


@app.route("/child-heartbeat", methods=["POST"])
def child_heartbeat():
    try:
        data = request.get_json(silent=True) or {}
        child_code = db_child_code(data.get("child_code", ""))
        ts_ms = int(data.get("ts_ms") or 0)
        if not child_code:
            return _json_error("child_code required", 400, error_code="missing_child_code")
        conn = db()
        cur = conn.cursor()
        device_name = ""
        device_row = find_child_device(cur, child_code, log_on_miss=False)
        if device_row:
            device_name = device_row["device_name"] or ""
        perms = data.get("permissions") or {}
        if not isinstance(perms, dict):
            perms = {}
        perms_ok = 1 if perms.get("mandatory_ok") else 0
        perms_json = json.dumps(perms, ensure_ascii=False)
        ts_val = ts_ms or int(datetime.now().timestamp() * 1000)
        cur.execute(
            """
            INSERT INTO child_status (child_code, last_seen_ms, device_name, permissions_json, permissions_ok)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(child_code) DO UPDATE SET
                last_seen_ms = excluded.last_seen_ms,
                device_name = excluded.device_name,
                permissions_json = excluded.permissions_json,
                permissions_ok = excluded.permissions_ok
            """,
            (child_code, ts_val, device_name, perms_json, perms_ok),
        )
        conn.commit()
        conn.close()
        return jsonify({"success": True, "status": "success"})
    except Exception as exc:
        logger.exception("child-heartbeat failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ظ†ط¨ط¶ط© ط§ظ„ط§طھطµط§ظ„", 500, error_code="server_error")


@app.route("/screen-time-events", methods=["POST"])
def screen_time_events():
    try:
        data = request.get_json(silent=True) or {}
        child_code = db_child_code(data.get("child_code", ""))
        events = data.get("events") or []
        if not child_code:
            return _json_error("child_code required", 400, error_code="missing_child_code")
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
        return jsonify({"success": True, "status": "success"})
    except Exception as exc:
        logger.exception("screen-time-events failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط±ظپط¹ ط£ط­ط¯ط§ط« ظˆظ‚طھ ط§ظ„ط´ط§ط´ط©", 500, error_code="server_error")


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
        "SELECT last_seen_ms, device_name, permissions_json, permissions_ok FROM child_status WHERE child_code = ?",
        (child_code,),
    )
    status_row = cur.fetchone()
    last_seen_ms = int(status_row["last_seen_ms"]) if status_row else 0
    device_name = status_row["device_name"] if status_row else ""
    permissions_ok = bool(status_row["permissions_ok"]) if status_row else False
    permissions = {}
    if status_row and status_row["permissions_json"]:
        try:
            permissions = json.loads(status_row["permissions_json"] or "{}")
        except Exception:
            permissions = {}

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
    unlimited = {p.lower() for p in (policy.get("unlimited_packages") or [])}

    cur.execute(
        """
        SELECT package_name, total_seconds
        FROM usage_daily WHERE child_code = ? AND day = ?
        ORDER BY total_seconds DESC
        """,
        (child_code, today),
    )
    top_apps_today = _enrich_app_rows(
        cur,
        child_code,
        [
            {
                "package_name": r["package_name"],
                "total_seconds": int(r["total_seconds"] or 0),
                "educational": str(r["package_name"] or "").lower() in unlimited,
            }
            for r in cur.fetchall()
        ],
    )

    educational_seconds = 0
    monitored_seconds = 0
    for row in top_apps_today:
        sec = int(row["total_seconds"] or 0)
        if row["educational"]:
            educational_seconds += sec
        else:
            monitored_seconds += sec

    today_start = f"{today} 00:00:00"
    cur.execute(
        "SELECT COUNT(*) AS cnt FROM alerts WHERE child_code = ? AND time >= ?",
        (child_code, today_start),
    )
    alerts_today = int(cur.fetchone()["cnt"] or 0)

    week_start = (datetime.now() - timedelta(days=7)).strftime("%Y-%m-%d 00:00:00")
    cur.execute(
        "SELECT COUNT(*) AS cnt FROM alerts WHERE child_code = ? AND time >= ?",
        (child_code, week_start),
    )
    alerts_week = int(cur.fetchone()["cnt"] or 0)

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
        "educational_seconds": educational_seconds,
        "monitored_seconds": monitored_seconds,
        "alerts_today": alerts_today,
        "alerts_week": alerts_week,
        "top_apps_today": top_apps_today,
        "permissions_ok": permissions_ok,
        "permissions": permissions,
        "policy": policy,
    })


@app.route("/guardian-settings", methods=["GET", "POST"])
def guardian_settings():
    try:
        if request.method == "GET":
            parent_email = _extract_parent_email(dict(request.args))
            if not parent_email:
                return _json_error("parent_email is required", 400, error_code="missing_parent_email")
            conn = db()
            settings = _guardian_settings_get(conn, parent_email)
            conn.close()
            return _json_success("Guardian settings", parent_email=parent_email, settings=settings)

        data = request.get_json(silent=True) or {}
        parent_email = _extract_parent_email(data)
        if not parent_email:
            return _json_error("parent_email is required", 400, error_code="missing_parent_email")
        settings = data.get("settings") or {}
        conn = db()
        cur = conn.cursor()
        _guardian_settings_save(conn, parent_email, settings)
        _audit_log(cur, parent_email, "", "guardian_settings_saved", json.dumps(settings, ensure_ascii=False)[:200])
        deleted = _cleanup_old_data(conn, int(settings.get("retention_days") or 30))
        conn.commit()
        conn.close()
        return _json_success(
            "Settings saved",
            parent_email=parent_email,
            settings=_guardian_settings_get(db(), parent_email),
            cleanup=deleted,
        )
    except Exception as exc:
        logger.exception("guardian-settings failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط­ظپط¸ ط§ظ„ط¥ط¹ط¯ط§ط¯ط§طھ", 500, error_code="server_error")


@app.route("/audit-log", methods=["GET"])
def audit_log_list():
    try:
        parent_email = _extract_parent_email(dict(request.args))
        if not parent_email:
            return _json_error("parent_email is required", 400, error_code="missing_parent_email")
        child_filter = db_child_code(request.args.get("child_code", ""))
        conn = db()
        cur = conn.cursor()
        if child_filter:
            cur.execute(
                """
                SELECT id, guardian_email, child_code, action, detail, created_at
                FROM audit_log
                WHERE guardian_email = ? AND (child_code = ? OR child_code = '' OR child_code IS NULL)
                ORDER BY id DESC LIMIT 100
                """,
                (parent_email, child_filter),
            )
        else:
            cur.execute(
                """
                SELECT id, guardian_email, child_code, action, detail, created_at
                FROM audit_log WHERE guardian_email = ?
                ORDER BY id DESC LIMIT 100
                """,
                (parent_email,),
            )
        rows = []
        for r in cur.fetchall():
            rows.append({
                "id": int(r["id"]),
                "guardian_email": r["guardian_email"],
                "child_code": normalize_child_code(r["child_code"] or ""),
                "action": r["action"],
                "detail": r["detail"],
                "created_at": r["created_at"],
            })
        conn.close()
        return _json_success("Audit log", entries=rows, count=len(rows))
    except Exception as exc:
        logger.exception("audit-log failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط¬ظ„ط¨ ط§ظ„ط³ط¬ظ„", 500, error_code="server_error")


@app.route("/send-email-summary", methods=["POST"])
def send_email_summary():
    """ط¥ط±ط³ط§ظ„ ظ…ظ„ط®طµ ظٹظˆظ…ظٹ ط£ظˆ ط£ط³ط¨ظˆط¹ظٹ ظ„ط¨ط±ظٹط¯ ظˆظ„ظٹ ط§ظ„ط£ظ…ط±."""
    try:
        data = request.get_json(silent=True) or {}
        parent_email = _extract_parent_email(data)
        child_code = db_child_code(data.get("child_code") or "")
        period = (data.get("period") or "daily").strip().lower()
        if not parent_email:
            return _json_error("parent_email is required", 400, error_code="missing_parent_email")
        if not child_code:
            return _json_error("child_code required", 400, error_code="missing_child_code")

        conn = db()
        sent = _send_guardian_summary_email(conn, parent_email, child_code, period)
        conn.commit()
        conn.close()
        if not sent:
            return _json_error(
                "طھط¹ط°ظ‘ط± ط¥ط±ط³ط§ظ„ ط§ظ„ط¨ط±ظٹط¯ â€” طھط­ظ‚ظ‚ظٹ ظ…ظ† SMTP/Resend ط¹ظ„ظ‰ Render",
                500,
                error_code="email_failed",
            )
        return _json_success(f"Summary email sent ({period})", email_sent=True, period=period)
    except Exception as exc:
        logger.exception("send-email-summary failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط¥ط±ط³ط§ظ„ ط§ظ„ظ…ظ„ط®طµ", 500, error_code="server_error")


@app.route("/weekly-chart", methods=["GET"])
def weekly_chart():
    """ط¨ظٹط§ظ†ط§طھ ط§ظ„ط±ط³ظˆظ… ط§ظ„ط¨ظٹط§ظ†ظٹط© â€” ط§ط³طھط®ط¯ط§ظ… ظٹظˆظ…ظٹ + ط£ظپط¶ظ„ ط§ظ„طھط·ط¨ظٹظ‚ط§طھ + ط§ظ„طھظ†ط¨ظٹظ‡ط§طھ."""
    try:
        child_code = db_child_code(request.args.get("child_code", ""))
        if not child_code:
            return _json_error("child_code required", 400, error_code="missing_child_code")

        days = _usage_period_days(request.args.get("days"), default=7)

        since_day = (datetime.now() - timedelta(days=days - 1)).strftime("%Y-%m-%d")
        conn = db()
        cur = conn.cursor()

        cur.execute(
            """
            SELECT day, COALESCE(SUM(total_seconds), 0) AS total_seconds
            FROM usage_daily
            WHERE child_code = ? AND day >= ?
            GROUP BY day
            ORDER BY day ASC
            """,
            (child_code, since_day),
        )
        by_day = {r["day"]: int(r["total_seconds"] or 0) for r in cur.fetchall()}
        usage_by_day = []
        for i in range(days):
            d = (datetime.now() - timedelta(days=days - 1 - i)).strftime("%Y-%m-%d")
            usage_by_day.append({"day": d, "total_seconds": by_day.get(d, 0)})

        cur.execute(
            """
            SELECT package_name, SUM(total_seconds) AS total_seconds
            FROM usage_daily
            WHERE child_code = ? AND day >= ?
            GROUP BY package_name
            ORDER BY total_seconds DESC
            LIMIT 8
            """,
            (child_code, since_day),
        )
        top_apps = _attach_avg_seconds_per_day(
            [
                {"package_name": r["package_name"], "total_seconds": int(r["total_seconds"] or 0)}
                for r in cur.fetchall()
            ],
            days,
        )
        avg_daily_screen_seconds = _avg_daily_screen_seconds(usage_by_day, days)

        policy = _screen_time_policy_get(conn, child_code)
        top_apps = _enrich_app_rows(cur, child_code, top_apps)
        unlimited = {p.lower() for p in (policy.get("unlimited_packages") or [])}
        educational_apps = []
        other_apps = []
        for app in top_apps:
            pkg = str(app["package_name"] or "").lower()
            if pkg in unlimited:
                educational_apps.append(app)
            else:
                other_apps.append(app)

        week_start = (datetime.now() - timedelta(days=7)).strftime("%Y-%m-%d 00:00:00")
        cur.execute(
            """
            SELECT COUNT(*) AS cnt FROM alerts
            WHERE child_code = ? AND time >= ?
            """,
            (child_code, week_start),
        )
        alerts_week = int(cur.fetchone()["cnt"] or 0)

        today = datetime.now().strftime("%Y-%m-%d")
        cur.execute(
            """
            SELECT COUNT(*) AS cnt FROM alerts
            WHERE child_code = ? AND time >= ?
            """,
            (child_code, f"{today} 00:00:00"),
        )
        alerts_today = int(cur.fetchone()["cnt"] or 0)

        cur.execute(
            """
            SELECT COUNT(*) AS cnt FROM screen_time_events
            WHERE child_code = ? AND time >= ? AND event_type LIKE '%sleep%'
            """,
            (child_code, week_start),
        )
        sleep_violations = int(cur.fetchone()["cnt"] or 0)

        conn.close()

        return jsonify({
            "success": True,
            "child_code": normalize_child_code(child_code),
            "child_code_clean": child_code,
            "since_day": since_day,
            "days": days,
            "usage_by_day": usage_by_day,
            "avg_daily_screen_seconds": avg_daily_screen_seconds,
            "top_apps": top_apps,
            "educational_apps": educational_apps,
            "other_apps": other_apps,
            "alerts_today": alerts_today,
            "alerts_week": alerts_week,
            "sleep_violations_week": sleep_violations,
        })
    except Exception as exc:
        logger.exception("weekly-chart failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ط¬ظ„ط¨ ط¨ظٹط§ظ†ط§طھ ط§ظ„ط±ط³ظ… ط§ظ„ط¨ظٹط§ظ†ظٹ", 500, error_code="server_error")


@app.route("/cron/email-summaries", methods=["GET", "POST"])
def cron_email_summaries():
    """
    ظ…ظ‡ظ…ط© ظ…ط¬ط¯ظˆظ„ط© â€” ط¹ظ„ظ‰ Render: Cron Job ظٹط¶ط±ط¨ ظ‡ط°ط§ ط§ظ„ظ…ط³ط§ط± ظٹظˆظ…ظٹط§ظ‹.
    Header: X-CRON-SECRET ط£ظˆ ?secret= ظ†ظپط³ CRON_SECRET (ط£ظˆ API_KEY).
    """
    try:
        secret = (
            request.headers.get("X-CRON-SECRET")
            or request.args.get("secret")
            or ""
        ).strip()
        expected = os.environ.get("CRON_SECRET") or os.environ.get("API_KEY", "")
        if not expected or secret != expected:
            return _json_error("ط؛ظٹط± ظ…طµط±ظ‘ط­", 401, error_code="unauthorized")
        stats = _run_scheduled_email_summaries()
        return _json_success("طھظ… طھط´ط؛ظٹظ„ ظ…ظ‡ظ…ط© ط§ظ„ط¨ط±ظٹط¯ ط§ظ„ظ…ط¬ط¯ظˆظ„ط©", stats=stats)
    except Exception as exc:
        logger.exception("cron email-summaries failed: %s", exc)
        return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ط£ط«ظ†ط§ط، ظ…ظ‡ظ…ط© ط§ظ„ط¨ط±ظٹط¯", 500, error_code="server_error")


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
        LIMIT 100
        """,
        (child_code, today),
    )
    apps = _enrich_app_rows(cur, child_code, [dict(r) for r in cur.fetchall()])
    conn.close()
    return jsonify({"child_code": child_code, "day": today, "apps": apps})


@app.errorhandler(404)
def not_found_json(error):
    return _json_error("ط§ظ„ظ…ط³ط§ط± ط؛ظٹط± ظ…ظˆط¬ظˆط¯", 404, error_code="not_found")


@app.errorhandler(500)
def server_error_json(error):
    logger.exception("unhandled 500: %s", error)
    return _json_error("ط®ط·ط£ ط¯ط§ط®ظ„ظٹ ظپظٹ ط§ظ„ط³ظٹط±ظپط±", 500, error_code="server_error")


@app.errorhandler(Exception)
def unhandled_exception(error):
    logger.exception("unhandled exception: %s", error)
    return _json_error("ط®ط·ط£ ط؛ظٹط± ظ…طھظˆظ‚ط¹", 500, error_code="server_error")


# ط¥ظ†ط´ط§ط، ط§ظ„ط¬ط¯ط§ظˆظ„ ط¹ظ†ط¯ طھط´ط؛ظٹظ„ ط§ظ„ط³ظٹط±ظپط±
init_db()
_log_db_startup()
_start_email_cron_thread()

# طھط´ط؛ظٹظ„ ظ…ط­ظ„ظٹ ظپظ‚ط·طŒ ط£ظ…ط§ Render ظٹط³طھط®ط¯ظ… gunicorn
if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)

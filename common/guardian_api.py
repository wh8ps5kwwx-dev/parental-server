"""استدعاءات REST لولي الأمر — نفس مسارات server.py (Render)."""
from __future__ import annotations

import os
from typing import Any

import requests

SERVER_URL = os.environ.get(
    "SERVER_URL", "https://parental-server-4mms.onrender.com"
).rstrip("/")
API_KEY = os.environ.get("API_KEY", "graduation-secret-key")
HEADERS = {"X-API-KEY": API_KEY, "Content-Type": "application/json"}


def _post(path: str, data: dict[str, Any]) -> dict[str, Any]:
    r = requests.post(
        f"{SERVER_URL}{path}",
        json=data,
        headers=HEADERS,
        timeout=20,
    )
    try:
        body = r.json()
    except Exception:
        body = {"status": "error", "message": r.text or f"HTTP {r.status_code}"}
    if r.status_code >= 400:
        body.setdefault("status", "error")
    return body


def send_email_code(email: str) -> dict[str, Any]:
    """إرسال رمز تحقق لبريد ولي الأمر."""
    return _post("/send-email-code", {"email": email.strip()})


def verify_email_code(email: str, code: str) -> dict[str, Any]:
    """التحقق من رمز البريد بعد وصوله للصندوق."""
    return _post("/verify-email-code", {"email": email.strip(), "code": code.strip()})


def send_link_code(guardian_email: str, child_code: str) -> dict[str, Any]:
    """إرسال رمز الربط لبريد ولي الأمر — مرة واحدة أثناء ربط الطفل."""
    return _post(
        "/send-link-code",
        {"guardian_email": guardian_email.strip(), "child_code": child_code.strip()},
    )


def add_child(payload: dict[str, Any]) -> dict[str, Any]:
    """ربط طفل بعد تحقق البريد + رمز جهاز الطفل."""
    return _post("/add-child", payload)

"""واجهة السيرفر لتطبيق الأم — نسخة جوال (بدون مجلد المشروع الكامل)."""
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
        timeout=25,
    )
    try:
        body = r.json()
    except Exception:
        body = {"status": "error", "message": r.text or f"HTTP {r.status_code}"}
    if r.status_code >= 400:
        body.setdefault("status", "error")
    return body


def _get(path: str, params: dict[str, Any] | None = None) -> Any:
    r = requests.get(
        f"{SERVER_URL}{path}",
        params=params or {},
        headers={"X-API-KEY": API_KEY},
        timeout=25,
    )
    try:
        return r.json()
    except Exception:
        return []


def send_email_code(email: str) -> dict[str, Any]:
    return _post("/send-email-code", {"email": email.strip()})


def verify_email_code(email: str, code: str) -> dict[str, Any]:
    return _post("/verify-email-code", {"email": email.strip(), "code": code.strip()})


def send_link_code(guardian_email: str, child_code: str) -> dict[str, Any]:
    return _post(
        "/send-link-code",
        {"guardian_email": guardian_email.strip(), "child_code": child_code.strip()},
    )


def verify_child_device_code(child_code: str, code: str) -> dict[str, Any]:
    return _post(
        "/verify-child-device-code",
        {"child_code": child_code.strip(), "code": code.strip()},
    )


def add_child(payload: dict[str, Any]) -> dict[str, Any]:
    return _post("/add-child", payload)


def send_command(
    action: str, value: str, child_code: str, guardian_email: str
) -> dict[str, Any]:
    return _post(
        "/send-command",
        {
            "action": action,
            "value": value,
            "child_code": child_code.strip(),
            "guardian_email": guardian_email.strip(),
        },
    )


def apply_default_blocklist(child_code: str) -> dict[str, Any]:
    return _post(
        "/apply-default-blocklist",
        {"child_code": child_code.strip()},
    )


def fetch_alerts(child_code: str) -> list[dict[str, Any]]:
    data = _get("/alerts", {"child_code": child_code.strip()})
    return data if isinstance(data, list) else []


def fetch_reports(child_code: str) -> list[dict[str, Any]]:
    data = _get("/reports", {"child_code": child_code.strip()})
    return data if isinstance(data, list) else []


def fetch_weekly_usage(child_code: str) -> list[dict[str, Any]]:
    data = _get("/weekly-report", {"child_code": child_code.strip()})
    if isinstance(data, dict):
        return data.get("apps") or []
    return []

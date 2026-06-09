"""تسجيل جهاز الطفل على السيرفر من Python (قبل فتح صلاحيات Android)."""

from __future__ import annotations

import json
import os
import uuid
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

DEFAULT_SERVER = "https://parental-server-4mms.onrender.com"
DEFAULT_API_KEY = "graduation-secret-key"


def register_child_device(
    child_email: str,
    *,
    server_root: str | None = None,
    api_key: str | None = None,
    device_name: str = "Python-Emulator",
    android_version: str = "Android-Emulator",
) -> dict:
    root = (server_root or os.environ.get("MYRANA_SERVER_ROOT") or DEFAULT_SERVER).rstrip("/")
    key = api_key or os.environ.get("MYRANA_API_KEY") or DEFAULT_API_KEY
    child_code = f"CHILD-{uuid.uuid4().hex[:8].upper()}"
    payload = json.dumps(
        {
            "child_code": child_code,
            "child_email": child_email.strip(),
            "device_name": device_name,
            "android_version": android_version,
        }
    ).encode("utf-8")
    req = Request(
        f"{root}/register-child-device",
        data=payload,
        headers={"Content-Type": "application/json", "X-API-KEY": key},
        method="POST",
    )
    try:
        with urlopen(req, timeout=30) as resp:
            body = json.loads(resp.read().decode("utf-8"))
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"فشل تسجيل الطفل ({exc.code}): {detail}") from exc
    except URLError as exc:
        raise RuntimeError(f"لا اتصال بالسيرفر: {exc}") from exc

    verify = (
        body.get("device_verify_code")
        or body.get("verification_code")
        or ""
    )
    return {
        "child_code": body.get("child_code") or child_code,
        "verify_code": str(verify).strip(),
        "child_email": child_email.strip(),
        "message": body.get("message") or "registered",
    }

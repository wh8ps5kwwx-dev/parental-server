"""إعدادات مشتركة مع تطبيق أندرويد عبر myrana_config.json في جذر المشروع."""

from __future__ import annotations

import json
from pathlib import Path

_ROOT_CONFIG = Path(__file__).resolve().parent.parent / "myrana_config.json"
_EXAMPLE = Path(__file__).resolve().parent.parent / "myrana_config.json.example"

_DEFAULT_ROOT = "https://parental-server-4mms.onrender.com"
_DEFAULT_BASE = f"{_DEFAULT_ROOT}/api/"
_DEFAULT_CHILD = "replace-with-child-device-id"
_DEFAULT_API_KEY = "graduation-secret-key"


def _load_shared() -> dict:
    for path in (_ROOT_CONFIG, _EXAMPLE):
        if path.is_file():
            try:
                return json.loads(path.read_text(encoding="utf-8"))
            except (json.JSONDecodeError, OSError):
                pass
    return {}


_cfg = _load_shared()

SERVER_ROOT = str(_cfg.get("serverRootUrl") or _DEFAULT_ROOT).rstrip("/")
BASE_URL = str(_cfg.get("serverBaseUrl") or f"{SERVER_ROOT}/api/").rstrip("/") + "/"
CHILD_DEVICE_ID = str(_cfg.get("childDeviceId") or _DEFAULT_CHILD).strip()
API_KEY = str(_cfg.get("apiKey") or _DEFAULT_API_KEY).strip()

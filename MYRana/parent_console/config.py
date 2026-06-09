"""إعدادات مشتركة مع تطبيق أندرويد عبر myrana_config.json في جذر المشروع."""

from __future__ import annotations

import json
from pathlib import Path

_ROOT_CONFIG = Path(__file__).resolve().parent.parent / "myrana_config.json"
_EXAMPLE = Path(__file__).resolve().parent.parent / "myrana_config.json.example"

_DEFAULT_BASE = "https://your-server.example/api/"
_DEFAULT_CHILD = "replace-with-child-device-id"


def _load_shared() -> dict:
    for path in (_ROOT_CONFIG, _EXAMPLE):
        if path.is_file():
            try:
                return json.loads(path.read_text(encoding="utf-8"))
            except (json.JSONDecodeError, OSError):
                pass
    return {}


_cfg = _load_shared()

BASE_URL = str(_cfg.get("serverBaseUrl") or _DEFAULT_BASE).rstrip("/") + "/"
CHILD_DEVICE_ID = str(_cfg.get("childDeviceId") or _DEFAULT_CHILD).strip()

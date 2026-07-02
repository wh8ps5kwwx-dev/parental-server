# تحويل اسم التطبيق (عربي/إنجليزي) إلى package name للحظر الفعلي على Android.
from __future__ import annotations

import json
from pathlib import Path

_MAP: dict[str, str] | None = None

# أسماء شائعة إضافية (عربي + إنجليزي)
_EXTRA_ALIASES = {
    "تيك توك": "com.zhiliaoapp.musically",
    "tiktok": "com.zhiliaoapp.musically",
    "يوتيوب": "com.google.android.youtube",
    "youtube": "com.google.android.youtube",
    "فيسبوك": "com.facebook.katana",
    "facebook": "com.facebook.katana",
    "انستقرام": "com.instagram.android",
    "instagram": "com.instagram.android",
    "سناب شات": "com.snapchat.android",
    "snapchat": "com.snapchat.android",
    "واتساب": "com.whatsapp",
    "whatsapp": "com.whatsapp",
    "تلغرام": "org.telegram.messenger",
    "telegram": "org.telegram.messenger",
    "قراني": "com.dvloper.granny",
    "granny": "com.dvloper.granny",
}


def _load_map() -> dict[str, str]:
    global _MAP
    if _MAP is not None:
        return _MAP
    here = Path(__file__).resolve().parent
    path = here / "app_package_map.json"
    merged: dict[str, str] = dict(_EXTRA_ALIASES)
    if path.is_file():
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for name, pkg in data.items():
            if name and pkg:
                merged[name.strip().lower()] = pkg.strip().lower()
    _MAP = merged
    return _MAP


def resolve_app_package(value: str) -> str:
    """
    يُرجع package name جاهزاً للحظر.
    - إن كان بالفعل مثل com.example.app يُعاد كما هو.
    - وإلا يُبحث في app_package_map.json والأسماء الشائعة.
    """
    raw = (value or "").strip()
    if not raw:
        return ""
    low = raw.lower()
    if "." in low and " " not in low and not low.startswith("http"):
        return low

    mapping = _load_map()
    if low in mapping:
        return mapping[low]

    for key, pkg in mapping.items():
        if low == key or low in key or key in low:
            return pkg

    return low

# -*- coding: utf-8 -*-
"""اختبار سريع بعد رفع Render — شغّلي: python RA/test_after_deploy.py"""

from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

BASE = os.environ.get("SERVER_URL", "https://parental-server-4mms.onrender.com").rstrip("/")
API_KEY = os.environ.get("API_KEY", "graduation-secret-key")
CHILD = os.environ.get("TEST_CHILD_CODE", "TEST-DEPLOY")


def req(method: str, path: str, body: dict | None = None) -> tuple[int, dict | str]:
    url = f"{BASE}{path}"
    data = json.dumps(body).encode("utf-8") if body is not None else None
    r = urllib.request.Request(
        url,
        data=data,
        method=method,
        headers={
            "X-API-KEY": API_KEY,
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(r, timeout=45) as resp:
            raw = resp.read().decode("utf-8")
            try:
                return resp.status, json.loads(raw)
            except json.JSONDecodeError:
                return resp.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", errors="replace")
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, raw


def ok(name: str, passed: bool, detail: str = "") -> bool:
    mark = "OK" if passed else "FAIL"
    line = f"[{mark}] {name}"
    if detail:
        line += f" — {detail}"
    print(line)
    return passed


def main() -> int:
    print(f"Server: {BASE}\n")
    fails = 0

    code, data = req("GET", "/")
    fails += 0 if ok("home", code == 200 and isinstance(data, dict)) else 1

    code, data = req("GET", "/blocklist/catalog")
    if isinstance(data, dict):
        counts = data.get("counts") or {}
        pkgs = counts.get("packages", 0)
        fails += 0 if ok("blocklist/catalog", code == 200 and pkgs > 50, f"{pkgs} packages") else 1
    else:
        fails += 1
        ok("blocklist/catalog", False, str(data)[:120])

    code, data = req(
        "POST",
        "/apply-default-blocklist",
        {"child_code": CHILD, "merge": True},
    )
    if isinstance(data, dict):
        c = data.get("counts") or {}
        fails += 0 if ok(
            "apply-default-blocklist",
            code == 200 and data.get("status") == "success",
            f"{c.get('packages', 0)} pkgs",
        ) else 1
        granny = "com.dvloper.granny"
        pkgs = data.get("blockedPackages") or []
        fails += 0 if ok("granny in policy", granny in pkgs, granny) else 1
    else:
        fails += 1
        ok("apply-default-blocklist", False, str(data)[:120])

    code, data = req(
        "POST",
        "/add-alert",
        {"child_code": CHILD, "message": "اختبار: محاولة فتح Granny"},
    )
    fails += 0 if ok("add-alert", code == 200) else 1

    code, data = req("GET", f"/alerts?child_code={CHILD}")
    if isinstance(data, list):
        fails += 0 if ok("alerts", code == 200 and len(data) > 0, f"{len(data)} alert(s)") else 1
    else:
        fails += 1
        ok("alerts", False, str(data)[:120])

    code, data = req("GET", f"/weekly-report?child_code={CHILD}")
    fails += 0 if ok("weekly-report", code == 200) else 1

    print()
    if fails:
        print(f"فشل {fails} اختبار/اختبارات — تأكدي أن blocklists/ مرفوعة مع server.py على Render.")
        return 1
    print("كل الاختبارات نجحت. جرّبي الآن: طفل يفتح Granny → الأم → عرض تنبيهات الحظر.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""واجهة ولي الأمر — Python (Kivy). Android للصلاحيات فقط."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
MYRANA = Path(r"c:\Users\rannn\AndroidStudioProjects\MYRana")

CANDIDATES = [
    MYRANA / "parent_console" / "main.py",
    ROOT / "mother-app" / "main.py",
]


def main() -> int:
    for script in CANDIDATES:
        if script.is_file():
            print(f"فتح واجهة Python: {script}")
            return subprocess.call([sys.executable, str(script)], cwd=str(script.parent))
    print("لم يُعثر على parent_console/main.py أو mother-app/main.py")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())

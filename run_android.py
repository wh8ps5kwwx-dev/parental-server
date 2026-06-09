#!/usr/bin/env python3
"""
MYRana — تشغيل محاكيين Android من Python:

  جهاز 1 (طفل):  صلاحيات + مراقبة خلفية
  جهاز 2 (ولي أمر): واجهة Android كاملة (تسجيل / ربط / حظر)

Python: تشغيل AVD، تثبيت، فتح التطبيقين — لا واجهة Windows.

  python run_android.py status
  python run_android.py start
  python run_android.py test --start-emulators
"""

from __future__ import annotations

import argparse
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

from common import android_devices as adb  # noqa: E402
from common.devices_config import (  # noqa: E402
    DevicesConfig,
    RoleDevice,
    load_config,
    save_child_registration,
    sync_properties_file,
)
from common.register_child import register_child_device  # noqa: E402

CONFIG_PATH = ROOT / "devices.local.json"


def _resolve_serial(
    role: RoleDevice, label: str, assigned: dict[str, str] | None = None
) -> str:
    if role.serial:
        return role.serial
    if assigned and label in assigned:
        return assigned[label]
    ready = adb.ready_devices()
    if len(ready) == 1:
        print(f"  [{label}] serial غير محدد — الجهاز الوحيد: {ready[0].serial}")
        return ready[0].serial
    raise RuntimeError(
        f"حدّد serial لـ {label} في devices.local.json\n"
        f"  adb devices"
    )


def _auto_assign_serials(cfg: DevicesConfig) -> dict[str, str]:
    if cfg.child.serial or cfg.parent.serial:
        return {}
    ready = adb.ready_devices()
    if len(ready) < 2:
        return {}
    a, b = ready[0].serial, ready[1].serial
    print(f"  [auto] child={a}  parent={b}")
    return {"child": a, "parent": b}


def _maybe_start_avd(role: RoleDevice, label: str) -> str | None:
    if role.serial:
        return role.serial
    if not role.avd:
        return None
    avds = adb.list_avds()
    if role.avd not in avds:
        print(f"  [{label}] AVD '{role.avd}' غير موجود. المتاح: {avds or '(لا شيء)'}")
        return None
    print(f"  [{label}] تشغيل {role.avd} ...")
    return adb.start_avd(role.avd)


def _ensure_child_code(cfg: DevicesConfig) -> DevicesConfig:
    if cfg.child_code:
        return cfg
    print("  تسجيل child_code على السيرفر (لربط تطبيق ولي الأمر)...")
    reg = register_child_device(cfg.child_email, server_root=cfg.server_root)
    cfg.child_code = reg["child_code"]
    cfg.verify_code = reg["verify_code"]
    save_child_registration(CONFIG_PATH, cfg.child_code, cfg.verify_code, cfg.child_email)
    print(f"  child_code  = {cfg.child_code}")
    print(f"  verify_code = {cfg.verify_code}")
    return cfg


def cmd_status(_: argparse.Namespace) -> int:
    print("=== adb devices ===")
    adb.print_status()
    avds = adb.list_avds()
    if avds:
        print("\n=== AVD ===")
        for name in avds:
            print(f"  • {name}")
    try:
        cfg = load_config()
        print("\n=== devices.local.json ===")
        print(f"  child.avd   = {cfg.child.avd}  serial={cfg.child.serial or '-'}")
        print(f"  parent.avd  = {cfg.parent.avd}  serial={cfg.parent.serial or '-'}")
        print(f"  childCode   = {cfg.child_code or '(يُولَّد عند test/install)'}")
    except FileNotFoundError as exc:
        print(f"\n[!] {exc}")
    print(
        "\nمحاكيان Android: طفل (صلاحيات) + ولي أمر (واجهة Kotlin)"
    )
    return 0


def cmd_start(_: argparse.Namespace) -> int:
    cfg = load_config()
    if not cfg.child.serial:
        s = _maybe_start_avd(cfg.child, "child")
        if s:
            cfg.child.serial = s
    if not cfg.parent.serial:
        s = _maybe_start_avd(cfg.parent, "parent")
        if s:
            cfg.parent.serial = s
    if not cfg.child.serial and not cfg.parent.serial:
        avds = adb.list_avds()
        raise RuntimeError(
            "لم يُشغَّل أي محاكي.\n"
            "  أنشئي Emulator_Child و Emulator_Parent من Device Manager\n"
            f"  المتاح: {avds or '(لا يوجد)'}"
        )
    sync_properties_file(cfg)
    print("\n=== adb devices ===")
    adb.print_status()
    if cfg.child.serial:
        print(f"  child  → {cfg.child.serial}")
    if cfg.parent.serial:
        print(f"  parent → {cfg.parent.serial}")
    return 0


def _gradle_install(cfg: DevicesConfig, role: RoleDevice, serial: str) -> None:
    adb.ensure_ready(serial)
    gradlew = cfg.android_project / ("gradlew.bat" if os.name == "nt" else "gradlew")
    env = os.environ.copy()
    env["ANDROID_SERIAL"] = serial
    print(f"  gradlew {role.gradle_task}  →  {serial}")
    subprocess.run(
        [str(gradlew), role.gradle_task, "--no-daemon"],
        cwd=str(cfg.android_project),
        env=env,
        check=True,
    )


def cmd_install(args: argparse.Namespace) -> int:
    cfg = load_config()
    if args.start_emulators:
        cmd_start(args)
        cfg = load_config()
    cfg = _ensure_child_code(cfg)
    assigned = _auto_assign_serials(cfg)
    child_serial = _resolve_serial(cfg.child, "child", assigned)
    parent_serial = _resolve_serial(cfg.parent, "parent", assigned)
    if child_serial == parent_serial:
        raise RuntimeError("الطفل وولي الأمر يحتاجان محاكيين مختلفين.")
    cfg.child.serial = child_serial
    cfg.parent.serial = parent_serial
    sync_properties_file(cfg)

    print("\n=== تثبيت تطبيق الطفل (صلاحيات) ===")
    _gradle_install(cfg, cfg.child, child_serial)
    print("\n=== تثبيت تطبيق ولي الأمر (واجهة Android) ===")
    _gradle_install(cfg, cfg.parent, parent_serial)
    print("\n[OK] تم التثبيت على المحاكيين.")
    return 0


def cmd_launch(_: argparse.Namespace) -> int:
    cfg = load_config()
    cfg = _ensure_child_code(cfg)
    assigned = _auto_assign_serials(cfg)
    child_serial = _resolve_serial(cfg.child, "child", assigned)
    parent_serial = _resolve_serial(cfg.parent, "parent", assigned)

    print(f"فتح تطبيق الطفل (صلاحيات) على {child_serial} ...")
    adb.launch_app(
        child_serial,
        cfg.child.package,
        cfg.child.activity,
        extras={"child_code": cfg.child_code, "child_email": cfg.child_email},
    )
    print(f"فتح تطبيق ولي الأمر على {parent_serial} ...")
    adb.launch_app(parent_serial, cfg.parent.package, cfg.parent.activity)
    print(f"\nعلى ولي الأمر: child_code={cfg.child_code}  verify={cfg.verify_code}")
    return 0


def cmd_test(args: argparse.Namespace) -> int:
    if args.start_emulators:
        cmd_start(args)
    code = cmd_install(args)
    return code if code != 0 else cmd_launch(args)


def main() -> int:
    parser = argparse.ArgumentParser(description="MYRana — محاكيان Android عبر Python")
    sub = parser.add_subparsers(dest="command", required=True)

    sub.add_parser("status").set_defaults(func=cmd_status)
    sub.add_parser("start").set_defaults(func=cmd_start)

    p_install = sub.add_parser("install")
    p_install.add_argument("--start-emulators", action="store_true")
    p_install.set_defaults(func=cmd_install)

    sub.add_parser("launch").set_defaults(func=cmd_launch)

    p_test = sub.add_parser("test", help="محاكيان + تثبيت + فتح التطبيقين")
    p_test.add_argument("--start-emulators", action="store_true")
    p_test.set_defaults(func=cmd_test)

    args = parser.parse_args()
    try:
        return args.func(args)
    except (RuntimeError, TimeoutError, subprocess.CalledProcessError) as exc:
        print(f"\n[X] خطأ: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

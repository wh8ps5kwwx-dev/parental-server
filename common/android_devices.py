"""تغليف adb — اكتشاف أجهزة USB والمحاكي (بدون منح صلاحيات)."""

from __future__ import annotations

import os
import shutil
import subprocess
import time
from dataclasses import dataclass
from typing import Iterable


@dataclass(frozen=True)
class AdbDevice:
    serial: str
    state: str
    product: str = ""
    model: str = ""


def find_adb() -> str:
    adb = os.environ.get("ADB") or shutil.which("adb")
    if not adb:
        raise RuntimeError(
            "لم يُعثر على adb. ثبّتي Android SDK Platform-Tools وأضيفيها إلى PATH."
        )
    return adb


def run_adb(*args: str, serial: str | None = None, check: bool = True) -> subprocess.CompletedProcess[str]:
    cmd = [find_adb()]
    if serial:
        cmd.extend(["-s", serial])
    cmd.extend(args)
    return subprocess.run(cmd, capture_output=True, text=True, check=check)


def list_devices() -> list[AdbDevice]:
    proc = run_adb("devices", "-l")
    devices: list[AdbDevice] = []
    for line in proc.stdout.splitlines():
        line = line.strip()
        if not line or line.startswith("List of devices"):
            continue
        parts = line.split()
        if len(parts) < 2:
            continue
        serial, state = parts[0], parts[1]
        meta = " ".join(parts[2:])
        product = _meta_value(meta, "product")
        model = _meta_value(meta, "model")
        devices.append(AdbDevice(serial=serial, state=state, product=product, model=model))
    return devices


def _meta_value(meta: str, key: str) -> str:
    token = f"{key}:"
    for part in meta.split():
        if part.startswith(token):
            return part[len(token) :]
    return ""


def ready_devices() -> list[AdbDevice]:
    return [d for d in list_devices() if d.state == "device"]


def wait_for_device(serial: str, timeout_sec: int = 120) -> None:
    deadline = time.time() + timeout_sec
    while time.time() < deadline:
        for dev in list_devices():
            if dev.serial == serial:
                if dev.state == "device":
                    return
                if dev.state == "unauthorized":
                    raise RuntimeError(
                        f"الجهاز {serial} غير مصرّح — وافقي على USB debugging على الشاشة."
                    )
        time.sleep(1.5)
    raise TimeoutError(f"انتهت مهلة انتظار الجهاز {serial} ({timeout_sec}s)")


def ensure_ready(serial: str) -> None:
    dev_map = {d.serial: d for d in list_devices()}
    if serial not in dev_map:
        raise RuntimeError(
            f"الجهاز {serial} غير متصل. شغّلي المحاكي أو وصّلي USB ثم: adb devices"
        )
    state = dev_map[serial].state
    if state == "unauthorized":
        raise RuntimeError(f"الجهاز {serial} unauthorized — وافقي على تصريح USB debugging.")
    if state != "device":
        raise RuntimeError(f"الجهاز {serial} في حالة '{state}' وليس 'device'.")


def find_sdk_root() -> str | None:
    for candidate in (
        os.environ.get("ANDROID_SDK_ROOT"),
        os.environ.get("ANDROID_HOME"),
        os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk"),
    ):
        if candidate and os.path.isdir(candidate):
            return candidate
    return None


def find_emulator_binary() -> str | None:
    sdk = find_sdk_root()
    if not sdk:
        return None
    exe = "emulator.exe" if os.name == "nt" else "emulator"
    path = os.path.join(sdk, "emulator", exe)
    return path if os.path.isfile(path) else None


def list_avds() -> list[str]:
    emu = find_emulator_binary()
    if not emu:
        return []
    proc = subprocess.run([emu, "-list-avds"], capture_output=True, text=True, check=False)
    return [line.strip() for line in proc.stdout.splitlines() if line.strip()]


def start_avd(avd_name: str, wait: bool = True) -> str | None:
    """تشغيل محاكي باسم AVD. يُرجع serial عند النجاح."""
    emu = find_emulator_binary()
    if not emu:
        raise RuntimeError("لم يُعثر على emulator — عيّني ANDROID_SDK_ROOT.")
    before = {d.serial for d in ready_devices()}
    subprocess.Popen(
        [emu, "-avd", avd_name],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    if not wait:
        return None
    deadline = time.time() + 180
    while time.time() < deadline:
        after = ready_devices()
        new_serials = [d.serial for d in after if d.serial not in before]
        if new_serials:
            serial = new_serials[0]
            wait_for_device(serial, timeout_sec=120)
            return serial
        if after and not before:
            serial = after[0].serial
            wait_for_device(serial, timeout_sec=120)
            return serial
        time.sleep(2)
    raise TimeoutError(f"لم يظهر محاكي جديد بعد تشغيل AVD '{avd_name}'")


def install_apk(serial: str, apk_path: str) -> None:
    ensure_ready(serial)
    run_adb("install", "-r", apk_path, serial=serial)


def launch_app(serial: str, package: str, activity: str, extras: dict[str, str] | None = None) -> None:
    ensure_ready(serial)
    component = f"{package}/{activity}"
    cmd = ["shell", "am", "start", "-n", component]
    for key, value in (extras or {}).items():
        if value:
            cmd.extend(["-e", key, value])
    run_adb(*cmd, serial=serial)


def print_status(devices: Iterable[AdbDevice] | None = None) -> None:
    items = list(devices) if devices is not None else list_devices()
    if not items:
        print("لا أجهزة متصلة. وصّلي USB أو شغّلي محاكي Android.")
        return
    print("الأجهزة المتصلة:")
    for d in items:
        extra = ""
        if d.model:
            extra = f"  model={d.model}"
        elif d.product:
            extra = f"  product={d.product}"
        mark = "[OK]" if d.state == "device" else "[--]"
        print(f"  {mark} {d.serial}  ({d.state}){extra}")

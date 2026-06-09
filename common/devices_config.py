"""قراءة devices.local.json — محاكيان Android (طفل + ولي أمر)."""

from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_ANDROID = Path(r"c:\Users\rannn\AndroidStudioProjects\MYRana")


@dataclass
class RoleDevice:
    serial: str
    avd: str
    package: str
    activity: str
    gradle_task: str


@dataclass
class DevicesConfig:
    android_project: Path
    child: RoleDevice
    parent: RoleDevice
    child_email: str
    child_code: str
    verify_code: str
    server_root: str


def _role(raw: dict, defaults: dict) -> RoleDevice:
    merged = {**defaults, **(raw or {})}
    return RoleDevice(
        serial=str(merged.get("serial") or "").strip(),
        avd=str(merged.get("avd") or "").strip(),
        package=str(merged["package"]),
        activity=str(merged["activity"]),
        gradle_task=str(merged["gradleTask"]),
    )


def load_config(path: Path | None = None) -> DevicesConfig:
    cfg_path = path or (PROJECT_ROOT / "devices.local.json")
    example = PROJECT_ROOT / "devices.local.example.json"

    if not cfg_path.is_file():
        if example.is_file():
            raise FileNotFoundError(
                f"انسخي {example.name} -> devices.local.json وعدّلي الإعدادات."
            )
        raise FileNotFoundError(f"ملف الإعدادات غير موجود: {cfg_path}")

    data = json.loads(cfg_path.read_text(encoding="utf-8"))
    android_project = Path(data.get("androidProject") or str(DEFAULT_ANDROID)).expanduser()

    child_defaults = {
        "package": "com.example.myrana.child",
        "activity": "com.example.myrana.ui.PermissionsLauncherActivity",
        "gradleTask": "installChildDebug",
    }
    parent_defaults = {
        "package": "com.example.myrana.parent",
        "activity": "com.example.myrana.parent.ui.ParentMainActivity",
        "gradleTask": "installParentDebug",
    }

    return DevicesConfig(
        android_project=android_project,
        child=_role(data.get("child"), child_defaults),
        parent=_role(data.get("parent"), parent_defaults),
        child_email=str(data.get("childEmail") or "child@example.com").strip(),
        child_code=str(data.get("childCode") or "").strip(),
        verify_code=str(data.get("verifyCode") or "").strip(),
        server_root=str(
            data.get("serverRoot") or "https://parental-server-4mms.onrender.com"
        ).rstrip("/"),
    )


def save_child_registration(
    cfg_path: Path, child_code: str, verify_code: str, child_email: str
) -> None:
    data = json.loads(cfg_path.read_text(encoding="utf-8"))
    data["childCode"] = child_code
    data["verifyCode"] = verify_code
    data["childEmail"] = child_email
    cfg_path.write_text(
        json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )


def sync_properties_file(cfg: DevicesConfig) -> Path:
    props = cfg.android_project / "scripts" / "emulators.local.properties"
    props.parent.mkdir(parents=True, exist_ok=True)
    lines = [
        "# مُولَّد من Python — محاكيان Android",
        f"child.serial={cfg.child.serial}",
        f"parent.serial={cfg.parent.serial}",
        "",
    ]
    props.write_text("\n".join(lines), encoding="utf-8")
    return props

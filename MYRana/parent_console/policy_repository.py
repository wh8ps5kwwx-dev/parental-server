"""
مرآة لـ PolicyRepository.kt: تخزين SQLite محلي + مزامنة REST مع الخادم.

يُستخدم من واجهة Kivy (main.py) أو من سطر الأوامر:
    python policy_repository.py sync
    python policy_repository.py add-host bad.example
    python policy_repository.py add-pkg com.bad.app
"""

from __future__ import annotations

import sqlite3
import sys

import db_store
from sync_api import PolicyClient, sync_roundtrip


class PolicyRepository:
    """نفس ترتيب المزامنة في Kotlin: رفع المعلّق ثم سحب السياسة من الخادم."""

    def __init__(
        self,
        conn: sqlite3.Connection | None = None,
        client: PolicyClient | None = None,
    ) -> None:
        self._conn = conn or db_store.connect()
        self._client = client or PolicyClient()

    @property
    def connection(self) -> sqlite3.Connection:
        return self._conn

    def add_blocked_site(self, host_pattern: str) -> None:
        db_store.add_host(self._conn, host_pattern)

    def add_blocked_package(self, package_name: str) -> None:
        db_store.add_package(self._conn, package_name)

    def local_snapshot(self) -> tuple[list[str], list[str]]:
        hosts = [row["host"] for row in db_store.list_hosts(self._conn)]
        packages = [row["package"] for row in db_store.list_packages(self._conn)]
        return hosts, packages

    def sync_with_server(self) -> str:
        return sync_roundtrip(self._conn, self._client)


def _main() -> int:
    repo = PolicyRepository()
    if len(sys.argv) < 2:
        hosts, packages = repo.local_snapshot()
        print("المواقع:", hosts or ["— فارغ"])
        print("الحزم:", packages or ["— فارغ"])
        print("\nأوامر: sync | add-host <نطاق> | add-pkg <حزمة>")
        return 0

    cmd = sys.argv[1].lower()
    if cmd == "sync":
        print(repo.sync_with_server())
        return 0
    if cmd == "add-host" and len(sys.argv) >= 3:
        repo.add_blocked_site(sys.argv[2])
        print("تمت الإضافة (بانتظار المزامنة).")
        return 0
    if cmd == "add-pkg" and len(sys.argv) >= 3:
        repo.add_blocked_package(sys.argv[2])
        print("تمت الإضافة (بانتظار المزامنة).")
        return 0

    print("أمر غير معروف:", cmd)
    return 1


if __name__ == "__main__":
    raise SystemExit(_main())

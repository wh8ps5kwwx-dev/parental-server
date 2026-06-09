from __future__ import annotations

import sqlite3
import time
from pathlib import Path


DB_PATH = Path(__file__).resolve().parent / "parent_policy.sqlite3"


def connect() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    _migrate(conn)
    return conn


def _migrate(conn: sqlite3.Connection) -> None:
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS blocked_hosts (
            host TEXT PRIMARY KEY NOT NULL,
            pending INTEGER NOT NULL DEFAULT 0,
            created_ms INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS blocked_packages (
            package TEXT PRIMARY KEY NOT NULL,
            pending INTEGER NOT NULL DEFAULT 0,
            created_ms INTEGER NOT NULL
        );

        CREATE TABLE IF NOT EXISTS sync_meta (
            id INTEGER PRIMARY KEY CHECK (id = 1),
            last_pull_ms INTEGER NOT NULL DEFAULT 0,
            last_push_ms INTEGER NOT NULL DEFAULT 0,
            last_revision INTEGER NOT NULL DEFAULT 0
        );

        INSERT OR IGNORE INTO sync_meta (id) VALUES (1);
        """
    )
    conn.commit()


def list_hosts(conn: sqlite3.Connection) -> list[sqlite3.Row]:
    return list(conn.execute("SELECT host, pending FROM blocked_hosts ORDER BY host COLLATE NOCASE"))


def list_packages(conn: sqlite3.Connection) -> list[sqlite3.Row]:
    return list(conn.execute("SELECT package, pending FROM blocked_packages ORDER BY package COLLATE NOCASE"))


def add_host(conn: sqlite3.Connection, host: str) -> None:
    host = host.strip().lower()
    if not host:
        return
    now = int(time.time() * 1000)
    conn.execute(
        "INSERT INTO blocked_hosts(host, pending, created_ms) VALUES (?, 1, ?) "
        "ON CONFLICT(host) DO UPDATE SET pending = 1",
        (host, now),
    )
    conn.commit()


def add_package(conn: sqlite3.Connection, package_name: str) -> None:
    package_name = package_name.strip().lower()
    if not package_name:
        return
    now = int(time.time() * 1000)
    conn.execute(
        "INSERT INTO blocked_packages(package, pending, created_ms) VALUES (?, 1, ?) "
        "ON CONFLICT(package) DO UPDATE SET pending = 1",
        (package_name, now),
    )
    conn.commit()


def pending_hosts(conn: sqlite3.Connection) -> list[str]:
    rows = conn.execute(
        "SELECT host FROM blocked_hosts WHERE pending = 1 ORDER BY host COLLATE NOCASE"
    ).fetchall()
    return [r["host"] for r in rows]


def pending_packages(conn: sqlite3.Connection) -> list[str]:
    rows = conn.execute(
        "SELECT package FROM blocked_packages WHERE pending = 1 ORDER BY package COLLATE NOCASE"
    ).fetchall()
    return [r["package"] for r in rows]


def clear_pending_hosts(conn: sqlite3.Connection, hosts: list[str]) -> None:
    if not hosts:
        return
    conn.executemany(
        "UPDATE blocked_hosts SET pending = 0 WHERE host = ?",
        [(h,) for h in hosts],
    )
    conn.commit()


def clear_pending_packages(conn: sqlite3.Connection, packages: list[str]) -> None:
    if not packages:
        return
    conn.executemany(
        "UPDATE blocked_packages SET pending = 0 WHERE package = ?",
        [(p,) for p in packages],
    )
    conn.commit()


def replace_policy_local(conn: sqlite3.Connection, hosts: list[str], packages: list[str]) -> None:
    now = int(time.time() * 1000)
    conn.execute("DELETE FROM blocked_hosts")
    conn.execute("DELETE FROM blocked_packages")
    conn.executemany(
        "INSERT INTO blocked_hosts(host, pending, created_ms) VALUES (?, 0, ?)",
        [(h.strip().lower(), now) for h in hosts if h.strip()],
    )
    conn.executemany(
        "INSERT INTO blocked_packages(package, pending, created_ms) VALUES (?, 0, ?)",
        [(p.strip().lower(), now) for p in packages if p.strip()],
    )
    conn.commit()


def touch_pull(conn: sqlite3.Connection, revision: int | None) -> None:
    now = int(time.time() * 1000)
    if revision is None:
        conn.execute("UPDATE sync_meta SET last_pull_ms = ? WHERE id = 1", (now,))
    else:
        conn.execute(
            "UPDATE sync_meta SET last_pull_ms = ?, last_revision = ? WHERE id = 1",
            (now, int(revision)),
        )
    conn.commit()


def touch_push(conn: sqlite3.Connection, revision: int | None) -> None:
    now = int(time.time() * 1000)
    if revision is None:
        conn.execute("UPDATE sync_meta SET last_push_ms = ? WHERE id = 1", (now,))
    else:
        conn.execute(
            "UPDATE sync_meta SET last_push_ms = ?, last_revision = ? WHERE id = 1",
            (now, int(revision)),
        )
    conn.commit()

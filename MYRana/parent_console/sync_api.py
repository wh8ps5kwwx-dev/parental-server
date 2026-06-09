from __future__ import annotations

from typing import Any

import requests

import db_store
from config import API_KEY, BASE_URL, CHILD_DEVICE_ID


class PolicyClient:
    def __init__(
        self,
        base_url: str | None = None,
        child_id: str | None = None,
        api_key: str | None = None,
    ) -> None:
        self.base_url = (base_url or BASE_URL).rstrip("/") + "/"
        self.child_id = child_id or CHILD_DEVICE_ID
        self.headers = {"X-API-KEY": api_key or API_KEY}

    def fetch_policy(self) -> dict[str, Any]:
        url = f"{self.base_url}v1/devices/{self.child_id}/policy"
        response = requests.get(url, headers=self.headers, timeout=30)
        response.raise_for_status()
        return response.json()

    def push_policy(self, hosts: list[str], packages: list[str]) -> dict[str, Any]:
        url = f"{self.base_url}v1/devices/{self.child_id}/policy/push"
        payload = {"blockedHosts": hosts, "blockedPackages": packages}
        response = requests.post(url, json=payload, headers=self.headers, timeout=30)
        response.raise_for_status()
        return response.json()


def sync_roundtrip(conn, client: PolicyClient) -> str:
    hosts = db_store.pending_hosts(conn)
    packages = db_store.pending_packages(conn)
    if hosts or packages:
        response = client.push_policy(hosts, packages)
        db_store.clear_pending_hosts(conn, hosts)
        db_store.clear_pending_packages(conn, packages)
        db_store.touch_push(conn, response.get("revision"))

    envelope = client.fetch_policy()
    blocked_hosts = envelope.get("blockedHosts") or []
    blocked_packages = envelope.get("blockedPackages") or []
    db_store.replace_policy_local(conn, list(blocked_hosts), list(blocked_packages))
    db_store.touch_pull(conn, envelope.get("revision"))
    return "تمت المزامنة بنجاح."

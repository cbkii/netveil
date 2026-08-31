#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import re
import time
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MANIFEST = ROOT / "app/src/main/AndroidManifest.xml"
SCHEDULER = ROOT / "app/src/main/java/io/github/cbkii/netveil/country/CountryRefreshScheduler.java"
STORE = ROOT / "app/src/main/java/io/github/cbkii/netveil/country/CountryPackStore.java"
BUNDLED = ROOT / "app/src/main/assets/country-ip-pack.json"
GENERATOR_PATH = Path(__file__).with_name("generate_country_pack.py")
MAX_BYTES = 256 * 1024
EXPECTED_PERMISSIONS = {
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.INTERNET",
    "android.permission.RECEIVE_BOOT_COMPLETED",
}

spec = importlib.util.spec_from_file_location("generate_country_pack", GENERATOR_PATH)
generator = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(generator)


def fail(message: str) -> None:
    raise AssertionError(message)


def update_url() -> str:
    text = STORE.read_text(encoding="utf-8")
    match = re.search(
        r'public\s+static\s+final\s+String\s+UPDATE_URL\s*=\s*"([^"]+)"\s*;',
        text,
        re.S,
    )
    if not match:
        fail("unable to resolve CountryPackStore.UPDATE_URL")
    url = match.group(1)
    if not url.startswith("https://"):
        fail(f"country refresh endpoint is not HTTPS: {url}")
    return url


def check_manifest_scheduler_contract() -> None:
    manifest = MANIFEST.read_text(encoding="utf-8")
    permissions = set(re.findall(r'<uses-permission\s+android:name="([^"]+)"', manifest))
    if permissions != EXPECTED_PERMISSIONS:
        fail(
            "manifest permission contract mismatch: "
            f"expected={sorted(EXPECTED_PERMISSIONS)} actual={sorted(permissions)}"
        )

    scheduler = SCHEDULER.read_text(encoding="utf-8")
    if "setRequiredNetworkType" not in scheduler:
        fail("automatic refresh lost its JobScheduler connectivity constraint")
    for permission in ("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"):
        if permission not in permissions:
            fail(f"connectivity-constrained refresh is missing {permission}")


def fetch_public_pack(url: str) -> dict:
    last_error: Exception | None = None
    for attempt in range(1, 4):
        try:
            request = urllib.request.Request(
                url,
                headers={"Accept": "application/json", "User-Agent": "NetVeil-CI-country-pack/1"},
            )
            with urllib.request.urlopen(request, timeout=15) as response:
                if response.status != 200:
                    fail(f"public country endpoint returned HTTP {response.status}")
                if not response.geturl().startswith("https://"):
                    fail(f"public country endpoint redirected away from HTTPS: {response.geturl()}")
                declared = response.headers.get("Content-Length")
                if declared is not None and int(declared) > MAX_BYTES:
                    fail("public country pack exceeds the 256 KiB contract")
                payload = response.read(MAX_BYTES + 1)
                if len(payload) > MAX_BYTES:
                    fail("public country pack exceeds the 256 KiB contract")
            return json.loads(payload.decode("utf-8"))
        except Exception as exc:  # bounded retry for transient GitHub/CDN failures
            last_error = exc
            if attempt < 3:
                time.sleep(2)
    assert last_error is not None
    raise last_error


def main() -> int:
    check_manifest_scheduler_contract()
    url = update_url()
    remote = fetch_public_pack(url)
    generator.validate_pack(remote)

    bundled = json.loads(BUNDLED.read_text(encoding="utf-8"))
    generator.validate_pack(bundled)
    if remote != bundled:
        fail("anonymous public country endpoint does not match the current bundled pack")

    print(f"country refresh contract: OK ({url})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

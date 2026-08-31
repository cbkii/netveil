#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SELF = Path(__file__).resolve()
HISTORICAL = {ROOT / "CHANGELOG.md"}
SKIP_DIRS = {".git", ".gradle", "build", ".idea"}
TEXT_SUFFIXES = {".java", ".kt", ".kts", ".py", ".md", ".xml", ".yml", ".yaml", ".properties", ".txt"}

FORBIDDEN_LITERALS = (
    "cbkii/" + "media",
    "NETVEIL_" + "DATA_TOKEN",
    "netveil" + "-data",
    "LEGACY_" + "PREFIX",
    "LEGACY_" + "IPV4",
    "LEGACY_" + "GATEWAYS",
    "migrate" + "Legacy",
    "Legacy" + "NetworkInfoHooks",
    "io.github.libxposed:api:" + "101.0.1",
    "minApiVersion=" + "101",
    "targetApiVersion=" + "101",
)

SIBLING_CBKI_GITHUB = re.compile(
    r"https?://(?:raw\.githubusercontent\.com|github\.com)/cbkii/(?!netveil(?:/|$))[^\s)>'\"]+",
    re.IGNORECASE,
)

REQUIRED_DOCS = {
    "README.md",
    "README_ADV.md",
    "docs/DESIGN.md",
    "docs/COMPATIBILITY.md",
    "docs/COUNTRY-DATA.md",
    "docs/DEVICE-TEST-MATRIX.md",
    "docs/RELEASE-READINESS.md",
}
OBSOLETE_DOCS = {
    "docs/V1-RELEASE-READINESS.md",
    "docs/VALIDATION.md",
    "docs/SECOND-PASS-AUDIT.md",
}


def iter_text_files():
    for path in ROOT.rglob("*"):
        if not path.is_file() or path == SELF or path in HISTORICAL:
            continue
        if any(part in SKIP_DIRS for part in path.relative_to(ROOT).parts):
            continue
        if path.suffix.lower() not in TEXT_SUFFIXES:
            continue
        yield path


def fail(message: str, failures: list[str]) -> None:
    failures.append(message)


def main() -> int:
    failures: list[str] = []

    for required in sorted(REQUIRED_DOCS):
        if not (ROOT / required).is_file():
            fail(f"required living document missing: {required}", failures)
    for obsolete in sorted(OBSOLETE_DOCS):
        if (ROOT / obsolete).exists():
            fail(f"obsolete living document still present: {obsolete}", failures)

    for path in iter_text_files():
        rel = path.relative_to(ROOT).as_posix()
        try:
            text = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            continue
        for literal in FORBIDDEN_LITERALS:
            if literal in text:
                fail(f"{rel}: forbidden current-only/standalone marker: {literal}", failures)
        for match in SIBLING_CBKI_GITHUB.finditer(text):
            fail(f"{rel}: sibling cbkii GitHub dependency: {match.group(0)}", failures)

    gradle = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    if 'targetSdk = 36' not in gradle:
        fail("app/build.gradle.kts must target SDK 36", failures)
    if 'compileOnly("io.github.libxposed:api:102.0.0")' not in gradle:
        fail("app/build.gradle.kts must compile against libxposed API 102.0.0", failures)

    module_prop = (ROOT / "app/src/main/resources/META-INF/xposed/module.prop").read_text(
        encoding="utf-8"
    ).splitlines()
    expected_prop = ["minApiVersion=102", "targetApiVersion=102", "staticScope=false"]
    if module_prop != expected_prop:
        fail(f"module.prop must be exactly {expected_prop!r}, got {module_prop!r}", failures)

    config = (ROOT / "app/src/main/java/io/github/cbkii/netveil/config/ConfigKeys.java").read_text(
        encoding="utf-8"
    )
    if "CURRENT_SCHEMA_VERSION = 3" not in config:
        fail("profile configuration schema must be exactly version 3", failures)

    if failures:
        for message in failures:
            print(f"ERROR: {message}", file=sys.stderr)
        print(f"repository policy: {len(failures)} failure(s)", file=sys.stderr)
        return 1

    print("repository policy: standalone/current-only contract passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import ipaddress
import json
from pathlib import Path
import sys
import time
from typing import Iterable
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

COUNTRIES = ("AU", "US", "GB", "ID", "FR")
MAX_OUTPUT_PER_COUNTRY = 18
MIN_OUTPUT_PER_COUNTRY = 8
MAX_HTTP_BYTES = 24 * 1024 * 1024
HTTP_TIMEOUT = 20

RIR_URLS = {
    "apnic": "https://ftp.apnic.net/apnic/stats/apnic/delegated-apnic-extended-latest",
    "arin": "https://ftp.arin.net/pub/stats/arin/delegated-arin-extended-latest",
    "ripencc": "https://ftp.ripe.net/ripe/stats/delegated-ripencc-extended-latest",
}
COUNTRY_RIR = {"AU": "apnic", "ID": "apnic", "US": "arin", "GB": "ripencc", "FR": "ripencc"}
ROUTEVIEWS = "https://api.routeviews.org/asn/{asn}?af=4"
PEERINGDB = "https://www.peeringdb.com/api/net?asn={asn}"
TOR = "https://check.torproject.org/torbulkexitlist"
VPN = "https://raw.githubusercontent.com/X4BNet/lists_vpn/main/output/vpn/ipv4.txt"
PROXY = "https://raw.githubusercontent.com/monosans/proxy-list/main/proxies/all.txt"

# Curated access-network seeds keep classification small and auditable; routing and RIR evidence
# still decide which announced prefixes may enter the generated pack.
PROVIDERS = {
    "AU": [(1221, "Telstra", "high"), (4804, "Optus", "high"),
           (7545, "TPG Telecom", "high"), (4764, "Aussie Broadband", "high")],
    "US": [(7922, "Comcast", "high"), (7018, "AT&T", "high"),
           (701, "Verizon", "high"), (20115, "Charter/Spectrum", "high"),
           (5650, "Frontier", "medium")],
    "GB": [(2856, "BT", "high"), (5607, "Sky Broadband", "high"),
           (5089, "Virgin Media", "high"), (13285, "TalkTalk", "high")],
    "ID": [(7713, "Telkom Indonesia", "high"), (4761, "Indosat", "high"),
           (24203, "XL Axiata", "high"), (17451, "Biznet", "high")],
    "FR": [(3215, "Orange", "high"), (12322, "Free", "high"),
           (15557, "SFR", "high"), (5410, "Bouygues Telecom", "high")],
}


class SourceError(RuntimeError):
    pass


def log(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def fetch(url: str, *, attempts: int = 3, max_bytes: int = MAX_HTTP_BYTES) -> bytes:
    last: Exception | None = None
    for attempt in range(1, attempts + 1):
        try:
            request = Request(url, headers={"User-Agent": "NetVeil-country-generator/1", "Accept": "*/*"})
            with urlopen(request, timeout=HTTP_TIMEOUT) as response:
                if getattr(response, "status", 200) != 200:
                    raise SourceError(f"HTTP {response.status}")
                declared = response.headers.get("Content-Length")
                if declared and int(declared) > max_bytes:
                    raise SourceError(f"response exceeds {max_bytes} bytes")
                data = response.read(max_bytes + 1)
                if len(data) > max_bytes:
                    raise SourceError(f"response exceeds {max_bytes} bytes")
                return data
        except (HTTPError, URLError, TimeoutError, SourceError, OSError) as exc:
            last = exc
            if attempt < attempts:
                log(f"WARN source attempt {attempt}/{attempts} failed for {url}: {exc}")
                time.sleep(attempt)
    raise SourceError(f"unable to fetch {url}: {last}")


def parse_rir(text: str, country: str) -> list[ipaddress.IPv4Network]:
    networks: list[ipaddress.IPv4Network] = []
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        parts = line.split("|")
        if (len(parts) < 7 or parts[1] != country or parts[2] != "ipv4"
                or parts[6].lower() not in {"allocated", "assigned"}):
            continue
        try:
            start = ipaddress.IPv4Address(parts[3])
            count = int(parts[4])
            if count <= 0:
                continue
            end = ipaddress.IPv4Address(int(start) + count - 1)
            networks.extend(ipaddress.summarize_address_range(start, end))
        except (ValueError, ipaddress.AddressValueError):
            continue
    return networks


def in_country(prefix: ipaddress.IPv4Network, allocations: Iterable[ipaddress.IPv4Network]) -> bool:
    return any(prefix.subnet_of(allocation) for allocation in allocations)


def parse_cidrs(text: str) -> list[ipaddress.IPv4Network]:
    out: list[ipaddress.IPv4Network] = []
    for raw in text.splitlines():
        value = raw.strip()
        if not value or value.startswith("#"):
            continue
        try:
            network = ipaddress.ip_network(value, strict=False)
        except ValueError:
            continue
        if isinstance(network, ipaddress.IPv4Network):
            out.append(network)
    return out


def parse_tor(text: str) -> set[ipaddress.IPv4Address]:
    out: set[ipaddress.IPv4Address] = set()
    for raw in text.splitlines():
        try:
            value = ipaddress.ip_address(raw.strip())
        except ValueError:
            continue
        if isinstance(value, ipaddress.IPv4Address):
            out.add(value)
    return out


def parse_proxy(text: str) -> set[ipaddress.IPv4Address]:
    out: set[ipaddress.IPv4Address] = set()
    for raw in text.splitlines():
        value = raw.strip().split("//")[-1].split(":", 1)[0]
        try:
            address = ipaddress.ip_address(value)
        except ValueError:
            continue
        if isinstance(address, ipaddress.IPv4Address):
            out.add(address)
    return out


def usable_public(network: ipaddress.IPv4Network) -> bool:
    if network.prefixlen > 30 or network.num_addresses < 4:
        return False
    return network.is_global


def deterministic_host(country: str, asn: int, network: ipaddress.IPv4Network) -> ipaddress.IPv4Address:
    usable = network.num_addresses - 2
    digest = hashlib.sha256(f"{country}:{asn}:{network}".encode()).digest()
    offset = 1 + int.from_bytes(digest[:8], "big") % usable
    return ipaddress.IPv4Address(int(network.network_address) + offset)


def contains(address: ipaddress.IPv4Address, networks: Iterable[ipaddress.IPv4Network]) -> bool:
    return any(address in network for network in networks)


def peeringdb_access_signal(payload: dict) -> bool:
    rows = payload.get("data") or []
    for row in rows:
        raw = row.get("info_types") or row.get("info_type") or []
        if isinstance(raw, str):
            values = [raw]
        else:
            values = list(raw)
        if any("cable/dsl/isp" in str(value).lower() for value in values):
            return True
    return False


def route_prefixes(asn: int) -> list[ipaddress.IPv4Network]:
    payload = json.loads(fetch(ROUTEVIEWS.format(asn=asn), max_bytes=4 * 1024 * 1024))
    if not isinstance(payload, list):
        raise SourceError(f"unexpected RouteViews response for AS{asn}")
    out: list[ipaddress.IPv4Network] = []
    for raw in payload:
        try:
            network = ipaddress.ip_network(raw, strict=True)
        except ValueError:
            continue
        if isinstance(network, ipaddress.IPv4Network) and usable_public(network):
            out.append(network)
    return sorted(set(out), key=lambda n: (int(n.network_address), n.prefixlen))


def pdb_signal(asn: int) -> bool:
    try:
        payload = json.loads(fetch(PEERINGDB.format(asn=asn), attempts=2, max_bytes=512 * 1024))
        if not isinstance(payload, dict):
            raise SourceError("unexpected PeeringDB response")
        return peeringdb_access_signal(payload)
    except (SourceError, json.JSONDecodeError) as exc:
        log(f"WARN optional PeeringDB lookup failed for AS{asn}: {exc}")
        return False


def exclusion_data() -> tuple[list[ipaddress.IPv4Network], set[ipaddress.IPv4Address], set[ipaddress.IPv4Address], dict[str, bool]]:
    vpn: list[ipaddress.IPv4Network] = []
    tor: set[ipaddress.IPv4Address] = set()
    proxy: set[ipaddress.IPv4Address] = set()
    availability = {"vpn": False, "tor": False, "proxy": False}
    try:
        vpn = parse_cidrs(fetch(VPN, attempts=2, max_bytes=2 * 1024 * 1024).decode("utf-8", "replace"))
        availability["vpn"] = True
    except SourceError as exc:
        log(f"WARN optional VPN exclusion source unavailable: {exc}")
    try:
        tor = parse_tor(fetch(TOR, attempts=2, max_bytes=1024 * 1024).decode("utf-8", "replace"))
        availability["tor"] = True
    except SourceError as exc:
        log(f"WARN optional Tor exclusion source unavailable: {exc}")
    try:
        proxy = parse_proxy(fetch(PROXY, attempts=2, max_bytes=2 * 1024 * 1024).decode("utf-8", "replace"))
        availability["proxy"] = True
    except SourceError as exc:
        log(f"WARN optional proxy exclusion source unavailable: {exc}")
    return vpn, tor, proxy, availability


def round_robin(provider_candidates: list[list[dict]], limit: int) -> list[dict]:
    out: list[dict] = []
    indexes = [0] * len(provider_candidates)
    while len(out) < limit:
        progressed = False
        for i, values in enumerate(provider_candidates):
            if indexes[i] >= len(values):
                continue
            out.append(values[indexes[i]])
            indexes[i] += 1
            progressed = True
            if len(out) >= limit:
                break
        if not progressed:
            break
    return out


def build_country(country: str, allocations: list[ipaddress.IPv4Network],
                  vpn: list[ipaddress.IPv4Network], tor: set[ipaddress.IPv4Address],
                  proxy: set[ipaddress.IPv4Address]) -> list[dict]:
    groups: list[list[dict]] = []
    for asn, name, seed_confidence in PROVIDERS[country]:
        try:
            prefixes = route_prefixes(asn)
        except (SourceError, json.JSONDecodeError) as exc:
            log(f"WARN RouteViews failed for {country} AS{asn}: {exc}")
            groups.append([])
            continue
        access_signal = pdb_signal(asn)
        candidates: list[dict] = []
        seen: set[str] = set()
        for network in prefixes:
            if not in_country(network, allocations):
                continue
            address = deterministic_host(country, asn, network)
            if not address.is_global or str(address) in seen:
                continue
            seen.add(str(address))
            confidence = "high" if seed_confidence == "high" else "medium"
            if seed_confidence == "medium" and access_signal:
                confidence = "high"
            candidates.append({
                "ipv4": str(address),
                "confidence": confidence,
                "known_vpn": contains(address, vpn),
                "known_proxy": address in proxy,
                "known_tor": address in tor,
                "provider": name,
                "asn": asn,
            })
            if len(candidates) >= 8:
                break
        groups.append(candidates)
        time.sleep(1.05)  # RouteViews guest limit is currently one request/second.
    return round_robin(groups, MAX_OUTPUT_PER_COUNTRY)


def load_existing(path: Path) -> dict | None:
    if not path.is_file():
        return None
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    return value if isinstance(value, dict) and value.get("schema") == 1 else None


def validate_pack(pack: dict) -> None:
    if pack.get("schema") != 1:
        raise SourceError("invalid schema")
    generated_at = pack.get("generated_at")
    if not isinstance(generated_at, str) or not generated_at.endswith("Z"):
        raise SourceError("invalid generated_at")
    countries = pack.get("countries") or {}
    if set(countries) != set(COUNTRIES):
        raise SourceError("country set differs from AU/US/GB/ID/FR")
    for country in COUNTRIES:
        rows = countries.get(country)
        if not isinstance(rows, list) or not (MIN_OUTPUT_PER_COUNTRY <= len(rows) <= MAX_OUTPUT_PER_COUNTRY):
            raise SourceError(f"{country} candidate count is outside {MIN_OUTPUT_PER_COUNTRY}..{MAX_OUTPUT_PER_COUNTRY}")
        seen: set[str] = set()
        for row in rows:
            if not isinstance(row, dict):
                raise SourceError(f"{country} contains a non-object candidate")
            try:
                address = ipaddress.ip_address(row.get("ipv4", ""))
            except ValueError as exc:
                raise SourceError(f"{country} contains an invalid candidate") from exc
            if not isinstance(address, ipaddress.IPv4Address) or not address.is_global:
                raise SourceError(f"{country} contains invalid/non-public candidate {address}")
            if str(address) in seen:
                raise SourceError(f"{country} contains duplicate candidate {address}")
            seen.add(str(address))
            if row.get("confidence") not in {"high", "medium", "low"}:
                raise SourceError(f"{country} candidate {address} has invalid confidence")
            for flag in ("known_vpn", "known_proxy", "known_tor"):
                if not isinstance(row.get(flag), bool):
                    raise SourceError(f"{country} candidate {address} has invalid {flag}")
            if not isinstance(row.get("provider"), str) or not isinstance(row.get("asn"), int) or row["asn"] <= 0:
                raise SourceError(f"{country} candidate {address} has invalid provider/ASN provenance")


def generate(output: Path) -> bool:
    existing = load_existing(output)
    rir_cache: dict[str, str] = {}
    allocations: dict[str, list[ipaddress.IPv4Network]] = {}
    for country in COUNTRIES:
        rir = COUNTRY_RIR[country]
        if rir not in rir_cache:
            log(f"[source] RIR {rir}")
            rir_cache[rir] = fetch(RIR_URLS[rir]).decode("utf-8", "replace")
        allocations[country] = parse_rir(rir_cache[rir], country)
        if not allocations[country]:
            raise SourceError(f"no RIR IPv4 allocations found for {country}")

    vpn, tor, proxy, exclusion_availability = exclusion_data()
    countries: dict[str, list[dict]] = {}
    for country in COUNTRIES:
        log(f"[country] {country}")
        values = build_country(country, allocations[country], vpn, tor, proxy)
        if len(values) < MIN_OUTPUT_PER_COUNTRY:
            old = (existing or {}).get("countries", {}).get(country, [])
            if len(old) >= MIN_OUTPUT_PER_COUNTRY:
                log(f"WARN {country} live generation produced {len(values)} candidates; preserving previous valid country data")
                values = old
            else:
                raise SourceError(f"{country} live generation produced only {len(values)} candidates")
        countries[country] = values

    if existing and existing.get("countries") == countries \
            and existing.get("exclusion_sources_available") == exclusion_availability:
        log("No candidate/source-status changes; preserving existing generated_at and file bytes")
        return False

    pack = {
        "schema": 1,
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        "sources": [
            "RIR delegated statistics",
            "RouteViews current-origin prefixes",
            "PeeringDB provider classification",
            "Tor Project bulk exit list",
            "X4BNet lists_vpn",
            "monosans proxy-list",
        ],
        "exclusion_sources_available": exclusion_availability,
        "countries": countries,
    }
    validate_pack(pack)
    output.parent.mkdir(parents=True, exist_ok=True)
    temp = output.with_suffix(output.suffix + ".tmp")
    temp.write_text(json.dumps(pack, indent=2, sort_keys=False) + "\n", encoding="utf-8", newline="\n")
    temp.replace(output)
    log("Generated " + ", ".join(f"{c}={len(countries[c])}" for c in COUNTRIES)
        + " exclusions=" + ",".join(f"{k}:{'ok' if v else 'missing'}" for k, v in exclusion_availability.items()))
    return True


def self_test() -> None:
    sample = ("arin|US|ipv4|8.8.8.0|256|20200101|allocated\n"
              "arin|US|ipv4|9.9.9.0|256|20200101|available\n")
    nets = parse_rir(sample, "US")
    assert nets == [ipaddress.ip_network("8.8.8.0/24")]
    assert peeringdb_access_signal({"data": [{"info_types": ["Cable/DSL/ISP"]}]})
    assert not peeringdb_access_signal({"data": [{"info_types": ["Content"]}]})
    assert parse_proxy("http://8.8.8.8:80\nsocks5://1.1.1.1:1080") == {
        ipaddress.ip_address("8.8.8.8"), ipaddress.ip_address("1.1.1.1")}
    rr = round_robin([[{"x": 1}, {"x": 3}], [{"x": 2}, {"x": 4}]], 4)
    assert [v["x"] for v in rr] == [1, 2, 3, 4]
    a = deterministic_host("AU", 1221, ipaddress.ip_network("1.128.0.0/16"))
    assert a in ipaddress.ip_network("1.128.0.0/16") and a.is_global
    log("country generator self-test: passed")


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate NetVeil's compact country IPv4 candidate pack")
    parser.add_argument("--output", type=Path,
                        default=Path("app/src/main/assets/country-ip-pack.json"))
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    try:
        changed = generate(args.output)
        print("changed" if changed else "unchanged")
        return 0
    except (SourceError, OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

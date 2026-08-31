#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import ipaddress
import json
from pathlib import Path
import tempfile
import unittest

MODULE_PATH = Path(__file__).with_name("generate_country_pack.py")
spec = importlib.util.spec_from_file_location("generate_country_pack", MODULE_PATH)
generator = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(generator)

PUBLIC_FIXTURES = (
    "8.8.8.8", "1.1.1.1", "9.9.9.9", "208.67.222.222",
    "8.8.4.4", "1.0.0.1", "149.112.112.112", "208.67.220.220",
)


def candidate(ip: str, asn: int = 64500) -> dict:
    return {
        "ipv4": ip,
        "confidence": "high",
        "known_vpn": False,
        "known_proxy": False,
        "known_tor": False,
        "provider": "Fixture access network",
        "asn": asn,
    }


def valid_pack() -> dict:
    return {
        "schema": 1,
        "generated_at": "2026-08-31T00:00:00Z",
        "countries": {
            code: [candidate(ip, 64500 + country_i) for ip in PUBLIC_FIXTURES]
            for country_i, code in enumerate(generator.COUNTRIES)
        },
    }


class CountryGeneratorTests(unittest.TestCase):
    def test_rir_country_allocation_uses_only_active_statuses(self):
        text = (
            "arin|US|ipv4|8.8.8.0|256|20200101|allocated\n"
            "arin|US|ipv4|9.9.9.0|256|20200101|available\n"
            "arin|CA|ipv4|1.1.1.0|256|20200101|allocated\n"
        )
        self.assertEqual([ipaddress.ip_network("8.8.8.0/24")], generator.parse_rir(text, "US"))

    def test_proxy_and_tor_parsing(self):
        self.assertEqual(
            {ipaddress.ip_address("8.8.8.8"), ipaddress.ip_address("1.1.1.1")},
            generator.parse_proxy("http://8.8.8.8:80\nsocks5://1.1.1.1:1080\ninvalid"),
        )
        self.assertEqual(
            {ipaddress.ip_address("9.9.9.9")},
            generator.parse_tor("9.9.9.9\ninvalid\n"),
        )

    def test_round_robin_preserves_provider_diversity(self):
        groups = [[{"p": "a1"}, {"p": "a2"}], [{"p": "b1"}, {"p": "b2"}]]
        self.assertEqual(
            ["a1", "b1", "a2", "b2"],
            [row["p"] for row in generator.round_robin(groups, 4)],
        )

    def test_deterministic_host_stays_usable_inside_prefix(self):
        network = ipaddress.ip_network("8.8.8.0/24")
        first = generator.deterministic_host("US", 7922, network)
        second = generator.deterministic_host("US", 7922, network)
        self.assertEqual(first, second)
        self.assertIn(first, network)
        self.assertNotEqual(network.network_address, first)
        self.assertNotEqual(network.broadcast_address, first)

    def test_validate_pack_accepts_complete_pack_and_rejects_reserved_candidate(self):
        pack = valid_pack()
        generator.validate_pack(pack)
        pack["countries"]["AU"][0] = candidate("192.168.1.2")
        with self.assertRaises(generator.SourceError):
            generator.validate_pack(pack)

    def test_validate_pack_rejects_missing_provenance_or_flags(self):
        pack = valid_pack()
        del pack["countries"]["FR"][0]["known_tor"]
        with self.assertRaises(generator.SourceError):
            generator.validate_pack(pack)
        pack = valid_pack()
        pack["countries"]["GB"][0]["asn"] = 0
        with self.assertRaises(generator.SourceError):
            generator.validate_pack(pack)

    def test_bundled_pack_has_exact_mvp_countries_and_valid_candidates(self):
        path = Path("app/src/main/assets/country-ip-pack.json")
        pack = json.loads(path.read_text(encoding="utf-8"))
        generator.validate_pack(pack)
        self.assertEqual(set(generator.COUNTRIES), set(pack["countries"]))
        for country in generator.COUNTRIES:
            self.assertGreaterEqual(len(pack["countries"][country]), generator.MIN_OUTPUT_PER_COUNTRY)
            self.assertLessEqual(len(pack["countries"][country]), generator.MAX_OUTPUT_PER_COUNTRY)

    def test_unchanged_pack_is_read_without_normalisation_loss(self):
        pack = valid_pack()
        pack["exclusion_sources_available"] = {"vpn": True, "tor": True, "proxy": True}
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "pack.json"
            path.write_text(json.dumps(pack), encoding="utf-8")
            loaded = generator.load_existing(path)
            self.assertEqual(pack, loaded)


if __name__ == "__main__":
    unittest.main()

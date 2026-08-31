# Changelog

## Unreleased

- Make `cbkii/netveil` self-contained for its country-data runtime and publication path: the canonical pack is bundled from and anonymously served by this repository.
- Separate country-data provenance from online refresh outcome and reject rollback or same-version conflicting packs.
- Serialise manual and scheduled country refreshes around one fetch/classify/temp/cache transaction.
- Move the module baseline to libxposed API 102 / Vector v2.2+ and target Android API 36 while retaining Android 15 installation support.
- Cut profile storage to strict schema 3. Incompatible NetVeil profile preferences are cleared into a fresh current store rather than converted.
- Remove old independent IPv4/gateway/prefix configuration parsing, helper paths and policy inference.
- Keep route-hidden gateways semantically absent and translate absence only at Android surfaces that require a fixed-width value.
- Consolidate deprecated-but-observable Android `NetworkInfo` VPN masking into one current hook transaction.
- Add an executable repository policy that rejects sibling NetVeil repository coupling and unsupported profile/framework branches.
- Replace stale architecture/validation documents with current design, compatibility, device-test and release-readiness contracts.
- Retain the exact Android permission allow-list: `ACCESS_NETWORK_STATE`, `INTERNET`, `RECEIVE_BOOT_COMPLETED`.

## 0.2.1

Second-pass Android 15/16 and Pixel/AOSP hardening baseline.

- Reworked local-interface masking around one stable physical presentation interface.
- Added package-stable whitelist randomisation with explicit reroll support.
- Require every configured IPv4 to have a different, same-subnet gateway.
- Expanded `LinkProperties` coverage, including aggregate/SystemApi views and Private DNS metadata.
- Added tagged `RouteInfo` consistency rather than globally rewriting arbitrary route objects.
- Expanded `NetworkCapabilities` and legacy `NetworkInfo` VPN masking.
- Made direct `hasTransport(TRANSPORT_VPN)` masking independent of private-field reflection.
- Added classic socket and Android NIO local-address masking.
- Added selected Java/Android system-property masking for DNS, gateway, IP, VPN and proxy metadata.
- Added interface lookup/enumeration filtering while preserving loopback.
- Kept proxy handling metadata-only; NetVeil does not force `NO_PROXY` or alter routing.
- Retained modern libxposed API 101 compatibility and Android 15 minimum SDK.
- Documented native, procfs/sysfs and server-side hardening boundaries.

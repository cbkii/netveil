# Changelog

## Unreleased — v1 hardening

- Add **All scoped apps (Global)** as the first/default profile while keeping Vector/LSPosed scope as the authoritative outer execution gate.
- Add per-app `INHERIT_GLOBAL`, `CUSTOM`, and `DISABLED` policy resolution; pre-Global package profiles preserve upgrade behaviour by defaulting to `CUSTOM`.
- Replace the blank package-name-first screen with a searchable target selector containing Global, saved/custom entries, launchable installed apps, and manual package entry fallback without `QUERY_ALL_PACKAGES`.
- Replace independent IPv4/gateway whitelists with complete `NetworkIdentity` objects so randomisation cannot mix unrelated topology values.
- Add route-hidden identities as the default: arbitrary IPv4 values no longer require a gateway, prefix, or `/0` workaround, and projected `LinkProperties` omit synthetic IPv4 routes.
- Retain Explicit virtual-network mode for coherent IPv4/prefix/gateway topology with same-subnet validation and an explicit `/0` warning.
- Make Global randomisation deterministic per package by deriving the effective seed from the Global base seed plus actual package name; Custom profiles retain their own reroll seed.
- Add conservative migration for legacy independent IP/gateway/prefix profiles: unambiguous one-gateway mappings may become Explicit; ambiguous or unmatched mappings become route-hidden rather than guessed.
- Add persistent field labels, inline identity/DNS validation, resolved-profile preview, invalid-field focus/scroll, and safer target switching so editable selector text cannot silently retarget unsaved form data.
- Keep direct in-app Vector/LSPosed scope management out of the API-101 baseline because the current official `libxposed/service` line is API 102; framework Manager remains the authoritative scope UI.
- Add Global/Custom/Disabled resolution, identity/migration, route-mode, `/0`/`/31`/`/32`, DNS and deterministic-seed tests plus expanded physical UX/device-matrix coverage.
- Enforce one immutable NetVeil profile per app process and explicitly reject `system_server` scope.
- Canonicalise IPv4 configuration numerically and preserve package-stable whitelist randomisation.
- Centralise Wi-Fi/cellular/Ethernet/CLAT/VPN interface classification and remove the hard-coded `wlan0` fallback.
- Replace route/interface side-table rewriting with framework-native projected objects where possible.
- Add coherent `NetworkCapabilities`, `LinkProperties`, `WifiInfo` and legacy `NetworkInfo` getter/string/Parcelable projection.
- Restrict VPN-specific capability sanitisation to objects that are genuinely raw VPN capabilities.
- Collapse CLAT to its underlying physical transport and preserve IPv6 socket address families.
- Suppress explicit `TRANSPORT_VPN` network requests without replacing the genuine active `Network` handle or changing routing.
- Treat DHCP-server metadata as unknown instead of assuming it equals the configured gateway.
- Narrow Java/Android property interception and honour caller-supplied default values.
- Add transactional required-hook health tracking and rollback for incomplete installations.
- Disable unused AGP built-in Kotlin support for the Java-only APK.
- Extend normal CI to assemble and verify an ephemeral-signed release APK in addition to debug, lint and unit tests.
- Add an explicit v1 physical release-readiness matrix for Android 15/16 and Pixel 8/9-class devices.

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

# Compatibility

## Intended baseline

- Android 15 (API 35) and newer
- Google Pixel/AOSP-first qualification
- arm64 primary target
- modern libxposed framework API 101+
- Vector and compatible LSPosed-family frameworks

The application `minSdk` is deliberately 35. This avoids pretending that the v0.2.1 hook matrix has been qualified on older Android networking implementations.

## libxposed API

The module compiles against `io.github.libxposed:api:101.0.1` and advertises API 101. Newer frameworks implementing API 101 compatibility can load it.

API 102 is available upstream, but NetVeil does not need its hot-reload/detach additions yet. Raising the declared target would add a framework requirement without improving the current hook implementation.

## Android 15/16 considerations

Modern applications can obtain network identity from multiple independent paths. v0.2.1 therefore covers both legacy and modern APIs, including `LinkProperties` aggregate getters and Java socket/NIO local-address reads.

Hidden/SystemApi methods are discovered reflectively and are best-effort. Method presence can differ between Android branches.

## Pixel interface naming

The presentation-interface selector explicitly recognises common Pixel/AOSP cellular names beginning with `rmnet` and prioritises `wlan*` when present.

VPN-style names currently include prefixes such as `tun`, `tap`, `ppp`, `wg`, `ipsec`, `xfrm`, `tailscale`, `zt` and `vpn`.

This list is heuristic. Device testing must verify the actual interface inventory on each target environment.

## Coexistence

NetVeil should be tested with other scoped privacy modules such as XPL-EX because multiple modules may hook the same framework methods. Hook ordering can affect which result is ultimately visible.

For qualification, disable overlapping XPL-EX network spoofing for the same target first, establish the NetVeil baseline, then re-enable only the desired complementary XPL-EX functions.

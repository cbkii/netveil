# Compatibility

## Maintained baseline

- Android 15+ installation (`minSdk 35`)
- Android 16 app behaviour target (`targetSdk 36`, `compileSdk 36`)
- Google Pixel/AOSP-first qualification
- arm64 primary device family
- libxposed API `102.0.0`
- `minApiVersion=102`, `targetApiVersion=102`
- Vector v2.2+ or another framework implementing API 102 correctly

NetVeil does not maintain an API-101 module path. API-102 hot reload is not enabled by current module metadata and is outside the present lifecycle contract.

## Android 15 and 16

Supporting Android 15 does not use a separate NetVeil implementation. Version-dependent hidden/deprecated Android methods are discovered or installed as optional hooks and fail open when unavailable. Required current surfaces remain release gates.

The configuration app targets API 36 behaviour while retaining installation on API 35.

## Deprecated Android APIs

Deprecation does not imply that an API is unobservable. `NetworkInfo` remains covered where present because applications can still inspect it on supported releases. This coverage is maintained as part of the current Android hook graph.

## Pixel/AOSP interface handling

Interface classification recognises common Wi-Fi, cellular, Ethernet, CLAT and VPN/tunnel naming. `v4-*` CLAT interfaces are normalised to an underlying physical interface. There is no hard-coded presentation-interface fallback.

Device validation must still record the real interface inventory because OEM/kernel naming can differ.

## Coexistence with other modules

Multiple scoped modules can hook the same network methods. Establish a NetVeil-only baseline first, then enable complementary features. Overlapping network spoofing should be treated as an explicit hook-order compatibility test rather than assumed to compose.

## Configuration compatibility boundary

Only profile schema 3 is current. Incompatible NetVeil profile preferences are replaced with an empty current store when the configuration app is opened; injected processes do not interpret them. Country-data cache and refresh settings are separate from this profile store.

# NetVeil — Advanced documentation

This document describes the current implementation. The end-user starting point is [README.md](README.md).

## Current technical baseline

- package: `dev.ip.netveil`
- Java 17
- `minSdk 35`
- `targetSdk 36`
- `compileSdk 36`
- libxposed `102.0.0`
- `minApiVersion=102`
- `targetApiVersion=102`
- `staticScope=false`
- Vector v2.2+ or another genuine API-102 framework

Vector/LSPosed Manager remains the authority for module scope. NetVeil does not use API-102 hot reload at present; adopting that lifecycle is a separate engineering change.

## Process and profile resolution

Only an already-scoped app process can execute NetVeil. `system_server` is rejected, and one first package claims one immutable effective profile for the process lifetime.

Resolution is:

```text
Off for this app -> no profile hooks
Custom           -> package profile
Use Global       -> Global profile
no per-app mode  -> Global profile
```

The `profiles` SharedPreferences store has a strict current schema (`3`). Injected processes do not interpret absent or mismatched schemas. The configuration app initialises a fresh schema-3 store when necessary; it does not translate incompatible profile formats. Country-data cache and refresh scheduling use separate storage.

Profiles contain complete `NetworkIdentity` values and DNS sets. A route-hidden identity has an IPv4 address and **no gateway value** in the core model. An explicit identity has IPv4, prefix and gateway as one coherent tuple.

## Projection model

NetVeil constructs one process-stable virtual network model and projects covered Android/Java surfaces from it. Randomisation selects a complete identity and complete DNS set before hook installation.

The presentation interface is selected from real non-loopback physical interfaces, preferring Wi-Fi, common cellular, Ethernet and then other physical interfaces. CLAT `v4-*` interfaces are normalised to their underlying physical interface. NetVeil does not invent a `wlan0` fallback.

### Route-hidden mode

Core state contains:

```text
IPv4: configured value
prefix: /32 host representation where a prefix is structurally required
gateway: absent
synthetic IPv4 routes: absent
```

Adapters translate absence only where an Android structure cannot represent it. For example, `DhcpInfo.gateway` receives integer zero; string system-property getters return their caller/default value rather than a fabricated `0.0.0.0` gateway.

### Explicit mode

The configured IPv4/prefix/gateway tuple is validated as a coherent subnet. Projected DHCP, route and property surfaces use that same tuple.

## Covered Android/Java surfaces

Coverage includes, where available on Android 15/16:

- `WifiInfo` IP getter/string/Parcel views;
- `WifiManager.getDhcpInfo()`;
- `ConnectivityManager` network/capability/link-property queries;
- `NetworkCapabilities` getters/string/Parcel projection;
- `LinkProperties` addresses, DNS, routes, interface, proxy and related aggregate getters;
- `NetworkInterface` enumeration/lookups/address views;
- `InterfaceAddress` projection;
- classic socket local-address getters;
- Android/libcore NIO local-address getters;
- Java and selected Android network/proxy property reads;
- selected Settings VPN indicators;
- explicit `TRANSPORT_VPN` network requests.

### Deprecated `NetworkInfo`

`NetworkInfo` is deprecated Android API, but remains observable on supported Android releases. NetVeil therefore maintains one dedicated `NetworkInfoHooks` transaction that:

- suppresses direct VPN-type queries;
- filters VPN entries from all-network-info results;
- presents a raw VPN `NetworkInfo` as the selected physical transport through getters;
- keeps string and Parcel projection coherent;
- rolls back its hook transaction if installation fails.

This is current Android surface coverage, not a configuration compatibility layer.

## VPN, proxy and IPv6 semantics

VPN hiding sanitises app-visible metadata while retaining the genuine active network handle and real traffic path. NetVeil does not disconnect or bypass a VPN.

Proxy hiding changes covered metadata only. It does not replace `ProxySelector`, force `NO_PROXY`, or modify real proxy routing.

IPv6 suppression removes IPv6 values only where absence is semantically valid. It never converts an IPv6 socket into a fabricated IPv4 socket.

## Failure model

Required hook installation is transactional through `HookHealth`. If the required main hook graph is incomplete, installed handles are rolled back. `NetworkInfoHooks` is rolled back as part of the same module initialisation failure path.

Runtime transformations are protective/fail-open: a transformation error returns the original framework result rather than crashing the target process. This preserves availability but means physical qualification must inspect logs for fallbacks, not merely confirm that an app did not crash.

## Country-data architecture

There is one NetVeil-owned canonical pack:

```text
app/src/main/assets/country-ip-pack.json
```

It is bundled into the APK and served anonymously from this repository's public `main` branch. Manual and scheduled refreshes run the same bounded HTTPS fetch/validate/classify/cache-replace path and are serialised process-wide to prevent cache races.

The app distinguishes bundled data, online cache provenance and online refresh outcome. Country data never rewrites a saved profile automatically.

See [docs/COUNTRY-DATA.md](docs/COUNTRY-DATA.md).

## Permissions and package visibility

The exact intended permission set is:

```text
android.permission.ACCESS_NETWORK_STATE
android.permission.INTERNET
android.permission.RECEIVE_BOOT_COMPLETED
```

NetVeil does not request `QUERY_ALL_PACKAGES`. The target picker uses launcher visibility plus saved/manual package names.

## Repository and release policy

`.github/scripts/test_repository_policy.py` is an executable architecture gate. It verifies the current framework/profile baseline, required living docs and repository independence, and rejects NetVeil-owned dependencies on sibling repositories.

The normal Build workflow runs that policy, country-data contracts, JVM tests, unsuppressed release lint, debug/release assembly, ephemeral release signing, APK package/Xposed metadata checks and the exact permission check.

Manual Release reruns repository/country policy and build qualification against the release source before publication. Signing secrets are used only by Manual Release.

## Boundaries

The Java backend does not claim to mask native/kernel/server-side observations including `getifaddrs`, raw netlink/ioctl, direct procfs/sysfs reads, native system properties or remote public-IP/ASN/latency/geolocation inference.

A native/system backend would be a separate component with a different failure domain.

## Authoritative documents

- [Design](docs/DESIGN.md)
- [Compatibility](docs/COMPATIBILITY.md)
- [Country data](docs/COUNTRY-DATA.md)
- [Device test matrix](docs/DEVICE-TEST-MATRIX.md)
- [Release readiness](docs/RELEASE-READINESS.md)

Historical release behaviour belongs in `CHANGELOG.md`; current architecture belongs in the documents above.

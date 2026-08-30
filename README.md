# NetVeil

NetVeil is a deliberately narrow **modern LSPosed/libxposed module for Android 15+** that masks app-visible network identity inside selected application processes.

It is not a general device-spoofing framework and does not reroute traffic. NetVeil is limited to:

- whitelisted local IPv4 identity;
- optional virtual gateway/route identity;
- whitelisted DNS sets;
- VPN visibility hiding;
- proxy visibility hiding;
- optional IPv6 suppression on covered Java/Android metadata surfaces.

NetVeil has no Internet permission, VPN service, root daemon, analytics, advertising SDK, Compose stack or AndroidX UI dependency. It does not change the public/source IP observed by remote servers.

## Scope and profile model

**Vector/LSPosed scope is the outer execution gate.** NetVeil does not inject itself into arbitrary apps and its Global profile does not broaden framework scope.

The configuration UI defaults to:

```text
★ All scoped apps (Global)
```

That profile is the fallback for any app process where Vector/LSPosed has already loaded NetVeil. A package can optionally override it with one of three policies:

```text
INHERIT_GLOBAL   use the Global profile
CUSTOM           use a package-specific profile
DISABLED         install no NetVeil profile hooks for this package
```

Existing package profiles from the pre-Global configuration format are treated as `CUSTOM` until the user explicitly changes their policy, preserving upgrade behaviour.

NetVeil remains on libxposed API `101.0.1` with `minApiVersion=101`, `targetApiVersion=101`, `staticScope=false`, and an empty recommended `scope.list`. The current official `libxposed/service` line is API 102, so NetVeil intentionally does **not** add that service dependency merely to query/manage scope. Vector/LSPosed Manager remains the authoritative external scope manager for this API-101 compatibility baseline.

## Target selector

The configuration screen no longer requires a blank package-name-first workflow. The target selector includes:

- **All scoped apps (Global)**;
- saved/custom package names;
- launchable installed applications visible through normal Android launcher-package queries;
- manual exact package entry as a fallback.

NetVeil declares only a launcher-intent `<queries>` visibility rule for this picker. It does not request `QUERY_ALL_PACKAGES`.

## Network identities

Profiles contain one or more complete `NetworkIdentity` entries rather than independent IP and gateway whitelists.

Each identity has one of two route modes.

### Hide gateway/routes — default

Only an IPv4 value is required:

```text
IPv4: 202.128.115.2
Gateway/routes: hidden
```

This mode is intended for arbitrary API-visible IP identities. It uses a host-only IPv4 representation on covered fixed-width address surfaces, exposes no synthetic IPv4 gateway/default route in projected `LinkProperties`, and avoids requiring a fake subnet relationship.

This is the normal replacement for the old `/0` workaround.

### Explicit virtual network — advanced

Use this only when the target should see a coherent virtual LAN:

```text
IPv4:    192.168.50.20
Prefix:  /24
Gateway: 192.168.50.1
```

The gateway must:

- be a valid IPv4 literal;
- differ from the client IPv4;
- belong to the configured prefix.

`/0` remains valid CIDR and is accepted, but the UI warns that it covers the entire IPv4 address space and is unusual for a local network.

## Randomisation

Randomisation always selects a **whole NetworkIdentity** and a **whole DNS set**. It never independently mixes an IP with an unrelated gateway.

For a Custom package profile, the saved selection seed is package-local and stable until **Reroll**.

For Global, NetVeil derives the effective seed from:

```text
Global base seed + actual package name
```

This means:

- all processes belonging to one package receive the same stable selection;
- different inheriting packages can receive different whitelist-derived selections;
- **Reroll Global** changes the base seed and deterministically rerolls all inheriting packages after their processes are restarted.

## Migration from older profiles

The configuration schema is versioned. Older profiles containing independent IPv4/gateway whitelists and one prefix remain readable.

Migration is conservative:

- if a legacy IPv4 has exactly one compatible gateway, it becomes an Explicit identity;
- if the mapping is absent or ambiguous, it becomes a route-hidden identity instead of guessing;
- old preference fields remain readable until the profile is explicitly reset/removed, allowing rollback/debug inspection.

Saving through the new UI writes the structured identity format.

## Configuration UX

The configuration screen uses platform Android widgets only. It provides:

- persistent field labels and helper text;
- inline identity validation;
- an explicit `/0` warning;
- parsed DNS-set feedback;
- a resolved-profile preview;
- Global/custom/disabled policy visibility;
- Save-time focus/scroll to invalid inputs;
- Toasts only for transient operations such as Save, Reroll and Delete.

If the editable target text is changed directly, Save first loads that target and requires a second explicit Save after review. This prevents unsaved Global/custom form contents from being accidentally written under a newly typed package.

## v1 network-projection architecture

NetVeil presents one coherent virtual network model rather than independently rewriting unrelated getters:

- exactly one primary package/profile can claim one app process;
- `system_server` scope is explicitly rejected;
- framework-native projected `LinkProperties`, `RouteInfo`, `InterfaceAddress`, `NetworkCapabilities`, `WifiInfo` and legacy `NetworkInfo` objects are used where practical;
- getter, framework `toString()` and covered Parcelable views are derived from the same projected model;
- CLAT `v4-*` interfaces are collapsed to the underlying physical transport;
- explicit VPN network requests are suppressed without replacing the genuine active `Network` handle or changing actual traffic routing.

The authoritative stable-release gate is documented in [`docs/V1-RELEASE-READINESS.md`](docs/V1-RELEASE-READINESS.md).

## Covered Java/Android surfaces

### IPv4 / DHCP

- `WifiInfo.getIpAddress()`
- `WifiInfo.toString()` and covered `writeToParcel()` output
- `WifiManager.getDhcpInfo()`
- `LinkProperties.getLinkAddresses()` and aggregate/SystemApi address views where present
- `NetworkInterface.getInetAddresses()` on the selected presentation interface
- synthetic `InterfaceAddress` address/prefix/broadcast values for that interface
- classic socket local IPv4 getters
- Android libcore NIO channel local-address getters when present

IPv6 socket address families are preserved. NetVeil does not turn an IPv6 socket into a fake IPv4 socket merely because IPv6 metadata suppression is enabled.

### Gateway / routes

In Explicit route mode, NetVeil projects:

- DHCP gateway/netmask;
- `LinkProperties` IPv4 connected/default routes;
- framework-native synthetic `RouteInfo` objects;
- selected Android `SystemProperties` gateway metadata.

In Hidden mode, object-based IPv4 gateway/default-route metadata is omitted. Legacy fixed-width fields use neutral zero/host-only values where an absence cannot be represented directly. DHCP-server metadata remains unknown rather than being invented as the gateway.

### DNS

- `LinkProperties.getDnsServers()`
- Private-DNS name/activity/validated-server visibility
- DHCP DNS fields
- selected Android `SystemProperties` DNS properties

### VPN visibility

- `NetworkCapabilities` VPN transport/capability/transport-info views
- owner/admin/underlying-network VPN metadata where mutable equivalents exist
- `NetworkCapabilities.toString()` and covered Parcelable output
- `ConnectivityManager.getAllNetworks()` VPN-handle filtering while retaining the genuine active handle
- legacy `NetworkInfo` VPN queries, getters, strings and Parcel views
- VPN-style `NetworkInterface` enumeration/lookups
- common always-on/lockdown VPN settings reads
- explicit `NetworkRequest` registrations/requests for `TRANSPORT_VPN`

VPN-specific `NetworkCapabilities` changes are applied only when the **origin** capability object genuinely has `TRANSPORT_VPN`.

### Proxy visibility

- `LinkProperties.getHttpProxy()`
- `ConnectivityManager.getDefaultProxy()`
- common Java proxy system properties
- selected Android proxy system properties

NetVeil does not replace `ProxySelector`, force `NO_PROXY`, or otherwise change connectivity.

## Presentation interface and Pixel/CLAT behaviour

NetVeil selects one physical presentation interface rather than making every interface claim the same virtual IPv4. Classification distinguishes loopback, Wi-Fi, common Android cellular interfaces, Ethernet, CLAT and VPN/tunnel-style names.

For Android 464XLAT, a `v4-*` CLAT interface is normalised to its underlying physical interface before presentation-transport classification. There is no hard-coded `wlan0` fallback; unresolved presentation state fails open instead of inventing Wi-Fi topology.

## Process and hook lifecycle

NetVeil is app-process-only:

- `system_server` is rejected;
- only the first package loaded into a process may claim the NetVeil identity;
- later package loads cannot stack a second profile;
- required hook installation is transactional and rolls back if incomplete;
- runtime transformations use protective fail-open behaviour and hook-health diagnostics.

## Important boundary: this does not change the public IP

NetVeil changes only what covered Java/Android APIs report to the scoped app. It does **not** alter Linux routing, modify the real interface configuration, provide a VPN/proxy, rewrite packets, change DNS traffic, or change the public/source IP seen remotely.

## Known hard boundaries

The Java backend does not claim to intercept:

- native `getifaddrs()`;
- raw netlink;
- direct `ioctl` interface enumeration;
- direct `/proc/net/*` or `/sys/class/net/*` reads;
- native system-property APIs;
- server-side public-IP, latency or geolocation inference.

A future native/system backend, if added, should remain optional and separately qualified.

## Build and CI

Requirements:

- JDK 17
- Android SDK 36
- Android Gradle Plugin 9.3.1
- Gradle 9.5.0+

The project is Java-only and disables AGP's built-in Kotlin support. Normal CI runs JVM tests, unsuppressed `lintRelease`, debug assembly and an ephemeral-signed release build. It verifies the release APK signature, package `dev.ip.netveil`, modern Xposed metadata, absence of `android.permission.INTERNET`, and artifact hashes.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
```

## Use

1. Install/open NetVeil.
2. Configure **All scoped apps (Global)**, usually using route-hidden identities.
3. Optionally select an installed/saved app and choose **Inherit Global**, **Custom override**, or **Disable NetVeil for this app**.
4. Enable NetVeil in Vector/LSPosed and select the actual target apps in framework scope.
5. Force-stop/restart each affected target process after profile/scope changes or Reroll.

DNS sets use one line per selectable set:

```text
1.1.1.1, 1.0.0.1
9.9.9.9, 149.112.112.112
```

## Stable-release gate

CI success is not physical compatibility evidence. Before a stable release, execute [`docs/V1-RELEASE-READINESS.md`](docs/V1-RELEASE-READINESS.md) and [`docs/DEVICE-TEST-MATRIX.md`](docs/DEVICE-TEST-MATRIX.md), including Android 15/16, Pixel 8/9-class devices, Wi-Fi/cellular/VPN/CLAT states, targetSdk 35/36 probes, multi-process targets, Global/custom/disabled resolution, migration, Parcel consistency, Vector logs and proof that NetVeil does not alter real public egress.

## Licence

Apache-2.0.

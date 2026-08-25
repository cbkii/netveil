# NetVeil

NetVeil is a deliberately narrow **modern LSPosed/libxposed module for Android 15+** that masks app-visible network identity on a per-app basis.

It is intentionally not a general device-spoofing framework. NetVeil is limited to:

- whitelisted local IPv4 identity;
- whitelisted gateway identity;
- whitelisted DNS sets;
- VPN visibility hiding;
- proxy visibility hiding;
- optional IPv6 suppression on covered Java/Android metadata surfaces.

NetVeil has no Internet permission, VPN service, root daemon, analytics, advertising SDK, Compose stack or AndroidX UI dependency. It does not change routing or the public IP observed by remote servers.

## v1 hardening status

The current development branch is the v1 release-hardening candidate. Its primary qualification environment is Google Pixel/AOSP Android 15 and Android 16 with a modern libxposed-compatible framework such as Vector.

The v1 architecture presents one coherent virtual network model rather than independently rewriting unrelated getters:

- exactly one primary package/profile can claim one app process;
- `system_server` scope is explicitly rejected;
- framework-native projected `LinkProperties`, `RouteInfo`, `InterfaceAddress`, `NetworkCapabilities`, `WifiInfo` and legacy `NetworkInfo` objects are used where practical;
- getter, framework `toString()` and covered Parcelable views are derived from the same projected model;
- CLAT `v4-*` interfaces are collapsed to the underlying physical transport;
- explicit VPN network requests are suppressed without replacing the genuine active `Network` handle or changing actual traffic routing.

The authoritative stable-release gate is documented in [`docs/V1-RELEASE-READINESS.md`](docs/V1-RELEASE-READINESS.md).

## Configuration model

Each package has an independent profile containing:

- one or more allowed IPv4 addresses;
- one or more allowed gateways;
- one or more DNS sets;
- IPv4 prefix length;
- randomisation toggle;
- VPN hiding toggle;
- proxy hiding toggle;
- IPv6 suppression toggle.

When randomisation is enabled, NetVeil chooses **only from user-entered whitelist values**. A per-package selection seed makes the chosen IPv4, gateway and DNS set stable across all processes until the user explicitly presses **Reroll**.

IPv4 text is canonicalised numerically before identity comparison or deduplication. Every selectable IPv4 must have at least one different, same-subnet gateway, so fixed and rerolled profiles cannot resolve to an invalid client/gateway pair.

## Covered Java/Android surfaces

### IPv4 / DHCP

- `WifiInfo.getIpAddress()`
- `WifiInfo.toString()` and covered `writeToParcel()` output
- `WifiManager.getDhcpInfo()`
- `LinkProperties.getLinkAddresses()`
- hidden/SystemApi `LinkProperties.getAllLinkAddresses()` where present
- hidden/SystemApi `LinkProperties.getAddresses()` / `getAllAddresses()` where present
- `NetworkInterface.getInetAddresses()` on the selected presentation interface
- synthetic `InterfaceAddress` address/prefix/broadcast values for that interface
- classic socket local IPv4 getters
- Android libcore NIO channel local-address getters when present

IPv6 socket address families are preserved. NetVeil does not turn an IPv6 socket into a fake IPv4 socket merely because IPv6 metadata suppression is enabled.

### Gateway / routes

- DHCP gateway
- `LinkProperties.getRoutes()`
- hidden/SystemApi `LinkProperties.getAllRoutes()` where present
- framework-native synthetic IPv4 connected/default `RouteInfo` objects
- selected Android `SystemProperties` DHCP/network gateway properties

DHCP-server metadata is presented as unknown instead of assuming the DHCP server is identical to the configured gateway.

### DNS

- `LinkProperties.getDnsServers()`
- Private-DNS name/activity/validated-server visibility
- DHCP DNS fields
- selected Android `SystemProperties` DNS properties

NetVeil presents the configured DNS set as conventional resolver addresses. Private-DNS metadata is hidden rather than leaking the real configured hostname/server state.

### VPN visibility

- `NetworkCapabilities.hasTransport(TRANSPORT_VPN)`
- `NetworkCapabilities.getTransportTypes()`
- `NET_CAPABILITY_NOT_VPN`
- `VpnTransportInfo` exposure where present
- owner/admin/underlying-network VPN metadata where the platform exposes mutable equivalents
- `NetworkCapabilities.toString()` and covered Parcelable output
- `ConnectivityManager.getAllNetworks()` VPN-handle filtering while retaining the genuine active handle
- legacy `ConnectivityManager` VPN `NetworkInfo` queries
- legacy raw-VPN `NetworkInfo` type/name/extra-info/string/Parcelable views
- VPN-style `NetworkInterface` enumeration and lookup filtering
- VPN interface address filtering
- common always-on/lockdown VPN settings reads
- explicit `NetworkRequest` registrations/requests for `TRANSPORT_VPN`

VPN-specific `NetworkCapabilities` changes are applied only when the **origin** capability object genuinely has `TRANSPORT_VPN`; unrelated physical capability objects are not globally stripped of ownership or administrator metadata.

### Proxy visibility

- `LinkProperties.getHttpProxy()`
- `ConnectivityManager.getDefaultProxy()`
- common Java proxy system properties
- selected Android proxy system properties

NetVeil deliberately does **not** replace `ProxySelector`, force `NO_PROXY`, or otherwise change real connectivity.

## Presentation interface and Pixel/CLAT behaviour

NetVeil selects one physical presentation interface rather than making every interface claim the same virtual IPv4. Classification distinguishes loopback, Wi-Fi, common Android cellular interfaces, Ethernet, CLAT and VPN/tunnel-style names.

For Android 464XLAT, a `v4-*` CLAT interface is normalised to its underlying physical interface before presentation-transport classification. This avoids an IPv6-only cellular connection being incorrectly presented as Wi-Fi simply because the translated IPv4 lives on `v4-rmnet*`.

There is no hard-coded `wlan0` fallback. If a credible presentation interface cannot be resolved, the affected transformation fails open rather than inventing a Wi-Fi topology.

## Process and hook lifecycle

NetVeil is app-process-only:

- `system_server` is rejected;
- only the first package loaded into a process may claim the NetVeil identity;
- later packages loaded into the same process cannot stack another profile;
- required hook installation is transactional and rolls back if incomplete;
- runtime hook transformations use protective fail-open behaviour and are counted in hook-health diagnostics.

This prevents shared-process or `createPackageContext(..., CONTEXT_INCLUDE_CODE)` scenarios from mixing two different virtual identities into the same ART process.

## Important boundary: this does not change the public IP

NetVeil changes only what covered Java/Android APIs report to the scoped app.

It does **not**:

- alter Linux routing;
- modify the real interface configuration;
- provide a VPN or proxy;
- rewrite packets;
- change DNS traffic itself;
- change the public/source IP observed by remote servers.

If a remote service reports the connection source address, it still sees the real ISP/VPN/proxy exit address.

## Known hard boundaries

The v1 Java backend does not claim to intercept raw native/kernel observations such as:

- native `getifaddrs()`;
- raw netlink;
- direct `ioctl` interface enumeration;
- direct `/proc/net/*` reads;
- direct `/sys/class/net/*` reads;
- native system-property APIs;
- server-side public-IP, latency or geolocation inference.

Covered Parcelable hardening now includes the principal Java/framework objects NetVeil virtualises (`WifiInfo`, `NetworkCapabilities`, `LinkProperties` and legacy `NetworkInfo`). This does **not** make arbitrary private framework state or native/kernel observations virtual.

A future native/system backend, if ever added, should remain optional and separately qualified rather than silently expanding the failure domain of the default Java module.

## Build and CI

Requirements:

- JDK 17
- Android SDK 36
- Android Gradle Plugin 9.3.1
- Gradle 9.5.0+

The module compiles against libxposed API `101.0.1` and declares `minApiVersion=101` / `targetApiVersion=101`. API 101 remains intentional because NetVeil does not require API-102-only hot-reload/detach functionality for v1.

The project is Java-only and disables AGP's built-in Kotlin support. The source tree intentionally does not vendor the Gradle wrapper JAR; CI provisions Gradle directly.

Normal CI runs JVM tests, unsuppressed `lintRelease`, a debug build and an **ephemeral-signed release build**. It verifies the release APK signature, package `dev.ip.netveil`, modern Xposed metadata, absence of `android.permission.INTERNET`, and artifact hashes. Production signing secrets are used only by Manual Release.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
```

## Use

1. Build/install the APK.
2. Open **NetVeil**.
3. Enter the exact target package name.
4. Enter allowed IPv4 values, gateways and DNS sets.
5. Choose the prefix length.
6. Enable the desired VPN/proxy/IPv6 options.
7. Save the profile.
8. Enable NetVeil in Vector/LSPosed and scope it to the same target package.
9. Force-stop every process belonging to the target app and restart it.
10. Use **Reroll** when a new whitelist-derived identity is required, then force-stop/restart the target processes again.

DNS input uses one selectable set per line:

```text
1.1.1.1, 1.0.0.1
9.9.9.9, 149.112.112.112
```

## Stable-release gate

Do not treat CI success alone as physical compatibility evidence. Before publishing v1.0.0, execute the matrix in [`docs/V1-RELEASE-READINESS.md`](docs/V1-RELEASE-READINESS.md), including Android 15/16, Pixel 8/9-class devices, Wi-Fi/cellular/VPN/CLAT states, targetSdk 35/36 probes, multi-process targets, Parcel consistency, Vector logs and proof that NetVeil does not alter real public egress.

## Licence

Apache-2.0.

# NetVeil

NetVeil is a deliberately narrow **modern LSPosed/libxposed module for Android 15+** that masks API-visible network identity on a per-app basis.

It is intentionally not a general device-spoofing framework. NetVeil is limited to:

- whitelisted local IPv4 identity;
- whitelisted gateway identity;
- whitelisted DNS sets;
- VPN visibility hiding;
- proxy visibility hiding;
- optional IPv6 suppression on covered Java/Android APIs.

NetVeil has no Internet permission, VPN service, root daemon, analytics, advertising SDK, Compose stack or AndroidX UI dependency.

## v0.2.1 status

v0.2.1 is a **development/qualification baseline**, rebuilt after a second-pass review of Android 15/16 networking surfaces and recently maintained LSPosed/Xposed VPN-hiding modules.

The intended primary qualification environment is Google Pixel/AOSP Android 15 and Android 16 with a modern libxposed-compatible framework such as Vector.

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

When randomisation is enabled, NetVeil chooses **only from user-entered whitelist values**.

### Stable randomisation

v0.1 selected random values independently when each target process started. That could make a multi-process application expose different identities simultaneously.

v0.2.1 stores one per-package selection seed. All processes deterministically resolve the same profile until the user presses **Reroll**. Rerolling requires all target-app processes to be restarted before the new identity is authoritative everywhere.

Gateways are selected only when they are in the configured subnet of the selected IPv4 and are not equal to the selected client address. The configuration UI rejects enabled profiles unless every allowed IPv4 has at least one compatible gateway, so every fixed or rerolled whitelist choice remains valid.

## Covered Java/Android surfaces

### IPv4 / DHCP

- `WifiInfo.getIpAddress()`
- `WifiManager.getDhcpInfo()`
- `LinkProperties.getLinkAddresses()`
- hidden/SystemApi `LinkProperties.getAllLinkAddresses()` where present
- hidden/SystemApi `LinkProperties.getAddresses()` / `getAllAddresses()` where present
- `NetworkInterface.getInetAddresses()` on the selected presentation interface
- `InterfaceAddress` address, prefix and broadcast for the selected presentation interface
- classic socket local-address getters
- Android libcore NIO channel local-address getters when present

### Gateway / routes

- DHCP gateway
- `LinkProperties.getRoutes()`
- hidden/SystemApi `LinkProperties.getAllRoutes()` where present
- tagged `RouteInfo` destination/gateway/interface/default-route/matching/string views
- selected Android `SystemProperties` DHCP/network gateway properties

Route masking is deliberately applied only to route objects obtained through virtualised `LinkProperties` results, rather than globally rewriting every `RouteInfo` in the target process.

### DNS

- `LinkProperties.getDnsServers()`
- Private-DNS name/activity/validated-server visibility
- DHCP DNS fields
- selected Android `SystemProperties` DNS properties

NetVeil presents the configured DNS set as conventional resolver addresses. Private-DNS metadata is hidden rather than leaking the real configured hostname/server state.

### VPN visibility

- `NetworkCapabilities.hasTransport(TRANSPORT_VPN)`
- `NetworkCapabilities.getTransportTypes()`
- `NET_CAPABILITY_NOT_VPN` queries/listing
- owner UID and administrator UID metadata where exposed
- underlying-network metadata where exposed
- legacy `ConnectivityManager` VPN `NetworkInfo` queries
- legacy `NetworkInfo` VPN type/name/extra-info views
- VPN-style `NetworkInterface` enumeration and lookup filtering
- VPN interface address filtering
- common always-on/lockdown VPN settings reads
- VPN-style Android system-property values where encountered

### Proxy visibility

- `LinkProperties.getHttpProxy()`
- `ConnectivityManager.getDefaultProxy()`
- common Java proxy system properties
- common Android proxy system properties

NetVeil deliberately does **not** replace `ProxySelector` or otherwise force a non-proxy route, because that would change real connectivity rather than merely mask observable metadata.

## Presentation interface

v0.2.1 no longer rewrites every non-VPN interface with the same IPv4. It selects one physical presentation interface, preferring:

1. `wlan*`;
2. Pixel/common cellular names such as `rmnet*`;
3. `eth*`;
4. another non-loopback, non-VPN interface.

Loopback remains loopback. Non-presentation physical interfaces are filtered from the covered Java enumeration/lookup paths instead of all claiming the same virtual address.

## Important boundary: this does not change the public IP

NetVeil changes only what hooked Java/Android APIs report to the scoped app.

It does **not**:

- alter Linux routing;
- modify the real interface configuration;
- provide a VPN or proxy;
- rewrite packets;
- change the public/source IP observed by remote servers.

If a remote service reports the connection source address, it still sees the real ISP/VPN/proxy exit address.

## Known hard boundaries

A pure app-process Java Xposed module cannot comprehensively hide all network state from determined native code. v0.2.1 does not claim to intercept:

- native `getifaddrs()`;
- raw netlink;
- direct `ioctl` interface enumeration;
- direct `/proc/net/*` reads;
- direct `/sys/class/net/*` reads;
- native system-property APIs;
- server-side public-IP checks.

Parcelable round-trips are also an explicit hardening boundary. Android framework classes including `LinkProperties`, `WifiInfo`, and `NetworkCapabilities` can serialise backing state directly rather than necessarily calling the getters NetVeil hooks. NetVeil does not currently mutate those private backing fields or reproduce Android's private Parcel layouts because doing so app-side would be brittle across Android releases.

A future native/system-side backend should be optional and separately qualified rather than bloating the default module.

## Build

Requirements:

- JDK 17
- Android SDK 36
- Android Gradle Plugin 9.3.1
- Gradle 9.5.0+

The module compiles against libxposed API `101.0.1` and declares `minApiVersion=101` / `targetApiVersion=101`. API 101 remains intentional because NetVeil does not currently require API-102-only hot-reload/detach functionality.

The source bundle intentionally does not vendor the Gradle wrapper JAR. CI provisions Gradle directly.

```bash
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

## Use

1. Build and install the APK.
2. Open **NetVeil**.
3. Enter the exact target package name.
4. Enter allowed IPv4 values, gateways and DNS sets.
5. Choose the prefix length.
6. Enable the desired VPN/proxy/IPv6 masking options.
7. Save the profile.
8. Enable NetVeil in LSPosed/Vector and scope it to the same target package.
9. Force-stop all processes belonging to the target app and restart it.
10. Use **Reroll** when you want a new whitelist-derived identity, then force-stop/restart all target processes again.

DNS input uses one selectable set per line:

```text
1.1.1.1, 1.0.0.1
9.9.9.9, 149.112.112.112
```

## Before v1.0.0

Do not publish v0.2.1 as a security guarantee. Before a stable release:

- obtain a clean CI build of the exact repository head;
- run the Android 15/16 device test matrix in `docs/DEVICE-TEST-MATRIX.md`;
- inspect Vector/LSPosed logs for missing or failing optional hooks;
- test multi-process targets;
- test Wi-Fi, cellular and active-VPN states;
- verify NetVeil never changes real network routing/public egress;
- decide whether native detection remains explicitly out of scope or becomes a separate optional backend.

## Licence

Apache-2.0.

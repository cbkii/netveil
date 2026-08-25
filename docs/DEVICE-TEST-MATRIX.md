# Device test matrix

Use this checklist as the hands-on execution sheet for the authoritative stable-release contract in [`V1-RELEASE-READINESS.md`](V1-RELEASE-READINESS.md). Record the exact APK hash, source commit, framework version and Android build for every run.

## Baseline state

- [ ] Confirm NetVeil is enabled only for the intended target package.
- [ ] Confirm overlapping network spoofing from other scoped modules is disabled for the initial baseline.
- [ ] Record Android build fingerprint/security patch.
- [ ] Record Vector/LSPosed version and libxposed API compatibility.
- [ ] Record actual interface inventory before testing.
- [ ] Record NetVeil source SHA and APK SHA-256.

## Fixed profile

Configure one IPv4, gateway and DNS set.

- [ ] `WifiInfo` getter/string/Parcel views agree on the configured IPv4 when applicable.
- [ ] `DhcpInfo` reports configured IPv4/gateway/DNS and derived mask; DHCP server remains unknown.
- [ ] `LinkProperties` getter/string/Parcel views agree on configured IPv4/DNS/routes/interface/proxy state.
- [ ] route objects expose the virtual subnet/default gateway coherently.
- [ ] selected presentation `NetworkInterface` reports virtual IPv4.
- [ ] lookup by the fake IPv4 returns the selected presentation interface.
- [ ] loopback remains loopback.
- [ ] unrelated physical interfaces do not all inherit the virtual IPv4.
- [ ] classic/NIO local IPv4 socket getters report the virtual IPv4.
- [ ] IPv6 sockets retain an IPv6 address family.

## Stable randomisation

Configure at least three IPv4s, compatible gateways and three DNS sets.

- [ ] all processes of the target app report the same selected profile.
- [ ] restarting a process without rerolling keeps the same profile.
- [ ] Reroll changes only to values present in the configured whitelists.
- [ ] selected gateway remains in the configured subnet and differs from the client address.
- [ ] every target-app process is restarted after reroll.
- [ ] a later package loaded into an already-claimed process cannot install a second profile.

## VPN hiding

Test once without a VPN and once with the normal VPN active.

- [ ] `hasTransport(TRANSPORT_VPN)` is false in the scoped app only for raw VPN capability objects.
- [ ] VPN transport is absent from projected `getTransportTypes()`.
- [ ] `NET_CAPABILITY_NOT_VPN` is present in the projected VPN capability object.
- [ ] `VpnTransportInfo`, VPN owner/admin metadata and underlying-network metadata are hidden where those APIs exist.
- [ ] non-VPN `NetworkCapabilities` objects retain their original ownership/admin metadata.
- [ ] active raw-VPN legacy `NetworkInfo` getter/string/Parcel views present the physical transport.
- [ ] direct legacy VPN network queries return no VPN entry.
- [ ] VPN-style interfaces are absent from covered Java enumeration/lookups.
- [ ] explicit `NetworkRequest(TRANSPORT_VPN)` callback/request registration does not disclose a VPN.
- [ ] unregistering a callback/PendingIntent whose VPN request was suppressed is harmless.
- [ ] always-on/lockdown VPN settings are not exposed through covered Settings getters.
- [ ] actual traffic still traverses the real VPN when enabled.
- [ ] remote public-IP test reports the real VPN exit, proving NetVeil did not reroute traffic.

## Proxy hiding

- [ ] Java proxy system properties are hidden and two-argument calls return the caller-supplied default.
- [ ] selected Android proxy properties are hidden with correct default semantics.
- [ ] `LinkProperties.getHttpProxy()` is hidden.
- [ ] `ConnectivityManager.getDefaultProxy()` is hidden.
- [ ] actual proxy/VPN connectivity continues unchanged.

## Wi-Fi / cellular / CLAT

- [ ] test while Wi-Fi is primary.
- [ ] test while cellular is primary.
- [ ] test transition Wi-Fi -> cellular with a full target-app restart.
- [ ] verify presentation interface selection is plausible on the Pixel build.
- [ ] on IPv6-only cellular/464XLAT, verify `v4-rmnet*` is collapsed to the underlying cellular transport rather than presented as Wi-Fi.
- [ ] where CLAT exists on Wi-Fi, verify `v4-wlan*` resolves to Wi-Fi.

## IPv6

- [ ] with IPv6 suppression enabled, collection/metadata surfaces where absence is valid do not expose real IPv6 addresses/prefixes.
- [ ] with suppression disabled, IPv6 passthrough does not break IPv4 spoofing.
- [ ] IPv6 socket local-address getters never return a fabricated IPv4 socket identity.

## Multi-module coexistence

- [ ] establish NetVeil-only baseline.
- [ ] enable desired non-network features in another scoped privacy module.
- [ ] inspect target output again for hook-order regressions.
- [ ] deliberately enable overlapping network hooks and record which module owns any differing return value.
- [ ] inspect Vector/LSPosed logs for NetVeil required-hook failures or protective fallbacks.

## CI/framework-object consistency

- [ ] debug and ephemeral-signed release APKs build successfully from the same source head.
- [ ] release APK signature/package/Xposed metadata/permission checks pass.
- [ ] `WifiInfo`, `NetworkCapabilities`, `LinkProperties` and legacy `NetworkInfo` Parcel round-trips agree with their getter/string projections.
- [ ] no custom NetVeil-only `toString()` format is observable for projected Android framework objects.

## Expected native/server disclosures

These remain outside the v1 Java backend and are expected to expose real state where the platform permits them:

- [ ] native `getifaddrs()`;
- [ ] raw netlink/ioctl interface or route queries;
- [ ] direct `/proc/net/*` reads;
- [ ] direct `/sys/class/net/*` reads;
- [ ] native system-property APIs;
- [ ] server-side public-IP, latency or geolocation inference.

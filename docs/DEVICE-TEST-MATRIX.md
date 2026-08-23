# Device test matrix

Use this matrix before publishing a stable release. Record the exact APK hash, source commit, framework version and Android build for every run.

## Baseline state

- [ ] Confirm NetVeil is enabled only for the intended target package.
- [ ] Confirm overlapping XPL-EX network spoofing is disabled for the initial baseline.
- [ ] Record Android build fingerprint/security patch.
- [ ] Record Vector/LSPosed version and libxposed API compatibility.
- [ ] Record actual interface inventory before testing.

## Fixed profile

Configure one IPv4, gateway and DNS set.

- [ ] `WifiInfo` reports the configured IPv4 when applicable.
- [ ] `DhcpInfo` reports configured IPv4/gateway/DNS and derived mask.
- [ ] `LinkProperties` reports configured IPv4/DNS and no real Private-DNS hostname.
- [ ] route getters report virtual subnet/default gateway coherently.
- [ ] selected presentation `NetworkInterface` reports virtual IPv4.
- [ ] loopback remains `127.0.0.1`/loopback.
- [ ] unrelated physical interfaces do not all inherit the virtual IPv4.
- [ ] socket local-address getters report the virtual IPv4 for non-loopback IPv4 sockets.

## Stable randomisation

Configure at least three IPv4s, compatible gateways and three DNS sets.

- [ ] all processes of the target app report the same selected profile.
- [ ] restarting a process without rerolling keeps the same profile.
- [ ] Reroll changes only to values present in the configured whitelists.
- [ ] selected gateway remains in the configured subnet.
- [ ] every target-app process is restarted after reroll.

## VPN hiding

Test once without a VPN and once with the user's normal VPN active.

- [ ] `hasTransport(TRANSPORT_VPN)` is false in the scoped app.
- [ ] VPN transport is absent from `getTransportTypes()`.
- [ ] `NET_CAPABILITY_NOT_VPN` appears true/present.
- [ ] legacy VPN `NetworkInfo` queries do not expose a VPN entry.
- [ ] VPN-style interfaces are absent from normal Java enumeration/lookups.
- [ ] always-on/lockdown VPN settings are not exposed through covered Settings getters.
- [ ] actual traffic still traverses the real VPN when the VPN is enabled.
- [ ] remote public-IP test reports the real VPN exit, proving NetVeil did not reroute traffic.

## Proxy hiding

- [ ] Java proxy system properties are hidden.
- [ ] `LinkProperties.getHttpProxy()` is hidden.
- [ ] `ConnectivityManager.getDefaultProxy()` is hidden.
- [ ] actual proxy/VPN connectivity continues to work unchanged.

## Wi-Fi / cellular

- [ ] test while Wi-Fi is primary.
- [ ] test while cellular is primary.
- [ ] test transition Wi-Fi -> cellular with a full target-app restart.
- [ ] verify presentation interface selection is plausible on the Pixel build.

## IPv6 / CLAT

- [ ] with IPv6 suppression enabled, covered APIs do not expose real IPv6 addresses/prefixes.
- [ ] with suppression disabled, IPv6 passthrough does not break IPv4 spoofing.
- [ ] verify CLAT/stacked-link hidden APIs do not expose VPN/interface identifiers unexpectedly.

## Multi-module coexistence

- [ ] establish NetVeil-only baseline.
- [ ] enable desired non-network XPL-EX features.
- [ ] inspect target output again for hook-order regressions.
- [ ] inspect Vector/LSPosed logs for NetVeil fallback warnings.

## Known negative tests

These are expected to remain outside v0.2.1 coverage:

- [ ] native `getifaddrs()` may reveal real interfaces.
- [ ] raw netlink/ioctl may reveal real interfaces/routes.
- [ ] `/proc/net/*` or `/sys/class/net/*` may reveal real state.
- [ ] server-side public-IP checks reveal the genuine egress address.
- [ ] deliberate Parcelable round-trips may reveal backing state from framework objects such as `LinkProperties`, `WifiInfo`, or `NetworkCapabilities`.

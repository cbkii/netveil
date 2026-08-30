# Device test matrix

Use this checklist as the hands-on execution sheet for the authoritative stable-release contract in [`V1-RELEASE-READINESS.md`](V1-RELEASE-READINESS.md). Record the exact APK hash, source commit, framework version and Android build for every run.

## Baseline state

- [ ] Confirm NetVeil framework scope contains only the intended target applications.
- [ ] Confirm the NetVeil app opens on **All scoped apps (Global)** by default.
- [ ] Confirm the target selector shows Global, saved/custom entries, and launchable installed apps without `QUERY_ALL_PACKAGES`.
- [ ] Confirm manual package entry remains usable for packages not shown by launcher visibility.
- [ ] Confirm overlapping network spoofing from other scoped modules is disabled for the initial baseline.
- [ ] Record Android build fingerprint/security patch.
- [ ] Record Vector/LSPosed version and libxposed API compatibility.
- [ ] Record actual interface inventory before testing.
- [ ] Record NetVeil source SHA and APK SHA-256.

## Global and per-app resolution

Create a valid Global profile, then exercise at least two scoped target packages.

- [ ] An app with no saved override resolves Global.
- [ ] `INHERIT_GLOBAL` resolves Global.
- [ ] `CUSTOM` resolves the package-specific profile instead of Global.
- [ ] `DISABLED` installs no NetVeil profile hooks for that package even though it remains in Vector/LSPosed scope.
- [ ] Removing a package override returns it to Global inheritance.
- [ ] Disabling/resetting Global does not overwrite retained Custom profiles.
- [ ] A package outside Vector/LSPosed scope is unaffected regardless of Global configuration.
- [ ] a later package loaded into an already-claimed process cannot install a second profile.

## Route-hidden identity — default

Configure one route-hidden IPv4 and one DNS set. Do **not** enter a prefix/gateway.

- [ ] UI saves successfully without the old `/0` workaround.
- [ ] `WifiInfo` getter/string/Parcel views agree on the configured IPv4 when applicable.
- [ ] `DhcpInfo` reports the configured IPv4/DNS and neutral fixed-width gateway/host-mask metadata rather than inventing a LAN gateway.
- [ ] `LinkProperties` getter/string/Parcel views expose the configured IPv4/DNS/interface/proxy state.
- [ ] projected `LinkProperties` contain no synthetic IPv4 connected route or default gateway route.
- [ ] selected presentation `NetworkInterface` reports virtual IPv4.
- [ ] lookup by the fake IPv4 returns the selected presentation interface.
- [ ] loopback remains loopback.
- [ ] unrelated physical interfaces do not all inherit the virtual IPv4.
- [ ] classic/NIO local IPv4 socket getters report the virtual IPv4.
- [ ] IPv6 sockets retain an IPv6 address family.

## Explicit virtual network

Configure a coherent LAN identity such as `192.168.50.20/24` via `192.168.50.1`.

- [ ] UI accepts a different same-subnet gateway.
- [ ] UI rejects gateway == client IPv4.
- [ ] UI rejects an out-of-subnet gateway with an actionable inline error.
- [ ] `/0` remains technically accepted but displays the explicit whole-IPv4-space warning.
- [ ] `/31` peer addressing behaves according to the configured prefix model.
- [ ] `/32` does not accept a different gateway as same-subnet.
- [ ] `DhcpInfo` reports configured IPv4/gateway/DNS and derived mask; DHCP server remains unknown.
- [ ] `LinkProperties` exposes the configured connected/default IPv4 routes coherently.
- [ ] synthetic `RouteInfo` objects agree with getter/string/Parcel projections.

## Stable randomisation

Configure at least three complete NetworkIdentity entries and three DNS sets in Global.

- [ ] all processes of one target package report the same selected identity/DNS set.
- [ ] restarting a process without rerolling keeps the same selection.
- [ ] two different inheriting packages derive independent package seeds from the same Global base seed.
- [ ] selections contain only configured whole identities and whole DNS sets.
- [ ] Global Reroll changes the base seed and changes only to configured values after inheriting processes are restarted.
- [ ] a Custom package profile keeps its own seed and is unaffected by Global Reroll.
- [ ] Custom Reroll affects only that package profile.

## Legacy-profile migration

Use a preference snapshot/profile from the pre-Global v1.0.x format.

- [ ] Existing package profile defaults to `CUSTOM`, preserving its prior precedence.
- [ ] An IPv4 with exactly one compatible legacy gateway migrates to an Explicit identity.
- [ ] An IPv4 with no compatible gateway migrates to route-hidden rather than being rejected.
- [ ] An IPv4 with multiple compatible legacy gateways migrates to route-hidden rather than guessing.
- [ ] The previously observed `/0` workaround with ambiguous gateways loads as route-hidden identities.
- [ ] Saving the migrated profile writes the structured identity format.
- [ ] legacy fields remain available until explicit reset/remove for rollback/debug inspection.

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

## Configuration UX

- [ ] Global is first/default selector entry.
- [ ] saved/manual custom packages remain selectable after app restart.
- [ ] launchable installed apps display human-readable labels and package names.
- [ ] editing selector text to a different package cannot accidentally save current Global/custom form data under the wrong target.
- [ ] inline errors persist long enough to diagnose; configuration problems are not Toast-only.
- [ ] Save scrolls/focuses the first invalid identity/DNS control.
- [ ] resolved preview agrees with the effective Global/Custom/Disabled policy.
- [ ] Android 15/16 edge-to-edge, gesture navigation, three-button navigation, landscape and large display/font scaling remain usable.

## Country IPv4 presets and refresh

Exercise the feature once on Global and once on a Custom override. Country presets are a configuration population source, not a separate runtime hook path.

- [ ] AU, US, GB, ID and FR each produce at least one candidate with the default filters.
- [ ] **Exclude medium/low-confidence providers** is enabled by default and relaxing it can add eligible lower-confidence candidates without altering other profile settings.
- [ ] **Exclude known VPN / proxy / Tor addresses** is enabled by default and its opt-out affects only country candidate selection.
- [ ] **Add to list** preserves existing manual identities, canonicalises/deduplicates candidates and imports them as route-hidden identities.
- [ ] **Replace list** changes only network identities; DNS/privacy/randomisation/profile policy remain untouched until the normal Save action.
- [ ] imported identities never invent an ISP gateway/prefix and save without the historical `/0` workaround.
- [ ] bundled data works with airplane mode/no network.
- [ ] **Refresh country data now** is asynchronous and does not block the main UI thread.
- [ ] successful refresh validates and atomically replaces only the app-private cache, then updates the displayed data timestamp/source.
- [ ] malformed, oversized, stale, unsupported-schema or non-public-address remote data is rejected and the previous valid pack remains usable.
- [ ] HTTP/TLS/endpoint failure leaves the existing cached/bundled data usable and shows a persistent last-refresh warning.
- [ ] automatic refresh is disabled by default; enabling it defaults to Monthly and allows Weekly/Daily.
- [ ] background refresh changes only the country-data cache and does not rewrite saved profile identities.
- [ ] reboot with automatic refresh enabled retains the persisted JobScheduler cadence; with it disabled no country refresh job is active.
- [ ] refresh failures do not create extra retry cadence beyond the selected periodic schedule.
- [ ] if the configured pack endpoint is not anonymously readable, offline country presets remain usable and the limitation is reported as refresh failure rather than profile failure.

## Multi-module coexistence

- [ ] establish NetVeil-only baseline.
- [ ] enable desired non-network features in another scoped privacy module.
- [ ] inspect target output again for hook-order regressions.
- [ ] deliberately enable overlapping network hooks and record which module owns any differing return value.
- [ ] inspect Vector/LSPosed logs for NetVeil required-hook failures or protective fallbacks.

## CI/framework-object consistency

- [ ] debug and ephemeral-signed release APKs build successfully from the same source head.
- [ ] release APK signature/package/Xposed metadata/permission checks pass.
- [ ] bundled country pack is present in the APK and validates against the same schema/permission/source policy used by CI.
- [ ] `WifiInfo`, `NetworkCapabilities`, `LinkProperties` and legacy `NetworkInfo` Parcel round-trips agree with their getter/string projections.
- [ ] no custom NetVeil-only `toString()` format is observable for projected Android framework objects.

## Expected native/server disclosures

These remain outside the Java backend and are expected to expose real state where the platform permits them:

- [ ] native `getifaddrs()`;
- [ ] raw netlink/ioctl interface or route queries;
- [ ] direct `/proc/net/*` reads;
- [ ] direct `/sys/class/net/*` reads;
- [ ] native system-property APIs;
- [ ] server-side public-IP, latency or geolocation inference.

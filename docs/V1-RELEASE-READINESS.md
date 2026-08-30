# NetVeil v1 release-readiness gate

This document is the acceptance contract for the first stable NetVeil APK. It distinguishes what CI can prove from what still requires a physical Pixel/Vector run.

## Supported design boundary

NetVeil is an app-process, modern-libxposed API 101 module. It virtualises Android/Java-visible network metadata for selected applications without changing routing, the kernel network namespace, DNS traffic, or the public source IP observed by remote servers.

**Vector/LSPosed scope is the outer execution gate.** The NetVeil Global profile applies only inside app processes where the framework has already loaded the module. Global does not broaden scope or cause NetVeil to inject itself into arbitrary installed applications.

The v1 Java backend intentionally does **not** claim to hide raw native/kernel observations such as `getifaddrs`, direct netlink, ioctl, `/proc/net/*`, `/sys/class/net/*`, or server-side network characteristics. Those probes are expected to expose real state unless a separate native/kernel component handles them.

`system_server` is explicitly rejected. A process can claim exactly one primary package/effective-profile generation; later packages loaded into the same process cannot stack a second NetVeil identity.

## v1 implementation invariants

A release candidate must satisfy all of these invariants:

- Vector/LSPosed scope remains authoritative for whether NetVeil executes in an app process;
- Global is the default/fallback configuration for already-scoped packages;
- per-app policy precedence is exactly `DISABLED` -> no profile hooks, `CUSTOM` -> package profile, `INHERIT_GLOBAL`/no override -> Global;
- existing pre-Global package profiles default to `CUSTOM` until explicitly changed, preserving upgrade behaviour;
- one immutable resolved profile is used for the target process after activation;
- all configured IPv4 values are canonical numeric literals;
- every selectable network identity is a complete `NetworkIdentity`, never an independently mixed IP/gateway pair;
- route-hidden identities require only IPv4 and omit synthetic IPv4 connected/default routes from projected `LinkProperties`;
- explicit-route identities require a different same-subnet gateway for the configured prefix;
- `/0` remains valid CIDR in Explicit mode but is treated as an unusual whole-IPv4-space topology and surfaced as a warning, not as the normal workaround for arbitrary identities;
- Global randomisation derives a package-specific stable selection from the Global base seed plus actual package name;
- Custom-profile randomisation remains package-local and stable until explicitly rerolled;
- a reroll can select only whole configured identities and whole configured DNS sets;
- legacy independent IPv4/gateway/prefix profiles migrate conservatively: exactly one compatible gateway may become Explicit; ambiguous or unmatched mappings become route-hidden rather than guessed;
- country presets support AU/US/GB/ID/FR and feed the same canonical `NetworkIdentity` model as manual entries rather than adding a separate hook path;
- country imports are route-hidden and never infer a gateway/prefix from RIR/BGP/provider data;
- country-provider confidence filtering and known VPN/proxy/Tor exclusion are enabled by default but remain user-opt-out selection policies;
- country-data refresh never rewrites saved profiles automatically and uses valid refreshed cache -> valid previous cache -> bundled APK data as its failure hierarchy;
- downloaded country packs are bounded HTTPS input and fail closed on invalid schema/timestamp/country set/count/public-IP/provenance/anonymity metadata or rollback to older data;
- automatic country-data refresh is disabled by default; when enabled its default is Monthly with Weekly/Daily choices and failures do not create a separate retry cadence;
- no hard-coded `wlan0` fallback: the physical presentation interface must resolve from the process network environment;
- CLAT `v4-*` interfaces are collapsed to their underlying Wi-Fi/cellular interface before transport classification;
- only raw VPN `NetworkCapabilities` objects receive VPN-specific sanitisation;
- non-VPN capability objects pass through unchanged unless another explicit NetVeil feature applies;
- synthetic/projected framework objects are used for routes, interface addresses, capabilities, link properties and Wi-Fi IP state instead of equality-based side-table rewriting;
- `toString()` and `writeToParcel()` use the same projected object model as normal getters;
- deprecated raw VPN `NetworkInfo` objects are projected through getters, strings and Parcel writes;
- explicit `NetworkRequest` requests for `TRANSPORT_VPN` are suppressed while the genuine active `Network` handle is preserved so NetVeil does not reroute traffic;
- IPv6 sockets are never converted into fake IPv4 sockets; IPv6 suppression applies only on collection/metadata surfaces where absence is semantically valid;
- DHCP server metadata is left unknown rather than being invented as the configured gateway;
- Java/Android property hooks use explicit network property families and honour caller-supplied default values;
- incomplete required-hook installation is rolled back instead of leaving a partially active profile;
- the configuration selector can enumerate Global, saved/custom packages and launchable installed apps without requesting `QUERY_ALL_PACKAGES`;
- manually editing the target selector cannot silently save the currently displayed profile under a different package.

## Scope-service compatibility

NetVeil intentionally remains on libxposed API `101.0.1` with `minApiVersion=101`, `targetApiVersion=101`, `staticScope=false`, and an empty recommended `scope.list`.

The current official `libxposed/service` line is API 102. Direct in-app scope querying/management is therefore **not** a v1 requirement and must not raise NetVeil's framework API requirement merely to populate the target selector. Vector/LSPosed Manager remains the authoritative external scope manager for this API-101 compatibility baseline.

## CI acceptance

Every PR and `main` build must run:

1. workflow/source sanity checks;
2. deterministic country-generator tests plus bundled-pack schema/source validation;
3. JVM unit tests, including Global/Custom/Disabled resolution, migration and country-pack parsing/filtering tests;
4. unsuppressed `lintRelease`;
5. debug APK assembly;
6. release APK assembly using an ephemeral non-production signing key;
7. `apksigner` verification of the CI release APK;
8. package identity check for `dev.ip.netveil`;
9. modern `META-INF/xposed/java_init.list` and `META-INF/xposed/module.prop` checks;
10. bundled `assets/country-ip-pack.json` presence/validation;
11. an **exact** Android permission allow-list containing only the intended permissions, currently `android.permission.INTERNET` and `android.permission.RECEIVE_BOOT_COMPLETED`, and no broad `QUERY_ALL_PACKAGES` visibility;
12. artifact SHA-256 output, APK-size output and CI artifact preservation.

The country-data workflow additionally performs a bounded live-source regeneration check. Pull-request validation is read-only and may publish the generated pack only as an audit artifact; repository write permission is confined to scheduled/manual update execution.

The production Manual Release workflow remains the only workflow allowed to use the repository release-signing secrets, and it must enforce the same package/Xposed/country-pack/permission contract on the exact release APK bytes.

## Physical compatibility matrix

The following matrix is a **release gate**, not a claim of already-completed testing.

| Device | Android 15 | Android 16 |
| --- | --- | --- |
| Pixel 8 | required | required |
| Pixel 8 Pro | representative optional extension | representative optional extension |
| Pixel 9 / 9 Pro | required representative Tensor G4 test | required representative Tensor G4 test |
| Pixel 9a (`tegu`) | required | **primary required target** |

Record for every physical run:

- device model/codename;
- Android build fingerprint and security-patch level;
- Vector exact version/build and libxposed API level;
- NetVeil source SHA and APK SHA-256;
- selected target package/process and effective Global/override policy;
- real interface inventory before NetVeil masking;
- NetVeil/Vector logs;
- JSON probe result;
- real public IP before/after NetVeil activation.

## Test scenarios

Run both a `targetSdk 35` and a `targetSdk 36` probe APK for each applicable device/OS combination.

### Scope and profile resolution

With at least two applications scoped in Vector/LSPosed:

- Global is the first/default selector entry and provides the fallback profile;
- an app with no saved override resolves Global;
- `INHERIT_GLOBAL` resolves Global;
- `CUSTOM` resolves the package-specific profile instead of Global;
- `DISABLED` installs no NetVeil profile hooks while the process remains framework-scoped;
- removing an override returns the package to Global inheritance;
- a package outside Vector/LSPosed scope remains unaffected regardless of Global configuration;
- saved/manual package names remain selectable even when launcher visibility cannot discover them.

### Route-hidden identity — default

Configure one route-hidden IPv4 and one DNS set without entering a prefix or gateway.

- the UI saves without the historical `/0` workaround;
- `WifiInfo` getter/string/Parcel views agree on the configured IPv4 where applicable;
- `DhcpInfo` exposes the configured IPv4/DNS with neutral fixed-width gateway/host-mask metadata rather than inventing a LAN gateway;
- projected `LinkProperties` expose the configured IPv4/DNS/interface/proxy state but contain no synthetic IPv4 connected route or default gateway route;
- selected presentation `NetworkInterface` and covered socket-local-address views agree on the virtual IPv4;
- IPv6 sockets retain their IPv6 address family.

### Explicit virtual network

Configure a coherent virtual LAN such as `192.168.50.20/24` via `192.168.50.1`.

- the same-subnet different-gateway rule is enforced with actionable inline validation;
- `/0` is technically accepted but clearly warned as whole-address-space topology;
- `/31` and `/32` edge cases follow the configured prefix model;
- DHCP and `LinkProperties` gateway/netmask/routes agree with the explicit identity;
- synthetic `RouteInfo` objects agree across getters, strings and Parcel round-trips.

### Global and Custom randomisation

Configure multiple complete identities and multiple DNS sets.

- all processes of one inheriting package receive the same Global-derived identity/DNS selection;
- different inheriting packages can derive independent selections from the same Global base seed;
- restarting without rerolling preserves the selection;
- Global Reroll changes only the Global base seed and therefore affects inheriting packages after restart;
- Custom profiles retain their own seed and are unaffected by Global Reroll;
- Custom Reroll affects only that package;
- every resolved value remains one of the user-configured whole identities/DNS sets.

### Legacy migration

Load a pre-Global v1.0.x preference snapshot/profile.

- the existing package defaults to `CUSTOM`;
- an IPv4 with exactly one compatible legacy gateway may migrate to Explicit;
- an IPv4 with zero or multiple compatible gateways migrates to route-hidden rather than guessing;
- the previously valid `/0` workaround with ambiguous gateways migrates to route-hidden identities;
- saving the migrated profile writes the structured identity representation;
- legacy fields remain readable until explicit profile reset/removal for rollback/debug inspection.

### Country IPv4 presets and refresh

Using Global and then a Custom override:

- AU, US, GB, ID and FR all populate candidates from the bundled pack with default filtering;
- default high-confidence and known VPN/proxy/Tor exclusions are enabled and can be independently opted out;
- **Add** preserves/deduplicates existing manual identities while **Replace** changes only the network identity list;
- every imported candidate is route-hidden and no gateway/prefix is fabricated;
- offline/airplane-mode use falls back to bundled/cached data;
- **Refresh country data now** runs off the UI thread and updates only a fully validated, non-older app-private cache;
- malformed, oversized, stale, unsupported, special-address or otherwise invalid remote data leaves the prior valid data untouched;
- automatic refresh is off by default, defaults to Monthly when enabled, supports Weekly/Daily, survives reboot when enabled and never rewrites saved profiles;
- failed periodic refresh stays on the configured cadence rather than scheduling an extra retry series;
- if the configured HTTPS pack endpoint is not anonymously readable, refresh failure is isolated from profile editing/use and bundled data remains functional.

### Transport/state combinations

- Wi-Fi without VPN;
- Wi-Fi with VPN;
- cellular without VPN;
- cellular with VPN;
- IPv6/dual-stack where available;
- IPv6-only cellular with 464XLAT/CLAT where available;
- Private DNS enabled;
- Android proxy configured;
- route-hidden profile;
- explicit virtual-network profile;
- randomised profile before and after explicit reroll;
- target main process plus a secondary `:remote` process;
- NetVeil alone;
- NetVeil alongside another scoped privacy module, with overlapping network hooks disabled first and then deliberately enabled for conflict testing.

### Java/framework probe surfaces

The probe APK must capture and compare at least:

- `WifiInfo` IP getter, string and Parcel round-trip;
- `WifiManager.getDhcpInfo()`;
- `ConnectivityManager.getActiveNetwork()`;
- `ConnectivityManager.getAllNetworks()`;
- `getNetworkCapabilities()` and capability Parcel round-trip;
- `getLinkProperties()` and link-property Parcel round-trip;
- legacy `NetworkInfo` getter/string/Parcel views;
- `LinkProperties` addresses, DNS, routes, interface, proxy, Private DNS, DHCP server, NAT64 and stacked links;
- `NetworkInterface` enumeration and lookup by name/index/real address/fake address;
- `InterfaceAddress` values;
- classic TCP/UDP local IPv4 addresses;
- NIO socket-channel local addresses;
- IPv6 socket family preservation;
- explicit `NetworkRequest(TRANSPORT_VPN)` registration/request behaviour;
- Java proxy properties;
- Android DNS/gateway/IP/proxy property families and their default-value overloads.

For every projected framework object, compare normal getters, `toString()`, and Parcel round-trip output. They must describe the same effective virtual topology for the selected route mode.

### Configuration UX

On Android 15 and Android 16:

- the target selector opens on Global and lists saved/custom plus launchable installed applications;
- manual package entry can select packages outside launcher discovery;
- manually replacing selector text cannot accidentally write the visible Global/custom form to a different package;
- inline identity and DNS errors remain visible and actionable;
- Save focuses/scrolls the first invalid field;
- resolved preview agrees with Global/Custom/Disabled policy and selected route mode;
- country controls remain usable without exposing per-ISP/ASN management complexity and explain why Internet access exists;
- edge-to-edge, display cutouts, gesture navigation, three-button navigation, landscape and large font/display scaling remain usable.

## Expected negative/native probes

The following are expected to expose real state in v1 and must be recorded as expected limitations rather than false failures:

- JNI/native `getifaddrs()`;
- direct netlink interface/route queries;
- direct ioctl interface enumeration;
- `/proc/net/*` and `/sys/class/net/*` where SELinux permits access;
- public/remote source IP;
- latency/geolocation/server-side VPN inference.

If these requirements expand, that is a separate native/system backend project and must not silently change the v1 Java backend failure domain.

## Release decision

A v1 APK is release-ready only when:

- all required CI gates are green on the exact release-source commit;
- country generator/bundled-pack validation and the live-source check are green, with optional-source degradation explicitly reported rather than silently treated as authoritative exclusion coverage;
- Global/Custom/Disabled precedence and legacy migration behave as documented;
- route-hidden identities do not require a fake gateway/prefix and do not synthesize IPv4 routes;
- Explicit identities remain internally coherent across DHCP, routes, getters, strings and Parcel views;
- country presets preserve Add/Replace semantics, offline fallback, strict downloaded-pack validation and the user-selected refresh cadence;
- no required hook fails installation on the physical matrix;
- no unexpected protective fallback appears in the target-app logs during the matrix;
- all supported Java/framework probes agree with the configured effective virtual identity;
- real routing/public egress is unchanged by NetVeil;
- known native/kernel disclosures match the documented boundary;
- the production-signed APK passes package/version/signature/Xposed-metadata/country-pack/permission/checksum verification in Manual Release.

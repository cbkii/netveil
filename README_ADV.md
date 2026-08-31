# NetVeil — Advanced documentation

This document is for developers, contributors, advanced testers and users who need to understand NetVeil's implementation details.

For normal installation and configuration, start with the [end-user README](README.md).

## Technical baseline

NetVeil is a deliberately narrow modern **LSPosed/libxposed** module for Android 15 and newer. The APK package is:

```text
dev.ip.netveil
```

Current baseline:

- `minSdk 35` — Android 15;
- `targetSdk 35`;
- `compileSdk 36`;
- Java 17;
- libxposed API `101.0.1`;
- `minApiVersion=101`;
- `targetApiVersion=101`;
- `staticScope=false`;
- empty recommended `scope.list`.

NetVeil intentionally does not require the newer libxposed service API merely to manage framework scope from inside the app. Vector/LSPosed Manager remains the authoritative scope-management UI for the API-101 compatibility baseline.

See [Compatibility](docs/COMPATIBILITY.md) for the maintained support statement.

## Runtime scope and profile resolution

Vector/LSPosed scope is the outer execution gate. NetVeil does not inject itself into arbitrary apps and the Global profile never broadens framework scope.

Effective policy is:

```text
not scoped in Vector/LSPosed -> NetVeil does not execute

scoped + Off for this app    -> no NetVeil profile hooks
scoped + Custom              -> package-specific profile
scoped + Use Global          -> Global profile
scoped + no override         -> Global profile
```

Existing package profiles from the older pre-Global format are treated as Custom until the user explicitly changes their policy. This preserves upgrade behaviour.

Only one package/profile may claim a process. `system_server` is explicitly rejected.

## Stored profile model

Profiles store complete `NetworkIdentity` objects rather than independent IPv4 and gateway lists.

Each identity uses one of two route modes:

- **Omit gateway & routes** — an arbitrary IPv4 identity without synthetic route topology;
- **Explicit virtual network** — a coherent IPv4, prefix and gateway tuple.

Randomisation selects a whole identity and a whole DNS set. It does not independently mix an IPv4 address with an unrelated gateway.

For Global, the effective selection seed is derived from the Global base seed plus the actual package name. This gives different inheriting packages independent stable selections while keeping all processes of one package consistent.

Custom profiles retain their own selection seed and reroll state.

### Legacy migration

The preference schema is versioned and remains able to read the old independent IPv4/gateway/prefix format.

Migration is conservative:

- an unambiguous compatible mapping may become an Explicit identity;
- missing or ambiguous mappings become route-hidden identities rather than guessed topology;
- legacy multi-IP or multi-gateway `/0` profiles are treated as ambiguous and migrate to route-hidden identities;
- a genuinely unambiguous single-IP + single-gateway `/0` profile may remain Explicit.

The old fields remain readable until explicit reset/removal so rollback and debugging remain possible.

## Network projection model

NetVeil constructs one coherent app-visible network model and derives covered framework views from it. This avoids independently rewriting unrelated getters into contradictory states.

The model covers, where practical:

- primary app-visible IPv4 identity;
- DNS set;
- optional explicit gateway and IPv4 routes;
- physical presentation interface;
- VPN visibility policy;
- proxy visibility policy;
- optional IPv6-address suppression.

Framework-native projected objects are preferred where Android exposes usable constructors or mutable equivalents. Getter, string and covered Parcelable views are kept aligned with the same resolved model.

### Presentation interface selection

NetVeil classifies loopback, Wi-Fi, common Android cellular interfaces, Ethernet, CLAT and VPN/tunnel-style interfaces.

It does not make every interface claim the same spoofed IPv4 address. One physical presentation interface is selected for the process.

Android 464XLAT `v4-*` interfaces are collapsed to their underlying physical transport for presentation purposes. There is no hard-coded `wlan0` fallback. If the presentation interface cannot be resolved safely, the relevant transformation fails open instead of inventing Wi-Fi topology.

## Covered surfaces and hard boundaries

The exact implementation evolves with Android and libxposed. The list below is a practical map, not a promise that every OEM/private API remains hookable forever.

### IPv4 and DHCP

Covered paths include:

- `WifiInfo.getIpAddress()`;
- covered `WifiInfo.toString()` / Parcel views;
- `WifiManager.getDhcpInfo()`;
- `LinkProperties` address views;
- selected `NetworkInterface` address views;
- projected `InterfaceAddress` values;
- classic socket local IPv4 getters;
- Android/libcore NIO local-address getters where available.

IPv6 socket address families are preserved. IPv6 metadata suppression does not turn an actual IPv6 socket into a fake IPv4 socket.

### Gateway and routes

In Explicit mode, the projection may include:

- DHCP gateway/netmask;
- IPv4 connected/default routes in `LinkProperties`;
- framework-native projected `RouteInfo` objects;
- selected Android system-property gateway metadata.

In route-hidden mode, object-based IPv4 gateway/default-route metadata is omitted. Fixed-width legacy structures use neutral/host-only values where absence cannot be represented directly.

DHCP-server metadata is not invented from the configured gateway.

### DNS

Covered paths include:

- `LinkProperties.getDnsServers()`;
- private-DNS visibility where available;
- DHCP DNS fields;
- selected Android DNS system properties.

NetVeil changes metadata. It does not redirect DNS traffic.

### VPN visibility

The VPN-hiding path covers selected:

- `NetworkCapabilities` transport/capability/transport-info views;
- VPN owner/admin/underlying-network metadata where mutable equivalents exist;
- `NetworkCapabilities.toString()` and covered Parcel views;
- `ConnectivityManager.getAllNetworks()` filtering while retaining the genuine active handle;
- legacy `NetworkInfo` VPN queries and covered string/Parcel views;
- VPN-style `NetworkInterface` enumeration/lookups;
- common always-on/lockdown settings reads;
- explicit `NetworkRequest` requests for `TRANSPORT_VPN`.

VPN-specific capability changes are applied only when the origin capability object genuinely represents a VPN.

NetVeil does not disconnect, disable or reroute the real VPN.

### Proxy visibility

Covered paths include:

- `LinkProperties.getHttpProxy()`;
- `ConnectivityManager.getDefaultProxy()`;
- common Java proxy system properties;
- selected Android proxy system properties.

NetVeil does not replace `ProxySelector`, force `NO_PROXY`, or otherwise change connectivity.

### Native and server-side boundaries

The Java backend does not claim to intercept:

- native `getifaddrs()`;
- raw netlink;
- direct `ioctl` interface enumeration;
- direct `/proc/net/*` reads;
- direct `/sys/class/net/*` reads;
- native system-property APIs;
- server-side public-IP checks;
- latency, ASN or geolocation inference performed remotely.

This is why the [end-user README](README.md#limits) describes NetVeil as API-visible masking rather than a complete network-identity sandbox.

A future native/system backend, if added, should remain optional and separately qualified.

## Hook and process lifecycle

NetVeil is app-process-only.

The runtime is designed around these invariants:

- `system_server` is rejected;
- only the first package loaded into a process may claim the NetVeil identity;
- later package loads cannot stack a second profile;
- the resolved effective profile is immutable for that process lifetime;
- required hook installation is transactional;
- an incomplete required-hook set is rolled back;
- transformations use protective fail-open behaviour;
- hook-health diagnostics remain available for investigation.

A saved profile or reroll therefore requires the target process to restart before the new resolved state can take effect.

## Android object consistency

The main correctness requirement is not simply that one getter returns a requested string. Multiple Android surfaces must describe one believable state.

Where supported, NetVeil therefore keeps related views aligned across:

- `LinkProperties`;
- `RouteInfo`;
- `InterfaceAddress`;
- `NetworkCapabilities`;
- `WifiInfo`;
- legacy `NetworkInfo`;
- selected socket/interface metadata;
- framework string and Parcelable representations.

The physical-device matrix exists specifically because local JVM tests cannot prove all framework-object construction, hidden/private API behaviour and Parcel consistency on real Android 15/16 firmware.

See [Device test matrix](docs/DEVICE-TEST-MATRIX.md).

## Package visibility and permissions

The configuration app provides a searchable target picker without requesting broad installed-package visibility.

It declares a launcher-intent `<queries>` rule so normal launchable apps can appear in the picker. Manual exact package entry remains available.

NetVeil does **not** request `QUERY_ALL_PACKAGES`.

The intended normal Android permission allow-list is exactly:

```text
android.permission.ACCESS_NETWORK_STATE
android.permission.INTERNET
android.permission.RECEIVE_BOOT_COMPLETED
```

`INTERNET` is used only for the optional country-data pack.

`ACCESS_NETWORK_STATE` is required by modern Android when the persisted `JobScheduler` refresh job declares a connectivity constraint.

`RECEIVE_BOOT_COMPLETED` is required by Android for the optional persisted `JobScheduler` refresh job. NetVeil does not add an exported boot receiver for this purpose.

These are normal permissions and do not create runtime permission prompts.

CI and Manual Release verify the exact APK permission set.

## Country-data architecture

Country presets feed the same ordinary route-hidden `NetworkIdentity` model used by manual configuration. They are not a separate spoofing engine.

The repository-side generator uses public routing/allocation/provider evidence and optional anonymity-exclusion data. Candidate addresses are derived passively; NetVeil does not ping or scan them.

The Android cache contract is:

```text
valid refreshed cache
        ↓
valid previous cache
        ↓
bundled APK pack
```

A downloaded pack is treated as untrusted input and is validated before atomic cache replacement. A failed or degraded update must not destroy the last valid local data.

Automatic refresh uses platform `JobScheduler`, is off by default and updates only the cached data pack. It does not rewrite saved profiles.

The authoritative source list, generator rules, confidence semantics, exclusion handling and updater workflow are maintained in [Country data](docs/COUNTRY-DATA.md). Keep those details there rather than duplicating them into this file.

## Launcher icon implementation

The launcher icon is a lightweight adaptive icon built from Android vector resources.

Key constraints:

- adaptive canvas remains `108 × 108 dp`;
- essential foreground artwork stays inside the central safe area;
- background remains transparent/visually quiet;
- the visible mark is kept large enough to remain recognisable at launcher size without placing essential artwork in crop-prone outer regions.

The repository also contains `docs/assets/netveil-icon.svg`, a documentation preview derived from the same artwork for GitHub rendering. It is not the Android launcher resource itself.

## Build

Requirements:

- JDK 17;
- Android SDK 36;
- Android Gradle Plugin 9.3.1;
- Gradle 9.5.0 or newer.

The app is Java-only and disables AGP's built-in Kotlin support.

A normal local verification run is:

```bash
gradle --no-daemon \
  :app:testDebugUnitTest \
  :app:lintRelease \
  :app:assembleDebug
```

Release signing is supplied separately through `keystore.properties`; signing material is not stored in the repository.

## CI and release qualification

The normal Build workflow performs, among other checks:

- repository/workflow sanity;
- country-generator and bundled-pack tests;
- JVM unit tests;
- unsuppressed `lintRelease`;
- debug build;
- ephemeral-signed release build;
- APK signature verification;
- package-name verification for `dev.ip.netveil`;
- modern Xposed metadata checks;
- bundled country-pack checks;
- exact Android permission allow-list;
- artifact upload.

The Manual Release workflow is the authoritative release path and separately qualifies the signed release artifact before publication.

Do not treat a green CI build as physical Android compatibility evidence.

## Validation and stable-release gates

Use the repository documents according to their role:

| Document | Authority |
| --- | --- |
| [Validation](docs/VALIDATION.md) | Focused engineering validation notes and commands. |
| [Compatibility](docs/COMPATIBILITY.md) | Maintained compatibility baseline. |
| [Device test matrix](docs/DEVICE-TEST-MATRIX.md) | Physical Android/Pixel runtime coverage. |
| [v1 release readiness](docs/V1-RELEASE-READINESS.md) | Stable-release acceptance contract. |
| [Second-pass audit](docs/SECOND-PASS-AUDIT.md) | Historical cross-module/research audit findings. |
| [Design notes](docs/DESIGN.md) | Internal architecture and design rationale. |
| [Country data](docs/COUNTRY-DATA.md) | Dataset generation, source and refresh policy. |

Before a stable release, the physical matrix must cover the documented Android 15/16 and Pixel-class scenarios, including Wi-Fi, cellular, VPN, CLAT, multi-process targets, profile precedence, migration, Parcel/string consistency and proof that NetVeil does not alter real public egress.

## Contributor guidance

Keep changes narrow and preserve the project's core invariants:

1. Inspect state before changing behaviour.
2. Keep Vector/LSPosed scope authoritative.
3. Preserve one coherent network identity per process.
4. Prefer framework-native projected objects over unrelated getter patches.
5. Fail open rather than crash a target app when a non-critical transformation cannot be applied safely.
6. Do not silently broaden permissions or package visibility.
7. Add focused tests for parser, profile, projection and migration changes.
8. Treat physical Android validation as separate from JVM/CI success.
9. Update the authoritative detailed document instead of copying the same explanation into several READMEs.

For user-facing behaviour, keep [README.md](README.md) plain and concise. Put advanced implementation detail here or in the focused documents under `docs/`.

## Licence

NetVeil is licensed under the [Apache License 2.0](LICENSE).

# Release readiness

This is the authoritative release contract for the current NetVeil codebase. CI evidence and physical-device evidence are separate requirements.

## Supported design boundary

NetVeil is an app-process libxposed API-102 module for Android 15+. It virtualises selected Android/Java-visible network metadata inside framework-scoped applications without changing kernel routing, DNS traffic, VPN connectivity or public egress.

Vector/LSPosed scope remains authoritative. `system_server` is rejected, and one first package claims one immutable profile for a process lifetime.

## Current configuration contract

- Profile schema is exactly `3`.
- Injected processes do not resolve absent/mismatched schemas.
- The configuration app replaces an incompatible `profiles` store with an empty schema-3 store.
- No alternate profile-format parser or conversion path is release-supported.
- Country-data cache and refresh state remain outside the profile store.
- Per-app precedence is `Off` -> no profile, `Custom` -> package profile, otherwise Global.
- A missing per-app policy means Global, irrespective of unrelated stored package fields.
- Every configured network identity is a complete `NetworkIdentity`.
- Route-hidden identities contain no semantic gateway.
- Explicit identities require a coherent IPv4/prefix/gateway tuple.
- Randomisation selects complete identities and complete DNS sets only.

## Framework/platform contract

Release source must contain:

```text
minSdk = 35
targetSdk = 36
compileSdk = 36
io.github.libxposed:api:102.0.0
minApiVersion=102
targetApiVersion=102
staticScope=false
```

Vector v2.2+ is the primary qualified framework baseline. Automatic API-102 hot reload is not part of the current module lifecycle.

## Network projection invariants

- One physical presentation interface is selected; there is no hard-coded `wlan0` fallback.
- CLAT is normalised to its underlying physical interface.
- Route-hidden profiles omit synthetic IPv4 connected/default routes.
- Fixed-width surfaces translate an absent route-hidden gateway only at their adapter boundary; string property surfaces preserve absence/default semantics.
- Explicit-route DHCP, route and property projections agree with the same configured tuple.
- `NetworkCapabilities` VPN sanitisation applies only to raw VPN capability objects.
- The genuine active `Network` handle is retained so NetVeil does not reroute traffic.
- Explicit `TRANSPORT_VPN` requests are suppressed without altering real connectivity.
- Deprecated `NetworkInfo` query/getter/string/Parcel coverage remains coherent where those Android APIs are present.
- IPv6 sockets retain their address family.
- DHCP server metadata is not fabricated from the configured gateway.
- Required hook installation is transactional; runtime transformations are protective/fail-open.

## Country-data contract

- The canonical generated pack is `app/src/main/assets/country-ip-pack.json` in this repository.
- The same file is the bundled asset and anonymous public refresh endpoint on `main`.
- No sibling repository, PAT or cross-repository publication credential is part of NetVeil country-data operation.
- Manual and scheduled refreshes share the same process-serialised fetch/classify/cache-replace transaction.
- Downloaded data is bounded HTTPS input and fully validated before mutation.
- Older data and same-version conflicting data are rejected.
- Cache replacement is atomic or fails without destroying the last valid data.
- A background refresh never rewrites saved profiles.

See [COUNTRY-DATA.md](COUNTRY-DATA.md) for source/provenance requirements.

## Repository independence/current-only gate

`.github/scripts/test_repository_policy.py` must pass. It verifies required living documents and rejects executable/living references that would reintroduce external NetVeil repository coupling or unsupported profile/framework branches.

Historical release statements may remain in `CHANGELOG.md`; they do not define current behaviour.

## CI acceptance

Every final PR/main head must pass:

1. repository/current-only policy;
2. workflow/source sanity;
3. deterministic country generator and bundled-pack validation;
4. public same-repository country-refresh contract;
5. JVM tests for strict profile schema, Global/Custom/Off resolution, network identities, projection boundary semantics and country-pack behaviour;
6. unsuppressed `lintRelease`;
7. debug APK assembly;
8. ephemeral-signed release APK assembly;
9. `apksigner` verification;
10. applicationId/package check for `dev.ip.netveil`;
11. modern Xposed metadata checks;
12. bundled country-pack presence;
13. exact Android permission set:

```text
android.permission.ACCESS_NETWORK_STATE
android.permission.INTERNET
android.permission.RECEIVE_BOOT_COMPLETED
```

14. no `QUERY_ALL_PACKAGES`;
15. APK SHA-256, size output and CI artefact preservation.

The country-data workflow must also perform live-source generation validation. PR execution is read-only; the write job may update only canonical `main` using job-scoped `GITHUB_TOKEN`, branch-movement protection and publication verification.

Manual Release must rerun repository/country contracts, tests/lint/build and exact APK qualification before publishing production-signed bytes.

## Physical release gate

Run [DEVICE-TEST-MATRIX.md](DEVICE-TEST-MATRIX.md) on the primary Pixel 9a/Android 16/Vector v2.2+ environment before declaring a release physically qualified. Android 15 should also be sampled for the maintained `minSdk 35` support boundary.

Physical evidence must include:

- source SHA and APK SHA-256;
- exact Android build/security patch;
- exact framework version/API support;
- target package/process and effective policy;
- real interface inventory;
- NetVeil/framework logs;
- probe outputs for covered APIs;
- public-IP observation proving routing is unchanged.

A green CI build does not prove hidden/SystemApi construction and Parcel behaviour on a physical Android device.

## Merge/release decision

A PR is merge-ready when its exact final head is based on current `main`, all required CI is green, the diff is audited, and no unresolved valid review finding remains.

A release is ready only after that source is merged and the required physical matrix passes on the intended release APK. Release publication is a separate explicit action from PR merge-readiness.

# Design

## Core invariant

A scoped app process receives at most one immutable NetVeil network profile. All covered Android/Java observations derive from that one resolved profile; hooks do not independently randomise or invent unrelated values.

Vector/LSPosed scope is the outer execution gate. NetVeil is app-process-only and rejects `system_server`.

## Current configuration model

The `profiles` preference file uses schema `3` only. Runtime resolution requires that exact schema. The configuration app replaces an incompatible profile store with a fresh schema-3 store rather than maintaining alternate format paths.

Policy precedence is:

```text
Off for this app -> no profile
Custom           -> package profile
Use Global       -> Global profile
no override      -> Global profile
```

A profile contains:

- enabled state;
- one or more complete `NetworkIdentity` entries;
- one or more complete DNS sets;
- stable selection seed;
- VPN/proxy/IPv6 visibility policies.

Randomisation selects whole identities and whole DNS sets. Global derives a package-specific stable seed from the Global seed and package name; Custom uses its own seed.

## Network identities

### Route-hidden

The normal arbitrary-IPv4 mode contains an IPv4 address with no gateway in the semantic model. No synthetic IPv4 connected/default routes are added to projected `LinkProperties`.

Some fixed-width Android structures cannot encode an absent gateway. Translation to their neutral value occurs only at that boundary. The core `Profile.Resolved.gateway` remains null.

### Explicit virtual network

An explicit identity binds IPv4, prefix and gateway together. The gateway must differ from the client IPv4 and share the configured subnet. DHCP, route and property projections consume the same tuple.

## Presentation interface

NetVeil selects one real non-loopback physical presentation interface and classifies Wi-Fi, cellular, Ethernet, CLAT and VPN/tunnel names. CLAT interfaces are normalised to the underlying physical interface. If a safe presentation interface cannot be found, affected transformations fail open instead of fabricating one.

## Framework-object projection

Where practical, NetVeil creates projected framework objects rather than patching unrelated getters independently. `NetworkCapabilities`, `LinkProperties`, `WifiInfo`, route/address objects and deprecated `NetworkInfo` are kept coherent across covered getters, strings and Parcel writes.

Deprecated `NetworkInfo` remains a supported observation surface on Android 15/16. Its handling is isolated in `NetworkInfoHooks`, including direct VPN query filtering and getter/string/Parcel projection.

## VPN and proxy masking

VPN masking changes metadata only. The genuine active `Network` handle is retained, and real traffic remains on its existing VPN/physical route. Explicit `TRANSPORT_VPN` requests are suppressed so the request itself does not become a disclosure path.

Proxy masking similarly changes covered metadata without modifying the real proxy selector or routing.

## IPv6

IPv6 suppression is limited to metadata/collection surfaces where absence is valid. Socket address families are preserved.

## Failure behaviour

Required hook installation is transactional. An incomplete required hook set is rolled back. Runtime transformation failures are protective and fail open to the original result; logs are therefore part of physical qualification.

## Country data

Country presets are only an input source for ordinary route-hidden identities. The repository owns one canonical pack, bundles it into the APK and serves the same file from public `main`. Refresh is bounded, validated, anti-rollback and process-serialised before atomic cache replacement.

## Non-goals

NetVeil does not mutate kernel routing, establish a VPN, rewrite packets, bind an app to a different network, or claim to mask native/kernel/server-side network observations.

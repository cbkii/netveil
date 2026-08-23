# Design

## Core invariant

For a package, every target process must resolve the same immutable virtual network profile from persistent configuration.

The profile contains one selected IPv4, one compatible gateway and one selected DNS set. Hook implementations consume that resolved profile; they do not independently randomise values.

## Observation versus mutation

NetVeil is an observation-layer module.

It may change returned Java/Android metadata inside a scoped application process. It must not intentionally:

- modify routing tables;
- bind the process to a different `Network`;
- disable a real proxy;
- establish or tear down a VPN;
- modify kernel interface state;
- rewrite packets.

This is why v0.2.1 does not replace `ConnectivityManager.getActiveNetwork()` with an underlying physical network and does not force `ProxySelector.NO_PROXY`.

## Whitelist authority

User-entered IPv4, gateway and DNS values are authoritative. Randomisation must select only from those values.

Derived values are permitted only where required for structural consistency, for example:

- subnet/network address from IPv4 + prefix;
- broadcast address from IPv4 + prefix;
- default route destination `0.0.0.0/0`.

A gateway candidate must share the selected IPv4 subnet and must not equal the client address.

## Multi-process consistency

The configuration UI stores `selection_seed` per package. Random selection is a deterministic hash of that seed plus a field-specific salt.

This avoids process-local randomness. A reroll changes only the stored seed; all target processes must then be restarted before the new profile is authoritative.

## Presentation interface

Only one non-loopback physical interface is used as the virtual IPv4 presentation surface. The preference order is Wi-Fi, Pixel/common cellular, Ethernet, then another physical interface.

This corrects v0.1 behaviour where multiple physical interfaces could report the same spoofed IPv4.

## Route masking

Global `RouteInfo` rewriting is unsafe because unrelated route objects may exist in the target process.

v0.2.1 tags route instances only when they are returned from a virtualised `LinkProperties.getRoutes()` / `getAllRoutes()` result. Tagged routes expose a virtual destination, gateway and interface.

`RouteInfo.writeToParcel()` remains an explicit bypass because app-local mutation of hidden backing fields is intentionally avoided.

## VPN hiding

VPN hiding sanitises metadata while preserving connectivity. Covered indicators include transport flags, `NOT_VPN`, selected ownership metadata, legacy VPN `NetworkInfo`, VPN-style interface enumeration/lookups and common always-on/lockdown settings.

A system-side implementation could sanitise Binder-delivered objects earlier, but it has a larger compatibility and failure domain. It should remain an optional future backend.

## Fail-open behaviour

A hook transformation error returns the original framework result rather than crashing the scoped application. Missing hidden/SystemApi methods are skipped.

This is an availability-first policy and can cause information leakage. Qualification therefore requires LSPosed/Vector log inspection; a clean app experience alone is not sufficient evidence that every requested mask is active.

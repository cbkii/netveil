# v0.2.1 second-pass audit

This document records the main architectural changes made after reviewing Android 15/16 source and recently maintained Xposed/LSPosed network-privacy projects.

## External implementations reviewed

The review used implementation ideas and coverage maps as references; NetVeil remains an independent implementation.

- libxposed API and modern module documentation
- LSPosed modern API documentation
- Vector API-101/API-102 compatibility work
- `okhsunrog/vpnhide`
- `thelok1s/hands-off-my-vpn`
- `dddqmmx/arirang`
- `ssmengyan/Xfly`
- recent COPG VPN-hiding work

## Findings applied

### 1. v0.1 NetworkInterface rewriting was over-broad

v0.1 could give the virtual IPv4 to every non-VPN interface. v0.2.1 selects one presentation interface, preserves loopback, and filters other physical interfaces from covered Java enumeration/lookup paths.

### 2. Process-local randomisation was inconsistent

v0.2.1 uses a stored package seed and deterministic field selection. A multi-process app therefore resolves the same profile.

### 3. LinkProperties had independent aggregate getters

v0.2.1 adds optional hooks for aggregate address/route/interface methods and Private-DNS metadata where present.

### 4. RouteInfo must not be rewritten globally

Routes are tagged only when obtained from a virtualised LinkProperties result. Route getter masking is then restricted to those tagged objects.

### 5. Java socket/NIO paths can reveal local IPv4

v0.2.1 covers classic socket local-address methods and optional Android libcore NIO channel getters.

### 6. VPN state has more surfaces than TRANSPORT_VPN

v0.2.1 also masks `NET_CAPABILITY_NOT_VPN`, selected ownership/underlying-network metadata, legacy NetworkInfo state, VPN interfaces and common always-on/lockdown settings.

### 7. Proxy hiding must not become routing mutation

Approaches that replace the real proxy selector or active network were rejected because they can change actual connectivity. NetVeil only masks observation-layer metadata.

## Deliberately deferred

- `system_server`/Binder-side rewriting
- native `getifaddrs`/netlink/ioctl interception
- procfs/sysfs interception
- native system-property interception
- Parcel backing-state mutation

These would enlarge the failure domain and should be designed as an optional backend if later required.

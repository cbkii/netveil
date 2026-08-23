# Changelog

## 0.2.1

Second-pass Android 15/16 and Pixel/AOSP hardening baseline.

- Reworked local-interface masking around one stable physical presentation interface.
- Added package-stable whitelist randomisation with explicit reroll support.
- Require every configured IPv4 to have a different, same-subnet gateway.
- Expanded `LinkProperties` coverage, including aggregate/SystemApi views and Private DNS metadata.
- Added tagged `RouteInfo` consistency rather than globally rewriting arbitrary route objects.
- Expanded `NetworkCapabilities` and legacy `NetworkInfo` VPN masking.
- Made direct `hasTransport(TRANSPORT_VPN)` masking independent of private-field reflection.
- Added classic socket and Android NIO local-address masking.
- Added selected Java/Android system-property masking for DNS, gateway, IP, VPN and proxy metadata.
- Added interface lookup/enumeration filtering while preserving loopback.
- Kept proxy handling metadata-only; NetVeil does not force `NO_PROXY` or alter routing.
- Retained modern libxposed API 101 compatibility and Android 15 minimum SDK.
- Documented native, procfs/sysfs, server-side and Parcelable hardening boundaries.

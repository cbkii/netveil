# NetVeil v1 release-readiness gate

This document is the acceptance contract for the first stable NetVeil APK. It distinguishes what CI can prove from what still requires a physical Pixel/Vector run.

## Supported design boundary

NetVeil is an app-process, modern-libxposed API 101 module. It virtualises Android/Java-visible network metadata for selected applications without changing routing, the kernel network namespace, DNS traffic, or the public source IP observed by remote servers.

The v1 Java backend intentionally does **not** claim to hide raw native/kernel observations such as `getifaddrs`, direct netlink, ioctl, `/proc/net/*`, `/sys/class/net/*`, or server-side network characteristics. Those probes are expected to expose real state unless a separate native/kernel component handles them.

`system_server` is explicitly rejected. A process can claim exactly one primary package/profile generation; later packages loaded into the same process cannot stack a second NetVeil identity.

## v1 implementation invariants

A release candidate must satisfy all of these invariants:

- one immutable resolved profile per target process;
- all configured IPv4 values are canonical numeric literals and every selectable IPv4 has a different same-subnet gateway;
- randomisation is package-stable until the user explicitly rerolls;
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
- incomplete required-hook installation is rolled back instead of leaving a partially active profile.

## CI acceptance

Every PR and `main` build must run:

1. workflow/source sanity checks;
2. JVM unit tests;
3. unsuppressed `lintRelease`;
4. debug APK assembly;
5. release APK assembly using an ephemeral non-production signing key;
6. `apksigner` verification of the CI release APK;
7. package identity check for `dev.ip.netveil`;
8. modern `META-INF/xposed/java_init.list` and `META-INF/xposed/module.prop` checks;
9. assertion that the APK does not request `android.permission.INTERNET`;
10. artifact SHA-256 output and CI artifact preservation.

The production Manual Release workflow remains the only workflow allowed to use the repository release-signing secrets.

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
- selected target package/process;
- real interface inventory before NetVeil masking;
- NetVeil/Vector logs;
- JSON probe result;
- real public IP before/after NetVeil activation.

## Test scenarios

Run both a `targetSdk 35` and a `targetSdk 36` probe APK for each applicable device/OS combination.

### Transport/state combinations

- Wi-Fi without VPN;
- Wi-Fi with VPN;
- cellular without VPN;
- cellular with VPN;
- IPv6/dual-stack where available;
- IPv6-only cellular with 464XLAT/CLAT where available;
- Private DNS enabled;
- Android proxy configured;
- fixed profile;
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

For every projected framework object, compare normal getters, `toString()`, and Parcel round-trip output. They must describe the same virtual topology.

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
- no required hook fails installation on the physical matrix;
- no unexpected protective fallback appears in the target-app logs during the matrix;
- all supported Java/framework probes agree with the configured virtual identity;
- real routing/public egress is unchanged by NetVeil;
- known native/kernel disclosures match the documented boundary;
- the production-signed APK passes package/version/signature/Xposed-metadata/checksum verification in Manual Release.

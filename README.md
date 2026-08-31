<p align="center">
  <img src="docs/assets/netveil-icon.svg" alt="NetVeil app icon" width="132" />
</p>

<h1 align="center">NetVeil</h1>

<p align="center"><strong>Control the Android network details visible to selected apps.</strong></p>

NetVeil is a small modern libxposed module for **Android 15+**. It changes selected Java/Android network metadata inside apps already scoped in Vector/LSPosed. It does not reroute traffic or change the public IP seen by remote servers.

## Requirements

- Android 15 or newer (`minSdk 35`), with Android 16 (`targetSdk 36`) as the current platform target.
- Vector **v2.2+** or another framework that genuinely implements libxposed API 102.
- NetVeil enabled and each intended target app selected in the framework scope.

NetVeil itself never broadens framework scope.

## What it can present

- One or more allowed IPv4 identities.
- One or more allowed DNS sets.
- Either no gateway/routes, or a coherent explicit IPv4/prefix/gateway network.
- Hidden app-visible VPN indicators.
- Hidden app-visible proxy indicators.
- Optional IPv6-address suppression on covered metadata surfaces.
- Stable per-app random selection from saved identities and DNS sets.
- A Global profile with optional per-app Custom or Off overrides.
- Country IPv4 candidates for AU, US, GB, ID and FR.

For exact hook coverage and limitations, see [Advanced documentation](README_ADV.md).

## Install and configure

1. Install a NetVeil release APK.
2. Enable NetVeil in Vector/LSPosed and scope the intended apps.
3. Open NetVeil and configure **All scoped apps (Global)**.
4. Add at least one network identity and one DNS set.
5. Leave **Omit gateway & routes** selected for ordinary arbitrary IPv4 identities.
6. Save.
7. Force-stop and reopen affected target apps.

A normal profile edit does not require a phone reboot.

### Per-app modes

| Mode | Behaviour |
| --- | --- |
| **Use Global** | Uses the Global profile. |
| **Custom** | Uses a package-specific profile. |
| **Off for this app** | Installs no NetVeil profile hooks in that app. |

### Network identities

For most spoofed IPv4 values, use:

```text
IPv4: 202.128.115.2
Gateway & routes: omitted
```

Use **Explicit virtual network** only when the app should see a coherent topology such as:

```text
IPv4:    192.168.50.20
Prefix:  /24
Gateway: 192.168.50.1
```

The gateway must differ from the client IPv4 and be inside the configured prefix.

### DNS and randomisation

Enter one selectable DNS set per line, comma-separating multiple servers in one set:

```text
1.1.1.1, 1.0.0.1
9.9.9.9, 149.112.112.112
```

Randomisation always selects a whole network identity and a whole DNS set. A package keeps the same selection until the applicable profile is rerolled and the package process is restarted.

## Country IPv4 presets

Country presets populate the same normal route-hidden identity list. **Add to list** preserves existing identities; **Replace list** replaces only that draft identity list. Saved profiles are changed only when the user presses Save.

The canonical data file is owned by this repository and is both bundled in the APK and anonymously served from public `main`. Manual or scheduled refresh validates online data before replacing the app-private cache. Automatic refresh is off by default.

See [Country data](docs/COUNTRY-DATA.md) for provenance, filtering and update rules.

## Configuration format

NetVeil uses one current profile schema. Incompatible stored NetVeil profile configuration is not converted. Opening the configuration app replaces an incompatible `profiles` preference store with a fresh current store; country-data cache and refresh scheduling state are separate and are not part of that reset.

This keeps runtime profile resolution deterministic and removes upgrade-format branches from injected app processes.

## Privacy and permissions

The APK has no analytics, advertising or telemetry. Its exact Android permission allow-list is:

```text
android.permission.ACCESS_NETWORK_STATE
android.permission.INTERNET
android.permission.RECEIVE_BOOT_COMPLETED
```

These normal permissions support the optional HTTPS country-data refresh and its connectivity-constrained persisted `JobScheduler` job. They do not produce runtime permission prompts.

NetVeil does not upload profiles, selected spoofed values, installed-app lists, framework scope or device identifiers.

## Limits

NetVeil is an **app-visible Java/framework metadata projection**, not a network namespace or packet-routing product. It does not claim to hide:

- native `getifaddrs`, netlink or ioctl observations;
- direct `/proc/net/*` or `/sys/class/net/*` reads where permitted;
- native system-property access;
- the public source IP seen by remote services;
- remote ASN, latency or geolocation inference.

The real VPN/proxy/routing path remains unchanged.

## Development

The maintained baseline is Java 17, Android SDK 36, targetSdk 36, minSdk 35, libxposed API 102, Gradle 9.5.0 and Android Gradle Plugin 9.3.1.

Normal verification:

```bash
gradle --no-daemon --stacktrace --warning-mode=all \
  :app:testDebugUnitTest \
  :app:lintRelease \
  :app:assembleDebug
```

CI additionally builds and verifies an ephemeral-signed release APK, enforces repository independence/current-only policy, validates country data, checks the exact Android permissions and preserves build artefacts.

See [Release readiness](docs/RELEASE-READINESS.md) and [Device test matrix](docs/DEVICE-TEST-MATRIX.md).

## Licence

Apache License 2.0. See [LICENSE](LICENSE).

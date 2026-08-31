<p align="center">
  <img src="docs/assets/netveil-icon.svg" alt="NetVeil app icon" width="132" />
</p>

<h1 align="center">NetVeil</h1>

<p align="center">
  <strong>Control the network details that selected Android apps can see.</strong>
</p>

<p align="center">
  <a href="https://github.com/cbkii/netveil/actions/workflows/build.yml"><img alt="Build status" src="https://github.com/cbkii/netveil/actions/workflows/build.yml/badge.svg" /></a>
  <img alt="Android 15+" src="https://img.shields.io/badge/Android-15%2B-3DDC84?logo=android&logoColor=white" />
  <a href="LICENSE"><img alt="License: Apache-2.0" src="https://img.shields.io/badge/License-Apache--2.0-blue.svg" /></a>
</p>

NetVeil is a small **Vector/LSPosed module for Android 15 and newer**. It changes selected network information reported to apps that you choose in Vector/LSPosed.

You can give scoped apps a chosen IPv4 identity and DNS servers, hide common VPN or proxy indicators, and optionally hide IPv6 addresses on the Android APIs NetVeil covers.

> [!IMPORTANT]
> NetVeil does **not** change your real/public IP address, route traffic, disconnect a VPN, or act as a VPN/proxy. Websites and remote servers still see the real network connection used by the device.

## What NetVeil can do

- Use one or more allowed IPv4 identities.
- Use one or more allowed DNS sets.
- Omit gateway and route details, or present a coherent virtual IPv4 network.
- Hide common app-visible VPN indicators.
- Hide common app-visible proxy indicators.
- Optionally suppress IPv6 addresses on covered APIs.
- Randomly choose from your saved identities and DNS sets while keeping each app internally consistent.
- Use one **Global** profile for all scoped apps, with optional per-app overrides.
- Add IPv4 candidates from built-in country presets for Australia, United States, United Kingdom, Indonesia and France.

For implementation details and the exact API coverage, see [Advanced documentation](README_ADV.md).

## Requirements

- **Android 15 or newer** (`API 35+`).
- A working **Vector/LSPosed** environment compatible with modern libxposed modules.
- NetVeil enabled in Vector/LSPosed.
- Each app you want NetVeil to affect selected in Vector/LSPosed scope.

NetVeil does not expand its own scope. If an app is not scoped in Vector/LSPosed, NetVeil does not run inside that app.

See [Compatibility](docs/COMPATIBILITY.md) for the current support baseline.

## Install

1. Download the APK from [GitHub Releases](https://github.com/cbkii/netveil/releases).
2. Install the APK normally and open **NetVeil**.
3. Enable NetVeil in Vector/LSPosed.
4. Select the apps you want NetVeil to affect in Vector/LSPosed scope.
5. Configure the **All scoped apps (Global)** profile in NetVeil.
6. Save your changes.
7. Force-stop and reopen each affected app so it starts with the new profile.

You can then add per-app overrides if needed.

## Quick setup

### 1. Start with Global

NetVeil opens on:

```text
★ All scoped apps (Global)
```

Global is the default profile for every app that is already scoped in Vector/LSPosed.

A simple starting configuration is:

1. Leave **Enable masking for this profile** on.
2. Add one or more **Network identities**.
3. For ordinary spoofed IPv4 values, use **Omit gateway & routes**.
4. Add at least one DNS set.
5. Leave the privacy switches you want enabled.
6. Turn on randomisation only if you want NetVeil to choose between multiple saved identities or DNS sets.
7. Press **Save changes**.

### 2. Restart the target app

Profile and scope changes apply when the target process starts. Force-stop and reopen the app after:

- changing a profile;
- changing Vector/LSPosed scope;
- pressing **Reroll** or **Reroll Global**.

A full phone reboot is normally unnecessary for an ordinary profile edit.

## Global and per-app profiles

Vector/LSPosed scope is always the outer gate. Inside that scope, NetVeil provides three choices for an individual app:

| Mode | What it does |
| --- | --- |
| **Use Global** | Uses the Global profile. |
| **Custom** | Uses separate settings saved only for that app. |
| **Off for this app** | NetVeil installs no profile hooks for that app, even if Vector/LSPosed still scopes it. |

Use **Global** for the common setup and create **Custom** profiles only where an app needs different values.

## Network identities

Each saved identity keeps its IPv4 address and route settings together. NetVeil does not randomly combine an address with an unrelated gateway.

### Omit gateway & routes — recommended

Use this for most arbitrary IPv4 identities. You only need to enter an IPv4 address.

```text
IPv4: 202.128.115.2
Gateway & routes: omitted
```

This replaces the old need to use a `/0` prefix just to make unrelated public IPv4 values pass gateway validation.

### Explicit virtual network — advanced

Use this only when the app should see a coherent IPv4 network with a prefix and gateway.

```text
IPv4:    192.168.50.20
Prefix:  /24
Gateway: 192.168.50.1
```

The gateway must be different from the IPv4 address and belong to the selected subnet. NetVeil validates this before saving.

For the underlying projection model, see [Advanced documentation](README_ADV.md#network-projection-model).

## DNS and randomisation

Enter one selectable DNS set per line. Separate multiple servers in the same set with commas.

```text
1.1.1.1, 1.0.0.1
9.9.9.9, 149.112.112.112
```

When randomisation is enabled, NetVeil selects a **whole network identity** and a **whole DNS set**. The choice stays stable for that app until you reroll it.

With Global randomisation, different apps can receive different stable selections from the same allowed lists.

## Country IPv4 presets

The **Country IPv4 preset** section can add a small set of candidate addresses for:

- Australia;
- United States;
- United Kingdom;
- Indonesia;
- France.

The normal defaults keep only high-confidence provider candidates and exclude addresses currently known by the data sources as VPN, proxy or Tor endpoints.

Use:

- **Add to list** to keep your existing identities and append the preset;
- **Replace list** to replace the current identity list with the preset.

Imported addresses use **Omit gateway & routes**. NetVeil does not invent an ISP gateway for them.

Manual refresh downloads and validates a small public data pack. Automatic refresh is **off by default** and, when enabled, updates only the cached country data—not your saved profiles.

For data sources, filtering rules and maintenance details, see [Country data](docs/COUNTRY-DATA.md).

## Privacy and permissions

NetVeil has no analytics, advertising or telemetry.

The APK currently requests only these install-time permissions:

| Permission | Why it is used |
| --- | --- |
| `android.permission.INTERNET` | Download the optional public country-data pack. |
| `android.permission.RECEIVE_BOOT_COMPLETED` | Allow Android to preserve the optional scheduled country-data refresh job across reboot. |

NetVeil does not upload your profiles, installed-app list, selected spoofed values, device identifiers or Vector/LSPosed scope.

If the country-data endpoint is unavailable, the bundled or previously cached data remains usable.

## Limits

NetVeil changes information returned by covered **Java/Android framework APIs** inside scoped app processes. It does not claim to hide every possible network signal.

In particular, an app may still learn about the real connection through native code, direct kernel/procfs/sysfs access, server-side IP checks, latency, geolocation or other signals outside NetVeil's covered API surface.

For the exact boundary and covered framework surfaces, see [Advanced documentation](README_ADV.md#covered-surfaces-and-hard-boundaries).

## Troubleshooting

**Nothing changes in an app:** confirm NetVeil is enabled and the app is selected in Vector/LSPosed scope, then force-stop and reopen the app.

**The app still shows old values:** the old process may still be running. Force-stop it completely and reopen it.

**An identity will not save:** use **Omit gateway & routes** for an arbitrary IPv4 value. Use **Explicit virtual network** only when the prefix and gateway form a valid subnet.

**Country refresh fails:** keep using the bundled/cached data. Online refresh requires the configured data endpoint to be anonymously reachable over HTTPS.

For deeper diagnostics, compatibility notes and test procedures, use the links below.

## Documentation

| Document | Use it for |
| --- | --- |
| [Advanced documentation](README_ADV.md) | Architecture, hook coverage, build/CI and contributor details. |
| [Compatibility](docs/COMPATIBILITY.md) | Supported Android/Vector baseline and compatibility notes. |
| [Country data](docs/COUNTRY-DATA.md) | Preset generation, filtering, sources and refresh policy. |
| [Changelog](CHANGELOG.md) | Version history and changes. |
| [Design notes](docs/DESIGN.md) | Internal design decisions. |
| [Validation](docs/VALIDATION.md) | Focused engineering validation notes. |
| [Device test matrix](docs/DEVICE-TEST-MATRIX.md) | Physical Android/Pixel test coverage. |
| [v1 release readiness](docs/V1-RELEASE-READINESS.md) | Stable-release acceptance gates. |

## Licence

NetVeil is licensed under the [Apache License 2.0](LICENSE).

# Device test matrix

Use this as the physical runtime sheet for [RELEASE-READINESS.md](RELEASE-READINESS.md). Record source SHA, APK SHA-256, Android build and framework version for every run.

## Baseline

- [ ] NetVeil source and APK hashes recorded.
- [ ] Android build fingerprint/security patch recorded.
- [ ] Vector version is v2.2+ and API-102 module loading is confirmed.
- [ ] Intended target apps only are scoped.
- [ ] Real interface inventory is recorded before masking.
- [ ] Overlapping network-spoofing hooks from other modules are disabled for the baseline.

## Current profile schema

- [ ] A fresh install initialises the current profile store.
- [ ] An absent/mismatched profile schema produces no effective profile in an injected process.
- [ ] Opening NetVeil with incompatible profile preferences replaces only the `profiles` store with an empty current store.
- [ ] Country-data cache and automatic-refresh settings are unaffected by that profile-store reset.
- [ ] No incompatible profile values are translated into current identities or policies.

## Basic setup

Start from an empty current profile store.

- [ ] NetVeil opens in **Basic** mode.
- [ ] Basic selects the saved country when present; true first-run selection follows a supported device-locale country and otherwise falls back to Australia.
- [ ] Basic immediately builds a usable draft from bundled/valid cached data without waiting for network access.
- [ ] The draft contains high-confidence/non-anonymous country identities, hidden routes, 2–4 bundled DNS sets, randomisation enabled, VPN/proxy hiding enabled and IPv6 suppression enabled.
- [ ] Opening Basic and changing Country do not create/save Global.
- [ ] Guidance clearly indicates that Apply is required or Advanced can be used and saved.
- [ ] **Apply Global profile** creates the one ordinary Global profile.
- [ ] Saved Basic state reports `Global profile active · <country>`.
- [ ] A changed recommendation reports an available Global update rather than silently applying it.
- [ ] Background country-data refresh never rewrites the saved Global profile.
- [ ] **Refresh & replace Global** refreshes, regenerates and replaces only after a complete valid recommendation is available.
- [ ] Failed manual refresh leaves the previous saved Global profile unchanged and reports the failure.

## Basic/Advanced ownership

- [ ] A Basic-generated Global profile is fully visible/editable in Advanced.
- [ ] Saving a materially customised Global through Advanced causes Basic to report an Advanced-customised Global state.
- [ ] With **Allow Basic to replace Advanced Global** off, Basic Apply/Update/Refresh cannot overwrite that Advanced-owned profile.
- [ ] Changing only the override toggle never rewrites Global.
- [ ] With the toggle on, an explicit Basic replacement action can replace the Advanced-owned Global.
- [ ] An incomplete/invalid Advanced-owned Global remains protected while the toggle is off.
- [ ] There is still only one runtime Global profile; no Basic overlay/precedence state affects hook resolution.

## Advanced configuration UX

- [ ] **Advanced** retains target selection, Global/Custom/Off, profile enabled state, identities, route mode, country add/replace, DNS editing, randomisation, privacy, effective preview, Save and Reroll.
- [ ] **Populate recommended DNS** fills the selected country's bundled 2–4 complete DNS sets without saving them.
- [ ] The populated DNS lines can be edited/removed before Save.
- [ ] **Clear selected profile** appears immediately below **Load selected profile**.
- [ ] Clear confirmation is simply `Clear <Profile name>?` with Cancel/Clear actions.
- [ ] Clearing Global retains separate per-app Custom profiles.
- [ ] Clearing a per-app stored profile/policy returns the package to ordinary Global inheritance.
- [ ] Clear is disabled when the selected target has no stored state.
- [ ] The previous duplicate bottom Reset/Remove action is absent.
- [ ] Loading/switching/clearing/replacing with meaningful unsaved Advanced edits prompts `Discard unsaved changes?` once.
- [ ] Cancel retains edits; Discard proceeds; Save returns the editor to a clean state.

## Global and per-app resolution

Create a valid Global profile and exercise at least two scoped packages.

- [ ] A package with no per-app mode uses Global.
- [ ] **Use Global** uses Global.
- [ ] **Custom** uses the package-specific profile.
- [ ] **Off for this app** installs no NetVeil profile hooks.
- [ ] Clearing an override returns the package to Global.
- [ ] A package outside framework scope remains unaffected.
- [ ] A later package loaded into an already-claimed process cannot install a second NetVeil identity.

## Route-hidden identity

Configure one IPv4 identity with **Omit gateway & routes** and one DNS set.

- [ ] Profile saves without a gateway.
- [ ] Effective core profile has no gateway value.
- [ ] `WifiInfo` getter/string/Parcel views agree on the configured IPv4 where applicable.
- [ ] `DhcpInfo` uses configured IPv4/DNS and a neutral zero gateway value.
- [ ] Android string gateway-property getters return their supplied/default absence value rather than `0.0.0.0`.
- [ ] `LinkProperties` exposes the configured IPv4/DNS but no synthetic IPv4 connected/default route.
- [ ] Selected presentation `NetworkInterface` exposes the virtual IPv4.
- [ ] Classic/NIO local IPv4 getters expose the virtual IPv4.
- [ ] IPv6 sockets retain IPv6 address family.

## Explicit virtual network

Configure `192.168.50.20/24` via `192.168.50.1`.

- [ ] UI accepts the coherent tuple.
- [ ] Gateway equal to client IPv4 is rejected.
- [ ] Out-of-subnet gateway is rejected.
- [ ] `/0`, `/31` and `/32` edge behaviour matches validation semantics.
- [ ] DHCP gateway/netmask and projected routes agree with the explicit identity.
- [ ] Getter/string/Parcel views of projected route/link objects agree.

## Stable randomisation

- [ ] Multiple identities and DNS sets always resolve as complete configured units.
- [ ] One package remains stable across process restarts until reroll.
- [ ] Two inheriting packages can derive different stable selections from Global.
- [ ] Global reroll affects inheriting packages after process restart.
- [ ] Custom reroll affects only that package.

## VPN visibility

Test without and with the normal VPN active.

- [ ] Raw VPN `NetworkCapabilities` is projected without VPN transport indicators.
- [ ] Non-VPN capabilities remain unchanged except for independently configured features.
- [ ] `getAllNetworks()` does not disclose non-active VPN handles while preserving the real active handle needed for connectivity.
- [ ] Direct `TRANSPORT_VPN` network requests are suppressed.
- [ ] VPN-style interface enumeration/lookups are hidden when requested.
- [ ] Selected always-on/lockdown Settings values are hidden.
- [ ] Real traffic continues over the real VPN.
- [ ] Remote public-IP check confirms NetVeil did not reroute traffic.

## Deprecated NetworkInfo coverage

- [ ] Direct VPN-type `getNetworkInfo` query returns no VPN result.
- [ ] `getAllNetworkInfo` omits raw VPN entries.
- [ ] Direct VPN-type network-handle query is hidden.
- [ ] Active/raw VPN `NetworkInfo` type/name/extra-info views present the selected physical transport.
- [ ] `toString()` agrees with those getters.
- [ ] Parcel round-trip agrees with getters/string and does not corrupt the destination Parcel on projection failure.
- [ ] With VPN hiding disabled, these surfaces pass through.

## Proxy and properties

- [ ] Java proxy properties are hidden with correct one/two-argument default semantics.
- [ ] Selected Android proxy properties are hidden without changing real routing.
- [ ] Route-hidden Android gateway property uses the caller/default value.
- [ ] Explicit-route Android gateway property exposes the configured gateway.
- [ ] DNS/IP property projections match the effective profile.

## Wi-Fi, cellular and CLAT

- [ ] Wi-Fi primary.
- [ ] Cellular primary.
- [ ] Wi-Fi -> cellular after full target-process restart.
- [ ] Presentation interface selection matches the actual Pixel interface inventory.
- [ ] Cellular `v4-rmnet*` CLAT collapses to cellular where present.
- [ ] Wi-Fi CLAT collapses to Wi-Fi where present.

## IPv6

- [ ] With suppression enabled, covered metadata collections omit real IPv6 addresses where absence is valid.
- [ ] With suppression disabled, IPv6 passthrough coexists with IPv4 projection.
- [ ] IPv6 socket getters never return a fabricated IPv4 address.

## Country data

- [ ] AU/US/GB/ID/FR produce candidates with default filters.
- [ ] **Add to list** deduplicates while preserving manual identities.
- [ ] **Replace list** changes only the draft identity list.
- [ ] Imported values remain route-hidden and do not invent gateways.
- [ ] Bundled data works offline.
- [ ] **Refresh now** performs a real online request and reports Updated or Online data already current.
- [ ] Successful online data is validated before atomic cache replacement.
- [ ] Malformed/oversized/older/conflicting data leaves the previous valid data intact.
- [ ] Manual and periodic refresh cannot race one another over the cache/temp file.
- [ ] Automatic refresh is off by default and persists the selected Monthly/Weekly/Daily cadence when enabled.
- [ ] Ordinary refresh changes only country-data cache, never saved profiles.

## General configuration UX

- [ ] Global is the default Advanced target.
- [ ] Saved and launchable targets are visible without `QUERY_ALL_PACKAGES`.
- [ ] Manual package entry works.
- [ ] Editing target text cannot silently save the visible form under another package.
- [ ] Inline errors and first-invalid-field focus are usable.
- [ ] Basic remains compact at normal and large font/display scale.
- [ ] Edge-to-edge, cutout, gesture/three-button navigation, landscape and large font/display scaling remain usable.

## Expected disclosures

Record these as expected boundaries rather than Java-backend failures:

- [ ] native `getifaddrs`;
- [ ] raw netlink/ioctl;
- [ ] direct procfs/sysfs reads where allowed;
- [ ] native system properties;
- [ ] server-side public IP, ASN, latency or geolocation inference.

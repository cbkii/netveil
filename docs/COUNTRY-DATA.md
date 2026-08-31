# Country ISP IPv4 candidate data

NetVeil can populate a profile's IPv4 identity list with a small representative set of public IPv4 addresses associated with consumer/access-provider networks in:

- Australia (`AU`)
- United States (`US`)
- United Kingdom (`GB`)
- Indonesia (`ID`)
- France (`FR`)

This is a configuration convenience feature. It does **not** prove that a candidate belongs to a particular subscriber, is residential at the moment it is used, or can never be used by a VPN/proxy service.

## User-visible behaviour

The app intentionally exposes only:

- country;
- **Exclude medium/low-confidence providers** (on by default);
- **Exclude known VPN / proxy / Tor addresses** (on by default);
- **Add to list** or **Replace list**;
- manual **Refresh country data now**;
- optional automatic refresh (off by default; monthly by default when enabled, with weekly/daily choices).

There is no per-ISP/ASN management UI. Provider and routing detail remain generation/audit metadata.

Imported values are stored as ordinary route-hidden `NetworkIdentity` values. NetVeil never invents an ISP gateway from BGP/RIR data and never changes actual routing or public egress.

Automatic refresh updates only the cached country database. It never rewrites an existing saved profile. The user remains authoritative over profile changes through **Add** and **Replace**.

## Data flow

```text
public routing/allocation/provider sources
              +
       exclusion sources
              |
              v
.github/scripts/generate_country_pack.py
              |
              v
app/src/main/assets/country-ip-pack.json
              |
       bundled APK fallback
              |
              +---- HTTPS refresh ----> app-private validated cache
```

The Android app does not scrape RIR, BGP, PeeringDB, Tor or blocklist services itself. It downloads only the compact generated NetVeil pack.

Fallback order is:

1. valid refreshed cache;
2. valid previous cache;
3. bundled APK pack.

A failed download, malformed pack, stale/older pack or non-atomic cache replacement never deletes/replaces the last valid data. The bundled pack remains usable offline.

## Pack validation contract

The generated/downloaded data is treated as untrusted structured input. The current schema requires:

- schema version `1`;
- a parseable UTC `generated_at` timestamp that is not implausibly far in the future;
- exactly the five MVP country keys `AU`, `US`, `GB`, `ID`, `FR`;
- a bounded candidate count per country;
- canonical public/global IPv4 candidates only, excluding private/shared/link-local/documentation/multicast/reserved address families;
- no duplicate candidate within a country;
- confidence of `high`, `medium` or `low`;
- explicit boolean `known_vpn`, `known_proxy`, `known_tor` values;
- provider name and positive ASN provenance.

The Android cache loader also refuses to replace a newer valid cache with an older downloaded pack. Cache replacement is atomic; if the platform cannot provide atomic replacement, refresh fails safely and keeps the old/bundled data.

## Generator evidence

Candidate generation deliberately combines different kinds of evidence rather than treating any one database as proof of a residential address.

### RIR delegated/extended statistics

Country-allocation corroboration comes from the relevant RIR's standard delegated/extended statistics:

- APNIC: AU, ID
- ARIN: US
- RIPE NCC: GB, FR

Only active `allocated`/`assigned` IPv4 records are eligible; `available` and other non-active resource states are ignored. These files describe Internet-number-resource allocation/delegation and are not subscriber lists. NetVeil does not download or redistribute bulk Whois contact data.

References:

- APNIC/NRO RIR statistics exchange format: <https://www.apnic.net/about-apnic/corporate-documents/documents/resource-guidelines/rir-statistics-exchange-format/>
- ARIN extended delegation statistics: <https://www.arin.net/reference/research/statistics/nro_stats/>

### RouteViews

RouteViews is used to obtain current IPv4 prefixes originated by a curated access-provider ASN. The generator uses `/asn/<ASN>?af=4`, respects the unauthenticated API rate limit, validates the response shape, and acknowledges RouteViews as the routing-data source.

References:

- API documentation: <https://api.routeviews.org/docs/>
- RouteViews project: <https://www.routeviews.org/>

### PeeringDB

A narrow anonymous lookup for a curated ASN may be used as **optional corroboration** of an access-network classification (for example `Cable/DSL/ISP`). NetVeil does not copy or redistribute the PeeringDB database. Provider selection does not depend solely on PeeringDB, and an unavailable/rate-limited PeeringDB lookup is non-fatal.

PeeringDB data/API use remains subject to its Acceptable Use Policy. Do not repurpose the generator to bulk-download or redistribute PeeringDB records.

References:

- API specification: <https://docs.peeringdb.com/api_specs/>
- Acceptable Use Policy: <https://www.peeringdb.com/aup>

### VPN/proxy/Tor exclusion intelligence

The generator currently uses the following optional exclusion sources where available:

- Tor Project bulk exit data: <https://check.torproject.org/torbulkexitlist>
- X4BNet `lists_vpn`: <https://github.com/X4BNet/lists_vpn> (MIT-licensed repository)
- `monosans/proxy-list`: <https://github.com/monosans/proxy-list> (MIT-licensed repository)

An unavailable optional exclusion source is reported as a warning and does not destroy the previous valid pack. Generated packs record exclusion-source availability separately from the per-candidate flags so a source outage is auditable rather than silently indistinguishable from a clean result.

The app's default **Exclude known VPN / proxy / Tor addresses** filter removes candidates carrying any available positive signal. The wording is intentionally **known** rather than guaranteed: no public blocklist can prove that an address will never act as a VPN/proxy endpoint later.

## Candidate generation rules

For each supported country the generator:

1. loads active country IPv4 allocations/assignments from its RIR statistics;
2. queries current RouteViews-originated IPv4 prefixes for a small curated set of consumer/access-provider ASNs;
3. optionally corroborates provider classification through PeeringDB;
4. rejects non-global/special-use and very small unusable prefixes;
5. derives a deterministic host address inside qualifying prefixes without contacting that address;
6. applies available VPN/proxy/Tor exclusion intelligence and records source availability;
7. round-robins across providers so one provider does not dominate the pack;
8. emits only a small reserve set (maximum 18 per country; the app normally exposes at most 12 after filters).

The generator never:

- pings candidates;
- opens connections to candidates;
- port-scans candidates;
- attempts subscriber identification;
- derives or stores subscriber contact information.

## Confidence

Confidence is internal evidence quality, not a claim about a specific person or household.

- **High**: current BGP origin + RIR country corroboration + curated/credible access-provider classification.
- **Medium**: current BGP origin and country/provider evidence with weaker access-provider corroboration.
- **Low**: reserved for weaker candidates; excluded by the default UI policy.

The normal UI defaults to excluding medium/low confidence candidates.

## Network permission and privacy

NetVeil requests Android `INTERNET` solely so the configuration app/background refresh job can fetch the small public country pack over HTTPS.

It does **not** send:

- NetVeil profile contents;
- selected spoofed IP/DNS values;
- installed-app lists;
- Vector/LSPosed scope;
- device identifiers;
- analytics or telemetry.

`RECEIVE_BOOT_COMPLETED` is also declared because Android requires it for a `JobScheduler` job marked persisted across reboot. Automatic refresh itself is off by default.

The Xposed module's target-process networking behaviour is unchanged by these permissions; country presets only populate the same canonical profile model used by manual values.

## Refresh and failure policy

Repository automation performs a bounded live regeneration daily. Installed apps refresh only when the user presses **Refresh country data now** or explicitly enables periodic refresh.

When periodic refresh is enabled:

- monthly is the default;
- weekly and daily are optional;
- Android `JobScheduler` is used rather than adding AndroidX/WorkManager;
- a network constraint is required;
- execution is inexact and battery/Doze-aware;
- failures retain the last valid pack and stay on the configured cadence rather than creating an additional retry schedule.

The configured pack URL must be anonymously HTTPS-readable for online refresh. If the repository/data endpoint is private or unavailable, refresh fails safely and NetVeil continues with cached/bundled data. This is a deployment condition, not a reason to weaken cache validation or add embedded credentials to the APK.

## Repository automation and permissions

Pull-request country-data validation runs with **read-only repository permission**. It runs deterministic tests plus the live generator and, when live generation succeeds, uploads the generated compact pack as a short-retention workflow artifact for audit.

Only scheduled/manual updater execution receives `contents: write`. That update job consumes the already validated artifact, re-runs pack tests, commits only when bytes changed, and uses a force-with-lease guard so it cannot silently overwrite a branch that moved during execution.

This separation keeps normal PR validation non-mutating while still making the exact live-generated pack inspectable.

## Maintenance

The country pack is deliberately generated data and should remain small. Do not replace it with bulk RIR/BGP/GeoIP/provider databases.

Before adding a new source or country:

- verify current source format and automated-use/redistribution terms;
- add deterministic parser/fixture tests;
- bound downloads, timeouts and retries;
- keep optional-source failures non-fatal when a trustworthy existing pack remains;
- validate the final generated schema, provenance and global IPv4 values;
- update attribution in this document.

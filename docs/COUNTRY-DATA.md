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
- **High-confidence providers only** (on by default);
- **Exclude known VPN / proxy / Tor addresses** (on by default);
- **Add to list** or **Replace list**;
- manual **Refresh now**;
- optional automatic refresh (off by default; monthly by default when enabled, with weekly/daily choices).

There is no per-ISP/ASN management UI. Provider and routing detail remain generation/audit metadata.

Imported values are stored as ordinary route-hidden `NetworkIdentity` values. NetVeil never invents an ISP gateway from BGP/RIR data and never changes actual routing or public egress.

Refreshing updates only the country candidate database. It never rewrites an existing saved profile. The user remains authoritative over profile changes through **Add to list** and **Replace list**.

## Canonical data flow

`cbkii/netveil` is public. There is one canonical generated pack:

`app/src/main/assets/country-ip-pack.json`

The same file is both bundled into the APK and anonymously served from `main`:

`https://raw.githubusercontent.com/cbkii/netveil/main/app/src/main/assets/country-ip-pack.json`

```text
public routing/allocation/provider sources
              +
       exclusion sources
              |
              v
.github/scripts/generate_country_pack.py
              |
              v
strict validation
              |
              v
cbkii/netveil/main/app/src/main/assets/country-ip-pack.json
              |                         |
              |                         +---- anonymous HTTPS refresh
              |                                      |
              v                                      v
       bundled APK fallback                 app-private validated cache
```

There is no second mirror repository, no GitHub Pages data copy, no application token, and no cross-repository publication credential.

The Android app does not scrape RIR, BGP, PeeringDB, Tor or blocklist services itself. **Refresh now** means "check the latest validated NetVeil candidate dataset online". It does not generate candidate addresses on the phone.

## Bundled data, online cache and refresh outcomes

The dataset's `generated_at` value is the time the canonical dataset was generated. It is not the time a phone checked for updates.

NetVeil tracks device-local online-check metadata separately. The UI distinguishes:

- **Bundled with APK** — the currently active pack came from the APK;
- **Online cache** — the currently active pack came from a previously validated online update;
- **Updated online** — an online check succeeded and a newer generated pack was atomically installed;
- **Online data already current** — an online check succeeded and the active pack already represents that generated version;
- **Refresh failed · using previous online cache** — the online request/validation failed but a valid online cache remains active;
- **Refresh failed · using bundled APK data** — the online request/validation failed and the bundled pack remains active.

A successful unchanged check records a new **Last checked online** timestamp even though it does not rewrite the cache. Loading an existing cache at app startup does not imply or report a new network request.

## Update and anti-rollback policy

Downloaded packs are parsed and validated before any cache mutation.

Given the currently active valid pack and a validated remote pack:

- a newer `generated_at` is **UPDATED**;
- the same `generated_at` with the same candidate data is **UNCHANGED**;
- an older `generated_at` is rejected;
- the same `generated_at` with changed candidate data is rejected as an ambiguous same-version mutation.

Materially changed canonical data therefore must advance `generated_at`.

Cache replacement is atomic. If the platform cannot provide atomic replacement, refresh fails safely and keeps the previous valid cache or bundled pack.

Fallback order is:

1. valid online cache at least as new as the bundled pack;
2. bundled APK pack.

A failed download, malformed pack, older pack, same-version conflict or non-atomic cache replacement never destroys the last valid data.

## Pack validation contract

The generated/downloaded data is treated as untrusted structured input. The current schema requires:

- schema version `1`;
- a parseable UTC `generated_at` timestamp that is not implausibly far in the future;
- exactly the five country keys `AU`, `US`, `GB`, `ID`, `FR`;
- a bounded candidate count per country;
- canonical public/global IPv4 candidates only, excluding private/shared/link-local/documentation/multicast/reserved address families;
- no duplicate candidate within a country;
- confidence of `high`, `medium` or `low`;
- explicit boolean `known_vpn`, `known_proxy`, `known_tor` values;
- provider name and positive ASN provenance.

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

An unavailable optional exclusion source is reported as a warning and does not destroy the canonical valid pack. Generated packs record exclusion-source availability separately from per-candidate flags so a source outage is auditable rather than silently indistinguishable from a clean result.

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

The normal UI defaults to high-confidence providers only.

## Network permission and privacy

NetVeil requests three normal Android permissions for this feature:

- `INTERNET` — fetches the small canonical country pack over HTTPS;
- `ACCESS_NETWORK_STATE` — required by modern Android when a `JobScheduler` job declares a connectivity constraint;
- `RECEIVE_BOOT_COMPLETED` — required for a `JobScheduler` job persisted across reboot.

None of these is a runtime permission prompt. Automatic refresh itself remains off by default.

NetVeil does **not** send:

- NetVeil profile contents;
- selected spoofed IP/DNS values;
- installed-app lists;
- Vector/LSPosed scope;
- device identifiers;
- analytics or telemetry.

The Xposed module's target-process networking behaviour is unchanged by these permissions; country presets only populate the same canonical profile model used by manual values.

## Manual and automatic refresh

Installed apps refresh only when the user presses **Refresh now** or explicitly enables periodic refresh.

When periodic refresh is enabled:

- monthly is the default;
- weekly and daily are optional;
- Android `JobScheduler` is used rather than AndroidX/WorkManager;
- a network constraint is required;
- scheduling is transactional: a rejected job does not leave automatic refresh recorded as enabled;
- startup restoration failures are contained and disable automatic refresh rather than crashing the Activity;
- execution is inexact and battery/Doze-aware;
- manual and scheduled refresh record the same `UPDATED` / `UNCHANGED` / `FAILED` outcome metadata;
- failures retain the last valid pack and stay on the configured cadence rather than creating an additional retry schedule.

## Repository automation and permissions

Pull-request country-data validation runs with **read-only repository permission**. It validates the checked-in pack, tests the generator, verifies the public endpoint contract and can generate an audit candidate pack without mutating the repository.

Scheduled/manual execution can update the canonical pack only when running against authoritative `main`. The write job:

1. downloads the validated generated artifact from the read-only job;
2. validates it again;
3. confirms `main` has not moved;
4. compares material dataset content while ignoring a generation timestamp that changed by itself;
5. creates no commit when the material dataset is unchanged;
6. if changed, commits only `app/src/main/assets/country-ip-pack.json` using the job-scoped `GITHUB_TOKEN` with `contents: write`;
7. pushes with branch-movement/lease protection;
8. verifies the immutable commit-specific raw URL;
9. verifies the stable `main` raw URL with bounded retries.

A workflow dispatch from a non-main branch may validate/generate data, but it cannot update the canonical public dataset.

Normal PR/review CI validates that the public `main` endpoint is accessible and valid without requiring it to equal a feature branch's worktree. Authoritative `main`/post-publication validation additionally requires the remote canonical pack to equal the local canonical pack.

No PAT or repository secret is required for country-data publication.

## Maintenance

The country pack is deliberately generated data and should remain small. Do not replace it with bulk RIR/BGP/GeoIP/provider databases.

Before adding a new source or country:

- verify current source format and automated-use/redistribution terms;
- add deterministic parser/fixture tests;
- bound downloads, timeouts and retries;
- keep optional-source failures non-fatal when a trustworthy existing pack remains;
- validate the final generated schema, provenance and global IPv4 values;
- update attribution in this document.

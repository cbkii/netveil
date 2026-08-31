package io.github.cbkii.netveil.country;

import io.github.cbkii.netveil.config.Ipv4;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parsed, validated country/provider candidate pack. */
public final class CountryPack {
    public static final int SCHEMA = 1;
    public static final int DEFAULT_LIMIT = 12;
    public static final int MIN_COUNTRY_CANDIDATES = 8;
    public static final int MAX_COUNTRY_CANDIDATES = 64;
    public static final List<String> REQUIRED_COUNTRIES = List.of("AU", "US", "GB", "ID", "FR");

    public final String generatedAt;
    private final Instant generatedInstant;
    private final Map<String, List<Candidate>> countries;

    private CountryPack(String generatedAt, Instant generatedInstant,
                        Map<String, List<Candidate>> countries) {
        this.generatedAt = generatedAt;
        this.generatedInstant = generatedInstant;
        this.countries = Collections.unmodifiableMap(countries);
    }

    public static CountryPack parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optInt("schema", -1) != SCHEMA) {
            throw new JSONException("Unsupported country-pack schema");
        }
        String generatedAt = root.optString("generated_at", "").trim();
        if (generatedAt.isEmpty()) throw new JSONException("Missing generated_at");
        final Instant generatedInstant;
        try {
            generatedInstant = Instant.parse(generatedAt);
        } catch (DateTimeParseException e) {
            throw new JSONException("Invalid generated_at timestamp");
        }
        if (generatedInstant.isAfter(Instant.now().plus(2, ChronoUnit.DAYS))) {
            throw new JSONException("generated_at is implausibly far in the future");
        }

        JSONObject rawCountries = root.getJSONObject("countries");
        Set<String> suppliedCountries = new LinkedHashSet<>();
        Iterator<String> countryKeys = rawCountries.keys();
        while (countryKeys.hasNext()) suppliedCountries.add(countryKeys.next());
        if (!suppliedCountries.equals(new LinkedHashSet<>(REQUIRED_COUNTRIES))) {
            throw new JSONException("Country set differs from supported AU/US/GB/ID/FR schema");
        }

        Map<String, List<Candidate>> parsed = new LinkedHashMap<>();
        for (String code : REQUIRED_COUNTRIES) {
            JSONArray values = rawCountries.optJSONArray(code);
            if (values == null) throw new JSONException("Missing country " + code);
            if (values.length() < MIN_COUNTRY_CANDIDATES
                    || values.length() > MAX_COUNTRY_CANDIDATES) {
                throw new JSONException("Country " + code + " candidate count is outside "
                        + MIN_COUNTRY_CANDIDATES + ".." + MAX_COUNTRY_CANDIDATES);
            }

            List<Candidate> candidates = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.getJSONObject(i);
                String ipv4 = value.optString("ipv4", "").trim();
                if (!Ipv4.isLiteral(ipv4)) {
                    throw new JSONException("Invalid IPv4 candidate in " + code);
                }
                ipv4 = Ipv4.canonical(ipv4);
                if (!isPublicCandidate(ipv4)) {
                    throw new JSONException("Non-public/special IPv4 candidate in " + code + ": " + ipv4);
                }
                if (!seen.add(ipv4)) {
                    throw new JSONException("Duplicate IPv4 candidate in " + code + ": " + ipv4);
                }

                String confidence = value.optString("confidence", "").trim().toLowerCase();
                if (!confidence.equals("high") && !confidence.equals("medium")
                        && !confidence.equals("low")) {
                    throw new JSONException("Invalid confidence for " + code + " candidate " + ipv4);
                }
                boolean knownVpn = strictBoolean(value, "known_vpn", code, ipv4);
                boolean knownProxy = strictBoolean(value, "known_proxy", code, ipv4);
                boolean knownTor = strictBoolean(value, "known_tor", code, ipv4);
                String provider = value.optString("provider", "").trim();
                int asn = value.optInt("asn", 0);
                if (provider.isEmpty() || asn <= 0) {
                    throw new JSONException("Missing provider/ASN provenance for " + code
                            + " candidate " + ipv4);
                }

                candidates.add(new Candidate(
                        ipv4, confidence, knownVpn, knownProxy, knownTor, provider, asn));
            }
            parsed.put(code, Collections.unmodifiableList(candidates));
        }
        return new CountryPack(generatedAt, generatedInstant, parsed);
    }

    public boolean isAtLeastAsNewAs(CountryPack other) {
        return other == null || !generatedInstant.isBefore(other.generatedInstant);
    }

    public List<Candidate> candidates(String countryCode, boolean highOnly,
                                      boolean excludeAnonymous, int limit) {
        List<Candidate> source = countries.get(countryCode);
        if (source == null || limit <= 0) return Collections.emptyList();
        List<Candidate> out = new ArrayList<>();
        for (Candidate candidate : source) {
            if (highOnly && !candidate.confidence.equals("high")) continue;
            if (excludeAnonymous && candidate.hasAnonymousSignal()) continue;
            out.add(candidate);
            if (out.size() >= limit) break;
        }
        return Collections.unmodifiableList(out);
    }

    private static boolean strictBoolean(JSONObject object, String key,
                                         String country, String ipv4) throws JSONException {
        Object raw = object.opt(key);
        if (!(raw instanceof Boolean)) {
            throw new JSONException("Invalid/missing " + key + " for " + country
                    + " candidate " + ipv4);
        }
        return (Boolean) raw;
    }

    static boolean isPublicCandidate(String ipv4) {
        if (!Ipv4.isLiteral(ipv4)) return false;
        long value = Ipv4.toUnsignedLong(ipv4);
        return !inCidr(value, "0.0.0.0", 8)
                && !inCidr(value, "10.0.0.0", 8)
                && !inCidr(value, "100.64.0.0", 10)
                && !inCidr(value, "127.0.0.0", 8)
                && !inCidr(value, "169.254.0.0", 16)
                && !inCidr(value, "172.16.0.0", 12)
                && !inCidr(value, "192.0.0.0", 24)
                && !inCidr(value, "192.0.2.0", 24)
                && !inCidr(value, "192.88.99.0", 24)
                && !inCidr(value, "192.168.0.0", 16)
                && !inCidr(value, "198.18.0.0", 15)
                && !inCidr(value, "198.51.100.0", 24)
                && !inCidr(value, "203.0.113.0", 24)
                && !inCidr(value, "224.0.0.0", 4)
                && !inCidr(value, "240.0.0.0", 4);
    }

    private static boolean inCidr(long value, String network, int prefix) {
        long base = Ipv4.toUnsignedLong(network);
        long mask = prefix == 0 ? 0L : ((0xffffffffL << (32 - prefix)) & 0xffffffffL);
        return (value & mask) == (base & mask);
    }

    public static final class Candidate {
        public final String ipv4;
        public final String confidence;
        public final boolean knownVpn;
        public final boolean knownProxy;
        public final boolean knownTor;
        public final String provider;
        public final int asn;

        Candidate(String ipv4, String confidence, boolean knownVpn, boolean knownProxy,
                  boolean knownTor, String provider, int asn) {
            this.ipv4 = ipv4;
            this.confidence = confidence;
            this.knownVpn = knownVpn;
            this.knownProxy = knownProxy;
            this.knownTor = knownTor;
            this.provider = provider;
            this.asn = asn;
        }

        public boolean hasAnonymousSignal() {
            return knownVpn || knownProxy || knownTor;
        }
    }
}

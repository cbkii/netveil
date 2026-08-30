package io.github.cbkii.netveil.country;

import io.github.cbkii.netveil.config.Ipv4;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Parsed, validated country/provider candidate pack. */
public final class CountryPack {
    public static final int SCHEMA = 1;
    public static final int DEFAULT_LIMIT = 12;
    public static final List<String> REQUIRED_COUNTRIES = List.of("AU", "US", "GB", "ID", "FR");

    public final String generatedAt;
    private final Map<String, List<Candidate>> countries;

    private CountryPack(String generatedAt, Map<String, List<Candidate>> countries) {
        this.generatedAt = generatedAt;
        this.countries = Collections.unmodifiableMap(countries);
    }

    public static CountryPack parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.optInt("schema", -1) != SCHEMA) {
            throw new JSONException("Unsupported country-pack schema");
        }
        String generatedAt = root.optString("generated_at", "").trim();
        if (generatedAt.isEmpty()) throw new JSONException("Missing generated_at");
        JSONObject rawCountries = root.getJSONObject("countries");
        Map<String, List<Candidate>> parsed = new LinkedHashMap<>();
        for (String code : REQUIRED_COUNTRIES) {
            JSONArray values = rawCountries.optJSONArray(code);
            if (values == null) throw new JSONException("Missing country " + code);
            List<Candidate> candidates = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.getJSONObject(i);
                String ipv4 = value.optString("ipv4", "").trim();
                if (!Ipv4.isLiteral(ipv4)) continue;
                ipv4 = Ipv4.canonical(ipv4);
                if (!seen.add(ipv4)) continue;
                String confidence = value.optString("confidence", "low").trim().toLowerCase();
                if (!confidence.equals("high") && !confidence.equals("medium") && !confidence.equals("low")) {
                    confidence = "low";
                }
                candidates.add(new Candidate(
                        ipv4,
                        confidence,
                        value.optBoolean("known_vpn", false),
                        value.optBoolean("known_proxy", false),
                        value.optBoolean("known_tor", false),
                        value.optString("provider", ""),
                        value.optInt("asn", 0)));
            }
            if (candidates.isEmpty()) throw new JSONException("No valid candidates for " + code);
            parsed.put(code, Collections.unmodifiableList(candidates));
        }
        return new CountryPack(generatedAt, parsed);
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

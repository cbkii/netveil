package io.github.cbkii.netveil.config;

import io.github.cbkii.netveil.country.CountryPack;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/** Builds a complete ordinary Global profile from one supported country recommendation. */
public final class RecommendedProfileFactory {
    private RecommendedProfileFactory() {}

    public static Draft create(String countryCode, CountryPack pack, long selectionSeed) {
        if (pack == null) throw new IllegalArgumentException("country pack is required");
        List<CountryPack.Candidate> candidates = pack.candidates(
                countryCode, true, true, CountryPack.DEFAULT_LIMIT);
        List<String> ipv4 = new ArrayList<>();
        for (CountryPack.Candidate candidate : candidates) ipv4.add(candidate.ipv4);
        return create(countryCode, ipv4, pack.generatedAt, selectionSeed);
    }

    public static Draft create(String countryCode, List<String> ipv4Values,
                               String generatedAt, long selectionSeed) {
        if (countryCode == null || countryCode.isBlank()) {
            throw new IllegalArgumentException("country code is required");
        }
        if (selectionSeed == 0L) throw new IllegalArgumentException("selection seed must be non-zero");
        if (ipv4Values == null || ipv4Values.isEmpty()) {
            throw new IllegalArgumentException("country recommendation has no IPv4 candidates");
        }

        List<NetworkIdentity> identities = new ArrayList<>();
        for (String raw : ipv4Values) {
            if (!Ipv4.isLiteral(raw)) {
                throw new IllegalArgumentException("invalid recommended IPv4: " + raw);
            }
            NetworkIdentity identity = NetworkIdentity.hidden(raw);
            boolean duplicate = false;
            for (NetworkIdentity existing : identities) {
                if (existing.ipv4.equals(identity.ipv4)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) identities.add(identity);
        }
        if (identities.isEmpty()) {
            throw new IllegalArgumentException("country recommendation has no usable IPv4 candidates");
        }

        List<List<String>> dnsSets = DnsPresetProvider.forCountry(countryCode);
        if (dnsSets.isEmpty()) throw new IllegalArgumentException("country has no DNS recommendation");
        for (List<String> set : dnsSets) {
            if (set.isEmpty()) throw new IllegalArgumentException("empty DNS recommendation");
            for (String dns : set) {
                if (!Ipv4.isLiteral(dns)) {
                    throw new IllegalArgumentException("invalid recommended DNS IPv4: " + dns);
                }
            }
        }

        Profile profile = Profile.create(
                true,
                true,
                true,
                true,
                true,
                selectionSeed,
                identities,
                dnsSets);
        String fingerprint = fingerprint(countryCode, generatedAt, identities, dnsSets);
        return new Draft(profile, countryCode, generatedAt == null ? "" : generatedAt, fingerprint);
    }

    private static String fingerprint(String countryCode, String generatedAt,
                                      List<NetworkIdentity> identities,
                                      List<List<String>> dnsSets) {
        StringBuilder canonical = new StringBuilder()
                .append("recommended-v1|")
                .append(DnsPresetProvider.VERSION).append('|')
                .append(countryCode).append('|')
                .append(generatedAt == null ? "" : generatedAt)
                .append("|enabled=1|random=1|vpn=1|proxy=1|ipv6=1");
        for (NetworkIdentity identity : identities) canonical.append("|ip=").append(identity.serialize());
        for (List<String> set : dnsSets) canonical.append("|dns=").append(String.join(",", set));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) hex.append(String.format("%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    public static final class Draft {
        public final Profile profile;
        public final String countryCode;
        public final String generatedAt;
        public final String fingerprint;

        Draft(Profile profile, String countryCode, String generatedAt, String fingerprint) {
            this.profile = profile;
            this.countryCode = countryCode;
            this.generatedAt = generatedAt;
            this.fingerprint = fingerprint;
        }
    }
}

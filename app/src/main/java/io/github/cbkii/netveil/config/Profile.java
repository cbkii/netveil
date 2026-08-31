package io.github.cbkii.netveil.config;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Profile {
    public enum AppPolicy {
        INHERIT_GLOBAL("inherit"),
        CUSTOM("custom"),
        DISABLED("disabled");

        public final String storedValue;

        AppPolicy(String storedValue) {
            this.storedValue = storedValue;
        }

        static AppPolicy fromStored(String raw) {
            if (raw != null) {
                for (AppPolicy policy : values()) {
                    if (policy.storedValue.equals(raw)) return policy;
                }
            }
            return INHERIT_GLOBAL;
        }
    }

    public final boolean enabled;
    public final boolean randomize;
    public final boolean hideVpn;
    public final boolean hideProxy;
    public final boolean hideIpv6;
    public final long selectionSeed;
    public final List<NetworkIdentity> identities;
    public final List<List<String>> dnsSets;

    private Profile(boolean enabled, boolean randomize, boolean hideVpn, boolean hideProxy,
                    boolean hideIpv6, long selectionSeed, List<NetworkIdentity> identities,
                    List<List<String>> dnsSets) {
        this.enabled = enabled;
        this.randomize = randomize;
        this.hideVpn = hideVpn;
        this.hideProxy = hideProxy;
        this.hideIpv6 = hideIpv6;
        this.selectionSeed = selectionSeed;
        this.identities = Collections.unmodifiableList(new ArrayList<>(identities));
        List<List<String>> copiedDns = new ArrayList<>();
        for (List<String> set : dnsSets) {
            copiedDns.add(Collections.unmodifiableList(new ArrayList<>(set)));
        }
        this.dnsSets = Collections.unmodifiableList(copiedDns);
    }

    public static Profile create(boolean enabled, boolean randomize, boolean hideVpn,
                                 boolean hideProxy, boolean hideIpv6, long selectionSeed,
                                 List<NetworkIdentity> identities, List<List<String>> dnsSets) {
        return new Profile(enabled, randomize, hideVpn, hideProxy, hideIpv6, selectionSeed,
                identities == null ? Collections.emptyList() : identities,
                dnsSets == null ? Collections.emptyList() : dnsSets);
    }

    /** Load a Global or custom profile. Legacy independent IP/gateway fields are migrated in memory. */
    public static Profile load(SharedPreferences p, String target) {
        List<NetworkIdentity> identities;
        String identityKey = ConfigKeys.p(target, ConfigKeys.FIELD_IDENTITIES);
        if (p.contains(identityKey)) {
            identities = NetworkIdentity.parseStoredList(p.getString(identityKey, ""));
        } else {
            int legacyPrefix = clampPrefix(p.getInt(
                    ConfigKeys.p(target, ConfigKeys.LEGACY_PREFIX), 24));
            List<String> legacyIps = parseList(p.getString(
                    ConfigKeys.p(target, ConfigKeys.LEGACY_IPV4), ""));
            List<String> legacyGateways = parseList(p.getString(
                    ConfigKeys.p(target, ConfigKeys.LEGACY_GATEWAYS), ""));
            identities = NetworkIdentity.migrateLegacy(legacyIps, legacyGateways, legacyPrefix);
        }

        return new Profile(
                p.getBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_ENABLED), false),
                p.getBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_RANDOMIZE), false),
                p.getBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_VPN), true),
                p.getBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_PROXY), true),
                p.getBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_IPV6), true),
                p.getLong(ConfigKeys.p(target, ConfigKeys.FIELD_SELECTION_SEED), 0L),
                identities,
                parseDnsSets(p.getString(ConfigKeys.p(target, ConfigKeys.FIELD_DNS), ""))
        );
    }

    /** Existing package profiles from v1.0.x become CUSTOM automatically until explicitly changed. */
    public static AppPolicy appPolicy(SharedPreferences p, String pkg) {
        String key = ConfigKeys.p(pkg, ConfigKeys.FIELD_POLICY);
        if (p.contains(key)) return AppPolicy.fromStored(p.getString(key, null));
        return hasStoredProfile(p, pkg) ? AppPolicy.CUSTOM : AppPolicy.INHERIT_GLOBAL;
    }

    public static boolean hasStoredProfile(SharedPreferences p, String target) {
        return p.contains(ConfigKeys.p(target, ConfigKeys.FIELD_ENABLED))
                || p.contains(ConfigKeys.p(target, ConfigKeys.FIELD_IDENTITIES))
                || p.contains(ConfigKeys.p(target, ConfigKeys.LEGACY_IPV4))
                || p.contains(ConfigKeys.p(target, ConfigKeys.FIELD_DNS));
    }

    /** Resolve exactly what a scoped target process should use. Vector/LSPosed remains the outer gate. */
    public static Resolved resolveEffective(SharedPreferences p, String pkg) {
        AppPolicy policy = appPolicy(p, pkg);
        if (policy == AppPolicy.DISABLED) return null;
        if (policy == AppPolicy.CUSTOM) return load(p, pkg).resolve();

        Profile global = load(p, ConfigKeys.GLOBAL);
        long effectiveSeed = derivePackageSeed(global.selectionSeed, pkg);
        return global.resolveWithSeed(effectiveSeed);
    }

    public Resolved resolve() {
        return resolveWithSeed(selectionSeed);
    }

    public Resolved resolveForInheritedPackage(String pkg) {
        return resolveWithSeed(derivePackageSeed(selectionSeed, pkg));
    }

    private Resolved resolveWithSeed(long effectiveSeed) {
        if (!enabled || identities.isEmpty() || dnsSets.isEmpty()) return null;

        int identityIndex = randomize
                ? stableIndex(effectiveSeed, 0x4944454eL, identities.size()) : 0;
        int dnsIndex = randomize
                ? stableIndex(effectiveSeed, 0x444e5353L, dnsSets.size()) : 0;
        NetworkIdentity identity = identities.get(identityIndex);
        return new Resolved(identity, dnsSets.get(dnsIndex), hideVpn, hideProxy, hideIpv6,
                effectiveSeed);
    }

    static int stableIndex(long seed, long salt, int size) {
        if (size <= 0) throw new IllegalArgumentException("size must be positive");
        long z = mix64(seed ^ salt);
        return (int) Math.floorMod(z, size);
    }

    /** Stable across processes and launches, while decorrelating Global selections between packages. */
    public static long derivePackageSeed(long baseSeed, String packageName) {
        long hash = 0xcbf29ce484222325L;
        String value = packageName == null ? "" : packageName;
        for (int i = 0; i < value.length(); i++) {
            hash ^= value.charAt(i);
            hash *= 0x100000001b3L;
        }
        return mix64(baseSeed ^ hash ^ 0x4e45545645494cL);
    }

    private static long mix64(long z) {
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
    }

    public static List<String> parseList(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String part : raw.split("[\\r\\n,]+")) {
            String value = part.trim();
            if (value.isEmpty() || !Ipv4.isLiteral(value)) continue;
            String canonical = Ipv4.canonical(value);
            if (!out.contains(canonical)) out.add(canonical);
        }
        return Collections.unmodifiableList(out);
    }

    public static List<List<String>> parseDnsSets(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        List<List<String>> sets = new ArrayList<>();
        for (String line : raw.split("[\\r\\n]+")) {
            List<String> set = parseList(line);
            if (!set.isEmpty() && !sets.contains(set)) sets.add(set);
        }
        return Collections.unmodifiableList(sets);
    }

    /** Legacy helpers retained for old tests/tools and migration diagnostics. */
    public static boolean hasCompatibleGateway(List<String> ips, List<String> gateways, int prefix) {
        return !compatibleIps(canonicalize(ips), canonicalize(gateways), prefix).isEmpty();
    }

    public static boolean allIpsHaveCompatibleGateway(List<String> ips, List<String> gateways, int prefix) {
        List<String> canonicalIps = canonicalize(ips);
        return !canonicalIps.isEmpty()
                && compatibleIps(canonicalIps, canonicalize(gateways), prefix).size() == canonicalIps.size();
    }

    private static List<String> compatibleIps(List<String> ips, List<String> gateways, int prefix) {
        List<String> eligible = new ArrayList<>();
        for (String ip : ips) {
            for (String gateway : gateways) {
                if (!ip.equals(gateway) && Ipv4.sameSubnet(ip, gateway, prefix)) {
                    eligible.add(ip);
                    break;
                }
            }
        }
        return eligible;
    }

    private static List<String> canonicalize(List<String> values) {
        List<String> out = new ArrayList<>();
        if (values == null) return out;
        for (String value : values) {
            if (!Ipv4.isLiteral(value)) continue;
            String canonical = Ipv4.canonical(value);
            if (!out.contains(canonical)) out.add(canonical);
        }
        return out;
    }

    private static int clampPrefix(int p) {
        return Math.max(0, Math.min(32, p));
    }

    public static final class Resolved {
        public final String ipv4;
        public final NetworkIdentity.RouteMode routeMode;
        public final int prefixLength;
        /** 0.0.0.0 is the compatibility sentinel on fixed-width legacy surfaces when routes are hidden. */
        public final String gateway;
        public final List<String> dns;
        public final boolean hideVpn;
        public final boolean hideProxy;
        public final boolean hideIpv6;
        public final long selectionSeed;

        private Resolved(NetworkIdentity identity, List<String> dns, boolean hideVpn,
                         boolean hideProxy, boolean hideIpv6, long selectionSeed) {
            this.ipv4 = identity.ipv4;
            this.routeMode = identity.routeMode;
            this.prefixLength = identity.prefixLength;
            this.gateway = identity.gateway == null ? "0.0.0.0" : identity.gateway;
            this.dns = Collections.unmodifiableList(new ArrayList<>(dns));
            this.hideVpn = hideVpn;
            this.hideProxy = hideProxy;
            this.hideIpv6 = hideIpv6;
            this.selectionSeed = selectionSeed;
        }

        public boolean hasExplicitRoute() {
            return routeMode == NetworkIdentity.RouteMode.EXPLICIT;
        }
    }
}

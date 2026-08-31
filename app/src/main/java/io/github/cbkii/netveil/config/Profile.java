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

    public static boolean hasCurrentSchema(SharedPreferences p) {
        try {
            return p.getInt(ConfigKeys.SCHEMA_VERSION, Integer.MIN_VALUE)
                    == ConfigKeys.CURRENT_SCHEMA_VERSION;
        } catch (ClassCastException ignored) {
            return false;
        }
    }

    /**
     * Initialise the current profile store or hard-reset an incompatible one.
     *
     * <p>The "profiles" SharedPreferences file contains only NetVeil profile configuration.
     * Country-data cache and refresh scheduler state use separate storage and are not touched.</p>
     */
    public static boolean ensureCurrentSchema(SharedPreferences p) {
        if (hasCurrentSchema(p)) return true;
        return p.edit()
                .clear()
                .putInt(ConfigKeys.SCHEMA_VERSION, ConfigKeys.CURRENT_SCHEMA_VERSION)
                .commit();
    }

    /** Load only the current structured profile format. */
    public static Profile load(SharedPreferences p, String target) {
        if (!hasCurrentSchema(p)) return null;
        return new Profile(
                p.getBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_ENABLED), false),
                p.getBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_RANDOMIZE), false),
                p.getBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_VPN), true),
                p.getBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_PROXY), true),
                p.getBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_IPV6), true),
                p.getLong(ConfigKeys.p(target, ConfigKeys.FIELD_SELECTION_SEED), 0L),
                NetworkIdentity.parseStoredList(
                        p.getString(ConfigKeys.p(target, ConfigKeys.FIELD_IDENTITIES), "")),
                parseDnsSets(p.getString(ConfigKeys.p(target, ConfigKeys.FIELD_DNS), ""))
        );
    }

    public static AppPolicy appPolicy(SharedPreferences p, String pkg) {
        if (!hasCurrentSchema(p)) return AppPolicy.INHERIT_GLOBAL;
        String key = ConfigKeys.p(pkg, ConfigKeys.FIELD_POLICY);
        return p.contains(key)
                ? AppPolicy.fromStored(p.getString(key, null))
                : AppPolicy.INHERIT_GLOBAL;
    }

    public static boolean hasStoredProfile(SharedPreferences p, String target) {
        if (!hasCurrentSchema(p)) return false;
        return p.contains(ConfigKeys.p(target, ConfigKeys.FIELD_ENABLED))
                || p.contains(ConfigKeys.p(target, ConfigKeys.FIELD_IDENTITIES))
                || p.contains(ConfigKeys.p(target, ConfigKeys.FIELD_DNS));
    }

    /** Resolve exactly what a scoped target process should use. Vector/LSPosed remains the outer gate. */
    public static Resolved resolveEffective(SharedPreferences p, String pkg) {
        if (!hasCurrentSchema(p)) return null;
        AppPolicy policy = appPolicy(p, pkg);
        if (policy == AppPolicy.DISABLED) return null;
        if (policy == AppPolicy.CUSTOM) {
            Profile custom = load(p, pkg);
            return custom == null ? null : custom.resolve();
        }

        Profile global = load(p, ConfigKeys.GLOBAL);
        if (global == null) return null;
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

    public static final class Resolved {
        public final String ipv4;
        public final NetworkIdentity.RouteMode routeMode;
        public final int prefixLength;
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
            this.gateway = identity.gateway;
            this.dns = Collections.unmodifiableList(new ArrayList<>(dns));
            this.hideVpn = hideVpn;
            this.hideProxy = hideProxy;
            this.hideIpv6 = hideIpv6;
            this.selectionSeed = selectionSeed;
        }

        public boolean hasExplicitRoute() {
            return routeMode == NetworkIdentity.RouteMode.EXPLICIT && gateway != null;
        }
    }
}

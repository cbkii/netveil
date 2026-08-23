package io.github.cbkii.netveil.config;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Profile {
    public final boolean enabled;
    public final boolean randomize;
    public final boolean hideVpn;
    public final boolean hideProxy;
    public final boolean hideIpv6;
    public final int prefixLength;
    public final long selectionSeed;
    public final List<String> ipv4;
    public final List<String> gateways;
    public final List<List<String>> dnsSets;

    private Profile(boolean enabled, boolean randomize, boolean hideVpn, boolean hideProxy,
                    boolean hideIpv6, int prefixLength, long selectionSeed, List<String> ipv4,
                    List<String> gateways, List<List<String>> dnsSets) {
        this.enabled = enabled;
        this.randomize = randomize;
        this.hideVpn = hideVpn;
        this.hideProxy = hideProxy;
        this.hideIpv6 = hideIpv6;
        this.prefixLength = prefixLength;
        this.selectionSeed = selectionSeed;
        this.ipv4 = ipv4;
        this.gateways = gateways;
        this.dnsSets = dnsSets;
    }

    public static Profile load(SharedPreferences p, String pkg) {
        return new Profile(
                p.getBoolean(ConfigKeys.p(pkg, "enabled"), false),
                p.getBoolean(ConfigKeys.p(pkg, "randomize"), false),
                p.getBoolean(ConfigKeys.p(pkg, "hide_vpn"), true),
                p.getBoolean(ConfigKeys.p(pkg, "hide_proxy"), true),
                p.getBoolean(ConfigKeys.p(pkg, "hide_ipv6"), true),
                clampPrefix(p.getInt(ConfigKeys.p(pkg, "prefix"), 24)),
                p.getLong(ConfigKeys.p(pkg, "selection_seed"), 0L),
                parseList(p.getString(ConfigKeys.p(pkg, "ipv4"), "")),
                parseList(p.getString(ConfigKeys.p(pkg, "gateways"), "")),
                parseDnsSets(p.getString(ConfigKeys.p(pkg, "dns"), ""))
        );
    }

    public Resolved resolve() {
        if (!enabled || ipv4.isEmpty() || gateways.isEmpty() || dnsSets.isEmpty()) return null;

        List<String> eligibleIps = compatibleIps(ipv4, gateways, prefixLength);
        if (eligibleIps.isEmpty()) return null;
        int ipIndex = randomize ? stableIndex(selectionSeed, 0x49505634L, eligibleIps.size()) : 0;
        String selectedIp = eligibleIps.get(ipIndex);

        List<String> compatibleGateways = new ArrayList<>();
        for (String candidate : gateways) {
            if (!candidate.equals(selectedIp) && Ipv4.sameSubnet(selectedIp, candidate, prefixLength)) {
                compatibleGateways.add(candidate);
            }
        }
        if (compatibleGateways.isEmpty()) return null;

        int gatewayIndex = randomize
                ? stableIndex(selectionSeed, 0x47575459L, compatibleGateways.size()) : 0;
        int dnsIndex = randomize ? stableIndex(selectionSeed, 0x444e5353L, dnsSets.size()) : 0;

        return new Resolved(selectedIp, compatibleGateways.get(gatewayIndex), dnsSets.get(dnsIndex),
                prefixLength, hideVpn, hideProxy, hideIpv6, selectionSeed);
    }

    static int stableIndex(long seed, long salt, int size) {
        if (size <= 0) throw new IllegalArgumentException("size must be positive");
        long z = seed ^ salt;
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdl;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53l;
        z ^= (z >>> 33);
        return (int) Math.floorMod(z, size);
    }

    public static List<String> parseList(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String part : raw.split("[\\r\\n,]+")) {
            String s = part.trim();
            if (!s.isEmpty() && Ipv4.isLiteral(s) && !out.contains(s)) out.add(s);
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

    public static boolean hasCompatibleGateway(List<String> ips, List<String> gateways, int prefix) {
        return !compatibleIps(ips, gateways, prefix).isEmpty();
    }

    public static boolean allIpsHaveCompatibleGateway(List<String> ips, List<String> gateways, int prefix) {
        return !ips.isEmpty() && compatibleIps(ips, gateways, prefix).size() == ips.size();
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

    private static int clampPrefix(int p) {
        return Math.max(0, Math.min(32, p));
    }

    public static final class Resolved {
        public final String ipv4;
        public final String gateway;
        public final List<String> dns;
        public final int prefixLength;
        public final boolean hideVpn;
        public final boolean hideProxy;
        public final boolean hideIpv6;
        public final long selectionSeed;

        private Resolved(String ipv4, String gateway, List<String> dns, int prefixLength,
                         boolean hideVpn, boolean hideProxy, boolean hideIpv6, long selectionSeed) {
            this.ipv4 = ipv4;
            this.gateway = gateway;
            this.dns = dns;
            this.prefixLength = prefixLength;
            this.hideVpn = hideVpn;
            this.hideProxy = hideProxy;
            this.hideIpv6 = hideIpv6;
            this.selectionSeed = selectionSeed;
        }
    }
}

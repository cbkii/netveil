package io.github.cbkii.netveil.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Tiny, bundled DNS recommendations used by both Basic and Advanced configuration. */
public final class DnsPresetProvider {
    public static final int VERSION = 1;

    private static final List<String> CLOUDFLARE = List.of("1.1.1.1", "1.0.0.1");
    private static final List<String> QUAD9 = List.of("9.9.9.9", "149.112.112.112");
    private static final List<String> GOOGLE = List.of("8.8.8.8", "8.8.4.4");
    private static final Map<String, List<List<String>>> PRESETS = build();

    private DnsPresetProvider() {}

    private static Map<String, List<List<String>>> build() {
        Map<String, List<List<String>>> values = new LinkedHashMap<>();
        // All three providers use globally distributed anycast. Country-specific ordering keeps the
        // recommendation explicit without inventing undocumented ISP/local resolver addresses.
        values.put("AU", List.of(CLOUDFLARE, GOOGLE, QUAD9));
        values.put("US", List.of(CLOUDFLARE, QUAD9, GOOGLE));
        values.put("GB", List.of(CLOUDFLARE, QUAD9, GOOGLE));
        values.put("ID", List.of(CLOUDFLARE, GOOGLE, QUAD9));
        values.put("FR", List.of(CLOUDFLARE, QUAD9, GOOGLE));
        return Map.copyOf(values);
    }

    public static List<List<String>> forCountry(String countryCode) {
        List<List<String>> sets = PRESETS.get(countryCode);
        return sets == null ? List.of() : sets;
    }

    public static String format(String countryCode) {
        return formatSets(forCountry(countryCode));
    }

    public static String formatSets(List<List<String>> sets) {
        StringBuilder out = new StringBuilder();
        for (List<String> set : sets) {
            if (out.length() > 0) out.append('\n');
            out.append(String.join(", ", set));
        }
        return out.toString();
    }
}

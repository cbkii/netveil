package io.github.cbkii.netveil.country;

import java.util.List;
import java.util.Locale;

/** Small authoritative catalogue for countries supported by the bundled NetVeil data pack. */
public final class CountryCatalog {
    public static final String DEFAULT_CODE = "AU";
    private static final String[] CODES = {"AU", "US", "GB", "ID", "FR"};
    private static final String[] LABELS = {
            "Australia", "United States", "United Kingdom", "Indonesia", "France"
    };

    private CountryCatalog() {}

    public static String[] labels() {
        return LABELS.clone();
    }

    public static List<String> codes() {
        return List.of(CODES);
    }

    public static boolean supports(String code) {
        return indexOf(code) >= 0;
    }

    public static int indexOf(String code) {
        if (code == null) return -1;
        for (int i = 0; i < CODES.length; i++) {
            if (CODES[i].equalsIgnoreCase(code)) return i;
        }
        return -1;
    }

    public static String codeAt(int position) {
        int safe = Math.max(0, Math.min(position, CODES.length - 1));
        return CODES[safe];
    }

    public static String labelFor(String code) {
        int index = indexOf(code);
        return index < 0 ? code : LABELS[index];
    }

    public static String defaultForLocale(Locale locale) {
        String country = locale == null ? "" : locale.getCountry();
        String normalized = country == null ? "" : country.toUpperCase(Locale.ROOT);
        return supports(normalized) ? normalized : DEFAULT_CODE;
    }
}

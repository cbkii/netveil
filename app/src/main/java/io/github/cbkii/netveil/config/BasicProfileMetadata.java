package io.github.cbkii.netveil.config;

import android.content.SharedPreferences;

/** UI ownership metadata for the single saved Global profile. Runtime resolution ignores it. */
public final class BasicProfileMetadata {
    private static final String PREFIX = "basic_global.";
    private static final String MANAGED = PREFIX + "managed";
    private static final String COUNTRY = PREFIX + "country";
    private static final String FINGERPRINT = PREFIX + "fingerprint";
    private static final String GENERATED_AT = PREFIX + "generated_at";

    private BasicProfileMetadata() {}

    public static boolean isManaged(SharedPreferences prefs) {
        return prefs.getBoolean(MANAGED, false);
    }

    public static String country(SharedPreferences prefs) {
        return prefs.getString(COUNTRY, "");
    }

    public static String fingerprint(SharedPreferences prefs) {
        return prefs.getString(FINGERPRINT, "");
    }

    public static String generatedAt(SharedPreferences prefs) {
        return prefs.getString(GENERATED_AT, "");
    }

    public static SharedPreferences.Editor markBasic(
            SharedPreferences.Editor editor, RecommendedProfileFactory.Draft draft) {
        return editor.putBoolean(MANAGED, true)
                .putString(COUNTRY, draft.countryCode)
                .putString(FINGERPRINT, draft.fingerprint)
                .putString(GENERATED_AT, draft.generatedAt);
    }

    public static SharedPreferences.Editor clear(SharedPreferences.Editor editor) {
        return editor.remove(MANAGED)
                .remove(COUNTRY)
                .remove(FINGERPRINT)
                .remove(GENERATED_AT);
    }
}

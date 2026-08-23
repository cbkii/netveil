package io.github.cbkii.netveil.config;

public final class ConfigKeys {
    public static final String PREFS = "profiles";
    public static final String INDEX = "profile_index";

    private ConfigKeys() {}

    public static String p(String pkg, String field) {
        return "profile." + pkg + "." + field;
    }
}

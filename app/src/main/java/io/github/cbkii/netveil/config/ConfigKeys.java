package io.github.cbkii.netveil.config;

public final class ConfigKeys {
    public static final String PREFS = "profiles";
    public static final String INDEX = "profile_index";
    public static final String GLOBAL = "__global__";
    public static final String SCHEMA_VERSION = "config_schema_version";
    public static final int CURRENT_SCHEMA_VERSION = 2;

    public static final String FIELD_ENABLED = "enabled";
    public static final String FIELD_RANDOMIZE = "randomize";
    public static final String FIELD_HIDE_VPN = "hide_vpn";
    public static final String FIELD_HIDE_PROXY = "hide_proxy";
    public static final String FIELD_HIDE_IPV6 = "hide_ipv6";
    public static final String FIELD_SELECTION_SEED = "selection_seed";
    public static final String FIELD_IDENTITIES = "identities";
    public static final String FIELD_DNS = "dns";
    public static final String FIELD_POLICY = "policy";

    // Legacy v1.0.x fields retained for migration/rollback compatibility.
    public static final String LEGACY_PREFIX = "prefix";
    public static final String LEGACY_IPV4 = "ipv4";
    public static final String LEGACY_GATEWAYS = "gateways";

    private ConfigKeys() {}

    public static String p(String target, String field) {
        return "profile." + target + "." + field;
    }
}

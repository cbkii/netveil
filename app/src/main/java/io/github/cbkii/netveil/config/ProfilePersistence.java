package io.github.cbkii.netveil.config;

import android.content.SharedPreferences;

/** Shared persistence primitives for the current structured profile schema. */
public final class ProfilePersistence {
    private ProfilePersistence() {}

    public static SharedPreferences.Editor putProfile(
            SharedPreferences.Editor editor, String target, Profile profile) {
        return editor
                .putInt(ConfigKeys.SCHEMA_VERSION, ConfigKeys.CURRENT_SCHEMA_VERSION)
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_ENABLED), profile.enabled)
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_RANDOMIZE), profile.randomize)
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_VPN), profile.hideVpn)
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_PROXY), profile.hideProxy)
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_IPV6), profile.hideIpv6)
                .putLong(ConfigKeys.p(target, ConfigKeys.FIELD_SELECTION_SEED), profile.selectionSeed)
                .putString(ConfigKeys.p(target, ConfigKeys.FIELD_IDENTITIES),
                        NetworkIdentity.serializeList(profile.identities))
                .putString(ConfigKeys.p(target, ConfigKeys.FIELD_DNS),
                        DnsPresetProvider.formatSets(profile.dnsSets));
    }

    public static SharedPreferences.Editor clearProfile(
            SharedPreferences.Editor editor, String target) {
        for (String field : new String[]{
                ConfigKeys.FIELD_ENABLED, ConfigKeys.FIELD_RANDOMIZE, ConfigKeys.FIELD_HIDE_VPN,
                ConfigKeys.FIELD_HIDE_PROXY, ConfigKeys.FIELD_HIDE_IPV6,
                ConfigKeys.FIELD_SELECTION_SEED, ConfigKeys.FIELD_IDENTITIES, ConfigKeys.FIELD_DNS}) {
            editor.remove(ConfigKeys.p(target, field));
        }
        return editor;
    }
}

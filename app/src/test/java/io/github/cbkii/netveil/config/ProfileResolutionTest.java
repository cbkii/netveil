package io.github.cbkii.netveil.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class ProfileResolutionTest {
    @Test
    public void unconfiguredPackageFallsBackToGlobal() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        putProfile(prefs, ConfigKeys.GLOBAL, true, true, 10L,
                "H|10.0.0.2\nH|10.0.0.3", "1.1.1.1\n8.8.8.8");

        Profile.Resolved resolved = Profile.resolveEffective(prefs, "org.schabi.newpipe");
        assertNotNull(resolved);
        assertEquals(Profile.AppPolicy.INHERIT_GLOBAL,
                Profile.appPolicy(prefs, "org.schabi.newpipe"));
    }

    @Test
    public void customOverrideTakesPrecedenceOverGlobal() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        putProfile(prefs, ConfigKeys.GLOBAL, true, false, 1L,
                "H|10.0.0.2", "1.1.1.1");
        putProfile(prefs, "org.schabi.newpipe", true, false, 2L,
                "H|192.0.2.20", "9.9.9.9");
        prefs.edit().putString(ConfigKeys.p("org.schabi.newpipe", ConfigKeys.FIELD_POLICY),
                Profile.AppPolicy.CUSTOM.storedValue).commit();

        Profile.Resolved resolved = Profile.resolveEffective(prefs, "org.schabi.newpipe");
        assertEquals("192.0.2.20", resolved.ipv4);
        assertEquals("9.9.9.9", resolved.dns.get(0));
    }

    @Test
    public void disabledOverrideSuppressesGlobal() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        putProfile(prefs, ConfigKeys.GLOBAL, true, false, 1L,
                "H|10.0.0.2", "1.1.1.1");
        prefs.edit().putString(ConfigKeys.p("org.schabi.newpipe", ConfigKeys.FIELD_POLICY),
                Profile.AppPolicy.DISABLED.storedValue).commit();

        assertNull(Profile.resolveEffective(prefs, "org.schabi.newpipe"));
    }

    @Test
    public void legacyPackageProfileDefaultsToCustom() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        String pkg = "org.schabi.newpipe";
        prefs.edit()
                .putBoolean(ConfigKeys.p(pkg, ConfigKeys.FIELD_ENABLED), true)
                .putString(ConfigKeys.p(pkg, ConfigKeys.LEGACY_IPV4), "192.168.1.20")
                .putString(ConfigKeys.p(pkg, ConfigKeys.LEGACY_GATEWAYS), "192.168.1.1")
                .putInt(ConfigKeys.p(pkg, ConfigKeys.LEGACY_PREFIX), 24)
                .putString(ConfigKeys.p(pkg, ConfigKeys.FIELD_DNS), "1.1.1.1")
                .commit();

        assertEquals(Profile.AppPolicy.CUSTOM, Profile.appPolicy(prefs, pkg));
        Profile.Resolved resolved = Profile.resolveEffective(prefs, pkg);
        assertNotNull(resolved);
        assertEquals("192.168.1.20", resolved.ipv4);
        assertEquals("192.168.1.1", resolved.gateway);
    }

    @Test
    public void ambiguousLegacyGatewayMigrationBecomesRouteHidden() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        String pkg = "org.example.app";
        prefs.edit()
                .putBoolean(ConfigKeys.p(pkg, ConfigKeys.FIELD_ENABLED), true)
                .putString(ConfigKeys.p(pkg, ConfigKeys.LEGACY_IPV4), "1.129.22.61")
                .putString(ConfigKeys.p(pkg, ConfigKeys.LEGACY_GATEWAYS),
                        "192.168.1.1\n202.128.115.2")
                .putInt(ConfigKeys.p(pkg, ConfigKeys.LEGACY_PREFIX), 0)
                .putString(ConfigKeys.p(pkg, ConfigKeys.FIELD_DNS), "8.8.8.8")
                .commit();

        Profile.Resolved resolved = Profile.resolveEffective(prefs, pkg);
        assertNotNull(resolved);
        assertEquals(NetworkIdentity.RouteMode.HIDDEN, resolved.routeMode);
        assertEquals("0.0.0.0", resolved.gateway);
    }

    @Test
    public void observedLegacySlashZeroWorkaroundMigratesFirstSelectionHidden() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        String pkg = "org.schabi.newpipe";
        prefs.edit()
                .putBoolean(ConfigKeys.p(pkg, ConfigKeys.FIELD_ENABLED), true)
                .putString(ConfigKeys.p(pkg, ConfigKeys.LEGACY_IPV4),
                        "202.128.115.2\n1.129.22.61")
                .putString(ConfigKeys.p(pkg, ConfigKeys.LEGACY_GATEWAYS),
                        "192.168.1.1\n202.128.115.2")
                .putInt(ConfigKeys.p(pkg, ConfigKeys.LEGACY_PREFIX), 0)
                .putString(ConfigKeys.p(pkg, ConfigKeys.FIELD_DNS), "8.8.8.8\n1.1.1.1")
                .commit();

        Profile loaded = Profile.load(prefs, pkg);
        assertEquals(2, loaded.identities.size());
        assertEquals(NetworkIdentity.RouteMode.HIDDEN, loaded.identities.get(0).routeMode);
        assertEquals(NetworkIdentity.RouteMode.HIDDEN, loaded.identities.get(1).routeMode);

        Profile.Resolved resolved = Profile.resolveEffective(prefs, pkg);
        assertNotNull(resolved);
        assertEquals("202.128.115.2", resolved.ipv4);
        assertEquals(NetworkIdentity.RouteMode.HIDDEN, resolved.routeMode);
        assertEquals("0.0.0.0", resolved.gateway);
    }

    @Test
    public void globalRandomisationIsStablePerPackageAndDecorrelatedByPackage() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        putProfile(prefs, ConfigKeys.GLOBAL, true, true, 777L,
                "H|10.0.0.2\nH|10.0.0.3\nH|10.0.0.4\nH|10.0.0.5",
                "1.1.1.1\n8.8.8.8");

        Profile.Resolved a1 = Profile.resolveEffective(prefs, "org.schabi.newpipe");
        Profile.Resolved a2 = Profile.resolveEffective(prefs, "org.schabi.newpipe");
        Profile.Resolved b = Profile.resolveEffective(prefs, "org.mozilla.firefox");
        assertEquals(a1.selectionSeed, a2.selectionSeed);
        assertNotEquals(a1.selectionSeed, b.selectionSeed);
    }

    private static void putProfile(FakeSharedPreferences prefs, String target,
                                   boolean enabled, boolean randomize, long seed,
                                   String identities, String dns) {
        prefs.edit()
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_ENABLED), enabled)
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_RANDOMIZE), randomize)
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_VPN), true)
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_PROXY), true)
                .putBoolean(ConfigKeys.p(target, ConfigKeys.FIELD_HIDE_IPV6), false)
                .putLong(ConfigKeys.p(target, ConfigKeys.FIELD_SELECTION_SEED), seed)
                .putString(ConfigKeys.p(target, ConfigKeys.FIELD_IDENTITIES), identities)
                .putString(ConfigKeys.p(target, ConfigKeys.FIELD_DNS), dns)
                .commit();
    }
}

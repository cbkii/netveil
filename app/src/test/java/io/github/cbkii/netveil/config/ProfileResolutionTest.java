package io.github.cbkii.netveil.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProfileResolutionTest {
    @Test
    public void absentSchemaCannotResolve() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        putProfileFields(prefs, ConfigKeys.GLOBAL, true, false, 10L,
                "H|10.0.0.2", "1.1.1.1");

        assertFalse(Profile.hasCurrentSchema(prefs));
        assertNull(Profile.resolveEffective(prefs, "org.schabi.newpipe"));
        assertFalse(Profile.hasStoredProfile(prefs, ConfigKeys.GLOBAL));
    }

    @Test
    public void incompatibleSchemaIsHardResetToCurrentEmptyStore() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        prefs.edit()
                .putInt(ConfigKeys.SCHEMA_VERSION, 2)
                .putStringSet(ConfigKeys.INDEX, java.util.Set.of("org.example.old"))
                .putBoolean(ConfigKeys.p(ConfigKeys.GLOBAL, ConfigKeys.FIELD_ENABLED), true)
                .putString(ConfigKeys.p(ConfigKeys.GLOBAL, ConfigKeys.FIELD_IDENTITIES),
                        "H|10.0.0.2")
                .commit();

        assertTrue(Profile.ensureCurrentSchema(prefs));
        assertTrue(Profile.hasCurrentSchema(prefs));
        assertEquals(1, prefs.getAll().size());
        assertEquals(ConfigKeys.CURRENT_SCHEMA_VERSION,
                prefs.getInt(ConfigKeys.SCHEMA_VERSION, -1));
        assertFalse(Profile.hasStoredProfile(prefs, ConfigKeys.GLOBAL));
    }

    @Test
    public void unconfiguredPackageFallsBackToGlobal() {
        FakeSharedPreferences prefs = currentPrefs();
        putProfileFields(prefs, ConfigKeys.GLOBAL, true, true, 10L,
                "H|10.0.0.2\nH|10.0.0.3", "1.1.1.1\n8.8.8.8");

        Profile.Resolved resolved = Profile.resolveEffective(prefs, "org.schabi.newpipe");
        assertNotNull(resolved);
        assertEquals(Profile.AppPolicy.INHERIT_GLOBAL,
                Profile.appPolicy(prefs, "org.schabi.newpipe"));
    }

    @Test
    public void missingPolicyDoesNotInferCustomFromStoredFields() {
        FakeSharedPreferences prefs = currentPrefs();
        putProfileFields(prefs, ConfigKeys.GLOBAL, true, false, 1L,
                "H|10.0.0.2", "1.1.1.1");
        putProfileFields(prefs, "org.schabi.newpipe", true, false, 2L,
                "H|192.0.2.20", "9.9.9.9");

        assertEquals(Profile.AppPolicy.INHERIT_GLOBAL,
                Profile.appPolicy(prefs, "org.schabi.newpipe"));
        Profile.Resolved resolved = Profile.resolveEffective(prefs, "org.schabi.newpipe");
        assertNotNull(resolved);
        assertEquals("10.0.0.2", resolved.ipv4);
        assertEquals("1.1.1.1", resolved.dns.get(0));
    }

    @Test
    public void customOverrideTakesPrecedenceOverGlobal() {
        FakeSharedPreferences prefs = currentPrefs();
        putProfileFields(prefs, ConfigKeys.GLOBAL, true, false, 1L,
                "H|10.0.0.2", "1.1.1.1");
        putProfileFields(prefs, "org.schabi.newpipe", true, false, 2L,
                "H|192.0.2.20", "9.9.9.9");
        prefs.edit().putString(ConfigKeys.p("org.schabi.newpipe", ConfigKeys.FIELD_POLICY),
                Profile.AppPolicy.CUSTOM.storedValue).commit();

        Profile.Resolved resolved = Profile.resolveEffective(prefs, "org.schabi.newpipe");
        assertNotNull(resolved);
        assertEquals("192.0.2.20", resolved.ipv4);
        assertEquals("9.9.9.9", resolved.dns.get(0));
    }

    @Test
    public void disabledOverrideSuppressesGlobal() {
        FakeSharedPreferences prefs = currentPrefs();
        putProfileFields(prefs, ConfigKeys.GLOBAL, true, false, 1L,
                "H|10.0.0.2", "1.1.1.1");
        prefs.edit().putString(ConfigKeys.p("org.schabi.newpipe", ConfigKeys.FIELD_POLICY),
                Profile.AppPolicy.DISABLED.storedValue).commit();

        assertNull(Profile.resolveEffective(prefs, "org.schabi.newpipe"));
    }

    @Test
    public void globalRandomisationIsStablePerPackageAndDecorrelatedByPackage() {
        FakeSharedPreferences prefs = currentPrefs();
        putProfileFields(prefs, ConfigKeys.GLOBAL, true, true, 777L,
                "H|10.0.0.2\nH|10.0.0.3\nH|10.0.0.4\nH|10.0.0.5",
                "1.1.1.1\n8.8.8.8");

        Profile.Resolved a1 = Profile.resolveEffective(prefs, "org.schabi.newpipe");
        Profile.Resolved a2 = Profile.resolveEffective(prefs, "org.schabi.newpipe");
        Profile.Resolved b = Profile.resolveEffective(prefs, "org.mozilla.firefox");
        assertNotNull(a1);
        assertNotNull(a2);
        assertNotNull(b);
        assertEquals(a1.selectionSeed, a2.selectionSeed);
        assertNotEquals(a1.selectionSeed, b.selectionSeed);
    }

    private static FakeSharedPreferences currentPrefs() {
        FakeSharedPreferences prefs = new FakeSharedPreferences();
        prefs.edit().putInt(ConfigKeys.SCHEMA_VERSION, ConfigKeys.CURRENT_SCHEMA_VERSION).commit();
        return prefs;
    }

    private static void putProfileFields(FakeSharedPreferences prefs, String target,
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

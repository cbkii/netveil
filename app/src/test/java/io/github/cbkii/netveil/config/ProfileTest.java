package io.github.cbkii.netveil.config;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ProfileTest {
    @Test
    public void listParserRejectsGarbageAndCanonicalizesDuplicates() {
        assertEquals(
                List.of("10.0.0.2", "10.0.0.3"),
                Profile.parseList("10.0.0.2, bad\n10.0.0.3,10.0.000.002"));
    }

    @Test
    public void dnsLinesAreIndependentCanonicalSets() {
        var sets = Profile.parseDnsSets("1.1.1.1,1.0.0.1\n009.009.009.009");
        assertEquals(2, sets.size());
        assertEquals(2, sets.get(0).size());
        assertEquals("9.9.9.9", sets.get(1).get(0));
    }

    @Test
    public void stableIndexIsDeterministicAndBounded() {
        int a = Profile.stableIndex(123456789L, 42L, 7);
        int b = Profile.stableIndex(123456789L, 42L, 7);
        assertEquals(a, b);
        assertTrue(a >= 0 && a < 7);
    }

    @Test
    public void globalPackageSeedIsStableAndPackageSpecific() {
        long a1 = Profile.derivePackageSeed(77L, "org.schabi.newpipe");
        long a2 = Profile.derivePackageSeed(77L, "org.schabi.newpipe");
        long b = Profile.derivePackageSeed(77L, "org.mozilla.firefox");
        assertEquals(a1, a2);
        assertNotEquals(a1, b);
    }

    @Test
    public void inheritedRandomisationIsStablePerPackage() {
        Profile global = Profile.create(true, true, true, true, false, 1234L,
                List.of(
                        NetworkIdentity.hidden("10.0.0.2"),
                        NetworkIdentity.hidden("10.0.0.3"),
                        NetworkIdentity.hidden("10.0.0.4"),
                        NetworkIdentity.hidden("10.0.0.5")),
                List.of(List.of("1.1.1.1"), List.of("8.8.8.8")));
        Profile.Resolved first = global.resolveForInheritedPackage("org.schabi.newpipe");
        Profile.Resolved repeat = global.resolveForInheritedPackage("org.schabi.newpipe");
        assertEquals(first.ipv4, repeat.ipv4);
        assertEquals(first.dns, repeat.dns);
        assertEquals(first.selectionSeed, repeat.selectionSeed);
    }

    @Test
    public void rerollBaseSeedChangesDerivedSeed() {
        long first = Profile.derivePackageSeed(100L, "org.schabi.newpipe");
        long second = Profile.derivePackageSeed(101L, "org.schabi.newpipe");
        assertNotEquals(first, second);
    }

    @Test
    public void routeHiddenProfileHasNoGateway() {
        Profile profile = Profile.create(true, false, true, true, false, 1L,
                List.of(NetworkIdentity.hidden("202.128.115.2")),
                List.of(List.of("1.1.1.1")));
        Profile.Resolved resolved = profile.resolve();
        assertEquals(NetworkIdentity.RouteMode.HIDDEN, resolved.routeMode);
        assertFalse(resolved.hasExplicitRoute());
        assertNull(resolved.gateway);
    }
}

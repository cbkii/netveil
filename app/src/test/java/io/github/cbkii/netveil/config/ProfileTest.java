package io.github.cbkii.netveil.config;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ProfileTest {
    @Test
    public void listParserRejectsNonIpv4GarbageAndDuplicates() {
        assertEquals(
                List.of("10.0.0.2", "10.0.0.3"),
                Profile.parseList("10.0.0.2, bad\n10.0.0.3,10.0.0.2"));
    }

    @Test
    public void dnsLinesAreIndependentSets() {
        var sets = Profile.parseDnsSets("1.1.1.1,1.0.0.1\n9.9.9.9");
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
    public void gatewayCompatibilityRequiresSameSubnetAndDifferentAddress() {
        assertTrue(Profile.hasCompatibleGateway(
                List.of("192.168.50.20"), List.of("192.168.50.1"), 24));
        assertFalse(Profile.hasCompatibleGateway(
                List.of("192.168.50.20"), List.of("192.168.60.1"), 24));
        assertFalse(Profile.hasCompatibleGateway(
                List.of("192.168.50.20"), List.of("192.168.50.20"), 24));
    }

    @Test
    public void everyWhitelistedIpMustHaveACompatibleGateway() {
        assertTrue(Profile.allIpsHaveCompatibleGateway(
                List.of("192.168.50.20", "10.0.0.20"),
                List.of("192.168.50.1", "10.0.0.1"), 24));
        assertFalse(Profile.allIpsHaveCompatibleGateway(
                List.of("192.168.50.20", "10.0.0.20"),
                List.of("192.168.50.1"), 24));
    }
}

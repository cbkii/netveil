package io.github.cbkii.netveil.config;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Ipv4Test {
    @Test
    public void validatesLiteralIpv4() {
        assertTrue(Ipv4.isLiteral("192.168.1.2"));
        assertTrue(Ipv4.isLiteral("0.0.0.0"));
        assertTrue(Ipv4.isLiteral("255.255.255.255"));
        assertTrue(Ipv4.isLiteral("192.168.001.002"));
        assertFalse(Ipv4.isLiteral("256.1.1.1"));
        assertFalse(Ipv4.isLiteral("example.com"));
        assertFalse(Ipv4.isLiteral("1.2.3"));
    }

    @Test
    public void canonicalizesEquivalentNumericForms() {
        assertEquals("192.168.1.2", Ipv4.canonical(" 192.168.001.002 "));
        assertEquals(Ipv4.parse("192.168.1.2"), Ipv4.parse("192.168.001.002"));
    }

    @Test
    public void wifiIntUsesAndroidLegacyByteOrder() {
        assertEquals(0x0201A8C0, Ipv4.toWifiInt("192.168.1.2"));
    }

    @Test
    public void subnetComparisonRespectsPrefix() {
        assertTrue(Ipv4.sameSubnet("192.168.7.20", "192.168.7.1", 24));
        assertFalse(Ipv4.sameSubnet("192.168.7.20", "192.168.8.1", 24));
        assertTrue(Ipv4.sameSubnet("10.0.1.2", "10.0.254.1", 16));
    }

    @Test
    public void networkAndBroadcastAreDerivedCorrectly() {
        assertEquals("192.168.7.0", Ipv4.networkAddress("192.168.7.93", 24));
        assertEquals("10.20.0.0", Ipv4.networkAddress("10.20.31.42", 16));
        assertEquals("192.168.7.255", Ipv4.broadcastAddress("192.168.7.93", 24));
        assertEquals("255.255.255.0", Ipv4.netmask(24).getHostAddress());
    }
}

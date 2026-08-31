package io.github.cbkii.netveil.config;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class NetworkIdentityTest {
    @Test
    public void hiddenIdentityNeedsOnlyIpv4() {
        NetworkIdentity.Validation validation = NetworkIdentity.validate(
                "202.128.115.2", NetworkIdentity.RouteMode.HIDDEN, "", "");
        assertTrue(validation.valid);
        assertEquals("202.128.115.2", validation.identity.ipv4);
        assertEquals(NetworkIdentity.RouteMode.HIDDEN, validation.identity.routeMode);
        assertEquals(32, validation.identity.prefixLength);
        assertNull(validation.identity.gateway);
    }

    @Test
    public void explicitGatewayMustDifferAndShareSubnet() {
        assertFalse(NetworkIdentity.validate("192.168.1.2", NetworkIdentity.RouteMode.EXPLICIT,
                "24", "192.168.1.2").valid);
        assertFalse(NetworkIdentity.validate("192.168.1.2", NetworkIdentity.RouteMode.EXPLICIT,
                "24", "192.168.2.1").valid);
        assertTrue(NetworkIdentity.validate("192.168.1.2", NetworkIdentity.RouteMode.EXPLICIT,
                "24", "192.168.1.1").valid);
    }

    @Test
    public void slashZeroIsLegalButWarned() {
        NetworkIdentity.Validation validation = NetworkIdentity.validate(
                "1.129.22.61", NetworkIdentity.RouteMode.EXPLICIT, "0", "192.168.1.1");
        assertTrue(validation.valid);
        assertTrue(validation.warning.contains("entire IPv4 address space"));
    }

    @Test
    public void slash31CanUsePeerWhileSlash32CannotUseDifferentGateway() {
        assertTrue(NetworkIdentity.validate("10.0.0.0", NetworkIdentity.RouteMode.EXPLICIT,
                "31", "10.0.0.1").valid);
        assertFalse(NetworkIdentity.validate("10.0.0.1", NetworkIdentity.RouteMode.EXPLICIT,
                "32", "10.0.0.2").valid);
    }

    @Test
    public void storedRoundTripPreservesRouteModeAndAbsence() {
        List<NetworkIdentity> values = List.of(
                NetworkIdentity.hidden("202.128.115.2"),
                NetworkIdentity.explicit("192.168.1.20", 24, "192.168.1.1"));
        List<NetworkIdentity> parsed = NetworkIdentity.parseStoredList(
                NetworkIdentity.serializeList(values));
        assertEquals(2, parsed.size());
        assertEquals(NetworkIdentity.RouteMode.HIDDEN, parsed.get(0).routeMode);
        assertNull(parsed.get(0).gateway);
        assertEquals("192.168.1.1", parsed.get(1).gateway);
    }

    @Test
    public void invalidStoredEntriesAreDroppedRatherThanConverted() {
        List<NetworkIdentity> parsed = NetworkIdentity.parseStoredList(
                "192.168.1.20\nH|202.128.115.2\nE|192.168.1.20|24|192.168.1.1");
        assertEquals(2, parsed.size());
        assertEquals("202.128.115.2", parsed.get(0).ipv4);
        assertEquals("192.168.1.20", parsed.get(1).ipv4);
    }

    @Test
    public void canonicalIdentitySerializationIsStable() {
        NetworkIdentity value = NetworkIdentity.hidden("010.000.000.002");
        assertEquals("H|10.0.0.2", value.serialize());
        assertNotEquals("H|010.000.000.002", value.serialize());
    }
}

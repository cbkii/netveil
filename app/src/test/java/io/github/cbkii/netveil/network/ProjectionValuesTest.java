package io.github.cbkii.netveil.network;

import io.github.cbkii.netveil.config.NetworkIdentity;
import io.github.cbkii.netveil.config.Profile;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ProjectionValuesTest {
    @Test
    public void routeHiddenUsesNeutralDhcpGatewayAndPropertyDefault() {
        Profile.Resolved resolved = Profile.create(
                true, false, true, true, false, 1L,
                List.of(NetworkIdentity.hidden("202.128.115.2")),
                List.of(List.of("1.1.1.1"))).resolve();

        assertNull(resolved.gateway);
        assertEquals(0, ProjectionValues.dhcpGateway(resolved));
        assertEquals("caller-default", ProjectionValues.gatewayProperty(resolved, "caller-default"));
        assertNull(ProjectionValues.gatewayProperty(resolved, null));
    }

    @Test
    public void explicitRouteExposesConfiguredGatewayAtBoundaries() {
        Profile.Resolved resolved = Profile.create(
                true, false, true, true, false, 1L,
                List.of(NetworkIdentity.explicit("192.168.50.20", 24, "192.168.50.1")),
                List.of(List.of("1.1.1.1"))).resolve();

        assertEquals("192.168.50.1", resolved.gateway);
        assertEquals("192.168.50.1", ProjectionValues.gatewayProperty(resolved, "fallback"));
        assertEquals(io.github.cbkii.netveil.config.Ipv4.toWifiInt("192.168.50.1"),
                ProjectionValues.dhcpGateway(resolved));
    }
}

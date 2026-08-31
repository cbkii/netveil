package io.github.cbkii.netveil.network;

import io.github.cbkii.netveil.config.Ipv4;
import io.github.cbkii.netveil.config.Profile;

/** Small boundary adapters for fixed-width/string network metadata surfaces. */
public final class ProjectionValues {
    private ProjectionValues() {}

    /** DhcpInfo cannot represent an absent gateway; integer zero is its neutral value. */
    public static int dhcpGateway(Profile.Resolved profile) {
        return profile != null && profile.hasExplicitRoute()
                ? Ipv4.toWifiInt(profile.gateway)
                : 0;
    }

    /** String property surfaces can preserve genuine absence through their supplied default. */
    public static String gatewayProperty(Profile.Resolved profile, String fallback) {
        return profile != null && profile.gateway != null ? profile.gateway : fallback;
    }
}

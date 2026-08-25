package io.github.cbkii.netveil.xposed;

import android.net.NetworkCapabilities;

import java.util.Locale;

/** Produces internally coherent physical-looking copies of raw VPN capabilities. */
final class NetworkCapabilitiesSanitizer {
    private final VirtualNetworkProfile profile;
    private final OriginAccess origin;
    private final FrameworkObjectFactory factory;
    private final WeakIdentitySet<NetworkCapabilities> sanitized = new WeakIdentitySet<>();

    NetworkCapabilitiesSanitizer(VirtualNetworkProfile profile, OriginAccess origin,
                                 FrameworkObjectFactory factory) {
        this.profile = profile;
        this.origin = origin;
        this.factory = factory;
    }

    boolean isRawVpn(NetworkCapabilities capabilities) {
        if (capabilities == null || sanitized.contains(capabilities)) return false;
        try {
            Object result = origin.call(capabilities, NetworkCapabilities.class, "hasTransport",
                    new Class<?>[]{int.class}, NetworkCapabilities.TRANSPORT_VPN);
            return result instanceof Boolean && (Boolean) result;
        } catch (Throwable ignored) {
            return false;
        }
    }

    NetworkCapabilities sanitize(NetworkCapabilities original) throws Throwable {
        if (original == null || sanitized.contains(original) || !profile.source.hideVpn) return original;
        if (!isRawVpn(original)) return original;
        if (!profile.hasPresentationInterface()) {
            throw new IllegalStateException("presentation interface unresolved");
        }

        NetworkCapabilities copy = factory.copy(original);
        origin.callByName(copy, "removeTransportType", NetworkCapabilities.TRANSPORT_VPN);

        int[] transports = transportTypes(copy);
        if (transports.length == 0) {
            int presentation = profile.presentationTransport();
            if (presentation < 0) throw new IllegalStateException("presentation transport unresolved");
            origin.callByName(copy, "addTransportType", presentation);
        }

        origin.callByName(copy, "addCapability", NetworkCapabilities.NET_CAPABILITY_NOT_VPN);

        Object transportInfo = origin.callOptionalByName(copy, "getTransportInfo");
        if (transportInfo != null
                && transportInfo.getClass().getName().toLowerCase(Locale.ROOT).contains("vpn")) {
            origin.callByName(copy, "setTransportInfo", (Object) null);
        }

        origin.callOptionalByName(copy, "setOwnerUid", -1);
        origin.callOptionalByName(copy, "setAdministratorUids", new int[0]);
        origin.callOptionalByName(copy, "setUnderlyingNetworks", (Object) null);

        if (hasTransport(copy, NetworkCapabilities.TRANSPORT_VPN)) {
            throw new IllegalStateException("sanitized capabilities still expose TRANSPORT_VPN");
        }
        if (!hasCapability(copy, NetworkCapabilities.NET_CAPABILITY_NOT_VPN)) {
            throw new IllegalStateException("sanitized capabilities missing NOT_VPN");
        }

        sanitized.add(copy);
        return copy;
    }

    boolean isSanitized(NetworkCapabilities value) {
        return sanitized.contains(value);
    }

    int[] transportTypes(NetworkCapabilities capabilities) throws Throwable {
        Object result = origin.callByName(capabilities, "getTransportTypes");
        return result instanceof int[] ? (int[]) result : new int[0];
    }

    boolean hasTransport(NetworkCapabilities capabilities, int transport) throws Throwable {
        Object result = origin.call(capabilities, NetworkCapabilities.class, "hasTransport",
                new Class<?>[]{int.class}, transport);
        return result instanceof Boolean && (Boolean) result;
    }

    boolean hasCapability(NetworkCapabilities capabilities, int capability) throws Throwable {
        Object result = origin.call(capabilities, NetworkCapabilities.class, "hasCapability",
                new Class<?>[]{int.class}, capability);
        return result instanceof Boolean && (Boolean) result;
    }

    Object invokeGetter(NetworkCapabilities capabilities, String name, Object... args) throws Throwable {
        NetworkCapabilities projected = sanitize(capabilities);
        return origin.callByName(projected, name, args);
    }
}

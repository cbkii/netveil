package io.github.cbkii.netveil.xposed;

import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.ProxyInfo;
import android.net.RouteInfo;

import io.github.cbkii.netveil.config.Ipv4;
import io.github.cbkii.netveil.network.InterfaceClassifier;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;

/** Builds framework-native LinkProperties objects representing one coherent virtual local network. */
final class LinkPropertiesSanitizer {
    private final VirtualNetworkProfile profile;
    private final OriginAccess origin;
    private final FrameworkObjectFactory factory;
    private final WeakIdentitySet<LinkProperties> sanitized = new WeakIdentitySet<>();

    LinkPropertiesSanitizer(VirtualNetworkProfile profile, OriginAccess origin,
                            FrameworkObjectFactory factory) {
        this.profile = profile;
        this.origin = origin;
        this.factory = factory;
    }

    LinkProperties sanitize(LinkProperties original) throws Throwable {
        return sanitize(original, 0);
    }

    boolean isSanitized(LinkProperties value) {
        return sanitized.contains(value);
    }

    Object invokeGetter(LinkProperties original, String name, Object... args) throws Throwable {
        LinkProperties projected = sanitize(original);
        return origin.callByName(projected, name, args);
    }

    private LinkProperties sanitize(LinkProperties original, int depth) throws Throwable {
        if (original == null || sanitized.contains(original)) return original;
        if (!profile.hasPresentationInterface()) {
            throw new IllegalStateException("presentation interface unresolved");
        }
        if (depth > 2) throw new IllegalStateException("stacked LinkProperties depth exceeded");

        LinkProperties out = new LinkProperties();
        origin.callByName(out, "setInterfaceName", profile.presentationName());
        origin.callByName(out, "addLinkAddress", factory.linkAddress(profile.ipv4, profile.source.prefixLength));

        if (!profile.source.hideIpv6) preserveIpv6Addresses(original, out);

        for (InetAddress server : profile.dns) origin.callByName(out, "addDnsServer", server);
        origin.callOptionalByName(out, "setDomains", (Object) null);
        origin.callOptionalByName(out, "setDhcpServerAddress", (Object) null);

        if (!profile.source.hideProxy) {
            Object proxy = origin.callOptionalByName(original, "getHttpProxy");
            if (proxy instanceof ProxyInfo) origin.callOptionalByName(out, "setHttpProxy", proxy);
        } else {
            origin.callOptionalByName(out, "setHttpProxy", (Object) null);
        }

        Object mtu = origin.callOptionalByName(original, "getMtu");
        if (mtu instanceof Integer && (Integer) mtu > 0) origin.callOptionalByName(out, "setMtu", mtu);

        addVirtualIpv4Routes(out);
        if (!profile.source.hideIpv6) preserveIpv6Routes(original, out);
        preserveSafeStackedLinks(original, out, depth);

        if (!profile.source.hideIpv6) {
            Object nat64 = origin.callOptionalByName(original, "getNat64Prefix");
            if (nat64 != null) origin.callOptionalByName(out, "setNat64Prefix", nat64);
        }

        sanitized.add(out);
        return out;
    }

    private void addVirtualIpv4Routes(LinkProperties out) throws Throwable {
        String iface = profile.presentationName();
        RouteInfo connected = factory.route(
                new IpPrefix(profile.network, profile.source.prefixLength), null, iface);
        RouteInfo defaultRoute = factory.route(
                new IpPrefix(Ipv4.parse("0.0.0.0"), 0), profile.gateway, iface);
        origin.callByName(out, "addRoute", connected);
        origin.callByName(out, "addRoute", defaultRoute);
    }

    private void preserveIpv6Addresses(LinkProperties original, LinkProperties out) throws Throwable {
        Object result = origin.callByName(original, "getLinkAddresses");
        if (!(result instanceof List<?>)) return;
        for (Object value : (List<?>) result) {
            if (!(value instanceof LinkAddress)) continue;
            LinkAddress address = (LinkAddress) value;
            if (address.getAddress() instanceof Inet6Address) origin.callByName(out, "addLinkAddress", address);
        }
    }

    private void preserveIpv6Routes(LinkProperties original, LinkProperties out) throws Throwable {
        Object result = origin.callByName(original, "getRoutes");
        if (!(result instanceof List<?>)) return;
        for (Object value : (List<?>) result) {
            if (!(value instanceof RouteInfo)) continue;
            RouteInfo route = (RouteInfo) value;
            if (route.getDestination() == null
                    || !(route.getDestination().getAddress() instanceof Inet6Address)) continue;
            String iface = route.getInterface();
            if (profile.source.hideVpn && InterfaceClassifier.isVpn(iface)) continue;
            origin.callByName(out, "addRoute", route);
        }
    }

    private void preserveSafeStackedLinks(LinkProperties original, LinkProperties out, int depth) {
        Object result = origin.callOptionalByName(original, "getStackedLinks");
        if (!(result instanceof List<?>)) return;
        for (Object value : (List<?>) result) {
            if (!(value instanceof LinkProperties)) continue;
            LinkProperties stacked = (LinkProperties) value;
            try {
                Object ifaceValue = origin.callOptionalByName(stacked, "getInterfaceName");
                String iface = ifaceValue instanceof String ? (String) ifaceValue : null;
                InterfaceClassifier.Kind kind = InterfaceClassifier.classify(iface);
                if (kind == InterfaceClassifier.Kind.CLAT) continue;
                if (profile.source.hideVpn && kind == InterfaceClassifier.Kind.VPN) continue;
                LinkProperties projected = sanitize(stacked, depth + 1);
                origin.callOptionalByName(out, "addStackedLink", projected);
            } catch (Throwable ignored) {
                // The top-level projection remains valid; an unknown stacked link is safer omitted.
            }
        }
    }
}

package io.github.cbkii.netveil.xposed;

import android.content.ContentResolver;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.provider.Settings;
import android.util.Log;

import io.github.cbkii.netveil.config.Ipv4;
import io.github.cbkii.netveil.config.Profile;
import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

final class NetworkHooks {
    private static final String TAG = "NetVeil";
    private static final String FALLBACK_INTERFACE = "wlan0";

    private final NetVeilModule module;
    private final Profile.Resolved p;
    private final Inet4Address ip;
    private final Inet4Address gateway;
    private final Inet4Address network;
    private final Inet4Address broadcast;
    private final List<InetAddress> dns;

    private final Map<RouteInfo, RouteMask> routeMasks =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<NetworkInfo, Boolean> legacyVpnInfos =
            Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<InterfaceAddress, Boolean> interfaceAddressMasks =
            Collections.synchronizedMap(new WeakHashMap<>());

    private volatile String presentationInterface;

    NetworkHooks(NetVeilModule module, Profile.Resolved profile) {
        this.module = module;
        this.p = profile;
        this.ip = Ipv4.parse(profile.ipv4);
        this.gateway = Ipv4.parse(profile.gateway);
        this.network = Ipv4.parse(Ipv4.networkAddress(profile.ipv4, profile.prefixLength));
        this.broadcast = broadcastAddress(profile.ipv4, profile.prefixLength);

        List<InetAddress> values = new ArrayList<>();
        for (String value : profile.dns) values.add(Ipv4.parse(value));
        this.dns = Collections.unmodifiableList(values);
        this.presentationInterface = selectPresentationInterface();
    }

    void install() {
        installWifiHooks();
        installLinkPropertiesHooks();
        installRouteHooks();
        installNetworkCapabilitiesHooks();
        installLegacyConnectivityHooks();
        installNetworkInterfaceHooks();
        installSocketHooks();
        installSettingsHooks();
        installSystemPropertyHooks();
    }

    private void installWifiHooks() {
        hookAfter(WifiInfo.class, "getIpAddress", new Class<?>[0],
                (chain, original) -> Ipv4.toWifiInt(p.ipv4));
        hookAfter(WifiInfo.class, "toString", new Class<?>[0], this::spoofWifiInfoString);
        hookAfter(WifiManager.class, "getDhcpInfo", new Class<?>[0],
                (chain, original) -> spoofDhcp(original));
    }

    private void installLinkPropertiesHooks() {
        hookAfter(LinkProperties.class, "getLinkAddresses", new Class<?>[0],
                (chain, original) -> spoofLinkAddresses(original));
        hookOptional(LinkProperties.class, "getAllLinkAddresses", new Class<?>[0],
                (chain, original) -> spoofLinkAddresses(original));
        hookOptional(LinkProperties.class, "getAddresses", new Class<?>[0],
                (chain, original) -> spoofAddresses(original));
        hookOptional(LinkProperties.class, "getAllAddresses", new Class<?>[0],
                (chain, original) -> spoofAddresses(original));
        hookAfter(LinkProperties.class, "getDnsServers", new Class<?>[0],
                (chain, original) -> dns);
        hookOptional(LinkProperties.class, "getValidatedPrivateDnsServers", new Class<?>[0],
                (chain, original) -> Collections.emptyList());
        hookOptional(LinkProperties.class, "isPrivateDnsActive", new Class<?>[0],
                (chain, original) -> false);
        hookOptional(LinkProperties.class, "getPrivateDnsServerName", new Class<?>[0],
                (chain, original) -> null);
        hookOptional(LinkProperties.class, "getDomains", new Class<?>[0],
                (chain, original) -> null);
        hookOptional(LinkProperties.class, "getDhcpServerAddress", new Class<?>[0],
                (chain, original) -> gateway);
        hookOptional(LinkProperties.class, "getNat64Prefix", new Class<?>[0],
                (chain, original) -> p.hideIpv6 ? null : original);
        hookAfter(LinkProperties.class, "getHttpProxy", new Class<?>[0],
                (chain, original) -> p.hideProxy ? null : original);
        hookAfter(LinkProperties.class, "getInterfaceName", new Class<?>[0],
                (chain, original) -> spoofInterfaceName(original));
        hookOptional(LinkProperties.class, "getAllInterfaceNames", new Class<?>[0],
                (chain, original) -> Collections.singletonList(interfaceName()));
        hookAfter(LinkProperties.class, "getRoutes", new Class<?>[0], this::spoofRoutes);
        hookOptional(LinkProperties.class, "getAllRoutes", new Class<?>[0], this::spoofRoutes);
        hookOptional(LinkProperties.class, "getStackedLinks", new Class<?>[0],
                (chain, original) -> p.hideVpn ? Collections.emptyList() : original);
        hookAfter(LinkProperties.class, "toString", new Class<?>[0], this::spoofLinkPropertiesString);
    }

    private void installRouteHooks() {
        hookAfter(RouteInfo.class, "getGateway", new Class<?>[0], this::routeGateway);
        hookAfter(RouteInfo.class, "getDestination", new Class<?>[0], this::routeDestination);
        hookAfter(RouteInfo.class, "getInterface", new Class<?>[0], this::routeInterface);
        hookAfter(RouteInfo.class, "hasGateway", new Class<?>[0], this::routeHasGateway);
        hookAfter(RouteInfo.class, "isDefaultRoute", new Class<?>[0], this::routeIsDefault);
        hookAfter(RouteInfo.class, "matches", new Class<?>[]{InetAddress.class}, this::routeMatches);
        hookAfter(RouteInfo.class, "toString", new Class<?>[0], this::routeToString);
    }

    private void installNetworkCapabilitiesHooks() {
        hookAfter(NetworkCapabilities.class, "hasTransport", new Class<?>[]{int.class}, this::spoofHasTransport);
        hookAfter(NetworkCapabilities.class, "getTransportTypes", new Class<?>[0], this::spoofTransportTypes);
        hookAfter(NetworkCapabilities.class, "hasCapability", new Class<?>[]{int.class}, this::spoofHasCapability);
        hookOptional(NetworkCapabilities.class, "getCapabilities", new Class<?>[0], this::spoofCapabilities);
        hookOptional(NetworkCapabilities.class, "getTransportInfo", new Class<?>[0], this::spoofTransportInfo);
        hookOptional(NetworkCapabilities.class, "getOwnerUid", new Class<?>[0],
                (chain, original) -> p.hideVpn ? -1 : original);
        hookOptional(NetworkCapabilities.class, "getAdministratorUids", new Class<?>[0],
                (chain, original) -> p.hideVpn ? new int[0] : original);
        hookOptional(NetworkCapabilities.class, "getUnderlyingNetworks", new Class<?>[0],
                (chain, original) -> p.hideVpn ? null : original);
        hookAfter(NetworkCapabilities.class, "toString", new Class<?>[0], this::spoofCapabilitiesString);
    }

    private void installLegacyConnectivityHooks() {
        hookAfter(ConnectivityManager.class, "getDefaultProxy", new Class<?>[0],
                (chain, original) -> p.hideProxy ? null : original);
        hookAfter(ConnectivityManager.class, "getNetworkInfo", new Class<?>[]{int.class},
                this::spoofLegacyNetworkInfoByType);
        hookAfter(ConnectivityManager.class, "getAllNetworkInfo", new Class<?>[0],
                this::filterLegacyNetworkInfo);
        hookAfter(ConnectivityManager.class, "getActiveNetworkInfo", new Class<?>[0],
                this::tagActiveLegacyNetworkInfo);

        hookAfter(NetworkInfo.class, "getType", new Class<?>[0], this::spoofNetworkInfoType);
        hookAfter(NetworkInfo.class, "getTypeName", new Class<?>[0], this::spoofNetworkInfoTypeName);
        hookAfter(NetworkInfo.class, "getExtraInfo", new Class<?>[0], this::spoofNetworkInfoExtraInfo);
    }

    private void installNetworkInterfaceHooks() {
        hookAfter(NetworkInterface.class, "getNetworkInterfaces", new Class<?>[0],
                this::filterNetworkInterfaces);
        hookAfter(NetworkInterface.class, "getByName", new Class<?>[]{String.class},
                this::filterNetworkInterfaceLookup);
        hookAfter(NetworkInterface.class, "getByIndex", new Class<?>[]{int.class},
                this::filterNetworkInterfaceLookup);
        hookAfter(NetworkInterface.class, "getByInetAddress", new Class<?>[]{InetAddress.class},
                this::filterNetworkInterfaceLookup);
        hookAfter(NetworkInterface.class, "getInetAddresses", new Class<?>[0],
                this::spoofInterfaceAddresses);
        hookAfter(NetworkInterface.class, "getInterfaceAddresses", new Class<?>[0],
                this::spoofInterfaceAddressObjects);

        hookAfter(InterfaceAddress.class, "getAddress", new Class<?>[0], this::spoofInterfaceAddressValue);
        hookAfter(InterfaceAddress.class, "getBroadcast", new Class<?>[0], this::spoofInterfaceBroadcast);
        hookAfter(InterfaceAddress.class, "getNetworkPrefixLength", new Class<?>[0],
                this::spoofInterfacePrefix);
    }

    private void installSocketHooks() {
        hookAfter(Socket.class, "getLocalAddress", new Class<?>[0], this::spoofLocalInetAddress);
        hookAfter(Socket.class, "getLocalSocketAddress", new Class<?>[0], this::spoofLocalSocketAddress);
        hookAfter(DatagramSocket.class, "getLocalAddress", new Class<?>[0], this::spoofLocalInetAddress);
        hookAfter(DatagramSocket.class, "getLocalSocketAddress", new Class<?>[0], this::spoofLocalSocketAddress);
        hookAfter(ServerSocket.class, "getInetAddress", new Class<?>[0], this::spoofLocalInetAddress);
        hookAfter(ServerSocket.class, "getLocalSocketAddress", new Class<?>[0], this::spoofLocalSocketAddress);

        hookOptionalClass("sun.nio.ch.SocketChannelImpl", "getLocalAddress", new Class<?>[0],
                this::spoofLocalSocketAddress);
        hookOptionalClass("sun.nio.ch.DatagramChannelImpl", "getLocalAddress", new Class<?>[0],
                this::spoofLocalSocketAddress);
        hookOptionalClass("sun.nio.ch.ServerSocketChannelImpl", "getLocalAddress", new Class<?>[0],
                this::spoofLocalSocketAddress);
    }

    private void installSettingsHooks() {
        hookAfter(Settings.Secure.class, "getString", new Class<?>[]{ContentResolver.class, String.class},
                this::hideVpnSettingString);
        hookAfter(Settings.Global.class, "getString", new Class<?>[]{ContentResolver.class, String.class},
                this::hideVpnSettingString);
        hookAfter(Settings.Secure.class, "getInt", new Class<?>[]{ContentResolver.class, String.class, int.class},
                this::hideVpnSettingInt);
        hookAfter(Settings.Global.class, "getInt", new Class<?>[]{ContentResolver.class, String.class, int.class},
                this::hideVpnSettingInt);
    }

    private void installSystemPropertyHooks() {
        hookAfter(System.class, "getProperty", new Class<?>[]{String.class}, this::spoofJavaProperty);
        hookAfter(System.class, "getProperty", new Class<?>[]{String.class, String.class}, this::spoofJavaProperty);
        hookOptionalClass("android.os.SystemProperties", "get", new Class<?>[]{String.class},
                this::spoofAndroidProperty);
        hookOptionalClass("android.os.SystemProperties", "get", new Class<?>[]{String.class, String.class},
                this::spoofAndroidProperty);
    }

    private Object spoofWifiInfoString(XposedInterface.Chain chain, Object original) {
        if (!(original instanceof String)) return original;
        return ((String) original).replaceAll("(?i)(IP:\\s*)[^,]+", "$1" + p.ipv4);
    }

    private Object spoofLinkPropertiesString(XposedInterface.Chain chain, Object original) {
        if (!(original instanceof String)) return original;
        return "LinkProperties{iface=" + interfaceName()
                + ", ipv4=" + p.ipv4 + "/" + p.prefixLength
                + ", gateway=" + p.gateway
                + ", dns=" + p.dns
                + ", proxy=" + (p.hideProxy ? "hidden" : "passthrough")
                + ", ipv6=" + (p.hideIpv6 ? "hidden" : "passthrough") + "}";
    }

    private Object spoofTransportInfo(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn || original == null) return original;
        String name = original.getClass().getName().toLowerCase(Locale.ROOT);
        return name.contains("vpn") ? null : original;
    }

    private Object spoofCapabilitiesString(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn || !(original instanceof String)) return original;
        Object receiver = HookChainCompat.receiver(chain);
        if (!(receiver instanceof NetworkCapabilities)) return original;
        boolean vpn = rawHasTransport((NetworkCapabilities) receiver, NetworkCapabilities.TRANSPORT_VPN)
                || ((String) original).toLowerCase(Locale.ROOT).contains("vpn");
        if (!vpn) return original;
        return "NetworkCapabilities{transport=" + presentationTransportName() + ", NOT_VPN}";
    }

    private Object spoofDhcp(Object original) {
        if (!(original instanceof DhcpInfo)) return original;
        DhcpInfo d = (DhcpInfo) original;
        DhcpInfo out = new DhcpInfo();
        out.ipAddress = Ipv4.toWifiInt(p.ipv4);
        out.gateway = Ipv4.toWifiInt(p.gateway);
        out.netmask = prefixToWifiInt(p.prefixLength);
        out.dns1 = p.dns.isEmpty() ? 0 : Ipv4.toWifiInt(p.dns.get(0));
        out.dns2 = p.dns.size() < 2 ? 0 : Ipv4.toWifiInt(p.dns.get(1));
        out.serverAddress = Ipv4.toWifiInt(p.gateway);
        out.leaseDuration = d.leaseDuration;
        return out;
    }

    private Object spoofLinkAddresses(Object original) {
        List<LinkAddress> out = new ArrayList<>();
        out.add(LinkAddressCompat.create(ip, p.prefixLength));
        if (!p.hideIpv6 && original instanceof List<?>) {
            for (Object value : (List<?>) original) {
                if (value instanceof LinkAddress
                        && ((LinkAddress) value).getAddress() instanceof Inet6Address) {
                    out.add((LinkAddress) value);
                }
            }
        }
        return Collections.unmodifiableList(out);
    }

    private Object spoofAddresses(Object original) {
        List<InetAddress> out = new ArrayList<>();
        out.add(ip);
        if (!p.hideIpv6 && original instanceof List<?>) {
            for (Object value : (List<?>) original) {
                if (value instanceof Inet6Address) out.add((InetAddress) value);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private Object spoofInterfaceName(Object original) {
        if (!(original instanceof String)) return original;
        String value = (String) original;
        if (p.hideVpn && isVpnName(value)) return interfaceName();
        rememberPresentationInterface(value);
        return original;
    }

    private Object spoofRoutes(XposedInterface.Chain chain, Object original) {
        if (!(original instanceof List<?>)) return original;
        List<RouteInfo> out = new ArrayList<>();
        for (Object value : (List<?>) original) {
            if (!(value instanceof RouteInfo)) continue;
            RouteInfo route = (RouteInfo) value;
            try {
                IpPrefix destination = route.getDestination();
                InetAddress gatewayValue = route.getGateway();
                boolean ipv4 = (destination != null && destination.getAddress() instanceof Inet4Address)
                        || gatewayValue instanceof Inet4Address;
                if (!ipv4) {
                    if (!p.hideIpv6) out.add(route);
                    continue;
                }
                RouteMask mask = new RouteMask(route.isDefaultRoute(), route.hasGateway());
                routeMasks.put(route, mask);
                out.add(route);
            } catch (Throwable ignored) {
                out.add(route);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private Object routeGateway(XposedInterface.Chain chain, Object original) {
        RouteMask mask = routeMask(chain);
        if (mask == null) return original;
        return mask.hasGateway ? gateway : null;
    }

    private Object routeDestination(XposedInterface.Chain chain, Object original) {
        RouteMask mask = routeMask(chain);
        if (mask == null) return original;
        return mask.defaultRoute ? new IpPrefix(Ipv4.parse("0.0.0.0"), 0)
                : new IpPrefix(network, p.prefixLength);
    }

    private Object routeInterface(XposedInterface.Chain chain, Object original) {
        return routeMask(chain) == null ? original : interfaceName();
    }

    private Object routeHasGateway(XposedInterface.Chain chain, Object original) {
        RouteMask mask = routeMask(chain);
        return mask == null ? original : mask.hasGateway;
    }

    private Object routeIsDefault(XposedInterface.Chain chain, Object original) {
        RouteMask mask = routeMask(chain);
        return mask == null ? original : mask.defaultRoute;
    }

    private Object routeMatches(XposedInterface.Chain chain, Object original) {
        RouteMask mask = routeMask(chain);
        Object candidate = HookChainCompat.arg(chain, 0);
        if (mask == null || !(candidate instanceof InetAddress)) return original;
        IpPrefix prefix = mask.defaultRoute ? new IpPrefix(Ipv4.parse("0.0.0.0"), 0)
                : new IpPrefix(network, p.prefixLength);
        return prefix.contains((InetAddress) candidate);
    }

    private Object routeToString(XposedInterface.Chain chain, Object original) {
        RouteMask mask = routeMask(chain);
        if (mask == null) return original;
        String destination = mask.defaultRoute ? "0.0.0.0/0" : network.getHostAddress() + "/" + p.prefixLength;
        String via = mask.hasGateway ? " -> " + gateway.getHostAddress() : "";
        return destination + via + " " + interfaceName();
    }

    private RouteMask routeMask(XposedInterface.Chain chain) {
        Object receiver = HookChainCompat.receiver(chain);
        return receiver instanceof RouteInfo ? routeMasks.get(receiver) : null;
    }

    private Object spoofHasTransport(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn) return original;
        Object arg = HookChainCompat.arg(chain, 0);
        Object receiver = HookChainCompat.receiver(chain);
        if (!(arg instanceof Integer) || !(receiver instanceof NetworkCapabilities)) return original;
        int requested = (Integer) arg;
        // Hiding TRANSPORT_VPN must not depend on private-field reflection succeeding.
        if (requested == NetworkCapabilities.TRANSPORT_VPN) return false;
        boolean actualVpn = rawHasTransport((NetworkCapabilities) receiver, NetworkCapabilities.TRANSPORT_VPN);
        if (actualVpn && requested == presentationTransport()) return true;
        return original;
    }

    private Object spoofTransportTypes(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn || !(original instanceof int[])) return original;
        int[] values = (int[]) original;
        boolean hadVpn = false;
        List<Integer> kept = new ArrayList<>();
        for (int value : values) {
            if (value == NetworkCapabilities.TRANSPORT_VPN) hadVpn = true;
            else kept.add(value);
        }
        if (!hadVpn) return original;
        if (kept.isEmpty()) kept.add(presentationTransport());
        int[] out = new int[kept.size()];
        for (int i = 0; i < kept.size(); i++) out[i] = kept.get(i);
        return out;
    }

    private Object spoofHasCapability(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn) return original;
        Object arg = HookChainCompat.arg(chain, 0);
        if (arg instanceof Integer && ((Integer) arg) == NetworkCapabilities.NET_CAPABILITY_NOT_VPN) return true;
        return original;
    }

    private Object spoofCapabilities(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn || !(original instanceof int[])) return original;
        int[] values = (int[]) original;
        for (int value : values) {
            if (value == NetworkCapabilities.NET_CAPABILITY_NOT_VPN) return values;
        }
        int[] out = new int[values.length + 1];
        System.arraycopy(values, 0, out, 0, values.length);
        out[values.length] = NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
        return out;
    }

    private Object spoofLegacyNetworkInfoByType(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn) return original;
        Object type = HookChainCompat.arg(chain, 0);
        if (type instanceof Integer && ((Integer) type) == ConnectivityManager.TYPE_VPN) {
            if (original instanceof NetworkInfo) legacyVpnInfos.put((NetworkInfo) original, Boolean.TRUE);
            return null;
        }
        return original;
    }

    private Object filterLegacyNetworkInfo(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn || !(original instanceof NetworkInfo[])) return original;
        List<NetworkInfo> out = new ArrayList<>();
        for (NetworkInfo info : (NetworkInfo[]) original) {
            if (info == null) continue;
            if (rawLegacyNetworkType(info) == ConnectivityManager.TYPE_VPN) {
                legacyVpnInfos.put(info, Boolean.TRUE);
                continue;
            }
            out.add(info);
        }
        return out.toArray(new NetworkInfo[0]);
    }

    private Object tagActiveLegacyNetworkInfo(XposedInterface.Chain chain, Object original) {
        if (p.hideVpn && original instanceof NetworkInfo
                && rawLegacyNetworkType((NetworkInfo) original) == ConnectivityManager.TYPE_VPN) {
            legacyVpnInfos.put((NetworkInfo) original, Boolean.TRUE);
        }
        return original;
    }

    private Object spoofNetworkInfoType(XposedInterface.Chain chain, Object original) {
        return p.hideVpn && isTaggedLegacyVpn(chain) ? ConnectivityManager.TYPE_WIFI : original;
    }

    private Object spoofNetworkInfoTypeName(XposedInterface.Chain chain, Object original) {
        return p.hideVpn && isTaggedLegacyVpn(chain) ? "WIFI" : original;
    }

    private Object spoofNetworkInfoExtraInfo(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn || !isTaggedLegacyVpn(chain)) return original;
        return null;
    }

    private boolean isTaggedLegacyVpn(XposedInterface.Chain chain) {
        Object receiver = HookChainCompat.receiver(chain);
        return receiver instanceof NetworkInfo && legacyVpnInfos.containsKey(receiver);
    }

    private int rawLegacyNetworkType(NetworkInfo info) {
        try {
            Field field = NetworkInfo.class.getDeclaredField("mNetworkType");
            field.setAccessible(true);
            return field.getInt(info);
        } catch (Throwable ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private Object filterNetworkInterfaces(XposedInterface.Chain chain, Object original) {
        if (!(original instanceof Enumeration<?>)) return original;
        List<NetworkInterface> out = new ArrayList<>();
        Enumeration<?> values = (Enumeration<?>) original;
        while (values.hasMoreElements()) {
            Object value = values.nextElement();
            if (!(value instanceof NetworkInterface)) continue;
            NetworkInterface networkInterface = (NetworkInterface) value;
            String name = networkInterface.getName();
            if (isVpnName(name)) {
                if (!p.hideVpn) out.add(networkInterface);
                continue;
            }
            if (isLoopbackName(name) || isPresentationInterface(name)) out.add(networkInterface);
        }
        return Collections.enumeration(out);
    }

    private Object filterNetworkInterfaceLookup(XposedInterface.Chain chain, Object original) {
        Object arg = HookChainCompat.arg(chain, 0);
        if (arg instanceof String) {
            String requested = (String) arg;
            if (isVpnName(requested)) return p.hideVpn ? null : original;
            if (!isLoopbackName(requested) && !requested.equals(interfaceName())) return null;
        }
        if (arg instanceof Inet6Address && p.hideIpv6) return null;
        if (original instanceof NetworkInterface) {
            String name = ((NetworkInterface) original).getName();
            if (isVpnName(name)) return p.hideVpn ? null : original;
            if (!isLoopbackName(name) && !isPresentationInterface(name)) return null;
        }
        return original;
    }

    private Object spoofInterfaceAddresses(XposedInterface.Chain chain, Object original) {
        Object receiver = HookChainCompat.receiver(chain);
        if (!(receiver instanceof NetworkInterface)) return original;
        String name = ((NetworkInterface) receiver).getName();
        if (isVpnName(name)) return p.hideVpn ? Collections.emptyEnumeration() : original;
        if (isLoopbackName(name)) return original;
        if (!isPresentationInterface(name)) return Collections.emptyEnumeration();

        List<InetAddress> out = new ArrayList<>();
        out.add(ip);
        if (!p.hideIpv6 && original instanceof Enumeration<?>) {
            Enumeration<?> values = (Enumeration<?>) original;
            while (values.hasMoreElements()) {
                Object value = values.nextElement();
                if (value instanceof Inet6Address) out.add((InetAddress) value);
            }
        }
        return Collections.enumeration(out);
    }

    private Object spoofInterfaceAddressObjects(XposedInterface.Chain chain, Object original) {
        Object receiver = HookChainCompat.receiver(chain);
        if (!(receiver instanceof NetworkInterface)) return original;
        String name = ((NetworkInterface) receiver).getName();
        if (isVpnName(name)) return p.hideVpn ? Collections.emptyList() : original;
        if (isLoopbackName(name)) return original;
        if (!isPresentationInterface(name)) return Collections.emptyList();
        if (!(original instanceof List<?>)) return original;

        List<InterfaceAddress> out = new ArrayList<>();
        boolean claimedIpv4 = false;
        for (Object value : (List<?>) original) {
            if (!(value instanceof InterfaceAddress)) continue;
            InterfaceAddress ia = (InterfaceAddress) value;
            InetAddress address = ia.getAddress();
            if (address instanceof Inet4Address && !claimedIpv4) {
                interfaceAddressMasks.put(ia, Boolean.TRUE);
                out.add(ia);
                claimedIpv4 = true;
            } else if (!p.hideIpv6 && address instanceof Inet6Address) {
                out.add(ia);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private Object spoofInterfaceAddressValue(XposedInterface.Chain chain, Object original) {
        return isMaskedInterfaceAddress(chain) ? ip : original;
    }

    private Object spoofInterfaceBroadcast(XposedInterface.Chain chain, Object original) {
        return isMaskedInterfaceAddress(chain) ? broadcast : original;
    }

    private Object spoofInterfacePrefix(XposedInterface.Chain chain, Object original) {
        return isMaskedInterfaceAddress(chain) ? (short) p.prefixLength : original;
    }

    private boolean isMaskedInterfaceAddress(XposedInterface.Chain chain) {
        Object receiver = HookChainCompat.receiver(chain);
        return receiver instanceof InterfaceAddress && interfaceAddressMasks.containsKey(receiver);
    }

    private Object spoofLocalInetAddress(XposedInterface.Chain chain, Object original) {
        if (!(original instanceof InetAddress)) return original;
        InetAddress address = (InetAddress) original;
        return shouldSpoofLocalAddress(address) ? ip : original;
    }

    private Object spoofLocalSocketAddress(XposedInterface.Chain chain, Object original) {
        if (!(original instanceof InetSocketAddress)) return original;
        InetSocketAddress socketAddress = (InetSocketAddress) original;
        InetAddress address = socketAddress.getAddress();
        if (!shouldSpoofLocalAddress(address)) return original;
        return new InetSocketAddress(ip, socketAddress.getPort());
    }

    private boolean shouldSpoofLocalAddress(InetAddress address) {
        if (address == null || address.isLoopbackAddress() || address.isAnyLocalAddress()) return false;
        if (address instanceof Inet4Address) return true;
        return p.hideIpv6 && address instanceof Inet6Address;
    }

    private Object hideVpnSettingString(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn) return original;
        Object key = HookChainCompat.arg(chain, 1);
        return key instanceof String && isVpnSetting((String) key) ? null : original;
    }

    private Object hideVpnSettingInt(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn) return original;
        Object key = HookChainCompat.arg(chain, 1);
        return key instanceof String && isVpnSetting((String) key) ? 0 : original;
    }

    private Object spoofJavaProperty(XposedInterface.Chain chain, Object original) {
        Object key = HookChainCompat.arg(chain, 0);
        if (!(key instanceof String)) return original;
        String lower = ((String) key).toLowerCase(Locale.ROOT);
        if (p.hideProxy && isProxyKey(lower)) return null;
        return original;
    }

    private Object spoofAndroidProperty(XposedInterface.Chain chain, Object original) {
        Object key = HookChainCompat.arg(chain, 0);
        if (!(key instanceof String)) return original;
        String lower = ((String) key).toLowerCase(Locale.ROOT);
        if (p.hideProxy && isProxyKey(lower)) return "";
        if (isDnsProperty(lower)) return dnsForProperty(lower);
        if (p.hideIpv6 && lower.contains("ipv6")) return "";
        if (isGatewayProperty(lower)) return p.gateway;
        if (isIpProperty(lower)) return p.ipv4;
        if (p.hideVpn && (lower.contains("vpn") || lower.contains("tun"))) return "";
        return original;
    }

    private String dnsForProperty(String key) {
        if (dns.isEmpty()) return "";
        int index = 0;
        for (int i = key.length() - 1; i >= 0; i--) {
            char c = key.charAt(i);
            if (c >= '1' && c <= '9') {
                index = c - '1';
                break;
            }
        }
        index = Math.min(index, dns.size() - 1);
        return dns.get(index).getHostAddress();
    }

    private String selectPresentationInterface() {
        String best = null;
        int bestPriority = Integer.MAX_VALUE;
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface candidate = interfaces.nextElement();
                String name = candidate.getName();
                if (name == null || isVpnName(name) || isLoopbackName(name)) continue;
                try {
                    if (!candidate.isUp()) continue;
                } catch (Throwable ignored) {
                    continue;
                }
                boolean hasIpv4 = false;
                Enumeration<InetAddress> addresses = candidate.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                        hasIpv4 = true;
                        break;
                    }
                }
                if (!hasIpv4) continue;
                int priority = interfacePriority(name);
                if (priority < bestPriority) {
                    best = name;
                    bestPriority = priority;
                }
            }
        } catch (Throwable ignored) {
            // Fall back to lazy observation.
        }
        return best;
    }

    private boolean isPresentationInterface(String name) {
        if (name == null || isVpnName(name) || isLoopbackName(name)) return false;
        rememberPresentationInterface(name);
        return name.equals(presentationInterface);
    }

    private void rememberPresentationInterface(String name) {
        if (name == null || isVpnName(name) || isLoopbackName(name)) return;
        // Once startup inspection selected an active physical interface, do not let a target app
        // change the virtual topology merely by querying another interface name later.
        if (presentationInterface == null) presentationInterface = name;
    }

    private String interfaceName() {
        String value = presentationInterface;
        return value == null ? FALLBACK_INTERFACE : value;
    }

    private int presentationTransport() {
        String name = interfaceName().toLowerCase(Locale.ROOT);
        if (name.startsWith("wlan")) return NetworkCapabilities.TRANSPORT_WIFI;
        if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp")) {
            return NetworkCapabilities.TRANSPORT_CELLULAR;
        }
        if (name.startsWith("eth")) return NetworkCapabilities.TRANSPORT_ETHERNET;
        return NetworkCapabilities.TRANSPORT_WIFI;
    }

    private boolean rawHasTransport(NetworkCapabilities capabilities, int transport) {
        try {
            Field field = NetworkCapabilities.class.getDeclaredField("mTransportTypes");
            field.setAccessible(true);
            long mask = field.getLong(capabilities);
            return (mask & (1L << transport)) != 0;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String presentationTransportName() {
        String name = interfaceName().toLowerCase(Locale.ROOT);
        if (name.startsWith("wlan")) return "WIFI";
        if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp")) return "CELLULAR";
        if (name.startsWith("eth")) return "ETHERNET";
        return "PHYSICAL";
    }

    private static int interfacePriority(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.startsWith("wlan")) return 0;
        if (n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("pdp")) return 1;
        if (n.startsWith("eth")) return 2;
        return 10;
    }

    private static boolean isLoopbackName(String name) {
        return name != null && (name.equals("lo") || name.startsWith("lo:"));
    }

    private static boolean isVpnName(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.startsWith("tun") || n.startsWith("tap") || n.startsWith("ppp")
                || n.startsWith("wg") || n.startsWith("ipsec") || n.startsWith("xfrm")
                || n.startsWith("tailscale") || n.startsWith("zt") || n.startsWith("vpn");
    }

    private static boolean containsVpnToken(String value) {
        return value.contains("vpn") || value.contains("tun") || value.contains("wireguard")
                || value.contains("wg") || value.contains("ipsec") || value.contains("tailscale");
    }

    private static boolean isVpnSetting(String key) {
        String value = key.toLowerCase(Locale.ROOT);
        return value.equals("always_on_vpn_app") || value.equals("always_on_vpn_lockdown")
                || value.equals("always_on_vpn_lockdown_whitelist")
                || value.equals("vpn_lockdown_whitelist") || value.equals("legacy_vpn_name");
    }

    private static boolean isProxyKey(String key) {
        String value = key.toLowerCase(Locale.ROOT).replace('-', '_');
        return value.equals("http.proxyhost") || value.equals("https.proxyhost")
                || value.equals("socksproxyhost") || value.equals("http.proxyport")
                || value.equals("https.proxyport") || value.equals("socksproxyport")
                || value.equals("http_proxy") || value.equals("https_proxy")
                || value.equals("all_proxy") || value.equals("no_proxy")
                || value.contains("global_http_proxy") || value.contains("proxy_host")
                || value.contains("proxy_port");
    }

    private static boolean isDnsProperty(String key) {
        return key.matches(".*(?:^|[._-])dns[1-9]$") || key.contains(".dns1")
                || key.contains(".dns2") || key.contains(".dns3") || key.contains(".dns4");
    }

    private static boolean isGatewayProperty(String key) {
        return key.endsWith(".gateway") || key.endsWith("_gateway") || key.contains(".gateway.");
    }

    private static boolean isIpProperty(String key) {
        return key.endsWith(".ipaddress") || key.endsWith("_ipaddress") || key.contains(".ipaddress.");
    }

    private static int prefixToWifiInt(int prefix) {
        long mask = prefix == 0 ? 0 : (0xffffffffL << (32 - prefix)) & 0xffffffffL;
        return Integer.reverseBytes((int) mask);
    }

    private static Inet4Address broadcastAddress(String address, int prefix) {
        long ipValue = Ipv4.toUnsignedLong(address);
        long mask = prefix == 0 ? 0L : ((0xffffffffL << (32 - prefix)) & 0xffffffffL);
        long value = (ipValue & mask) | (~mask & 0xffffffffL);
        String text = ((value >>> 24) & 0xff) + "." + ((value >>> 16) & 0xff) + "."
                + ((value >>> 8) & 0xff) + "." + (value & 0xff);
        return Ipv4.parse(text);
    }

    private interface Transformer {
        Object apply(XposedInterface.Chain chain, Object original) throws Throwable;
    }

    private void hookAfter(Class<?> owner, String name, Class<?>[] params, Transformer transformer) {
        try {
            Method method = owner.getDeclaredMethod(name, params);
            installHook(method, owner.getName() + "." + name, transformer);
        } catch (NoSuchMethodException e) {
            module.log(Log.WARN, TAG, "required hook missing: " + owner.getName() + "." + name);
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "hook failed: " + owner.getName() + "." + name, t);
        }
    }

    private void hookOptional(Class<?> owner, String name, Class<?>[] params, Transformer transformer) {
        try {
            Method method = owner.getDeclaredMethod(name, params);
            installHook(method, owner.getName() + "." + name, transformer);
        } catch (NoSuchMethodException ignored) {
            // Hidden/SystemApi surface varies by Android release.
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "optional hook failed: " + owner.getName() + "." + name, t);
        }
    }

    private void hookOptionalClass(String className, String name, Class<?>[] params, Transformer transformer) {
        try {
            Class<?> owner = Class.forName(className, false, null);
            Method method = owner.getDeclaredMethod(name, params);
            installHook(method, className + "." + name, transformer);
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Optional libcore/hidden API surface.
        } catch (Throwable t) {
            module.log(Log.WARN, TAG, "optional class hook failed: " + className + "." + name, t);
        }
    }

    private void installHook(Method method, String label, Transformer transformer) {
        module.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object original = chain.proceed();
                    try {
                        return transformer.apply(chain, original);
                    } catch (Throwable t) {
                        module.log(Log.WARN, TAG, label + " fallback", t);
                        return original;
                    }
                });
    }

    private static final class RouteMask {
        final boolean defaultRoute;
        final boolean hasGateway;

        RouteMask(boolean defaultRoute, boolean hasGateway) {
            this.defaultRoute = defaultRoute;
            this.hasGateway = hasGateway;
        }
    }
}

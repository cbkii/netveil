package io.github.cbkii.netveil.xposed;

import android.content.ContentResolver;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Parcel;
import android.provider.Settings;

import io.github.cbkii.netveil.config.Ipv4;
import io.github.cbkii.netveil.config.Profile;
import io.github.cbkii.netveil.network.InterfaceClassifier;
import io.github.cbkii.netveil.network.PropertyMaskPolicy;
import io.github.libxposed.api.XposedInterface;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** App-process Java/framework virtual-network projection. */
final class NetworkHooks {
    private final NetVeilModule module;
    private final Profile.Resolved p;
    private final VirtualNetworkProfile model;
    private final OriginAccess origin;
    private final FrameworkObjectFactory factory;
    private final NetworkCapabilitiesSanitizer capabilities;
    private final LinkPropertiesSanitizer links;
    private final HookHealth health;

    private final WeakIdentitySet<NetworkInfo> legacyVpnInfos = new WeakIdentitySet<>();
    private final Set<Object> suppressedVpnRequests =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    NetworkHooks(NetVeilModule module, Profile.Resolved profile) {
        this.module = module;
        this.p = profile;
        this.model = new VirtualNetworkProfile(profile);
        this.origin = new OriginAccess(module);
        this.factory = new FrameworkObjectFactory(module, origin);
        this.capabilities = new NetworkCapabilitiesSanitizer(model, origin, factory);
        this.links = new LinkPropertiesSanitizer(model, origin, factory);
        this.health = new HookHealth(module);
    }

    String install() {
        installWifiHooks();
        installConnectivityHooks();
        installNetworkCapabilitiesHooks();
        installLinkPropertiesHooks();
        installLegacyHooks();
        installNetworkInterfaceHooks();
        installSocketHooks();
        installSettingsHooks();
        installPropertyHooks();
        installVpnRequestHooks();
        health.requireHealthy();
        return health.summary() + " transport=" + model.presentationTransportName();
    }

    private void installWifiHooks() {
        hookAfter(HookHealth.Requirement.REQUIRED, WifiInfo.class, "getIpAddress", noArgs(),
                (chain, original) -> Ipv4.toWifiInt(p.ipv4));
        hookAfter(HookHealth.Requirement.REQUIRED, WifiManager.class, "getDhcpInfo", noArgs(),
                (chain, original) -> spoofDhcpInfo(original));
        hookAfter(HookHealth.Requirement.OPTIONAL, WifiInfo.class, "toString", noArgs(),
                this::spoofWifiToString);
        hookAround(HookHealth.Requirement.REQUIRED, WifiInfo.class, "writeToParcel",
                new Class<?>[]{Parcel.class, int.class}, this::writeWifiParcel);
    }

    private void installConnectivityHooks() {
        hookAfter(HookHealth.Requirement.REQUIRED, ConnectivityManager.class, "getNetworkCapabilities",
                new Class<?>[]{Network.class},
                (chain, original) -> original instanceof NetworkCapabilities
                        ? capabilities.sanitize((NetworkCapabilities) original) : original);
        hookAfter(HookHealth.Requirement.REQUIRED, ConnectivityManager.class, "getLinkProperties",
                new Class<?>[]{Network.class},
                (chain, original) -> original instanceof LinkProperties
                        ? links.sanitize((LinkProperties) original) : original);
        hookAfter(HookHealth.Requirement.REQUIRED, ConnectivityManager.class, "getAllNetworks", noArgs(),
                this::filterAllNetworks);
        hookAfter(HookHealth.Requirement.REQUIRED, ConnectivityManager.class, "getDefaultProxy", noArgs(),
                (chain, original) -> p.hideProxy ? null : original);
        hookAfter(HookHealth.Requirement.OPTIONAL, ConnectivityManager.class, "getNetworkForType",
                new Class<?>[]{int.class}, this::hideLegacyVpnNetworkHandle);
    }

    private void installNetworkCapabilitiesHooks() {
        hookAfter(HookHealth.Requirement.REQUIRED, NetworkCapabilities.class, "hasTransport",
                new Class<?>[]{int.class},
                (chain, original) -> projectedCapabilitiesGetter(chain, "hasTransport", original,
                        HookChainCompat.arg(chain, 0)));
        hookAfter(HookHealth.Requirement.REQUIRED, NetworkCapabilities.class, "hasCapability",
                new Class<?>[]{int.class},
                (chain, original) -> projectedCapabilitiesGetter(chain, "hasCapability", original,
                        HookChainCompat.arg(chain, 0)));
        hookAfter(HookHealth.Requirement.REQUIRED, NetworkCapabilities.class, "getTransportTypes", noArgs(),
                (chain, original) -> projectedCapabilitiesGetter(chain, "getTransportTypes", original));
        hookOptionalCapabilityGetter("getCapabilities");
        hookOptionalCapabilityGetter("getTransportInfo");
        hookOptionalCapabilityGetter("getOwnerUid");
        hookOptionalCapabilityGetter("getAdministratorUids");
        hookOptionalCapabilityGetter("getUnderlyingNetworks");
        hookAfter(HookHealth.Requirement.REQUIRED, NetworkCapabilities.class, "toString", noArgs(),
                this::capabilitiesToString);
        hookAround(HookHealth.Requirement.REQUIRED, NetworkCapabilities.class, "writeToParcel",
                new Class<?>[]{Parcel.class, int.class}, this::writeCapabilitiesParcel);
    }

    private void installLinkPropertiesHooks() {
        String[] requiredGetters = {
                "getLinkAddresses", "getDnsServers", "getRoutes", "getInterfaceName", "getHttpProxy"
        };
        for (String name : requiredGetters) hookLinkGetter(HookHealth.Requirement.REQUIRED, name);

        String[] optionalGetters = {
                "getAllLinkAddresses", "getAddresses", "getAllAddresses", "getAllRoutes",
                "getAllInterfaceNames", "getValidatedPrivateDnsServers", "isPrivateDnsActive",
                "getPrivateDnsServerName", "getDomains", "getDhcpServerAddress", "getNat64Prefix",
                "getStackedLinks", "getMtu"
        };
        for (String name : optionalGetters) hookLinkGetter(HookHealth.Requirement.OPTIONAL, name);

        hookAfter(HookHealth.Requirement.REQUIRED, LinkProperties.class, "toString", noArgs(),
                this::linkPropertiesToString);
        hookAround(HookHealth.Requirement.REQUIRED, LinkProperties.class, "writeToParcel",
                new Class<?>[]{Parcel.class, int.class}, this::writeLinkPropertiesParcel);
    }

    private void installLegacyHooks() {
        hookAfter(HookHealth.Requirement.OPTIONAL, ConnectivityManager.class, "getNetworkInfo",
                new Class<?>[]{int.class}, this::legacyNetworkInfoByType);
        hookAfter(HookHealth.Requirement.OPTIONAL, ConnectivityManager.class, "getAllNetworkInfo", noArgs(),
                this::filterLegacyNetworkInfo);
        hookAfter(HookHealth.Requirement.OPTIONAL, ConnectivityManager.class, "getActiveNetworkInfo", noArgs(),
                this::tagActiveLegacyNetworkInfo);
        hookAfter(HookHealth.Requirement.OPTIONAL, NetworkInfo.class, "getType", noArgs(),
                this::legacyNetworkType);
        hookAfter(HookHealth.Requirement.OPTIONAL, NetworkInfo.class, "getTypeName", noArgs(),
                this::legacyNetworkTypeName);
        hookAfter(HookHealth.Requirement.OPTIONAL, NetworkInfo.class, "getExtraInfo", noArgs(),
                this::legacyExtraInfo);
    }

    private void installNetworkInterfaceHooks() {
        hookAfter(HookHealth.Requirement.REQUIRED, NetworkInterface.class, "getNetworkInterfaces", noArgs(),
                this::filterNetworkInterfaces);
        hookAfter(HookHealth.Requirement.REQUIRED, NetworkInterface.class, "getByName",
                new Class<?>[]{String.class}, this::filterNetworkInterfaceLookup);
        hookAfter(HookHealth.Requirement.REQUIRED, NetworkInterface.class, "getByIndex",
                new Class<?>[]{int.class}, this::filterNetworkInterfaceLookup);
        hookAfter(HookHealth.Requirement.REQUIRED, NetworkInterface.class, "getByInetAddress",
                new Class<?>[]{InetAddress.class}, this::filterNetworkInterfaceLookup);
        hookAfter(HookHealth.Requirement.REQUIRED, NetworkInterface.class, "getInetAddresses", noArgs(),
                this::spoofNetworkInterfaceAddresses);
        hookAfter(HookHealth.Requirement.REQUIRED, NetworkInterface.class, "getInterfaceAddresses", noArgs(),
                this::spoofInterfaceAddressObjects);
    }

    private void installSocketHooks() {
        hookAfter(HookHealth.Requirement.OPTIONAL, Socket.class, "getLocalAddress", noArgs(),
                this::spoofLocalInetAddress);
        hookAfter(HookHealth.Requirement.OPTIONAL, Socket.class, "getLocalSocketAddress", noArgs(),
                this::spoofLocalSocketAddress);
        hookAfter(HookHealth.Requirement.OPTIONAL, DatagramSocket.class, "getLocalAddress", noArgs(),
                this::spoofLocalInetAddress);
        hookAfter(HookHealth.Requirement.OPTIONAL, DatagramSocket.class, "getLocalSocketAddress", noArgs(),
                this::spoofLocalSocketAddress);
        hookAfter(HookHealth.Requirement.OPTIONAL, ServerSocket.class, "getInetAddress", noArgs(),
                this::spoofLocalInetAddress);
        hookAfter(HookHealth.Requirement.OPTIONAL, ServerSocket.class, "getLocalSocketAddress", noArgs(),
                this::spoofLocalSocketAddress);
        hookOptionalClass("sun.nio.ch.SocketChannelImpl", "getLocalAddress", noArgs(), this::spoofLocalSocketAddress);
        hookOptionalClass("sun.nio.ch.DatagramChannelImpl", "getLocalAddress", noArgs(), this::spoofLocalSocketAddress);
        hookOptionalClass("sun.nio.ch.ServerSocketChannelImpl", "getLocalAddress", noArgs(), this::spoofLocalSocketAddress);
    }

    private void installSettingsHooks() {
        hookAfter(HookHealth.Requirement.OPTIONAL, Settings.Secure.class, "getString",
                new Class<?>[]{ContentResolver.class, String.class}, this::hideVpnSettingString);
        hookAfter(HookHealth.Requirement.OPTIONAL, Settings.Global.class, "getString",
                new Class<?>[]{ContentResolver.class, String.class}, this::hideVpnSettingString);
        hookAfter(HookHealth.Requirement.OPTIONAL, Settings.Secure.class, "getInt",
                new Class<?>[]{ContentResolver.class, String.class, int.class}, this::hideVpnSettingInt);
        hookAfter(HookHealth.Requirement.OPTIONAL, Settings.Global.class, "getInt",
                new Class<?>[]{ContentResolver.class, String.class, int.class}, this::hideVpnSettingInt);
    }

    private void installPropertyHooks() {
        hookAfter(HookHealth.Requirement.OPTIONAL, System.class, "getProperty",
                new Class<?>[]{String.class}, this::spoofJavaProperty);
        hookAfter(HookHealth.Requirement.OPTIONAL, System.class, "getProperty",
                new Class<?>[]{String.class, String.class}, this::spoofJavaProperty);
        hookOptionalClass("android.os.SystemProperties", "get", new Class<?>[]{String.class},
                this::spoofAndroidProperty);
        hookOptionalClass("android.os.SystemProperties", "get", new Class<?>[]{String.class, String.class},
                this::spoofAndroidProperty);
    }

    private void installVpnRequestHooks() {
        installVpnRequestMethods("registerNetworkCallback");
        installVpnRequestMethods("requestNetwork");
        installVpnUnregisterMethods("unregisterNetworkCallback");
    }

    private Object spoofDhcpInfo(Object original) {
        if (!(original instanceof DhcpInfo)) return original;
        DhcpInfo input = (DhcpInfo) original;
        DhcpInfo out = new DhcpInfo();
        out.ipAddress = Ipv4.toWifiInt(p.ipv4);
        out.gateway = Ipv4.toWifiInt(p.gateway);
        out.netmask = prefixToWifiInt(p.prefixLength);
        out.dns1 = p.dns.isEmpty() ? 0 : Ipv4.toWifiInt(p.dns.get(0));
        out.dns2 = p.dns.size() < 2 ? 0 : Ipv4.toWifiInt(p.dns.get(1));
        out.serverAddress = 0;
        out.leaseDuration = input.leaseDuration;
        return out;
    }

    private Object spoofWifiToString(XposedInterface.Chain chain, Object original) throws Throwable {
        Object receiver = HookChainCompat.receiver(chain);
        if (!(receiver instanceof WifiInfo)) return original;
        WifiInfo projected = factory.copyWithIp((WifiInfo) receiver, model.ipv4);
        return factory.originToString(projected);
    }

    private Object writeWifiParcel(XposedInterface.Chain chain) throws Throwable {
        Object receiver = HookChainCompat.receiver(chain);
        Object parcel = HookChainCompat.arg(chain, 0);
        Object flags = HookChainCompat.arg(chain, 1);
        if (!(receiver instanceof WifiInfo) || !(parcel instanceof Parcel) || !(flags instanceof Integer)) {
            return chain.proceed();
        }
        WifiInfo projected = factory.copyWithIp((WifiInfo) receiver, model.ipv4);
        factory.writeToParcelOrigin(projected, (Parcel) parcel, (Integer) flags);
        return null;
    }

    private Object filterAllNetworks(XposedInterface.Chain chain, Object original) throws Throwable {
        if (!p.hideVpn || !(original instanceof Network[])) return original;
        Object receiver = HookChainCompat.receiver(chain);
        if (!(receiver instanceof ConnectivityManager)) return original;
        ConnectivityManager manager = (ConnectivityManager) receiver;
        Object activeValue = origin.callByName(manager, "getActiveNetwork");
        Network active = activeValue instanceof Network ? (Network) activeValue : null;

        List<Network> out = new ArrayList<>();
        for (Network network : (Network[]) original) {
            if (network == null) continue;
            if (active != null && active.equals(network)) {
                out.add(network);
                continue;
            }
            Object raw = origin.callByName(manager, "getNetworkCapabilities", network);
            if (raw instanceof NetworkCapabilities && capabilities.isRawVpn((NetworkCapabilities) raw)) continue;
            out.add(network);
        }
        return out.toArray(new Network[0]);
    }

    private Object hideLegacyVpnNetworkHandle(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn) return original;
        Object type = HookChainCompat.arg(chain, 0);
        return type instanceof Integer && ((Integer) type) == ConnectivityManager.TYPE_VPN ? null : original;
    }

    private void hookOptionalCapabilityGetter(String name) {
        hookAfter(HookHealth.Requirement.OPTIONAL, NetworkCapabilities.class, name, noArgs(),
                (chain, original) -> projectedCapabilitiesGetter(chain, name, original));
    }

    private Object projectedCapabilitiesGetter(XposedInterface.Chain chain, String name,
                                               Object original, Object... args) throws Throwable {
        Object receiver = HookChainCompat.receiver(chain);
        if (!(receiver instanceof NetworkCapabilities)) return original;
        NetworkCapabilities raw = (NetworkCapabilities) receiver;
        if (!p.hideVpn || capabilities.isSanitized(raw) || !capabilities.isRawVpn(raw)) return original;
        return capabilities.invokeGetter(raw, name, args);
    }

    private Object capabilitiesToString(XposedInterface.Chain chain, Object original) throws Throwable {
        Object receiver = HookChainCompat.receiver(chain);
        if (!(receiver instanceof NetworkCapabilities)) return original;
        NetworkCapabilities raw = (NetworkCapabilities) receiver;
        if (!p.hideVpn || capabilities.isSanitized(raw) || !capabilities.isRawVpn(raw)) return original;
        return factory.originToString(capabilities.sanitize(raw));
    }

    private Object writeCapabilitiesParcel(XposedInterface.Chain chain) throws Throwable {
        Object receiver = HookChainCompat.receiver(chain);
        Object parcel = HookChainCompat.arg(chain, 0);
        Object flags = HookChainCompat.arg(chain, 1);
        if (!(receiver instanceof NetworkCapabilities) || !(parcel instanceof Parcel)
                || !(flags instanceof Integer)) return chain.proceed();
        NetworkCapabilities raw = (NetworkCapabilities) receiver;
        if (!p.hideVpn || capabilities.isSanitized(raw) || !capabilities.isRawVpn(raw)) return chain.proceed();
        factory.writeToParcelOrigin(capabilities.sanitize(raw), (Parcel) parcel, (Integer) flags);
        return null;
    }

    private void hookLinkGetter(HookHealth.Requirement requirement, String name) {
        hookAfter(requirement, LinkProperties.class, name, noArgs(),
                (chain, original) -> projectedLinkGetter(chain, name, original));
    }

    private Object projectedLinkGetter(XposedInterface.Chain chain, String name, Object original) throws Throwable {
        Object receiver = HookChainCompat.receiver(chain);
        if (!(receiver instanceof LinkProperties)) return original;
        LinkProperties raw = (LinkProperties) receiver;
        if (links.isSanitized(raw)) return original;
        return links.invokeGetter(raw, name);
    }

    private Object linkPropertiesToString(XposedInterface.Chain chain, Object original) throws Throwable {
        Object receiver = HookChainCompat.receiver(chain);
        if (!(receiver instanceof LinkProperties)) return original;
        LinkProperties raw = (LinkProperties) receiver;
        if (links.isSanitized(raw)) return original;
        return factory.originToString(links.sanitize(raw));
    }

    private Object writeLinkPropertiesParcel(XposedInterface.Chain chain) throws Throwable {
        Object receiver = HookChainCompat.receiver(chain);
        Object parcel = HookChainCompat.arg(chain, 0);
        Object flags = HookChainCompat.arg(chain, 1);
        if (!(receiver instanceof LinkProperties) || !(parcel instanceof Parcel)
                || !(flags instanceof Integer)) return chain.proceed();
        LinkProperties raw = (LinkProperties) receiver;
        if (links.isSanitized(raw)) return chain.proceed();
        factory.writeToParcelOrigin(links.sanitize(raw), (Parcel) parcel, (Integer) flags);
        return null;
    }

    private Object legacyNetworkInfoByType(XposedInterface.Chain chain, Object original) throws Throwable {
        if (!p.hideVpn) return original;
        Object type = HookChainCompat.arg(chain, 0);
        if (type instanceof Integer && ((Integer) type) == ConnectivityManager.TYPE_VPN) {
            if (original instanceof NetworkInfo) legacyVpnInfos.add((NetworkInfo) original);
            return null;
        }
        return original;
    }

    private Object filterLegacyNetworkInfo(XposedInterface.Chain chain, Object original) throws Throwable {
        if (!p.hideVpn || !(original instanceof NetworkInfo[])) return original;
        List<NetworkInfo> out = new ArrayList<>();
        for (NetworkInfo info : (NetworkInfo[]) original) {
            if (info == null) continue;
            if (rawLegacyType(info) == ConnectivityManager.TYPE_VPN) {
                legacyVpnInfos.add(info);
                continue;
            }
            out.add(info);
        }
        return out.toArray(new NetworkInfo[0]);
    }

    private Object tagActiveLegacyNetworkInfo(XposedInterface.Chain chain, Object original) throws Throwable {
        if (p.hideVpn && original instanceof NetworkInfo
                && rawLegacyType((NetworkInfo) original) == ConnectivityManager.TYPE_VPN) {
            legacyVpnInfos.add((NetworkInfo) original);
        }
        return original;
    }

    private Object legacyNetworkType(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn || !isTaggedLegacyVpn(chain)) return original;
        return switch (model.presentationTransport()) {
            case NetworkCapabilities.TRANSPORT_WIFI -> ConnectivityManager.TYPE_WIFI;
            case NetworkCapabilities.TRANSPORT_CELLULAR -> ConnectivityManager.TYPE_MOBILE;
            case NetworkCapabilities.TRANSPORT_ETHERNET -> ConnectivityManager.TYPE_ETHERNET;
            default -> original;
        };
    }

    private Object legacyNetworkTypeName(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn || !isTaggedLegacyVpn(chain)) return original;
        return switch (model.presentationTransport()) {
            case NetworkCapabilities.TRANSPORT_WIFI -> "WIFI";
            case NetworkCapabilities.TRANSPORT_CELLULAR -> "MOBILE";
            case NetworkCapabilities.TRANSPORT_ETHERNET -> "ETHERNET";
            default -> original;
        };
    }

    private Object legacyExtraInfo(XposedInterface.Chain chain, Object original) {
        return p.hideVpn && isTaggedLegacyVpn(chain) ? null : original;
    }

    private boolean isTaggedLegacyVpn(XposedInterface.Chain chain) {
        Object receiver = HookChainCompat.receiver(chain);
        return receiver instanceof NetworkInfo && legacyVpnInfos.contains((NetworkInfo) receiver);
    }

    private int rawLegacyType(NetworkInfo info) throws Throwable {
        Object value = origin.call(info, NetworkInfo.class, "getType", noArgs());
        return value instanceof Integer ? (Integer) value : Integer.MIN_VALUE;
    }

    private Object filterNetworkInterfaces(XposedInterface.Chain chain, Object original) {
        if (!(original instanceof Enumeration<?>)) return original;
        List<NetworkInterface> out = new ArrayList<>();
        Enumeration<?> enumeration = (Enumeration<?>) original;
        while (enumeration.hasMoreElements()) {
            Object value = enumeration.nextElement();
            if (!(value instanceof NetworkInterface)) continue;
            NetworkInterface networkInterface = (NetworkInterface) value;
            String name = networkInterface.getName();
            InterfaceClassifier.Kind kind = InterfaceClassifier.classify(name);
            if (kind == InterfaceClassifier.Kind.LOOPBACK) {
                out.add(networkInterface);
            } else if (kind == InterfaceClassifier.Kind.VPN) {
                if (!p.hideVpn) out.add(networkInterface);
            } else if (isPresentation(networkInterface)) {
                out.add(networkInterface);
            }
        }
        return Collections.enumeration(out);
    }

    private Object filterNetworkInterfaceLookup(XposedInterface.Chain chain, Object original) {
        Object requested = HookChainCompat.arg(chain, 0);
        if (requested instanceof Inet4Address && requested.equals(model.ipv4)) {
            return model.presentationInterface();
        }
        if (requested instanceof Inet6Address && p.hideIpv6) return null;
        if (requested instanceof String) {
            String name = (String) requested;
            InterfaceClassifier.Kind kind = InterfaceClassifier.classify(name);
            if (kind == InterfaceClassifier.Kind.VPN && p.hideVpn) return null;
            if (kind == InterfaceClassifier.Kind.CLAT) return null;
            if (kind != InterfaceClassifier.Kind.LOOPBACK
                    && (model.presentationName() == null || !name.equals(model.presentationName()))) return null;
        }
        if (original instanceof NetworkInterface) {
            NetworkInterface networkInterface = (NetworkInterface) original;
            InterfaceClassifier.Kind kind = InterfaceClassifier.classify(networkInterface.getName());
            if (kind == InterfaceClassifier.Kind.VPN && p.hideVpn) return null;
            if (kind == InterfaceClassifier.Kind.LOOPBACK) return original;
            return isPresentation(networkInterface) ? original : null;
        }
        return original;
    }

    private Object spoofNetworkInterfaceAddresses(XposedInterface.Chain chain, Object original) {
        Object receiver = HookChainCompat.receiver(chain);
        if (!(receiver instanceof NetworkInterface)) return original;
        NetworkInterface networkInterface = (NetworkInterface) receiver;
        InterfaceClassifier.Kind kind = InterfaceClassifier.classify(networkInterface.getName());
        if (kind == InterfaceClassifier.Kind.LOOPBACK) return original;
        if (kind == InterfaceClassifier.Kind.VPN) return p.hideVpn ? Collections.emptyEnumeration() : original;
        if (!isPresentation(networkInterface)) return Collections.emptyEnumeration();

        List<InetAddress> out = new ArrayList<>();
        out.add(model.ipv4);
        if (!p.hideIpv6 && original instanceof Enumeration<?>) {
            Enumeration<?> enumeration = (Enumeration<?>) original;
            while (enumeration.hasMoreElements()) {
                Object value = enumeration.nextElement();
                if (value instanceof Inet6Address) out.add((InetAddress) value);
            }
        }
        return Collections.enumeration(out);
    }

    private Object spoofInterfaceAddressObjects(XposedInterface.Chain chain, Object original) throws Throwable {
        Object receiver = HookChainCompat.receiver(chain);
        if (!(receiver instanceof NetworkInterface)) return original;
        NetworkInterface networkInterface = (NetworkInterface) receiver;
        InterfaceClassifier.Kind kind = InterfaceClassifier.classify(networkInterface.getName());
        if (kind == InterfaceClassifier.Kind.LOOPBACK) return original;
        if (kind == InterfaceClassifier.Kind.VPN) return p.hideVpn ? Collections.emptyList() : original;
        if (!isPresentation(networkInterface)) return Collections.emptyList();

        List<InterfaceAddress> out = new ArrayList<>();
        out.add(factory.interfaceAddress(model.ipv4, model.broadcast, model.netmask));
        if (!p.hideIpv6 && original instanceof List<?>) {
            for (Object value : (List<?>) original) {
                if (!(value instanceof InterfaceAddress)) continue;
                InterfaceAddress address = (InterfaceAddress) value;
                if (address.getAddress() instanceof Inet6Address) out.add(address);
            }
        }
        return Collections.unmodifiableList(out);
    }

    private boolean isPresentation(NetworkInterface value) {
        NetworkInterface selected = model.presentationInterface();
        return selected != null && value != null && selected.getIndex() == value.getIndex()
                && selected.getName().equals(value.getName());
    }

    private Object spoofLocalInetAddress(XposedInterface.Chain chain, Object original) {
        if (!(original instanceof Inet4Address)) return original;
        InetAddress address = (InetAddress) original;
        if (address.isLoopbackAddress() || address.isAnyLocalAddress()) return original;
        return model.ipv4;
    }

    private Object spoofLocalSocketAddress(XposedInterface.Chain chain, Object original) {
        if (!(original instanceof InetSocketAddress)) return original;
        InetSocketAddress socket = (InetSocketAddress) original;
        InetAddress address = socket.getAddress();
        if (!(address instanceof Inet4Address) || address.isLoopbackAddress() || address.isAnyLocalAddress()) {
            return original;
        }
        return new InetSocketAddress(model.ipv4, socket.getPort());
    }

    private Object hideVpnSettingString(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn) return original;
        Object key = HookChainCompat.arg(chain, 1);
        return key instanceof String && isVpnSetting((String) key) ? null : original;
    }

    private Object hideVpnSettingInt(XposedInterface.Chain chain, Object original) {
        if (!p.hideVpn) return original;
        Object key = HookChainCompat.arg(chain, 1);
        if (!(key instanceof String) || !isVpnSetting((String) key)) return original;
        Object suppliedDefault = HookChainCompat.arg(chain, 2);
        return suppliedDefault instanceof Integer ? suppliedDefault : 0;
    }

    private Object spoofJavaProperty(XposedInterface.Chain chain, Object original) {
        if (!p.hideProxy) return original;
        Object key = HookChainCompat.arg(chain, 0);
        if (!(key instanceof String) || !PropertyMaskPolicy.isJavaProxyKey((String) key)) return original;
        Object suppliedDefault = HookChainCompat.arg(chain, 1);
        return suppliedDefault instanceof String ? suppliedDefault : null;
    }

    private Object spoofAndroidProperty(XposedInterface.Chain chain, Object original) {
        Object keyValue = HookChainCompat.arg(chain, 0);
        if (!(keyValue instanceof String)) return original;
        String key = (String) keyValue;
        PropertyMaskPolicy.AndroidPropertyKind kind = PropertyMaskPolicy.classifyAndroid(key);
        Object suppliedDefault = HookChainCompat.arg(chain, 1);
        String hidden = suppliedDefault instanceof String ? (String) suppliedDefault : "";

        return switch (kind) {
            case DNS -> dnsProperty(key, hidden);
            case GATEWAY -> p.gateway;
            case IPV4 -> p.ipv4;
            case PROXY -> p.hideProxy ? hidden : original;
            case NONE -> original;
        };
    }

    private String dnsProperty(String key, String fallback) {
        int index = PropertyMaskPolicy.dnsIndex(key);
        if (index < 0 || index >= model.dns.size()) return fallback;
        return model.dns.get(index).getHostAddress();
    }

    private void installVpnRequestMethods(String name) {
        int count = 0;
        for (Method method : ConnectivityManager.class.getDeclaredMethods()) {
            if (!method.getName().equals(name) || requestIndex(method) < 0) continue;
            count++;
            installMethodHook(HookHealth.Requirement.REQUIRED, method,
                    ConnectivityManager.class.getName() + "." + name, this::interceptVpnRequest);
        }
        if (count == 0) {
            health.expected(HookHealth.Requirement.REQUIRED);
            health.missing(HookHealth.Requirement.REQUIRED, ConnectivityManager.class.getName() + "." + name);
        }
    }

    private void installVpnUnregisterMethods(String name) {
        int count = 0;
        for (Method method : ConnectivityManager.class.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) continue;
            count++;
            installMethodHook(HookHealth.Requirement.REQUIRED, method,
                    ConnectivityManager.class.getName() + "." + name, this::interceptVpnUnregister);
        }
        if (count == 0) {
            health.expected(HookHealth.Requirement.REQUIRED);
            health.missing(HookHealth.Requirement.REQUIRED, ConnectivityManager.class.getName() + "." + name);
        }
    }

    private Object interceptVpnRequest(XposedInterface.Chain chain) throws Throwable {
        if (!p.hideVpn) return chain.proceed();
        Method originMethod = (Method) chain.getExecutable();
        int index = requestIndex(originMethod);
        Object request = index < 0 ? null : HookChainCompat.arg(chain, index);
        if (!(request instanceof NetworkRequest) || !requestHasVpn((NetworkRequest) request)) {
            return chain.proceed();
        }
        rememberRequestToken(originMethod, chain);
        return null;
    }

    private Object interceptVpnUnregister(XposedInterface.Chain chain) throws Throwable {
        Object token = HookChainCompat.arg(chain, 0);
        if (token != null && suppressedVpnRequests.remove(token)) return null;
        return chain.proceed();
    }

    private boolean requestHasVpn(NetworkRequest request) {
        try {
            Object value = origin.callByName(request, "hasTransport", NetworkCapabilities.TRANSPORT_VPN);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void rememberRequestToken(Method method, XposedInterface.Chain chain) {
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (NetworkRequest.class.isAssignableFrom(params[i])) continue;
            Object value = HookChainCompat.arg(chain, i);
            if (value == null) continue;
            String className = params[i].getName();
            if (className.contains("NetworkCallback") || className.equals("android.app.PendingIntent")) {
                suppressedVpnRequests.add(value);
            }
        }
    }

    private static int requestIndex(Method method) {
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (NetworkRequest.class.isAssignableFrom(params[i])) return i;
        }
        return -1;
    }

    private static boolean isVpnSetting(String raw) {
        String key = raw.toLowerCase(Locale.ROOT);
        return key.equals("always_on_vpn_app")
                || key.equals("always_on_vpn_lockdown")
                || key.equals("always_on_vpn_lockdown_whitelist")
                || key.equals("vpn_lockdown_whitelist")
                || key.equals("legacy_vpn_name");
    }

    private static int prefixToWifiInt(int prefix) {
        long mask = prefix == 0 ? 0L : (0xffffffffL << (32 - prefix)) & 0xffffffffL;
        return Integer.reverseBytes((int) mask);
    }

    private interface Transformer {
        Object apply(XposedInterface.Chain chain, Object original) throws Throwable;
    }

    private interface AroundTransformer {
        Object apply(XposedInterface.Chain chain) throws Throwable;
    }

    private void hookAfter(HookHealth.Requirement requirement, Class<?> owner, String name,
                           Class<?>[] params, Transformer transformer) {
        health.expected(requirement);
        String label = owner.getName() + "." + name;
        try {
            Method method = owner.getDeclaredMethod(name, params);
            installMethodHookAlreadyExpected(requirement, method, label, chain -> {
                Object original = chain.proceed();
                try {
                    return transformer.apply(chain, original);
                } catch (Throwable t) {
                    health.fallback(label, t);
                    return original;
                }
            });
        } catch (NoSuchMethodException e) {
            health.missing(requirement, label);
        } catch (Throwable t) {
            health.failed(requirement, label, t);
        }
    }

    private void hookAround(HookHealth.Requirement requirement, Class<?> owner, String name,
                            Class<?>[] params, AroundTransformer transformer) {
        health.expected(requirement);
        String label = owner.getName() + "." + name;
        try {
            Method method = owner.getDeclaredMethod(name, params);
            installMethodHookAlreadyExpected(requirement, method, label, chain -> {
                try {
                    return transformer.apply(chain);
                } catch (Throwable t) {
                    health.fallback(label, t);
                    return chain.proceed();
                }
            });
        } catch (NoSuchMethodException e) {
            health.missing(requirement, label);
        } catch (Throwable t) {
            health.failed(requirement, label, t);
        }
    }

    private void installMethodHook(HookHealth.Requirement requirement, Method method, String label,
                                   AroundTransformer transformer) {
        health.expected(requirement);
        try {
            installMethodHookAlreadyExpected(requirement, method, label, chain -> {
                try {
                    return transformer.apply(chain);
                } catch (Throwable t) {
                    health.fallback(label, t);
                    return chain.proceed();
                }
            });
        } catch (Throwable t) {
            health.failed(requirement, label, t);
        }
    }

    private void installMethodHookAlreadyExpected(HookHealth.Requirement requirement, Method method,
                                                   String label, AroundTransformer transformer) {
        XposedInterface.HookHandle handle = module.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(transformer::apply);
        health.installed(requirement, handle);
    }

    private void hookOptionalClass(String className, String name, Class<?>[] params, Transformer transformer) {
        health.expected(HookHealth.Requirement.OPTIONAL);
        String label = className + "." + name;
        try {
            Class<?> owner = Class.forName(className, false, null);
            Method method = owner.getDeclaredMethod(name, params);
            installMethodHookAlreadyExpected(HookHealth.Requirement.OPTIONAL, method, label, chain -> {
                Object original = chain.proceed();
                try {
                    return transformer.apply(chain, original);
                } catch (Throwable t) {
                    health.fallback(label, t);
                    return original;
                }
            });
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Version-dependent surface; absence is expected.
        } catch (Throwable t) {
            health.failed(HookHealth.Requirement.OPTIONAL, label, t);
        }
    }

    private static Class<?>[] noArgs() {
        return new Class<?>[0];
    }
}

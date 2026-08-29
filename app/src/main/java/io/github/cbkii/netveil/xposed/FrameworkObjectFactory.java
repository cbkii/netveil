package io.github.cbkii.netveil.xposed;

import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.NetworkCapabilities;
import android.net.RouteInfo;
import android.net.wifi.WifiInfo;
import android.os.Parcel;

import java.lang.reflect.Constructor;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;

/** Creates framework-native virtual objects without private-field mutation. */
final class FrameworkObjectFactory {
    private static final int RTN_UNICAST = 1;

    private final NetVeilModule module;
    private final OriginAccess origin;

    FrameworkObjectFactory(NetVeilModule module, OriginAccess origin) {
        this.module = module;
        this.origin = origin;
    }

    LinkAddress linkAddress(InetAddress address, int prefixLength) throws Throwable {
        Constructor<LinkAddress> constructor =
                LinkAddress.class.getDeclaredConstructor(InetAddress.class, int.class);
        return construct(constructor, address, prefixLength);
    }

    RouteInfo route(IpPrefix destination, InetAddress gateway, String iface) throws Throwable {
        try {
            Constructor<RouteInfo> constructor = RouteInfo.class.getDeclaredConstructor(
                    IpPrefix.class, InetAddress.class, String.class, int.class);
            return construct(constructor, destination, gateway, iface, RTN_UNICAST);
        } catch (NoSuchMethodException ignored) {
            Constructor<RouteInfo> constructor = RouteInfo.class.getDeclaredConstructor(
                    IpPrefix.class, InetAddress.class, String.class);
            return construct(constructor, destination, gateway, iface);
        }
    }

    InterfaceAddress interfaceAddress(Inet4Address address, Inet4Address broadcast,
                                      Inet4Address netmask) throws Throwable {
        try {
            Constructor<InterfaceAddress> constructor = InterfaceAddress.class.getDeclaredConstructor(
                    InetAddress.class, Inet4Address.class, InetAddress.class);
            return construct(constructor, address, broadcast, netmask);
        } catch (NoSuchMethodException ignored) {
            Constructor<InterfaceAddress> constructor = InterfaceAddress.class.getDeclaredConstructor(
                    Inet4Address.class, Inet4Address.class, Inet4Address.class);
            return construct(constructor, address, broadcast, netmask);
        }
    }

    NetworkCapabilities copy(NetworkCapabilities original) throws Throwable {
        Constructor<NetworkCapabilities> constructor =
                NetworkCapabilities.class.getDeclaredConstructor(NetworkCapabilities.class);
        return construct(constructor, original);
    }

    WifiInfo copyWithIp(WifiInfo original, Inet4Address address) throws Throwable {
        Constructor<WifiInfo> constructor = WifiInfo.class.getDeclaredConstructor(WifiInfo.class);
        WifiInfo copy = construct(constructor, original);
        origin.callByName(copy, "setInetAddress", address);
        return copy;
    }

    String originToString(Object value) throws Throwable {
        Object result = origin.callByName(value, "toString");
        return result instanceof String ? (String) result : String.valueOf(result);
    }

    /**
     * Write a projected framework object transactionally.
     *
     * <p>Android framework parcel writers can fail after advancing/writing the destination. The
     * outer protective hook may then fall back to the raw object's writer, so leaving partial
     * projected bytes in place would corrupt the Parcel. Restore both size and cursor before
     * propagating any failure.</p>
     */
    void writeToParcelOrigin(Object value, Parcel parcel, int flags) throws Throwable {
        int position = parcel.dataPosition();
        int size = parcel.dataSize();
        try {
            origin.callByName(value, "writeToParcel", parcel, flags);
        } catch (Throwable t) {
            parcel.setDataSize(size);
            parcel.setDataPosition(Math.min(position, size));
            throw t;
        }
    }

    private <T> T construct(Constructor<T> constructor, Object... args) throws Throwable {
        return origin.construct(constructor, args);
    }
}

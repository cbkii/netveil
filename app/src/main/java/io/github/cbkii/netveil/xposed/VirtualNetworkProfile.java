package io.github.cbkii.netveil.xposed;

import android.net.NetworkCapabilities;

import io.github.cbkii.netveil.config.Ipv4;
import io.github.cbkii.netveil.config.Profile;
import io.github.cbkii.netveil.network.InterfaceClassifier;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable process-stable virtual identity plus the selected real presentation interface. */
final class VirtualNetworkProfile {
    final Profile.Resolved source;
    final Inet4Address ipv4;
    final Inet4Address gateway;
    final Inet4Address network;
    final Inet4Address broadcast;
    final Inet4Address netmask;
    final List<InetAddress> dns;

    private final NetworkInterface presentationInterface;
    private final String presentationName;
    private final InterfaceClassifier.Kind presentationKind;

    VirtualNetworkProfile(Profile.Resolved source) {
        this.source = source;
        this.ipv4 = Ipv4.parse(source.ipv4);
        this.gateway = Ipv4.parse(source.gateway);
        this.network = Ipv4.parse(Ipv4.networkAddress(source.ipv4, source.prefixLength));
        this.broadcast = Ipv4.parse(Ipv4.broadcastAddress(source.ipv4, source.prefixLength));
        this.netmask = Ipv4.netmask(source.prefixLength);

        List<InetAddress> dnsValues = new ArrayList<>();
        for (String value : source.dns) dnsValues.add(Ipv4.parse(value));
        this.dns = Collections.unmodifiableList(dnsValues);

        NetworkInterface selected = selectPresentationInterface();
        this.presentationInterface = selected;
        this.presentationName = selected == null ? null : selected.getName();
        this.presentationKind = selected == null
                ? InterfaceClassifier.Kind.OTHER_PHYSICAL
                : InterfaceClassifier.classify(selected.getName());
    }

    boolean hasPresentationInterface() {
        return presentationInterface != null && presentationName != null;
    }

    NetworkInterface presentationInterface() {
        return presentationInterface;
    }

    String presentationName() {
        return presentationName;
    }

    int presentationTransport() {
        return switch (presentationKind) {
            case WIFI -> NetworkCapabilities.TRANSPORT_WIFI;
            case CELLULAR -> NetworkCapabilities.TRANSPORT_CELLULAR;
            case ETHERNET -> NetworkCapabilities.TRANSPORT_ETHERNET;
            default -> -1;
        };
    }

    String presentationTransportName() {
        return switch (presentationKind) {
            case WIFI -> "WIFI";
            case CELLULAR -> "CELLULAR";
            case ETHERNET -> "ETHERNET";
            default -> "UNRESOLVED";
        };
    }

    private static NetworkInterface selectPresentationInterface() {
        try {
            Enumeration<NetworkInterface> enumeration = NetworkInterface.getNetworkInterfaces();
            if (enumeration == null) return null;

            Map<String, NetworkInterface> byName = new LinkedHashMap<>();
            while (enumeration.hasMoreElements()) {
                NetworkInterface candidate = enumeration.nextElement();
                if (candidate != null && candidate.getName() != null) byName.put(candidate.getName(), candidate);
            }

            NetworkInterface best = null;
            int bestScore = Integer.MAX_VALUE;
            for (NetworkInterface candidate : byName.values()) {
                String rawName = candidate.getName();
                InterfaceClassifier.Kind kind = InterfaceClassifier.classify(rawName);
                if (kind == InterfaceClassifier.Kind.VPN || kind == InterfaceClassifier.Kind.LOOPBACK) continue;
                if (!isUp(candidate)) continue;

                NetworkInterface normalized = candidate;
                String normalizedName = InterfaceClassifier.normalizePhysicalName(rawName);
                if (kind == InterfaceClassifier.Kind.CLAT && normalizedName != null) {
                    NetworkInterface underlying = byName.get(normalizedName);
                    if (underlying != null && isUp(underlying)) normalized = underlying;
                    else continue;
                }

                int score = InterfaceClassifier.priority(normalized.getName());
                if (!hasUsableAddress(normalized)) score += 4;
                if (score < bestScore) {
                    best = normalized;
                    bestScore = score;
                }
            }
            return best;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isUp(NetworkInterface networkInterface) {
        try {
            return networkInterface.isUp();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasUsableAddress(NetworkInterface networkInterface) {
        try {
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses != null && addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (!address.isLoopbackAddress() && !address.isAnyLocalAddress()) return true;
            }
        } catch (Throwable ignored) {
            // score penalty only
        }
        return false;
    }
}

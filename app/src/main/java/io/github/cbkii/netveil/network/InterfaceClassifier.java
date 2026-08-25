package io.github.cbkii.netveil.network;

import java.util.Locale;
import java.util.regex.Pattern;

/** Central classification policy for Android network-interface names. */
public final class InterfaceClassifier {
    private static final Pattern ANONYMOUS_IFACE = Pattern.compile("(?i)^if[0-9]+$");

    private InterfaceClassifier() {}

    public enum Kind {
        LOOPBACK,
        WIFI,
        CELLULAR,
        ETHERNET,
        CLAT,
        VPN,
        OTHER_PHYSICAL
    }

    public static Kind classify(String rawName) {
        if (rawName == null || rawName.isBlank()) return Kind.OTHER_PHYSICAL;
        String name = rawName.toLowerCase(Locale.ROOT);
        if (name.equals("lo") || name.startsWith("lo:")) return Kind.LOOPBACK;
        if (isVpnName(name)) return Kind.VPN;
        if (name.startsWith("v4-")) return Kind.CLAT;
        if (name.startsWith("wlan") || name.startsWith("wifi")) return Kind.WIFI;
        if (name.startsWith("rmnet") || name.startsWith("ccmni") || name.startsWith("pdp")
                || name.startsWith("seth_lte")) return Kind.CELLULAR;
        if (name.startsWith("eth") || name.startsWith("rndis") || name.startsWith("usb")) {
            return Kind.ETHERNET;
        }
        return Kind.OTHER_PHYSICAL;
    }

    public static boolean isVpn(String name) {
        return classify(name) == Kind.VPN;
    }

    public static boolean isLoopback(String name) {
        return classify(name) == Kind.LOOPBACK;
    }

    public static boolean isClat(String name) {
        return classify(name) == Kind.CLAT;
    }

    public static String normalizePhysicalName(String rawName) {
        if (rawName == null) return null;
        String name = rawName.trim();
        if (name.regionMatches(true, 0, "v4-", 0, 3) && name.length() > 3) {
            return name.substring(3);
        }
        return name;
    }

    public static int priority(String rawName) {
        Kind kind = classify(rawName);
        return switch (kind) {
            case WIFI -> 0;
            case CELLULAR -> 1;
            case ETHERNET -> 2;
            case CLAT -> 3;
            case OTHER_PHYSICAL -> 10;
            case LOOPBACK, VPN -> Integer.MAX_VALUE;
        };
    }

    private static boolean isVpnName(String lowerName) {
        if (lowerName.contains("vpn")) return true;
        if (ANONYMOUS_IFACE.matcher(lowerName).matches()) return true;
        return lowerName.startsWith("tun")
                || lowerName.startsWith("tap")
                || lowerName.startsWith("wg")
                || lowerName.startsWith("ppp")
                || lowerName.startsWith("ipsec")
                || lowerName.startsWith("xfrm")
                || lowerName.startsWith("utun")
                || lowerName.startsWith("l2tp")
                || lowerName.startsWith("gre")
                || lowerName.startsWith("tailscale")
                || lowerName.startsWith("zt")
                || lowerName.startsWith("he-ipv6");
    }
}

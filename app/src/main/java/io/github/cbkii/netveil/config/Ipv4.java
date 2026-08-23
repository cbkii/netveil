package io.github.cbkii.netveil.config;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class Ipv4 {
    private Ipv4() {}

    public static boolean isLiteral(String raw) {
        if (raw == null) return false;
        String s = raw.trim();
        String[] parts = s.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            int value = 0;
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                if (c < '0' || c > '9') return false;
                value = value * 10 + (c - '0');
            }
            if (value > 255) return false;
        }
        return true;
    }

    public static Inet4Address parse(String raw) {
        if (!isLiteral(raw)) throw new IllegalArgumentException("Not an IPv4 literal: " + raw);
        try {
            InetAddress a = InetAddress.getByName(raw.trim());
            if (!(a instanceof Inet4Address)) throw new IllegalArgumentException("Not IPv4: " + raw);
            return (Inet4Address) a;
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("Invalid IPv4: " + raw, e);
        }
    }

    public static int toWifiInt(String raw) {
        byte[] b = parse(raw).getAddress();
        return (b[0] & 0xff) | ((b[1] & 0xff) << 8) | ((b[2] & 0xff) << 16) | ((b[3] & 0xff) << 24);
    }

    public static long toUnsignedLong(String raw) {
        byte[] b = parse(raw).getAddress();
        return ((long) (b[0] & 0xff) << 24)
                | ((long) (b[1] & 0xff) << 16)
                | ((long) (b[2] & 0xff) << 8)
                | (long) (b[3] & 0xff);
    }

    public static boolean sameSubnet(String a, String b, int prefixLength) {
        int prefix = Math.max(0, Math.min(32, prefixLength));
        if (prefix == 0) return true;
        long mask = (0xffffffffL << (32 - prefix)) & 0xffffffffL;
        return (toUnsignedLong(a) & mask) == (toUnsignedLong(b) & mask);
    }

    public static String networkAddress(String ip, int prefixLength) {
        int prefix = Math.max(0, Math.min(32, prefixLength));
        long mask = prefix == 0 ? 0L : ((0xffffffffL << (32 - prefix)) & 0xffffffffL);
        long value = toUnsignedLong(ip) & mask;
        return ((value >>> 24) & 0xff) + "." + ((value >>> 16) & 0xff) + "."
                + ((value >>> 8) & 0xff) + "." + (value & 0xff);
    }
}

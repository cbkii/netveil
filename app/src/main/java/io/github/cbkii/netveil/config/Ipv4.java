package io.github.cbkii.netveil.config;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

public final class Ipv4 {
    private Ipv4() {}

    public static boolean isLiteral(String raw) {
        try {
            octets(raw);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static String canonical(String raw) {
        int[] octets = octets(raw);
        return octets[0] + "." + octets[1] + "." + octets[2] + "." + octets[3];
    }

    public static Inet4Address parse(String raw) {
        int[] octets = octets(raw);
        byte[] address = new byte[] {
                (byte) octets[0], (byte) octets[1], (byte) octets[2], (byte) octets[3]
        };
        try {
            InetAddress parsed = InetAddress.getByAddress(address);
            if (!(parsed instanceof Inet4Address)) {
                throw new IllegalArgumentException("Not IPv4: " + raw);
            }
            return (Inet4Address) parsed;
        } catch (UnknownHostException impossible) {
            throw new IllegalArgumentException("Invalid IPv4: " + raw, impossible);
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
        int prefix = clampPrefix(prefixLength);
        if (prefix == 0) return true;
        long mask = (0xffffffffL << (32 - prefix)) & 0xffffffffL;
        return (toUnsignedLong(a) & mask) == (toUnsignedLong(b) & mask);
    }

    public static String networkAddress(String ip, int prefixLength) {
        int prefix = clampPrefix(prefixLength);
        long mask = prefix == 0 ? 0L : ((0xffffffffL << (32 - prefix)) & 0xffffffffL);
        return fromUnsignedLong(toUnsignedLong(ip) & mask);
    }

    public static String broadcastAddress(String ip, int prefixLength) {
        int prefix = clampPrefix(prefixLength);
        long mask = prefix == 0 ? 0L : ((0xffffffffL << (32 - prefix)) & 0xffffffffL);
        long value = (toUnsignedLong(ip) & mask) | (~mask & 0xffffffffL);
        return fromUnsignedLong(value);
    }

    public static Inet4Address netmask(int prefixLength) {
        int prefix = clampPrefix(prefixLength);
        long mask = prefix == 0 ? 0L : ((0xffffffffL << (32 - prefix)) & 0xffffffffL);
        return parse(fromUnsignedLong(mask));
    }

    private static String fromUnsignedLong(long value) {
        long v = value & 0xffffffffL;
        return ((v >>> 24) & 0xff) + "." + ((v >>> 16) & 0xff) + "."
                + ((v >>> 8) & 0xff) + "." + (v & 0xff);
    }

    private static int clampPrefix(int prefixLength) {
        return Math.max(0, Math.min(32, prefixLength));
    }

    private static int[] octets(String raw) {
        if (raw == null) throw new IllegalArgumentException("IPv4 value is null");
        String value = raw.trim();
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) throw new IllegalArgumentException("Not an IPv4 literal: " + raw);

        int[] out = new int[4];
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) {
                throw new IllegalArgumentException("Not an IPv4 literal: " + raw);
            }
            int parsed = 0;
            for (int j = 0; j < part.length(); j++) {
                char c = part.charAt(j);
                if (c < '0' || c > '9') {
                    throw new IllegalArgumentException("Not an IPv4 literal: " + raw);
                }
                parsed = parsed * 10 + (c - '0');
            }
            if (parsed > 255) throw new IllegalArgumentException("IPv4 octet out of range: " + raw);
            out[i] = parsed;
        }
        return out;
    }
}

package io.github.cbkii.netveil.network;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Narrow allowlist for property surfaces NetVeil intentionally virtualises. */
public final class PropertyMaskPolicy {
    private static final Pattern NET_DNS = Pattern.compile("^net\\.dns([1-4])$");
    private static final Pattern DHCP_DNS =
            Pattern.compile("^dhcp\\.[a-z0-9_.:-]+\\.dns([1-4])$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DHCP_GATEWAY =
            Pattern.compile("^dhcp\\.[a-z0-9_.:-]+\\.gateway$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DHCP_IPV4 =
            Pattern.compile("^dhcp\\.[a-z0-9_.:-]+\\.ipaddress$", Pattern.CASE_INSENSITIVE);

    private PropertyMaskPolicy() {}

    public enum AndroidPropertyKind { NONE, DNS, GATEWAY, IPV4, PROXY }

    public static boolean isJavaProxyKey(String raw) {
        if (raw == null) return false;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return key.equals("http.proxyhost")
                || key.equals("http.proxyport")
                || key.equals("https.proxyhost")
                || key.equals("https.proxyport")
                || key.equals("socksproxyhost")
                || key.equals("socksproxyport");
    }

    public static AndroidPropertyKind classifyAndroid(String raw) {
        if (raw == null) return AndroidPropertyKind.NONE;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (NET_DNS.matcher(key).matches() || DHCP_DNS.matcher(key).matches()) {
            return AndroidPropertyKind.DNS;
        }
        if (DHCP_GATEWAY.matcher(key).matches()) return AndroidPropertyKind.GATEWAY;
        if (DHCP_IPV4.matcher(key).matches()) return AndroidPropertyKind.IPV4;
        if (key.equals("net.gprs.http-proxy")
                || key.equals("persist.sys.global_http_proxy_host")
                || key.equals("persist.sys.global_http_proxy_port")) {
            return AndroidPropertyKind.PROXY;
        }
        return AndroidPropertyKind.NONE;
    }

    /** Returns a zero-based DNS slot, or -1 when the property is not a supported DNS key. */
    public static int dnsIndex(String raw) {
        if (raw == null) return -1;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        Matcher matcher = NET_DNS.matcher(key);
        if (!matcher.matches()) matcher = DHCP_DNS.matcher(key);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) - 1 : -1;
    }
}

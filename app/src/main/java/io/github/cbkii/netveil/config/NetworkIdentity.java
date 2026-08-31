package io.github.cbkii.netveil.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** One coherent API-visible IPv4 identity. */
public final class NetworkIdentity {
    public enum RouteMode {
        HIDDEN,
        EXPLICIT
    }

    public final String ipv4;
    public final RouteMode routeMode;
    public final int prefixLength;
    public final String gateway;

    private NetworkIdentity(String ipv4, RouteMode routeMode, int prefixLength, String gateway) {
        this.ipv4 = Ipv4.canonical(ipv4);
        this.routeMode = routeMode;
        this.prefixLength = routeMode == RouteMode.HIDDEN ? 32 : prefixLength;
        this.gateway = gateway == null ? null : Ipv4.canonical(gateway);
    }

    public static NetworkIdentity hidden(String ipv4) {
        if (!Ipv4.isLiteral(ipv4)) throw new IllegalArgumentException("Invalid IPv4: " + ipv4);
        return new NetworkIdentity(ipv4, RouteMode.HIDDEN, 32, null);
    }

    public static NetworkIdentity explicit(String ipv4, int prefixLength, String gateway) {
        Validation validation = validate(ipv4, RouteMode.EXPLICIT,
                String.valueOf(prefixLength), gateway);
        if (!validation.valid) throw new IllegalArgumentException(validation.error);
        return validation.identity;
    }

    public static Validation validate(String ipv4, RouteMode routeMode,
                                      String prefixText, String gateway) {
        if (!Ipv4.isLiteral(ipv4)) {
            return Validation.error("Enter a valid IPv4 address.");
        }
        String canonicalIp = Ipv4.canonical(ipv4);
        if (routeMode == RouteMode.HIDDEN) {
            return Validation.ok(new NetworkIdentity(canonicalIp, RouteMode.HIDDEN, 32, null), null);
        }

        int prefix;
        try {
            prefix = Integer.parseInt(prefixText == null ? "" : prefixText.trim());
        } catch (NumberFormatException e) {
            return Validation.error("Prefix must be a number from 0 to 32.");
        }
        if (prefix < 0 || prefix > 32) {
            return Validation.error("Prefix must be from 0 to 32.");
        }
        if (!Ipv4.isLiteral(gateway)) {
            return Validation.error("Enter a valid gateway IPv4 address, or hide gateway/routes.");
        }
        String canonicalGateway = Ipv4.canonical(gateway);
        if (canonicalIp.equals(canonicalGateway)) {
            return Validation.error("Gateway must be different from the IPv4 address.");
        }
        if (!Ipv4.sameSubnet(canonicalIp, canonicalGateway, prefix)) {
            String network = Ipv4.networkAddress(canonicalIp, prefix);
            return Validation.error("Gateway " + canonicalGateway + " is outside "
                    + network + "/" + prefix + ". Choose a gateway in that subnet or hide gateway/routes.");
        }

        String warning = prefix == 0
                ? "/0 covers the entire IPv4 address space and is unusual for a local network."
                : null;
        return Validation.ok(new NetworkIdentity(
                canonicalIp, RouteMode.EXPLICIT, prefix, canonicalGateway), warning);
    }

    public boolean hasExplicitRoute() {
        return routeMode == RouteMode.EXPLICIT && gateway != null;
    }

    public String serialize() {
        if (routeMode == RouteMode.HIDDEN) return "H|" + ipv4;
        return "E|" + ipv4 + "|" + prefixLength + "|" + gateway;
    }

    public static NetworkIdentity parseStored(String raw) {
        if (raw == null) return null;
        String line = raw.trim();
        if (line.isEmpty()) return null;
        String[] parts = line.split("\\|", -1);
        try {
            if (parts.length == 2 && "H".equals(parts[0])) return hidden(parts[1]);
            if (parts.length == 4 && "E".equals(parts[0])) {
                int prefix = Integer.parseInt(parts[2]);
                return explicit(parts[1], prefix, parts[3]);
            }
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        return null;
    }

    public static List<NetworkIdentity> parseStoredList(String raw) {
        if (raw == null || raw.trim().isEmpty()) return Collections.emptyList();
        List<NetworkIdentity> out = new ArrayList<>();
        for (String line : raw.split("[\\r\\n]+")) {
            NetworkIdentity identity = parseStored(line);
            if (identity != null && !contains(out, identity)) out.add(identity);
        }
        return Collections.unmodifiableList(out);
    }

    public static String serializeList(List<NetworkIdentity> identities) {
        if (identities == null || identities.isEmpty()) return "";
        List<String> lines = new ArrayList<>();
        for (NetworkIdentity identity : identities) {
            if (identity != null) lines.add(identity.serialize());
        }
        return String.join("\n", lines);
    }

    private static boolean contains(List<NetworkIdentity> values, NetworkIdentity candidate) {
        for (NetworkIdentity value : values) {
            if (value.ipv4.equals(candidate.ipv4)
                    && value.routeMode == candidate.routeMode
                    && value.prefixLength == candidate.prefixLength
                    && Objects.equals(value.gateway, candidate.gateway)) {
                return true;
            }
        }
        return false;
    }

    public static final class Validation {
        public final boolean valid;
        public final String error;
        public final String warning;
        public final NetworkIdentity identity;

        private Validation(boolean valid, String error, String warning, NetworkIdentity identity) {
            this.valid = valid;
            this.error = error;
            this.warning = warning;
            this.identity = identity;
        }

        static Validation ok(NetworkIdentity identity, String warning) {
            return new Validation(true, null, warning, identity);
        }

        static Validation error(String message) {
            return new Validation(false, message, null, null);
        }
    }
}

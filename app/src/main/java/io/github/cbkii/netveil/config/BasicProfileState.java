package io.github.cbkii.netveil.config;

/** Pure classification of the saved Global profile relative to the current Basic recommendation. */
public final class BasicProfileState {
    public enum Kind {
        ABSENT,
        ACTIVE,
        UPDATE_AVAILABLE,
        ADVANCED_CUSTOM,
        INVALID
    }

    private BasicProfileState() {}

    public static Kind classify(boolean hasStoredGlobal, boolean resolvable,
                                boolean basicManaged, String storedFingerprint,
                                String currentFingerprint) {
        if (!hasStoredGlobal) return Kind.ABSENT;
        // Ownership is the replacement boundary. An Advanced-owned profile remains protected even
        // if it is currently incomplete; Basic may replace it only after the user enables override.
        if (!basicManaged) return Kind.ADVANCED_CUSTOM;
        if (!resolvable) return Kind.INVALID;
        return currentFingerprint != null && currentFingerprint.equals(storedFingerprint)
                ? Kind.ACTIVE : Kind.UPDATE_AVAILABLE;
    }
}

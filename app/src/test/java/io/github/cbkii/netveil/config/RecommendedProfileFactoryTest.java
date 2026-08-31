package io.github.cbkii.netveil.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import io.github.cbkii.netveil.country.CountryCatalog;

import org.junit.Test;

import java.util.List;

public class RecommendedProfileFactoryTest {
    @Test
    public void everySupportedCountryBuildsCompleteRecommendedProfile() {
        for (String country : CountryCatalog.codes()) {
            RecommendedProfileFactory.Draft draft = RecommendedProfileFactory.create(
                    country, List.of("8.8.8.8", "9.9.9.9"), "2026-08-31T00:00:00Z", 42L);
            Profile profile = draft.profile;
            assertTrue(profile.enabled);
            assertTrue(profile.randomize);
            assertTrue(profile.hideVpn);
            assertTrue(profile.hideProxy);
            assertTrue(profile.hideIpv6);
            assertEquals(42L, profile.selectionSeed);
            assertEquals(2, profile.identities.size());
            assertTrue(profile.dnsSets.size() >= 2 && profile.dnsSets.size() <= 4);
            for (NetworkIdentity identity : profile.identities) {
                assertEquals(NetworkIdentity.RouteMode.HIDDEN, identity.routeMode);
                assertNull(identity.gateway);
            }
            assertNotNull(profile.resolve());
            assertFalse(draft.fingerprint.isBlank());
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyCandidatesAreRejected() {
        RecommendedProfileFactory.create(
                "AU", List.of(), "2026-08-31T00:00:00Z", 42L);
    }

    @Test(expected = IllegalArgumentException.class)
    public void zeroSeedIsRejected() {
        RecommendedProfileFactory.create(
                "AU", List.of("8.8.8.8"), "2026-08-31T00:00:00Z", 0L);
    }

    @Test
    public void fingerprintTracksRecommendationContent() {
        String first = RecommendedProfileFactory.create(
                "AU", List.of("8.8.8.8"), "2026-08-31T00:00:00Z", 1L).fingerprint;
        String changedIp = RecommendedProfileFactory.create(
                "AU", List.of("9.9.9.9"), "2026-08-31T00:00:00Z", 1L).fingerprint;
        String changedData = RecommendedProfileFactory.create(
                "AU", List.of("8.8.8.8"), "2026-09-01T00:00:00Z", 1L).fingerprint;
        assertNotEquals(first, changedIp);
        assertNotEquals(first, changedData);
    }
}

package io.github.cbkii.netveil.config;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BasicProfileStateTest {
    @Test
    public void absenceWinsWhenNothingIsStored() {
        assertEquals(BasicProfileState.Kind.ABSENT,
                BasicProfileState.classify(false, false, false, "", "current"));
    }

    @Test
    public void advancedOwnershipIsProtectedEvenWhenIncomplete() {
        assertEquals(BasicProfileState.Kind.ADVANCED_CUSTOM,
                BasicProfileState.classify(true, false, false, "", "current"));
        assertEquals(BasicProfileState.Kind.ADVANCED_CUSTOM,
                BasicProfileState.classify(true, true, false, "", "current"));
    }

    @Test
    public void managedButIncompleteIsInvalid() {
        assertEquals(BasicProfileState.Kind.INVALID,
                BasicProfileState.classify(true, false, true, "stored", "current"));
    }

    @Test
    public void matchingManagedRecommendationIsActive() {
        assertEquals(BasicProfileState.Kind.ACTIVE,
                BasicProfileState.classify(true, true, true, "same", "same"));
    }

    @Test
    public void changedManagedRecommendationOffersUpdate() {
        assertEquals(BasicProfileState.Kind.UPDATE_AVAILABLE,
                BasicProfileState.classify(true, true, true, "old", "new"));
    }
}

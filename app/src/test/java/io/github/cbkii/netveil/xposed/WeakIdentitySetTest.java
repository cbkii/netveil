package io.github.cbkii.netveil.xposed;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WeakIdentitySetTest {
    @Test
    public void equalButDistinctObjectsDoNotShareMembership() {
        EqualValue first = new EqualValue(7);
        EqualValue second = new EqualValue(7);
        assertTrue(first.equals(second));
        assertFalse(first == second);

        WeakIdentitySet<EqualValue> set = new WeakIdentitySet<>();
        set.add(first);

        assertTrue(set.contains(first));
        assertFalse(set.contains(second));
    }

    @Test
    public void clearRemovesIdentityMembership() {
        Object value = new Object();
        WeakIdentitySet<Object> set = new WeakIdentitySet<>();
        set.add(value);
        assertTrue(set.contains(value));

        set.clear();
        assertFalse(set.contains(value));
    }

    private static final class EqualValue {
        private final int value;

        EqualValue(int value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof EqualValue && ((EqualValue) other).value == value;
        }

        @Override
        public int hashCode() {
            return value;
        }
    }
}

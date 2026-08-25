package io.github.cbkii.netveil.xposed;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;

/** Weak set whose membership uses referential identity rather than equals/hashCode. */
final class WeakIdentitySet<T> {
    private final ReferenceQueue<T> queue = new ReferenceQueue<>();
    private final Set<IdentityRef<T>> refs = new HashSet<>();

    synchronized void add(T value) {
        if (value == null) return;
        drain();
        refs.add(new IdentityRef<>(value, queue));
    }

    synchronized boolean contains(T value) {
        if (value == null) return false;
        drain();
        return refs.contains(new IdentityRef<>(value));
    }

    synchronized void clear() {
        refs.clear();
        while (queue.poll() != null) {
            // drain
        }
    }

    @SuppressWarnings("unchecked")
    private void drain() {
        IdentityRef<T> ref;
        while ((ref = (IdentityRef<T>) queue.poll()) != null) refs.remove(ref);
    }

    private static final class IdentityRef<T> extends WeakReference<T> {
        private final int identityHash;

        IdentityRef(T referent, ReferenceQueue<T> queue) {
            super(referent, queue);
            identityHash = System.identityHashCode(referent);
        }

        IdentityRef(T referent) {
            super(referent);
            identityHash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof IdentityRef<?>)) return false;
            Object left = get();
            Object right = ((IdentityRef<?>) other).get();
            return left != null && left == right;
        }
    }
}

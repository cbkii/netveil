package io.github.cbkii.netveil.xposed;

import android.net.LinkAddress;

import java.lang.reflect.Constructor;
import java.net.InetAddress;

/**
 * Runtime bridge for LinkAddress constructors that exist on Android but are hidden from SDK stubs.
 *
 * <p>NetVeil is injected by an Xposed framework and needs to create a coherent virtual
 * LinkAddress. Keeping this access reflective avoids replacing the build SDK with private
 * framework stubs. Any access failure is allowed to propagate to NetworkHooks' PROTECTIVE
 * interceptor, which returns the original framework value rather than crashing the target app.</p>
 */
final class LinkAddressCompat {
    private LinkAddressCompat() {}

    static LinkAddress create(InetAddress address, int prefixLength) {
        try {
            Constructor<LinkAddress> constructor =
                    LinkAddress.class.getDeclaredConstructor(InetAddress.class, int.class);
            constructor.setAccessible(true);
            return constructor.newInstance(address, prefixLength);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("LinkAddress hidden constructor unavailable", e);
        }
    }
}

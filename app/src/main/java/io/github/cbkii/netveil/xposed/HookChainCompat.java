package io.github.cbkii.netveil.xposed;

import io.github.libxposed.api.XposedInterface;

final class HookChainCompat {
    private HookChainCompat() {}

    static Object arg(XposedInterface.Chain chain, int index) {
        try {
            return chain.getArg(index);
        } catch (Throwable ignored) {
            return null;
        }
    }

    static Object receiver(XposedInterface.Chain chain) {
        try {
            return chain.getThisObject();
        } catch (Throwable ignored) {
            return null;
        }
    }
}

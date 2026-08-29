package io.github.cbkii.netveil.xposed;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Tracks hook-install health and provides rollback for failed required-hook transactions. */
final class HookHealth {
    enum Requirement { REQUIRED, OPTIONAL }

    private static final String TAG = "NetVeil";

    private final NetVeilModule module;
    private final List<XposedInterface.HookHandle> installed = new ArrayList<>();
    private int requiredExpected;
    private int requiredInstalled;
    private int optionalExpected;
    private int optionalInstalled;
    private final List<String> requiredFailures = new ArrayList<>();
    private final AtomicInteger runtimeFallbacks = new AtomicInteger();

    HookHealth(NetVeilModule module) {
        this.module = module;
    }

    synchronized void expected(Requirement requirement) {
        if (requirement == Requirement.REQUIRED) requiredExpected++;
        else optionalExpected++;
    }

    synchronized void installed(Requirement requirement, XposedInterface.HookHandle handle) {
        if (requirement == Requirement.REQUIRED) requiredInstalled++;
        else optionalInstalled++;
        installed.add(handle);
    }

    synchronized void missing(Requirement requirement, String label) {
        if (requirement == Requirement.REQUIRED) requiredFailures.add(label + " (missing)");
    }

    synchronized void failed(Requirement requirement, String label, Throwable error) {
        if (requirement == Requirement.REQUIRED) requiredFailures.add(label + " (install failed)");
        module.log(requirement == Requirement.REQUIRED ? Log.ERROR : Log.WARN, TAG,
                (requirement == Requirement.REQUIRED ? "required" : "optional")
                        + " hook unavailable: " + label,
                error);
    }

    void fallback(String label, Throwable error) {
        runtimeFallbacks.incrementAndGet();
        module.log(Log.WARN, TAG, label + " fallback", error);
    }

    synchronized void requireHealthy() {
        if (requiredFailures.isEmpty() && requiredInstalled == requiredExpected) return;
        rollback();
        throw new IllegalStateException("required hook transaction incomplete: "
                + requiredInstalled + "/" + requiredExpected + "; failures=" + requiredFailures);
    }

    synchronized String summary() {
        return "required=" + requiredInstalled + "/" + requiredExpected
                + " optional=" + optionalInstalled + "/" + optionalExpected
                + " fallbacks=" + runtimeFallbacks.get();
    }

    private void rollback() {
        for (int i = installed.size() - 1; i >= 0; i--) {
            try {
                installed.get(i).unhook();
            } catch (Throwable t) {
                module.log(Log.WARN, TAG, "hook rollback failed", t);
            }
        }
        installed.clear();
        requiredInstalled = 0;
        optionalInstalled = 0;
    }
}

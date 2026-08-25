package io.github.cbkii.netveil.xposed;

import android.content.SharedPreferences;
import android.util.Log;

import io.github.cbkii.netveil.config.ConfigKeys;
import io.github.cbkii.netveil.config.Profile;
import io.github.libxposed.api.XposedModule;

import java.util.concurrent.atomic.AtomicBoolean;

public final class NetVeilModule extends XposedModule {
    private static final String TAG = "NetVeil";
    private static final String MODULE_PACKAGE = "dev.ip.netveil";

    private final AtomicBoolean processClaimed = new AtomicBoolean(false);
    private String processName = "";
    private boolean systemServer;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        systemServer = param.isSystemServer();
        if (systemServer) {
            log(Log.WARN, TAG, "system_server scope rejected; NetVeil is app-process only");
        }
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        if (systemServer || !param.isFirstPackage()) return;
        if (!processClaimed.compareAndSet(false, true)) return;

        String pkg = param.getPackageName();
        if (MODULE_PACKAGE.equals(pkg)) {
            log(Log.INFO, TAG, "module process excluded from hooks");
            return;
        }

        LegacyNetworkInfoHooks legacyHooks = null;
        try {
            SharedPreferences prefs = getRemotePreferences(ConfigKeys.PREFS);
            Profile.Resolved profile = Profile.load(prefs, pkg).resolve();
            if (profile == null) {
                log(Log.INFO, TAG, "inactive package=" + pkg + " process=" + processName
                        + " reason=no-resolvable-profile");
                return;
            }

            // Install the legacy Parcelable projection first. If the main hook graph fails its
            // required-hook transaction, roll this smaller transaction back as well so a process
            // never runs with a half-installed NetVeil identity.
            legacyHooks = new LegacyNetworkInfoHooks(this, profile);
            String legacyHealth = legacyHooks.install();
            String health = new NetworkHooks(this, profile).install();
            log(Log.INFO, TAG, "active package=" + pkg + " process=" + processName
                    + " " + health + " " + legacyHealth);
        } catch (Throwable t) {
            if (legacyHooks != null) legacyHooks.uninstall();
            // Never let a later package loaded into this process claim a second identity.
            log(Log.ERROR, TAG, "initialisation failed package=" + pkg + " process=" + processName, t);
        }
    }
}

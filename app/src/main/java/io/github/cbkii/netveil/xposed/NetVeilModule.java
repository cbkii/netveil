package io.github.cbkii.netveil.xposed;

import android.content.SharedPreferences;
import android.util.Log;

import io.github.cbkii.netveil.config.ConfigKeys;
import io.github.cbkii.netveil.config.Profile;
import io.github.libxposed.api.XposedModule;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class NetVeilModule extends XposedModule {
    private static final String TAG = "NetVeil";

    private final Set<String> installedPackages = ConcurrentHashMap.newKeySet();
    private String processName = "";

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        String pkg = param.getPackageName();
        if (pkg.equals(getApplicationInfo().packageName)) return;

        try {
            SharedPreferences prefs = getRemotePreferences(ConfigKeys.PREFS);
            Profile.Resolved profile = Profile.load(prefs, pkg).resolve();
            if (profile == null) return;
            if (!installedPackages.add(pkg)) return;

            new NetworkHooks(this, profile).install();
            log(Log.INFO, TAG,
                    "active package=" + pkg + " process=" + processName
                            + " ip=" + profile.ipv4 + "/" + profile.prefixLength
                            + " gateway=" + profile.gateway + " dnsCount=" + profile.dns.size());
        } catch (Throwable t) {
            installedPackages.remove(pkg);
            log(Log.ERROR, TAG, "initialisation failed for " + pkg, t);
        }
    }
}

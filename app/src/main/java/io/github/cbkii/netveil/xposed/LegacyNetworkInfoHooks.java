package io.github.cbkii.netveil.xposed;

import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Parcel;

import io.github.cbkii.netveil.config.Profile;
import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Closes deprecated NetworkInfo getter/string/Parcelable leaks for raw VPN objects.
 *
 * <p>This is kept separate from the main hook graph because NetworkInfo is a legacy API surface,
 * but its backing fields are written directly to Parcel on Android 15/16. Getter-only masking is
 * therefore insufficient for a coherent app-visible projection.</p>
 */
final class LegacyNetworkInfoHooks {
    private static final int LEGACY_TYPE_NONE = -1;

    private final NetVeilModule module;
    private final OriginAccess origin;
    private final VirtualNetworkProfile model;
    private final List<XposedInterface.HookHandle> handles = new ArrayList<>();

    LegacyNetworkInfoHooks(NetVeilModule module, Profile.Resolved profile) {
        this.module = module;
        this.origin = new OriginAccess(module);
        this.model = new VirtualNetworkProfile(profile);
    }

    String install() {
        try {
            hookAfter("getType", new Class<?>[0], (chain, original) ->
                    isRawVpn(receiver(chain)) ? presentationLegacyType(original) : original);
            hookAfter("getTypeName", new Class<?>[0], (chain, original) ->
                    isRawVpn(receiver(chain)) ? presentationLegacyTypeName(original) : original);
            hookAfter("getExtraInfo", new Class<?>[0], (chain, original) ->
                    isRawVpn(receiver(chain)) ? null : original);
            hookAfter("toString", new Class<?>[0], (chain, original) ->
                    isRawVpn(receiver(chain)) ? origin.callByName(project(receiver(chain)), "toString") : original);
            hookOptionalAfter("toShortString", new Class<?>[0], (chain, original) ->
                    isRawVpn(receiver(chain)) ? origin.callByName(project(receiver(chain)), "toShortString") : original);
            hookParcel();
            return "legacyNetworkInfo=" + handles.size();
        } catch (Throwable t) {
            uninstall();
            throw new IllegalStateException("legacy NetworkInfo hook transaction incomplete", t);
        }
    }

    void uninstall() {
        for (int i = handles.size() - 1; i >= 0; i--) {
            try {
                handles.get(i).unhook();
            } catch (Throwable ignored) {
                // Best-effort rollback during failed module initialisation.
            }
        }
        handles.clear();
    }

    private NetworkInfo receiver(XposedInterface.Chain chain) {
        Object value = HookChainCompat.receiver(chain);
        return value instanceof NetworkInfo ? (NetworkInfo) value : null;
    }

    private boolean isRawVpn(NetworkInfo info) {
        if (info == null) return false;
        try {
            Object value = origin.call(info, NetworkInfo.class, "getType", new Class<?>[0]);
            return value instanceof Integer && (Integer) value == ConnectivityManager.TYPE_VPN;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private Object presentationLegacyType(Object fallback) {
        return switch (model.presentationTransport()) {
            case NetworkCapabilities.TRANSPORT_WIFI -> ConnectivityManager.TYPE_WIFI;
            case NetworkCapabilities.TRANSPORT_CELLULAR -> ConnectivityManager.TYPE_MOBILE;
            case NetworkCapabilities.TRANSPORT_ETHERNET -> ConnectivityManager.TYPE_ETHERNET;
            default -> fallback;
        };
    }

    private Object presentationLegacyTypeName(Object fallback) {
        return switch (model.presentationTransport()) {
            case NetworkCapabilities.TRANSPORT_WIFI -> "WIFI";
            case NetworkCapabilities.TRANSPORT_CELLULAR -> "MOBILE";
            case NetworkCapabilities.TRANSPORT_ETHERNET -> "ETHERNET";
            default -> fallback;
        };
    }

    private NetworkInfo project(NetworkInfo original) throws Throwable {
        if (original == null || !isRawVpn(original)) return original;
        Object projectedType = presentationLegacyType(LEGACY_TYPE_NONE);
        if (!(projectedType instanceof Integer) || (Integer) projectedType == LEGACY_TYPE_NONE) {
            throw new IllegalStateException("presentation transport unresolved");
        }

        int subtype = intValue(origin.callByName(original, "getSubtype"), 0);
        String subtypeName = stringValue(origin.callByName(original, "getSubtypeName"));
        String typeName = String.valueOf(presentationLegacyTypeName(""));

        Constructor<NetworkInfo> constructor = NetworkInfo.class.getDeclaredConstructor(
                int.class, int.class, String.class, String.class);
        NetworkInfo out = origin.construct(constructor, (Integer) projectedType, subtype, typeName, subtypeName);

        Object detailed = origin.callByName(original, "getDetailedState");
        Object reason = origin.callByName(original, "getReason");
        if (detailed instanceof NetworkInfo.DetailedState) {
            origin.callByName(out, "setDetailedState", detailed, reason, null);
        }

        copyBoolean(original, out, "isFailover", "setFailover");
        copyBoolean(original, out, "isAvailable", "setIsAvailable");
        copyBoolean(original, out, "isRoaming", "setRoaming");
        return out;
    }

    private void copyBoolean(NetworkInfo source, NetworkInfo target, String getter, String setter) {
        Object value = origin.callOptionalByName(source, getter);
        if (value instanceof Boolean) origin.callOptionalByName(target, setter, value);
    }

    private void hookParcel() throws Throwable {
        Method method = NetworkInfo.class.getDeclaredMethod("writeToParcel", Parcel.class, int.class);
        XposedInterface.HookHandle handle = module.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    NetworkInfo raw = receiver(chain);
                    if (!isRawVpn(raw)) return chain.proceed();

                    Object parcelValue = HookChainCompat.arg(chain, 0);
                    Object flagsValue = HookChainCompat.arg(chain, 1);
                    if (!(parcelValue instanceof Parcel) || !(flagsValue instanceof Integer)) {
                        return chain.proceed();
                    }

                    Parcel parcel = (Parcel) parcelValue;
                    int position = parcel.dataPosition();
                    int size = parcel.dataSize();
                    try {
                        NetworkInfo projected = project(raw);
                        origin.callByName(projected, "writeToParcel", parcel, flagsValue);
                        return null;
                    } catch (Throwable t) {
                        // Restore the destination before falling back; writeToParcel can fail after
                        // partially writing fields, and appending the raw object would corrupt it.
                        parcel.setDataSize(size);
                        parcel.setDataPosition(Math.min(position, size));
                        return chain.proceed();
                    }
                });
        handles.add(handle);
    }

    private interface Transformer {
        Object apply(XposedInterface.Chain chain, Object original) throws Throwable;
    }

    private void hookAfter(String name, Class<?>[] params, Transformer transformer) throws Throwable {
        Method method = NetworkInfo.class.getDeclaredMethod(name, params);
        handles.add(module.hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object original = chain.proceed();
                    try {
                        return transformer.apply(chain, original);
                    } catch (Throwable ignored) {
                        return original;
                    }
                }));
    }

    private void hookOptionalAfter(String name, Class<?>[] params, Transformer transformer) throws Throwable {
        try {
            hookAfter(name, params, transformer);
        } catch (NoSuchMethodException ignored) {
            // Hidden legacy helper varies across platform releases.
        }
    }

    private static int intValue(Object value, int fallback) {
        return value instanceof Integer ? (Integer) value : fallback;
    }

    private static String stringValue(Object value) {
        return value instanceof String ? (String) value : "";
    }
}

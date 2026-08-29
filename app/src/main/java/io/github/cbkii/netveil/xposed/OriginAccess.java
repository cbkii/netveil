package io.github.cbkii.netveil.xposed;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Access-check-free calls into framework methods using modern libxposed origin invokers. */
final class OriginAccess {
    private final NetVeilModule module;
    private final Map<Method, XposedInterface.Invoker<?, Method>> methodInvokers = new ConcurrentHashMap<>();

    OriginAccess(NetVeilModule module) {
        this.module = module;
    }

    Object call(Object receiver, Class<?> owner, String name, Class<?>[] params, Object... args) throws Throwable {
        Method method = owner.getDeclaredMethod(name, params);
        return invoke(method, receiver, args);
    }

    Object callByName(Object receiver, String name, Object... args) throws Throwable {
        if (receiver == null) throw new IllegalArgumentException("receiver == null");
        Method method = findCompatible(receiver.getClass(), name, args);
        if (method == null) throw new NoSuchMethodException(receiver.getClass().getName() + "." + name);
        return invoke(method, receiver, args);
    }

    Object callOptionalByName(Object receiver, String name, Object... args) {
        try {
            return callByName(receiver, name, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    <T> T construct(Constructor<T> constructor, Object... args) throws Throwable {
        XposedInterface.CtorInvoker<T> invoker = module.getInvoker(constructor);
        invoker.setType(XposedInterface.Invoker.Type.ORIGIN);
        return invoker.newInstance(args);
    }

    private Object invoke(Method method, Object receiver, Object... args) throws Throwable {
        XposedInterface.Invoker<?, Method> invoker = methodInvokers.computeIfAbsent(method, key -> {
            XposedInterface.Invoker<?, Method> created = module.getInvoker(key);
            created.setType(XposedInterface.Invoker.Type.ORIGIN);
            return created;
        });
        return invoker.invoke(receiver, args);
    }

    private static Method findCompatible(Class<?> start, String name, Object[] args) {
        for (Class<?> owner = start; owner != null; owner = owner.getSuperclass()) {
            for (Method method : owner.getDeclaredMethods()) {
                if (!method.getName().equals(name)) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length != args.length) continue;
                boolean compatible = true;
                for (int i = 0; i < params.length; i++) {
                    if (!isCompatible(params[i], args[i])) {
                        compatible = false;
                        break;
                    }
                }
                if (compatible) return method;
            }
        }
        return null;
    }

    private static boolean isCompatible(Class<?> parameter, Object value) {
        if (value == null) return !parameter.isPrimitive();
        if (!parameter.isPrimitive()) return parameter.isInstance(value);
        if (parameter == boolean.class) return value instanceof Boolean;
        if (parameter == byte.class) return value instanceof Byte;
        if (parameter == short.class) return value instanceof Short || value instanceof Byte;
        if (parameter == int.class) return value instanceof Integer || value instanceof Short || value instanceof Byte;
        if (parameter == long.class) return value instanceof Long || value instanceof Integer
                || value instanceof Short || value instanceof Byte;
        if (parameter == float.class) return value instanceof Float || value instanceof Long
                || value instanceof Integer || value instanceof Short || value instanceof Byte;
        if (parameter == double.class) return value instanceof Double || value instanceof Float
                || value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte;
        if (parameter == char.class) return value instanceof Character;
        return false;
    }
}

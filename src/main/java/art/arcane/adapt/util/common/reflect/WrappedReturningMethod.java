package art.arcane.adapt.util.reflect;


import art.arcane.adapt.Adapt;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import art.arcane.adapt.util.common.format.C;

public final class WrappedReturningMethod<C, R> {

    private final Method method;

    public WrappedReturningMethod(Class<C> origin, String methodName, Class<?>... paramTypes) {
        Method m = null;
        try {
            m = origin.getDeclaredMethod(methodName, paramTypes);
            m.setAccessible(true);
        } catch (NoSuchMethodException e) {
            Adapt.error("Failed to created WrappedMethod %s#%s: %s%s".formatted(origin.getSimpleName(), methodName, e.getClass().getSimpleName(), e.getMessage().equals("") ? "" : " | " + e.getMessage()));
        }
        this.method = m;
    }

    public R invoke(Object... args) {
        return invoke(null, args);
    }

    public R invoke(C instance, Object... args) {
        if (method == null) {
            return null;
        }

        try {
            return (R) method.invoke(instance, args);
        } catch (InvocationTargetException | IllegalAccessException e) {
            Adapt.error("Failed to invoke WrappedMethod %s#%s: %s%s".formatted(method.getDeclaringClass().getSimpleName(), method.getName(), e.getClass().getSimpleName(), e.getMessage().equals("") ? "" : " | " + e.getMessage()));
            return null;
        }
    }
}
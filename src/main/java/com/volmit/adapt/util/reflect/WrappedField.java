package com.volmit.adapt.util.reflect;

import com.volmit.adapt.Adapt;

import java.lang.reflect.Field;

public class WrappedField<C, T> {

    private final Field field;

    public WrappedField(Class<C> origin, String methodName) {
        Field f = null;
        try {
            f = origin.getDeclaredField(methodName);
            f.setAccessible(true);
        } catch (NoSuchFieldException e) {
            Adapt.error("Failed to created WrappedField %s#%s: %s%s".formatted(origin.getSimpleName(), methodName, e.getClass().getSimpleName(), e.getMessage().equals("") ? "" : " | " + e.getMessage()));
        }
        this.field = f;
    }

    public T get() {
        return get(null);
    }

    public T get(C instance) {
        if (field == null) {
            return null;
        }

        try {
            return (T) field.get(instance);
        } catch (IllegalAccessException e) {
            Adapt.error("Failed to get WrappedField %s#%s: %s%s".formatted(field.getDeclaringClass().getSimpleName(), field.getName(), e.getClass().getSimpleName(), e.getMessage().equals("") ? "" : " | " + e.getMessage()));
            return null;
        }
    }

    public boolean hasFailed() {
        return field == null;
    }
}

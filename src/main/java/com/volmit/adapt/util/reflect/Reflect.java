package com.volmit.adapt.util.reflect;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;

public class Reflect {

    @NotNull
    public static Optional<Class<?>> getClass(@NotNull String className) {
        try {
            return Optional.of(Class.forName(className));
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }

    @NotNull
    public static <E extends Enum<E>> Optional<E> getEnum(@NotNull Class<E> enumClass, @NotNull String enumName) {
        try {
            return Optional.of(Enum.valueOf(enumClass, enumName));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @NotNull
    public static <E extends Enum<E>> E getEnum(@NotNull Class<E> enumClass, @NotNull String... enumNames) {
        if (enumNames.length == 0) throw new IllegalArgumentException("Need at least one enum name");
        for (String enumName : enumNames) {
            Optional<E> optionalEnum = getEnum(enumClass, enumName);
            if (optionalEnum.isPresent()) return optionalEnum.get();
        }
        throw new IllegalArgumentException("No Enum found for names " + Arrays.toString(enumNames));
    }

    @NotNull
    public static Optional<Method> getMethod(@NotNull Class<?> clazz, @NotNull String methodName, @NotNull Class<?>... parameterTypes) {
        try {
            return Optional.of(clazz.getDeclaredMethod(methodName, parameterTypes));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    public static <E> E getField(Class<E> clazz, @NotNull String... fieldNames) {
        if (fieldNames.length == 0) throw new IllegalArgumentException("Need at least one field name");
        for (String fieldName : fieldNames) {
            Optional<Field> optionalField = getField(clazz, fieldName);
            if (optionalField.isPresent()) {
                try {
                    return (E) optionalField.get().get(null);
                } catch (IllegalAccessException ignored) {
                }
            }
        }
        throw new IllegalArgumentException("No Field found for names " + Arrays.toString(fieldNames));
    }

    @NotNull
    public static Optional<Field> getField(@NotNull Class<?> clazz, @NotNull String fieldName) {
        try {
            return Optional.of(clazz.getDeclaredField(fieldName));
        } catch (NoSuchFieldException e) {
            return Optional.empty();
        }
    }
}

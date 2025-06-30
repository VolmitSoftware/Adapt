package com.volmit.adapt.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.NonNull;

import java.lang.reflect.Type;

public class Json {
    public static final Gson NORMAL = create(false);
    public static final Gson PRETTY = create(true);

    @NonNull
    public static String toJson(@NonNull Object src, boolean pretty) {
        return toJson(src, src.getClass(), pretty);
    }

    @NonNull
    public static String toJson(@NonNull Object src, @NonNull Type type, boolean pretty) {
        return (pretty ? PRETTY : NORMAL).toJson(src, type);
    }

    public static <T> T fromJson(@NonNull String json, @NonNull Class<T> type) {
        return NORMAL.fromJson(json, type);
    }

    public static <T> T fromJson(@NonNull String json, @NonNull Type type) {
        return NORMAL.fromJson(json, type);
    }

    private static Gson create(boolean pretty) {
        var builder = new GsonBuilder()
                .setLenient()
                .disableHtmlEscaping();
        if (pretty) builder.setPrettyPrinting();
        return builder.create();
    }
}

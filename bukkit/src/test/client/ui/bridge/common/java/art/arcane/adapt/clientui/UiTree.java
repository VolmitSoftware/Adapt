package art.arcane.adapt.clientui;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class UiTree {
    private static final String PROBE = "art.arcane.adapt.client.qa.UiProbe";
    private static Method snapshot;
    private static boolean resolved;

    private UiTree() {
    }

    static JsonElement snapshot() {
        Method method = resolve();
        if (method == null) {
            return JsonNull.INSTANCE;
        }
        try {
            Object value = method.invoke(null);
            return value == null ? JsonNull.INSTANCE : JsonParser.parseString(value.toString());
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException failure) {
            JsonObject error = new JsonObject();
            error.addProperty("probeError", String.valueOf(failure));
            return error;
        }
    }

    private static Method resolve() {
        if (resolved) {
            return snapshot;
        }
        resolved = true;
        for (ClassLoader loader : new ClassLoader[]{UiTree.class.getClassLoader(), Thread.currentThread().getContextClassLoader()}) {
            if (loader == null) {
                continue;
            }
            try {
                Method method = Class.forName(PROBE, true, loader).getMethod("snapshot");
                if (Modifier.isStatic(method.getModifiers())) {
                    snapshot = method;
                    return snapshot;
                }
            } catch (ClassNotFoundException | NoSuchMethodException | LinkageError ignored) {
                continue;
            }
        }
        return null;
    }
}

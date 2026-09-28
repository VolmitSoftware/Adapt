package art.arcane.adapt.gameplay;

import com.google.gson.JsonObject;

import java.lang.reflect.Method;

public final class ProtocolFixtures {
    private ProtocolFixtures() {
    }

    public static JsonObject attributes() {
        try {
            Object registry = Class.forName("net.minecraft.core.registries.BuiltInRegistries").getField("ATTRIBUTE").get(null);
            Class<?> registryType = Class.forName("net.minecraft.core.Registry");
            Method getId = registryType.getMethod("getId", Object.class);
            Method getKey = registryType.getMethod("getKey", Object.class);
            JsonObject result = new JsonObject();
            for (Object attribute : (Iterable<?>) registry) {
                int id = (Integer) getId.invoke(registry, attribute);
                result.addProperty(Integer.toString(id), getKey.invoke(registry, attribute).toString());
            }
            return result;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot observe the server attribute protocol registry", failure);
        }
    }
}

package art.arcane.adapt.mod.platform.text;

import art.arcane.adapt.mod.platform.ModPlatform;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JavaOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;

/**
 * Vanilla text components to and from their JSON form, with registry context when the server runs. The codec works on
 * plain Java values; {@link JsonValues} turns those into JSON text and back.
 */
public final class ComponentJson {
    private ComponentJson() {
    }

    /** The component as JSON, or null for null; throws {@link IllegalStateException} when the codec cannot write it. */
    public static String json(Component component) {
        if (component == null) {
            return null;
        }
        DataResult<Object> encoded = ComponentSerialization.CODEC.encodeStart(ops(), component);
        return JsonValues.write(encoded.getOrThrow(IllegalStateException::new));
    }

    /** Parsed component, or null for null, blank or unreadable JSON. */
    public static Component component(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return line(json);
        } catch (IllegalArgumentException unreadable) {
            return null;
        }
    }

    /** Parsed component; throws {@link IllegalArgumentException} naming the JSON when it is not a text component. */
    public static Component line(String json) {
        Object value;
        try {
            value = JsonValues.read(json);
        } catch (RuntimeException malformed) {
            throw new IllegalArgumentException("Not JSON: " + json, malformed);
        }
        return ComponentSerialization.CODEC.parse(ops(), value)
                .getOrThrow((String error) -> new IllegalArgumentException("Not a text component (" + error + "): " + json));
    }

    private static DynamicOps<Object> ops() {
        MinecraftServer server = ModPlatform.server();
        return server == null ? JavaOps.INSTANCE : server.registryAccess().createSerializationContext(JavaOps.INSTANCE);
    }
}

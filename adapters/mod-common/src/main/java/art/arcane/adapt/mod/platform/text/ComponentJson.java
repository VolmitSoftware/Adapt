package art.arcane.adapt.mod.platform.text;

import art.arcane.adapt.mod.platform.ModPlatform;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;

/** Vanilla text components to and from their JSON form, with registry context when the server runs. */
public final class ComponentJson {
    private ComponentJson() {
    }

    public static String json(Component component) {
        if (component == null) {
            return null;
        }
        return ComponentSerialization.CODEC.encodeStart(ops(), component).result().map(JsonElement::toString).orElse(null);
    }

    /** Parsed component, or null for null, blank or unreadable JSON. */
    public static Component component(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return ComponentSerialization.CODEC.parse(ops(), JsonParser.parseString(json)).result().orElse(null);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    private static DynamicOps<JsonElement> ops() {
        MinecraftServer server = ModPlatform.server();
        return server == null ? JsonOps.INSTANCE : server.registryAccess().createSerializationContext(JsonOps.INSTANCE);
    }
}

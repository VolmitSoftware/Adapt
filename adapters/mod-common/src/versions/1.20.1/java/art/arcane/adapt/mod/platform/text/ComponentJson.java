package art.arcane.adapt.mod.platform.text;

import net.minecraft.network.chat.Component;

/** Vanilla text components to and from their JSON form, through this version's component serializer. */
public final class ComponentJson {
    private ComponentJson() {
    }

    public static String json(Component component) {
        if (component == null) {
            return null;
        }
        try {
            return Component.Serializer.toJson(component);
        } catch (RuntimeException unwritable) {
            return null;
        }
    }

    /** Parsed component, or null for null, blank or unreadable JSON. */
    public static Component component(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return Component.Serializer.fromJson(json);
        } catch (RuntimeException unreadable) {
            return null;
        }
    }
}

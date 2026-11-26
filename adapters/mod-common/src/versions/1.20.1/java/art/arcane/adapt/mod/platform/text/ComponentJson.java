package art.arcane.adapt.mod.platform.text;

import net.minecraft.network.chat.Component;

/** Vanilla text components to and from their JSON form, through this version's component serializer. */
public final class ComponentJson {
    private ComponentJson() {
    }

    public static String json(Component component) {
        return component == null ? null : Component.Serializer.toJson(component);
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
        Component parsed;
        try {
            parsed = Component.Serializer.fromJson(json);
        } catch (RuntimeException unreadable) {
            throw new IllegalArgumentException("Not a text component: " + json, unreadable);
        }
        if (parsed == null) {
            throw new IllegalArgumentException("Not a text component: " + json);
        }
        return parsed;
    }
}

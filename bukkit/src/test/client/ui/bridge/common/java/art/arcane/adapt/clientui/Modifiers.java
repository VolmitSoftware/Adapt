package art.arcane.adapt.clientui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;

final class Modifiers {
    private Modifiers() {
    }

    static int mask(JsonObject input) {
        int mask = 0;
        if (input.has("mods")) {
            for (JsonElement element : input.getAsJsonArray("mods")) {
                mask |= switch (element.getAsString()) {
                    case "shift" -> Platform.MOD_SHIFT;
                    case "control", "ctrl" -> Platform.MOD_CONTROL;
                    case "alt" -> Platform.MOD_ALT;
                    case "super", "command", "cmd" -> Platform.MOD_SUPER;
                    default -> throw new IllegalArgumentException("Unknown modifier " + element.getAsString());
                };
            }
        }
        return mask;
    }

    static void hold(Minecraft client, JsonObject input, boolean down) {
        if (!input.has("mods")) {
            return;
        }
        for (JsonElement element : input.getAsJsonArray("mods")) {
            String key = switch (element.getAsString()) {
                case "shift" -> "left.shift";
                case "control", "ctrl" -> "left.control";
                case "alt" -> "left.alt";
                default -> "left.win";
            };
            int code = Keys.code(key);
            Keys.hold(code, down);
            Platform.key(client, code, down, down ? mask(input) : 0);
        }
    }
}

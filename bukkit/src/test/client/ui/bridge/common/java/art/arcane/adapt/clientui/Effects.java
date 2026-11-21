package art.arcane.adapt.clientui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffectInstance;

final class Effects {
    private Effects() {
    }

    static JsonObject sample(LocalPlayer player, long tick) {
        JsonObject entry = new JsonObject();
        entry.addProperty("t", tick);
        JsonArray effects = new JsonArray();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            JsonObject effect = new JsonObject();
            effect.addProperty("id", instance.getDescriptionId());
            effect.addProperty("duration", instance.getDuration());
            effect.addProperty("amplifier", instance.getAmplifier());
            effects.add(effect);
        }
        entry.add("effects", effects);
        return entry;
    }
}

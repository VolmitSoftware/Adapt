package art.arcane.adapt.bukkit.canvas.server;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;

/** Text component JSON to server components, so titles and tooltips need no Adventure on the server. */
final class CanvasText {
  private CanvasText() {
  }

  static Component component(ServerPlayer player, String json) {
    return ComponentSerialization.CODEC
        .parse(player.registryAccess().createSerializationContext(JsonOps.INSTANCE), JsonParser.parseString(json))
        .getOrThrow();
  }
}

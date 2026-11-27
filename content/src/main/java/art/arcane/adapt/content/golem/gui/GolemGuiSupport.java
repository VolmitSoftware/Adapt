package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.UUID;

public final class GolemGuiSupport {
  private GolemGuiSupport() {
  }

  public static void notify(PlayerRef player, Text message) {
    actionBar(player, TextFormat.AQUA, message);
  }

  public static void warn(PlayerRef player, Text message) {
    actionBar(player, TextFormat.RED, message);
  }

  /** Chat line with the Adapt prefix, as the plugin's own messages. */
  public static void message(PlayerRef player, String message) {
    player.sendMessage(TextFormat.GRAY + "[" + TextFormat.DARK_RED + "Adapt" + TextFormat.GRAY + "]: " + message);
  }

  static boolean ownsViewer(PlayerRef player) {
    return AdaptApi.scheduler().isRegionized() ? AdaptApi.scheduler().owns(player) : AdaptApi.scheduler().isTickThread();
  }

  static String itemName(ItemRef item) {
    return TextFormat.strip(item.displayName());
  }

  static String worldName(UUID worldId) {
    WorldRef world = worldId == null ? null : AdaptApi.platform().host().world(worldId);
    return world == null ? String.valueOf(worldId) : world.name();
  }

  private static void actionBar(PlayerRef player, TextFormat color, Text message) {
    AdaptApi.scheduler().runEntity(player, () -> HudStatus.show(player, color + message.resolve(player)));
  }
}

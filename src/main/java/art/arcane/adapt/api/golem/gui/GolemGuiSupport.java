package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.UUID;

final class GolemGuiSupport {
  private GolemGuiSupport() {
  }

  static boolean ownsViewer(Player player) {
    return J.isFoliaThreading() ? J.isOwnedByCurrentRegion(player) : J.isPrimaryThread();
  }

  static void notify(Player player, TextKey key, MessageArgument... arguments) {
    J.runEntity(player, () -> AdaptHud.actionBar(player, C.AQUA + AdaptLanguage.text(key, arguments)));
  }

  static void warn(Player player, TextKey key, MessageArgument... arguments) {
    J.runEntity(player, () -> AdaptHud.actionBar(player, C.RED + AdaptLanguage.text(key, arguments)));
  }

  static String worldName(UUID worldId) {
    World world = worldId == null ? null : Bukkit.getWorld(worldId);
    return world == null ? String.valueOf(worldId) : world.getName();
  }
}

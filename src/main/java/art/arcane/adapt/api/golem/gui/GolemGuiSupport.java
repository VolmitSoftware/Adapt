package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class GolemGuiSupport {
  private GolemGuiSupport() {
  }

  public static void notify(Player player, TextKey key, MessageArgument... arguments) {
    actionBar(player, C.AQUA, key, arguments);
  }

  public static void warn(Player player, TextKey key, MessageArgument... arguments) {
    actionBar(player, C.RED, key, arguments);
  }

  public static void warn(Player player, GolemGuiActions.Notice notice) {
    warn(player, notice.key(), notice.arguments().toArray(MessageArgument[]::new));
  }

  public static Object localized(TextKey key) {
    return new LocalizedText(key);
  }

  static boolean ownsViewer(Player player) {
    return J.isFoliaThreading() ? J.isOwnedByCurrentRegion(player) : J.isPrimaryThread();
  }

  static Object itemName(ItemStack item) {
    return new ItemName(item.clone());
  }

  static String worldName(UUID worldId) {
    World world = worldId == null ? null : Bukkit.getWorld(worldId);
    return world == null ? String.valueOf(worldId) : world.getName();
  }

  private static void actionBar(Player player, C color, TextKey key, MessageArgument... arguments) {
    J.runEntity(player, () -> LanguageAudience.run(player.getUniqueId(),
        () -> AdaptHud.actionBar(player, color + AdaptLanguage.text(key, arguments))));
  }

  private record LocalizedText(TextKey key) {
    @Override
    public String toString() {
      return AdaptLanguage.text(key);
    }
  }

  private record ItemName(ItemStack item) {
    @Override
    public String toString() {
      return PaperCompat.itemName(item);
    }
  }
}

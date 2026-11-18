package art.arcane.adapt.api.golem.gui;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.MessageArgument;
import art.arcane.volmlib.util.localization.TextKey;

import java.util.UUID;

public final class GolemGuiSupport {
  private GolemGuiSupport() {
  }

  public static void notify(PlayerRef player, TextKey key, MessageArgument... arguments) {
    actionBar(player, C.AQUA, key, arguments);
  }

  public static void warn(PlayerRef player, TextKey key, MessageArgument... arguments) {
    actionBar(player, C.RED, key, arguments);
  }

  public static void warn(PlayerRef player, GolemGuiActions.Notice notice) {
    warn(player, notice.key(), notice.arguments().toArray(MessageArgument[]::new));
  }

  public static Object localized(TextKey key) {
    return new LocalizedText(key);
  }

  /** Chat line with the Adapt prefix, as the plugin's own messages. */
  public static void message(PlayerRef player, String message) {
    player.sendMessage(C.GRAY + "[" + C.DARK_RED + "Adapt" + C.GRAY + "]: " + message);
  }

  static boolean ownsViewer(PlayerRef player) {
    return Tasks.isFoliaThreading() ? Tasks.isOwnedByCurrentRegion(player) : Tasks.isPrimaryThread();
  }

  static Object itemName(ItemRef item) {
    return new ItemName(item.copy());
  }

  static String worldName(UUID worldId) {
    WorldRef world = worldId == null ? null : AdaptCore.platform().host().world(worldId);
    return world == null ? String.valueOf(worldId) : world.name();
  }

  private static void actionBar(PlayerRef player, C color, TextKey key, MessageArgument... arguments) {
    Tasks.runEntity(player, () -> LanguageAudience.run(player.uuid(),
        () -> AdaptHud.actionBar(player, color + AdaptLanguage.text(key, arguments))));
  }

  private record LocalizedText(TextKey key) {
    @Override
    public String toString() {
      return AdaptLanguage.text(key);
    }
  }

  private record ItemName(ItemRef item) {
    @Override
    public String toString() {
      return C.stripColor(item.displayName());
    }
  }
}

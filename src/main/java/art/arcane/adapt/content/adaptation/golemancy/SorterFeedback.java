package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

final class SorterFeedback {
  private static final float VOLUME = 0.6F;
  private static final float TAKE_PITCH = 0.9F;
  private static final float DROP_PITCH = 1.2F;
  private static final float HAND_PITCH = 1.0F;

  private SorterFeedback() {
  }

  static void play(Adaptation<?> source, Location at, Kind kind) {
    Sound sound = switch (kind) {
      case TAKE, DROP -> Sound.ENTITY_COPPER_GOLEM_ITEM_DROP;
      case HAND -> Sound.ENTITY_ITEM_PICKUP;
    };
    float pitch = switch (kind) {
      case TAKE -> TAKE_PITCH;
      case DROP -> DROP_PITCH;
      case HAND -> HAND_PITCH;
    };
    Fx.now(source, at, FxPriority.AMBIENT).sound(sound, VOLUME, pitch);
  }

  static void requested(Player player, ItemStack item, int amount) {
    notify(player, GolemancyMessages.SORTER_CORE_FETCH_REQUESTED, item, amount);
  }

  static void delivered(Adaptation<?> source, Player player, ItemStack item, int amount) {
    play(source, player.getLocation(), Kind.HAND);
    notify(player, GolemancyMessages.SORTER_CORE_FETCH_DELIVERED, item, amount);
  }

  private static void notify(Player player, TextKey key, ItemStack item, int amount) {
    String name = PaperCompat.itemName(item);
    J.runEntity(player, () -> LanguageAudience.run(player.getUniqueId(),
        () -> AdaptHud.actionBar(player, C.AQUA + AdaptLanguage.text(key, trusted("amount", amount), untrusted("item", name)))));
  }

  enum Kind {
    TAKE,
    DROP,
    HAND
  }
}

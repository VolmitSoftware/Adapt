package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.TextKey;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;
import static art.arcane.volmlib.util.localization.MessageArgument.untrusted;

final class SorterFeedback {
  private static final float VOLUME = 0.6F;
  private static final float TAKE_PITCH = 0.9F;
  private static final float DROP_PITCH = 1.2F;
  private static final float HAND_PITCH = 1.0F;

  private SorterFeedback() {
  }

  static void play(Adaptation<?> source, Loc at, Kind kind) {
    Id sound = switch (kind) {
      case TAKE, DROP -> Sounds.ENTITY_COPPER_GOLEM_ITEM_DROP;
      case HAND -> Sounds.ENTITY_ITEM_PICKUP;
    };
    float pitch = switch (kind) {
      case TAKE -> TAKE_PITCH;
      case DROP -> DROP_PITCH;
      case HAND -> HAND_PITCH;
    };
    Fx.now(source, at, FxPriority.AMBIENT).sound(sound, VOLUME, pitch);
  }

  static void requested(PlayerRef player, ItemRef item, int amount) {
    notify(player, GolemancyMessages.SORTER_CORE_FETCH_REQUESTED, item, amount);
  }

  static void delivered(Adaptation<?> source, PlayerRef player, ItemRef item, int amount) {
    play(source, player.location(), Kind.HAND);
    notify(player, GolemancyMessages.SORTER_CORE_FETCH_DELIVERED, item, amount);
  }

  private static void notify(PlayerRef player, TextKey key, ItemRef item, int amount) {
    String name = C.stripColor(item.displayName());
    Tasks.runEntity(player, () -> LanguageAudience.run(player.uuid(),
        () -> AdaptHud.actionBar(player, C.AQUA + AdaptLanguage.text(key, trusted("amount", amount), untrusted("item", name)))));
  }

  enum Kind {
    TAKE,
    DROP,
    HAND
  }
}

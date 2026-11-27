package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;


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
    notify(player, Text.key("adapt.golemancy.sorter_core.fetch_requested"), item, amount);
  }

  static void delivered(Adaptation<?> source, PlayerRef player, ItemRef item, int amount) {
    play(source, player.location(), Kind.HAND);
    notify(player, Text.key("adapt.golemancy.sorter_core.fetch_delivered"), item, amount);
  }

  private static void notify(PlayerRef player, Text key, ItemRef item, int amount) {
    String name = TextFormat.strip(item.displayName());
    AdaptApi.scheduler().runEntity(player, () -> Text.renderFor(player.uuid(),
        () -> HudStatus.show(player, TextFormat.AQUA + key.withMarkup("amount", String.valueOf(amount)).with("item", name).resolve())));
  }

  enum Kind {
    TAKE,
    DROP,
    HAND
  }
}

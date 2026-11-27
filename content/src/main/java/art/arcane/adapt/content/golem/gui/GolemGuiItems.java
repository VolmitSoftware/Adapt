package art.arcane.adapt.content.golem.gui;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;

import java.util.concurrent.atomic.AtomicBoolean;

public final class GolemGuiItems {
  private GolemGuiItems() {
  }

  static void toGolem(MobRef golem, PlayerRef player, ItemRef held, Runnable task) {
    AtomicBoolean claimed = new AtomicBoolean();
    Loc fallback = player.location();
    boolean scheduled = AdaptApi.scheduler().runEntity(golem, () -> once(claimed, task), () -> once(claimed, () -> {
      toPlayer(player, held, fallback);
      GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
    }));
    if (!scheduled) {
      once(claimed, () -> {
        give(player, held);
        GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone"));
      });
    }
  }

  static void fromGolem(MobRef golem, PlayerRef player, Runnable task) {
    AtomicBoolean claimed = new AtomicBoolean();
    Runnable gone = () -> once(claimed, () -> GolemGuiSupport.warn(player, Text.key("adapt.golemancy.message.gone")));
    if (!AdaptApi.scheduler().runEntity(golem, () -> once(claimed, task), gone)) {
      gone.run();
    }
  }

  public static void toPlayer(PlayerRef player, ItemRef item, Loc fallback) {
    AtomicBoolean claimed = new AtomicBoolean();
    Runnable dropOnce = () -> once(claimed, () -> drop(item, fallback));
    if (!AdaptApi.scheduler().runEntity(player, () -> once(claimed, () -> give(player, item)), dropOnce)) {
      dropOnce.run();
    }
  }

  static void give(PlayerRef player, ItemRef item) {
    for (ItemRef leftover : player.inventory().addItem(item)) {
      player.world().dropItemNaturally(player.location().position(), leftover);
    }
    GolemancyRuntime.reveal().ifPresent(reveal -> reveal.requestUpdate(player));
  }

  static void drop(ItemRef item, Loc at) {
    WorldRef world = at.world();
    if (world == null || !AdaptApi.scheduler().runAt(at, () -> world.dropItemNaturally(at.position(), item))) {
      AdaptApi.log().warn("The Golem menu could not drop " + item.amount() + " " + item.type() + " at " + at + "; the item was lost.");
    }
  }

  private static void once(AtomicBoolean claimed, Runnable handOff) {
    if (claimed.compareAndSet(false, true)) {
      handOff.run();
    }
  }
}

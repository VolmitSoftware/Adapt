package art.arcane.adapt.content.adaptation.herbalism;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Herbalist's Luck's roll: a percent chance to drop one random item from a list at a block, with a sparkle. */
public final class HerbalismLuckyDrops {
  private final Adaptation<?> owner;
  private final Consumer<PlayerRef> onLucky;

  /** {@code onLucky} runs before each lucky drop lands. */
  public HerbalismLuckyDrops(Adaptation<?> owner, Consumer<PlayerRef> onLucky) {
    this.owner = owner;
    this.onLucky = onLucky;
  }

  public void roll(PlayerRef p, BlockRef block, double chancePercent, List<Id> choices) {
    double d = ThreadLocalRandom.current().nextDouble(100D);
    Id m = choices.get(ThreadLocalRandom.current().nextInt(choices.size()));
    if (d >= chancePercent) {
      return;
    }

    onLucky.accept(p);
    ItemRef luckDrop = AdaptApi.platform().host().items().create(m, 1);
    block.world().dropItem(block.location().position(), luckDrop);
    HerbalismLuck.luckySparkle(owner, block.location());
  }
}

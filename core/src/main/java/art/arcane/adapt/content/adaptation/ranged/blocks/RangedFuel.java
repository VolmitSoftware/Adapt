package art.arcane.adapt.content.adaptation.ranged.blocks;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.content.adaptation.ranged.RangedProjectiles;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Plain arrows from anywhere in the inventory; Snare and Ricochet Arrows are never taken. */
public class RangedFuel extends FuelBlock<RangedFuel.Config> {
  public RangedFuel() {
    super("block-ranged-fuel");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("arrow"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    return use.player().inventory().containsAtLeast(plainArrow(), getConfig().arrows);
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    int arrows = getConfig().arrows;
    ItemRef unit = plainArrow();
    return use.payer().payItemCost(player, "fuel", unit, arrows,
        () -> player.inventory().removeSimilar(unit, arrows) >= arrows);
  }

  private static ItemRef plainArrow() {
    return AdaptCore.platform().host().items().create(RangedProjectiles.ARROW, 1);
  }

  @ConfigDescription("1 arrow.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Arrows taken per use.", impact = "Higher values cost more arrows.")
    int arrows = 1;
  }
}

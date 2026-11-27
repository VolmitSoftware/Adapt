package art.arcane.adapt.content.adaptation.discovery.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.content.skill.SkillDiscovery;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;

/** One paper, or failing that one empty map, from anywhere in the inventory. */
public class DiscoveryFuel extends FuelBlock<DiscoveryFuel.Config> {
  private static final List<Id> CHARTS = List.of(Id.minecraft("paper"), Id.minecraft("map"));

  public DiscoveryFuel() {
    super(SkillDiscovery.ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("paper"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    return chart(use.player()) != null;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    ItemRef chart = chart(player);
    if (chart == null) {
      return false;
    }
    ItemRef unit = chart.copy();
    unit.setAmount(1);
    return use.payer().payItemCost(player, "fuel", unit, 1,
        () -> player.inventory().removeItem(unit.type(), 1) == 1);
  }

  private static ItemRef chart(PlayerRef player) {
    for (Id type : CHARTS) {
      for (ItemRef item : player.inventory().contents()) {
        if (item != null && !item.isEmpty() && item.type().equals(type)) {
          return item;
        }
      }
    }
    return null;
  }

  @ConfigDescription("1 paper or map.")
  public static class Config extends MutationBlock.Config {
  }
}

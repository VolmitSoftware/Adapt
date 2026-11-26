package art.arcane.adapt.content.adaptation.golemancy.blocks;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Nuggets from anywhere in the inventory, copper first; copper nuggets only exist on versions that have them. */
public class GolemancyFuel extends FuelBlock<GolemancyFuel.Config> {
  private static final List<Id> NUGGETS = List.of(Id.minecraft("copper_nugget"), Id.minecraft("iron_nugget"));

  public GolemancyFuel() {
    super("block-golemancy-fuel");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("iron_nugget"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    return nugget(use.player().inventory(), getConfig().amount) != null;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    int amount = getConfig().amount;
    Id nugget = nugget(player.inventory(), amount);
    if (nugget == null) {
      return false;
    }
    return use.payer().payItemCost(player, "fuel", AdaptCore.platform().host().items().create(nugget), amount,
        () -> player.inventory().removeItem(nugget, amount) >= amount);
  }

  private static @Nullable Id nugget(PlayerInventoryRef inventory, int amount) {
    for (Id nugget : NUGGETS) {
      if (inventory.contains(nugget, amount)) {
        return nugget;
      }
    }
    return null;
  }

  @ConfigDescription("1 copper or iron nugget.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Nuggets taken per use.", impact = "Higher values cost more metal.")
    int amount = 1;
  }
}

package art.arcane.adapt.content.adaptation.herbalism.blocks;

import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismGrowables;
import art.arcane.adapt.content.adaptation.herbalism.HerbalismSeedSower;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.LinkedHashMap;
import java.util.Map;

/** Seeds and crop produce from anywhere in the inventory, any mix of them. */
public class HerbalismFuel extends FuelBlock<HerbalismFuel.Config> {
  public HerbalismFuel() {
    super("block-herbalism-fuel");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("wheat_seeds"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    int held = 0;
    for (int count : fuel(use.player().inventory()).values()) {
      held += count;
    }
    return held >= getConfig().amount;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    Map<Id, Integer> fuel = fuel(player.inventory());
    if (fuel.isEmpty()) {
      return false;
    }
    ItemRef unit = null;
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && !item.isEmpty() && fuel.containsKey(item.type())) {
        unit = item.copy();
        unit.setAmount(1);
        break;
      }
    }
    int amount = getConfig().amount;
    AbilityDefaultCost take = () -> {
      int remaining = amount;
      for (Map.Entry<Id, Integer> entry : fuel.entrySet()) {
        if (remaining <= 0) {
          break;
        }
        remaining -= player.inventory().removeItem(entry.getKey(), Math.min(remaining, entry.getValue()));
      }
      return remaining <= 0;
    };
    return use.payer().payItemCost(player, "fuel", unit, amount, take);
  }

  private static Map<Id, Integer> fuel(PlayerInventoryRef inventory) {
    Map<Id, Integer> counts = new LinkedHashMap<>();
    for (ItemRef item : inventory.contents()) {
      if (item == null || item.isEmpty()) {
        continue;
      }
      Id type = item.type();
      if (HerbalismSeedSower.cropType(type) != null || HerbalismGrowables.isProduce(type)) {
        counts.merge(type, item.amount(), Integer::sum);
      }
    }
    return counts;
  }

  @ConfigDescription("3 seeds or crops.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Seeds or crops taken per use.", impact = "Higher values cost more of the harvest.")
    int amount = 3;
  }
}

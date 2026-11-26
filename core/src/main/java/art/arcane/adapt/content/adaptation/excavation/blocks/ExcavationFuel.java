package art.arcane.adapt.content.adaptation.excavation.blocks;

import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Dirt, sand and gravel from anywhere in the inventory, any mix of them. */
public class ExcavationFuel extends FuelBlock<ExcavationFuel.Config> {
  private static final Set<Id> SOIL = Set.of(Id.minecraft("dirt"), Id.minecraft("sand"), Id.minecraft("red_sand"),
      Id.minecraft("gravel"));

  public ExcavationFuel() {
    super("block-excavation-fuel");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("dirt"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    int held = 0;
    for (int count : soil(use.player().inventory()).values()) {
      held += count;
    }
    return held >= getConfig().amount;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    Map<Id, Integer> soil = soil(player.inventory());
    if (soil.isEmpty()) {
      return false;
    }
    ItemRef unit = null;
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && !item.isEmpty() && soil.containsKey(item.type())) {
        unit = item.copy();
        unit.setAmount(1);
        break;
      }
    }
    int amount = getConfig().amount;
    AbilityDefaultCost take = () -> {
      int remaining = amount;
      for (Map.Entry<Id, Integer> entry : soil.entrySet()) {
        if (remaining <= 0) {
          break;
        }
        remaining -= player.inventory().removeItem(entry.getKey(), Math.min(remaining, entry.getValue()));
      }
      return remaining <= 0;
    };
    return use.payer().payItemCost(player, "fuel", unit, amount, take);
  }

  private static Map<Id, Integer> soil(PlayerInventoryRef inventory) {
    Map<Id, Integer> counts = new LinkedHashMap<>();
    for (ItemRef item : inventory.contents()) {
      if (item != null && !item.isEmpty() && SOIL.contains(item.type())) {
        counts.merge(item.type(), item.amount(), Integer::sum);
      }
    }
    return counts;
  }

  @ConfigDescription("8 dirt, sand or gravel.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Dirt, sand or gravel taken per use.", impact = "Higher values cost more soil.")
    int amount = 8;
  }
}

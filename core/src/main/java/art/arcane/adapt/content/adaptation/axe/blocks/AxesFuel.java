package art.arcane.adapt.content.adaptation.axe.blocks;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.ComponentPort;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Durability from the axe in the main hand; never breaks the axe. */
public class AxesFuel extends FuelBlock<AxesFuel.Config> {
  public AxesFuel() {
    super("block-axes-fuel");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("grindstone"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    ItemRef axe = use.player().inventory().mainHand();
    return isAxe(axe) && !axe.isUnbreakable() && axe.maxDamage() - axe.damage() > getConfig().durability;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    int durability = getConfig().durability;
    return use.payer().payDurabilityCost(player, "fuel", durability, () -> {
      AdaptPorts.get(ComponentPort.class, ComponentPort.NONE).damageItem(player, EquipmentSlot.MAIN_HAND, durability);
      return true;
    });
  }

  @ConfigDescription("5 durability from your axe.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Durability taken from the axe.", impact = "Higher values wear the axe faster.")
    int durability = 5;
  }
}

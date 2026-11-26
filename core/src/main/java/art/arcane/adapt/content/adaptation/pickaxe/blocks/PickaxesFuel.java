package art.arcane.adapt.content.adaptation.pickaxe.blocks;

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

/** Durability from the pickaxe in the main hand; never breaks the pickaxe. */
public class PickaxesFuel extends FuelBlock<PickaxesFuel.Config> {
  public PickaxesFuel() {
    super("block-pickaxe-fuel");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("grindstone"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    ItemRef pickaxe = use.player().inventory().mainHand();
    return isPickaxe(pickaxe) && !pickaxe.isUnbreakable() && pickaxe.maxDamage() - pickaxe.damage() > getConfig().durability;
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

  @ConfigDescription("5 durability from your pickaxe.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Durability taken from the pickaxe.", impact = "Higher values wear the pickaxe faster.")
    int durability = 5;
  }
}

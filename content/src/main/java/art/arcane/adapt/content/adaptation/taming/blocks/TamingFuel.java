package art.arcane.adapt.content.adaptation.taming.blocks;

import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.FuelBlock;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.content.adaptation.taming.TamePackCommand;
import art.arcane.adapt.content.skill.SkillTaming;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Set;

/** A bone or a piece of raw or cooked meat from anywhere in the inventory. */
public class TamingFuel extends FuelBlock<TamingFuel.Config> {
  private static final Set<Id> MEAT = Set.of(Id.minecraft("beef"), Id.minecraft("cooked_beef"),
      Id.minecraft("porkchop"), Id.minecraft("cooked_porkchop"), Id.minecraft("mutton"), Id.minecraft("cooked_mutton"),
      Id.minecraft("chicken"), Id.minecraft("cooked_chicken"), Id.minecraft("rabbit"), Id.minecraft("cooked_rabbit"),
      Id.minecraft("rotten_flesh"));

  public TamingFuel() {
    super(SkillTaming.ID);
    registerConfiguration(Config.class);
    setIcon(TamePackCommand.BONE);
  }

  @Override
  public boolean canPay(BlockUse use) {
    return fuel(use.player()) != null;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    ItemRef held = fuel(player);
    if (held == null) {
      return false;
    }
    ItemRef unit = held.copy();
    unit.setAmount(1);
    int amount = getConfig().amount;
    return use.payer().payItemCost(player, "fuel", unit, amount,
        () -> player.inventory().removeItem(unit.type(), amount) >= amount);
  }

  private ItemRef fuel(PlayerRef player) {
    for (ItemRef item : player.inventory().contents()) {
      if (item != null && !item.isEmpty() && (TamePackCommand.BONE.equals(item.type()) || MEAT.contains(item.type()))
          && player.inventory().contains(item.type(), getConfig().amount)) {
        return item;
      }
    }
    return null;
  }

  @ConfigDescription("1 bone or piece of meat.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Bones or pieces of meat taken per use, all of one kind.", impact = "Higher values cost more.")
    int amount = 1;
  }
}

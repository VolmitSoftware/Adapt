package art.arcane.adapt.content.adaptation.architect.blocks;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.mutation.engine.BlockUse;
import art.arcane.adapt.api.mutation.engine.FuelBlock;
import art.arcane.adapt.api.mutation.engine.MutationBlock;
import art.arcane.adapt.content.adaptation.architect.ArchitectDemolition;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;

/** Blocks from the stack in the main hand; a block that carries its contents is never spent. */
public class ArchitectFuel extends FuelBlock<ArchitectFuel.Config> {
  public ArchitectFuel() {
    super("block-architect-fuel");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("stone_bricks"));
  }

  @Override
  public boolean canPay(BlockUse use) {
    ItemRef hand = use.player().inventory().mainHand();
    return isItem(hand) && AdaptCore.platform().host().items().isBlock(hand.type())
        && !ArchitectDemolition.keepsContentsInItem(hand.type()) && hand.amount() >= getConfig().amount;
  }

  @Override
  public boolean pay(BlockUse use) {
    PlayerRef player = use.player();
    ItemRef hand = player.inventory().mainHand();
    int amount = getConfig().amount;
    ItemRef unit = hand.copy();
    unit.setAmount(1);
    return use.payer().payItemCost(player, "fuel", unit, amount, () -> {
      ItemRef current = player.inventory().mainHand();
      if (!current.type().equals(unit.type()) || current.amount() < amount) {
        return false;
      }
      current.setAmount(current.amount() - amount);
      player.inventory().setMainHand(current);
      return true;
    });
  }

  @ConfigDescription("4 blocks from your hand.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "Blocks taken from the held stack per use.", impact = "Higher values cost more blocks.")
    int amount = 4;
  }
}

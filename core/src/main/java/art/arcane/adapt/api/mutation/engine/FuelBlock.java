package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.platform.entity.PlayerRef;

/**
 * What a skill pays with, for a Barter or for an upgrade's overcharge. {@link #pay} charges through the payer's cost
 * helpers ({@code use.payer().payItemCost(...)} and the like) so the ability API sees the cost; never charge through
 * {@code use.adaptation()}, which is not the payer during an overcharge.
 */
public abstract class FuelBlock<C extends MutationBlock.Config> extends MutationBlock<C> {
  protected FuelBlock(String id) {
    super(id, BlockSlot.FUEL);
  }

  public abstract boolean canPay(BlockUse use);

  /** Takes the cost; false when it could not be taken after all. */
  public abstract boolean pay(BlockUse use);

  /** Charges {@code hunger} food points through the payer, so the ability API sees the cost. */
  protected static boolean payHunger(BlockUse use, int hunger) {
    PlayerRef player = use.player();
    return use.payer().payHungerCost(player, "fuel", hunger, () -> {
      player.setFoodLevel(Math.max(0, player.foodLevel() - hunger));
      return true;
    });
  }
}

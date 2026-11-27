package art.arcane.adapt.api.mutation;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

/**
 * What a skill pays with, for a Barter or for an upgrade's overcharge. {@link #pay} charges through the payer's
 * cost helpers ({@code use.payer().payItemCost(...)} and the like) so ability cost providers see the cost. Never
 * charge through {@code use.adaptation()}, which is not the payer during an overcharge.
 */
@ApiStatus.Experimental
public abstract non-sealed class FuelBlock<C extends MutationBlock.Config> extends MutationBlock<C> {
  /**
   * @param skill the skill this block belongs to
   */
  protected FuelBlock(Id skill) {
    super(skill, BlockSlot.FUEL);
  }

  /**
   * Whether the player can afford the cost now, without taking it. The engine asks every Fuel of a run first; one
   * false and nothing runs or is paid.
   */
  public abstract boolean canPay(BlockUse use);

  /** Takes the cost; false when it could not be taken after all, which stops the run before any Strike or Boon. */
  public abstract boolean pay(BlockUse use);

  /**
   * Charges {@code hunger} food points through the payer under the cost key {@code fuel}; the default cost lowers
   * the food level, never below 0. False when the cost was refused.
   */
  protected static boolean payHunger(BlockUse use, int hunger) {
    PlayerRef player = use.player();
    return use.payer().payHungerCost(player, "fuel", hunger, () -> {
      player.setFoodLevel(Math.max(0, player.foodLevel() - hunger));
      return true;
    });
  }
}

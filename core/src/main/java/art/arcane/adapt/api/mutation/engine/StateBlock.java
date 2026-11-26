package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.api.mutation.blocks.BlockSlot;
import art.arcane.adapt.platform.entity.PlayerRef;

/**
 * The condition a skill cares about. Overrides of some Convergences ask for the State at any point in the last few
 * seconds instead of right now ("you blocked a hit with your shield in the last 4 s"); a State used that way overrides
 * {@link #heldWithin}, and a registry test fails when one that needs to does not.
 */
public abstract class StateBlock<C extends MutationBlock.Config> extends MutationBlock<C> {
  protected StateBlock(String id) {
    super(id, BlockSlot.STATE);
  }

  public abstract boolean holds(PlayerRef player);

  public boolean heldWithin(PlayerRef player, long windowMillis) {
    return holds(player);
  }
}

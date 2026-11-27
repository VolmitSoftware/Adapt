package art.arcane.adapt.api.mutation;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

/**
 * The condition a skill cares about. A pair's text may ask for the State at any point in the last few seconds
 * instead of right now ("you blocked a hit in the last 4 s"); a State that can be asked that way overrides
 * {@link #heldWithin}.
 */
@ApiStatus.Experimental
public abstract non-sealed class StateBlock<C extends MutationBlock.Config> extends MutationBlock<C> {
  /**
   * @param skill the skill this block belongs to
   */
  protected StateBlock(Id skill) {
    super(skill, BlockSlot.STATE);
  }

  /** Whether the condition holds for the player right now. */
  public abstract boolean holds(PlayerRef player);

  /** Whether the State held at any point in the last {@code windowMillis}; by default {@link #holds} now. */
  public boolean heldWithin(PlayerRef player, long windowMillis) {
    return holds(player);
  }
}

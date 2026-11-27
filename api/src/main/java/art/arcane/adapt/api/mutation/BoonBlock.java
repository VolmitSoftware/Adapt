package art.arcane.adapt.api.mutation;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

/** What a skill gives the player, usually for a few seconds. */
@ApiStatus.Experimental
public abstract non-sealed class BoonBlock<C extends MutationBlock.Config> extends MutationBlock<C> {
  /**
   * @param skill the skill this block belongs to
   */
  protected BoonBlock(Id skill) {
    super(skill, BlockSlot.BOON);
  }

  /** Gives the Boon to {@code use.player()} and reports what it did. */
  public abstract Outcome grant(BlockUse use);
}

package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.api.mutation.blocks.BlockSlot;

/**
 * What a skill does to an enemy (the fight form, {@code use.target()} set) or to the blocks, crops or drops around the
 * player (the work form, {@code use.at()} set). A Strike that pays out (drops, XP, healing, repairs) overrides
 * {@link #paysOut()}, and the engine then skips it whenever the action that set it off was farmed.
 */
public abstract class StrikeBlock<C extends MutationBlock.Config> extends MutationBlock<C> {
  protected StrikeBlock(String id) {
    super(id, BlockSlot.STRIKE_FIGHT, BlockSlot.STRIKE_WORK);
  }

  public abstract Outcome strike(BlockUse use);

  public boolean paysOut() {
    return false;
  }
}

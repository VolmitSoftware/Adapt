package art.arcane.adapt.api.mutation.engine;

import art.arcane.adapt.api.mutation.blocks.BlockSlot;

/** What a skill gives the player, usually for a few seconds. */
public abstract class BoonBlock<C extends MutationBlock.Config> extends MutationBlock<C> {
  protected BoonBlock(String id) {
    super(id, BlockSlot.BOON);
  }

  public abstract Outcome grant(BlockUse use);
}

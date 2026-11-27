package art.arcane.adapt.api.mutation;

import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

/**
 * What a skill does to an enemy (the fight form, {@code use.target()} set) or to the blocks, crops or drops around
 * the player (the work form, {@code use.at()} set). A kit has one of each.
 */
@ApiStatus.Experimental
public abstract non-sealed class StrikeBlock<C extends MutationBlock.Config> extends MutationBlock<C> {
  /**
   * @param skill the skill this block belongs to
   * @param form  {@link StrikeForm#FIGHT} or {@link StrikeForm#WORK}
   * @throws IllegalArgumentException for {@link StrikeForm#EITHER} or {@link StrikeForm#NONE}
   */
  protected StrikeBlock(Id skill, StrikeForm form) {
    super(skill, slot(form));
  }

  /** Runs the Strike at {@code use.target()} (fight form) or around {@code use.at()} (work form). */
  public abstract Outcome strike(BlockUse use);

  /**
   * Whether the Strike gives items, XP, healing, repairs or growth. The engine skips a Strike that pays out
   * whenever the action that set it off was farmed.
   */
  public boolean paysOut() {
    return false;
  }

  private static BlockSlot slot(StrikeForm form) {
    return switch (form) {
      case FIGHT -> BlockSlot.STRIKE_FIGHT;
      case WORK -> BlockSlot.STRIKE_WORK;
      case EITHER, NONE -> throw new IllegalArgumentException("A Strike is the fight form or the work form");
    };
  }
}

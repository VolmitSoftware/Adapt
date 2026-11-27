package art.arcane.adapt.engine.mutation.blocks;

import art.arcane.adapt.api.mutation.BlockKey;
import art.arcane.adapt.api.mutation.MutationPrice;
import art.arcane.adapt.api.mutation.MutationSlot;
import art.arcane.adapt.api.mutation.Recipe;
import art.arcane.adapt.api.mutation.SkillPair;
import art.arcane.adapt.api.mutation.StrikeForm;

import java.util.List;

/**
 * One of the five adaptations a pair owns, with the blocks it is built from; empty blocks when a signature replaces
 * it. A State block holds when it holds now, or, with {@code stateWindowSeconds} above 0, at any point in that many
 * seconds before.
 */
public record MutatedAdaptation(MutationSlot slot, List<BlockKey> blocks, StrikeForm form, int stateWindowSeconds) {
  public MutatedAdaptation {
    blocks = List.copyOf(blocks);
  }

  public SkillPair pair() {
    return slot.pair();
  }

  public Recipe recipe() {
    return slot.recipe();
  }

  public MutationPrice price() {
    return slot.recipe().price();
  }
}

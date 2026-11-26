package art.arcane.adapt.api.mutation.blocks;

import java.util.List;

/**
 * One of the five adaptations a (lead, partner) mutation owns, with the blocks it was built from. A State block holds
 * when it holds now, or, with {@code stateWindowSeconds} above 0, at any point in that many seconds before.
 */
public record MutatedAdaptation(String leadId, String partnerId, Recipe recipe, String name, String description,
                                List<BlockRef> blocks, StrikeForm form, int stateWindowSeconds) {
  public MutatedAdaptation {
    blocks = List.copyOf(blocks);
  }

  public MutationPrice price() {
    return recipe.price();
  }
}

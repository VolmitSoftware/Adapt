package art.arcane.adapt.api.mutation;

import org.jetbrains.annotations.ApiStatus;

import java.util.Locale;
import java.util.Objects;

/**
 * One of a mutation's five adaptations: the pair and the recipe. Signatures and pair text are registered per slot.
 *
 * @param pair   the two skills of the mutation, lead first
 * @param recipe which of the five adaptations this is
 */
@ApiStatus.Experimental
public record MutationSlot(SkillPair pair, Recipe recipe) {
  public MutationSlot {
    Objects.requireNonNull(pair, "pair");
    Objects.requireNonNull(recipe, "recipe");
  }

  /** The key of this adaptation inside its mutated skill line: the recipe name in lower case. */
  public String adaptationKey() {
    return recipe.name().toLowerCase(Locale.ROOT);
  }

  @Override
  public String toString() {
    return pair.key() + "#" + adaptationKey();
  }
}

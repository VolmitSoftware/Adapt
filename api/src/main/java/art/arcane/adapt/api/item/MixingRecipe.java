package art.arcane.adapt.api.item;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * A potion mix an adaptation lets its learners brew, as recipe views show it: from {@code level} on, {@code bottles}
 * bottles of {@code bottle} under {@code ingredient} brew into one mixed potion called {@code name}.
 *
 * @param level      the adaptation level that unlocks this mix
 * @param bottle     the potion item the bottles must be
 * @param bottles    how many bottles the mix takes
 * @param ingredient the item in the top slot
 * @param name       the display name of the mixed potion
 * @param color      the colour of the mixed potion
 */
@ApiStatus.Experimental
public record MixingRecipe(int level, Id bottle, int bottles, Id ingredient, String name, Rgb color) {
  public MixingRecipe {
    Objects.requireNonNull(bottle, "bottle");
    Objects.requireNonNull(ingredient, "ingredient");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(color, "color");
    if (level < 1 || bottles < 2) {
      throw new IllegalArgumentException("A mix needs a level of at least 1 and at least 2 bottles");
    }
  }
}

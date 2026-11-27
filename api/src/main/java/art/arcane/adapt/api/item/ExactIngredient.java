package art.arcane.adapt.api.item;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A custom item a recipe slot takes: any item of {@code base} that {@code matches} accepts, so a custom item never
 * stands in for the plain material it is made from, and the plain material never stands in for it.
 *
 * @param base the material every accepted item has
 * @param matches whether an item of {@code base} is this custom item
 */
@ApiStatus.Experimental
public record ExactIngredient(Id base, Predicate<ItemRef> matches) {
  /** Rejects a null base or test. */
  public ExactIngredient {
    Objects.requireNonNull(base);
    Objects.requireNonNull(matches);
  }
}

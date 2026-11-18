package art.arcane.adapt.api.recipe;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.Objects;
import java.util.function.Predicate;

public record ExactIngredient(Id base, Predicate<ItemRef> matches) {
  public ExactIngredient {
    Objects.requireNonNull(base);
    Objects.requireNonNull(matches);
  }
}

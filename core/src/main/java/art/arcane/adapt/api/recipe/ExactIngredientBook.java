package art.arcane.adapt.api.recipe;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ExactIngredientBook {
  private final Map<Id, List<ExactIngredient>> required = new ConcurrentHashMap<>();

  public void set(Id recipeKey, List<ExactIngredient> ingredients) {
    required.put(Objects.requireNonNull(recipeKey), List.copyOf(ingredients));
  }

  public void forget(Id recipeKey) {
    required.remove(Objects.requireNonNull(recipeKey));
  }

  public boolean satisfiedBy(Id recipeKey, List<ItemRef> items) {
    List<ExactIngredient> ingredients = recipeKey == null ? null : required.get(recipeKey);
    return ingredients != null && MultisetMatch.matches(ingredients, items, (ingredient, item) -> ingredient.matches().test(item));
  }
}

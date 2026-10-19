package art.arcane.adapt.api.recipe;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ExactIngredientBook {
  private final Map<NamespacedKey, List<ExactIngredient>> required = new ConcurrentHashMap<>();

  public void set(NamespacedKey recipeKey, List<ExactIngredient> ingredients) {
    required.put(Objects.requireNonNull(recipeKey), List.copyOf(ingredients));
  }

  public void forget(NamespacedKey recipeKey) {
    required.remove(Objects.requireNonNull(recipeKey));
  }

  public boolean satisfiedBy(NamespacedKey recipeKey, List<ItemStack> items) {
    List<ExactIngredient> ingredients = recipeKey == null ? null : required.get(recipeKey);
    return ingredients != null && MultisetMatch.matches(ingredients, items, (ingredient, item) -> ingredient.matches().test(item));
  }
}

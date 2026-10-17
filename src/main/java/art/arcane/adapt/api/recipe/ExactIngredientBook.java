package art.arcane.adapt.api.recipe;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ExactIngredientBook {
  private final Map<NamespacedKey, List<ExactIngredient>> required = new ConcurrentHashMap<>();

  public void set(NamespacedKey recipeKey, List<ExactIngredient> ingredients) {
    Objects.requireNonNull(recipeKey);
    if (ingredients.isEmpty()) {
      required.remove(recipeKey);
      return;
    }
    required.put(recipeKey, List.copyOf(ingredients));
  }

  public List<ExactIngredient> required(NamespacedKey recipeKey) {
    return recipeKey == null ? List.of() : required.getOrDefault(recipeKey, List.of());
  }

  public boolean satisfiedBy(NamespacedKey recipeKey, List<ItemStack> items) {
    return new Matching(required(recipeKey), items).complete();
  }

  private static final class Matching {
    private final List<ExactIngredient> ingredients;
    private final List<ItemStack> items;
    private final int[] itemByIngredient;

    private Matching(List<ExactIngredient> ingredients, List<ItemStack> items) {
      this.ingredients = ingredients;
      this.items = items;
      this.itemByIngredient = new int[ingredients.size()];
      Arrays.fill(itemByIngredient, -1);
    }

    private boolean complete() {
      if (ingredients.size() != items.size()) {
        return false;
      }
      for (int item = 0; item < items.size(); item++) {
        if (!assign(item, new boolean[ingredients.size()])) {
          return false;
        }
      }
      return true;
    }

    private boolean assign(int item, boolean[] visited) {
      for (int ingredient = 0; ingredient < ingredients.size(); ingredient++) {
        if (visited[ingredient] || !ingredients.get(ingredient).matches().test(items.get(item))) {
          continue;
        }
        visited[ingredient] = true;
        int holder = itemByIngredient[ingredient];
        if (holder < 0 || assign(holder, visited)) {
          itemByIngredient[ingredient] = item;
          return true;
        }
      }
      return false;
    }
  }
}

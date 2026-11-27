package art.arcane.adapt.content.golem.item;

import art.arcane.adapt.api.item.ExactIngredient;
import art.arcane.adapt.api.item.ShapedRecipeParser;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** The golem items each Golemancy recipe takes, so a craft is allowed only with exactly those items. */
public final class ExactIngredientBook {
  private final Map<Id, List<ExactIngredient>> required = new ConcurrentHashMap<>();

  /**
   * Parses {@code spec} under {@code recipeKey} and records the golem items it takes; a disabled or broken spec
   * forgets the key, so its recipe fails closed.
   */
  public Optional<RecipeSpec.Shaped> register(ShapedRecipeParser parser, Id recipeKey, ConfiguredShapedRecipe spec) {
    Optional<ShapedRecipeParser.Result> parsed = parser.recipe(recipeKey, spec.toConfig());
    if (parsed.isEmpty()) {
      forget(recipeKey);
      return Optional.empty();
    }
    set(recipeKey, parsed.get().exactIngredients());
    return Optional.of(parsed.get().draft().key(recipeKey));
  }

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

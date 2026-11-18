package art.arcane.adapt.platform.recipe;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Recipe definitions Adapt registers. Keys are recipe ids, for example {@code adapt:elevator}. The required level is
 * the adaptation level a player needs before the recipe enters their recipe book and before a crafting grid lets them
 * take the result; it is at least 1. Constructors without a level use 1.
 */
public sealed interface RecipeSpec {
  Id key();

  ItemRef result();

  int requiredLevel();

  /** The same recipe with another required level. */
  RecipeSpec withRequiredLevel(int level);

  private static int level(int requiredLevel) {
    return Math.max(1, requiredLevel);
  }

  record Shaped(Id key, List<String> shape, Map<Character, Ingredient> ingredients, ItemRef result, int requiredLevel) implements RecipeSpec {
    public Shaped {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(result, "result");
      shape = List.copyOf(shape);
      ingredients = Map.copyOf(ingredients);
      if (shape.isEmpty() || shape.size() > 3) {
        throw new IllegalArgumentException("shape needs 1..3 rows");
      }
      requiredLevel = level(requiredLevel);
    }

    public Shaped(Id key, List<String> shape, Map<Character, Ingredient> ingredients, ItemRef result) {
      this(key, shape, ingredients, result, 1);
    }

    @Override
    public Shaped withRequiredLevel(int level) {
      return new Shaped(key, shape, ingredients, result, level);
    }
  }

  record Shapeless(Id key, List<Ingredient> ingredients, ItemRef result, int requiredLevel) implements RecipeSpec {
    public Shapeless {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(result, "result");
      ingredients = List.copyOf(ingredients);
      if (ingredients.isEmpty() || ingredients.size() > 9) {
        throw new IllegalArgumentException("shapeless recipe needs 1..9 ingredients");
      }
      requiredLevel = level(requiredLevel);
    }

    public Shapeless(Id key, List<Ingredient> ingredients, ItemRef result) {
      this(key, ingredients, result, 1);
    }

    @Override
    public Shapeless withRequiredLevel(int level) {
      return new Shapeless(key, ingredients, result, level);
    }
  }

  /** Furnace, smoker, blast furnace and campfire recipes. */
  record Cooking(Id key, CookingStation station, Ingredient input, ItemRef result, float experience, int cookTicks,
                 int requiredLevel) implements RecipeSpec {
    public Cooking {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(station, "station");
      Objects.requireNonNull(input, "input");
      Objects.requireNonNull(result, "result");
      requiredLevel = level(requiredLevel);
    }

    public Cooking(Id key, CookingStation station, Ingredient input, ItemRef result, float experience, int cookTicks) {
      this(key, station, input, result, experience, cookTicks, 1);
    }

    @Override
    public Cooking withRequiredLevel(int level) {
      return new Cooking(key, station, input, result, experience, cookTicks, level);
    }
  }

  record Stonecutting(Id key, Ingredient input, ItemRef result, int requiredLevel) implements RecipeSpec {
    public Stonecutting {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(input, "input");
      Objects.requireNonNull(result, "result");
      requiredLevel = level(requiredLevel);
    }

    public Stonecutting(Id key, Ingredient input, ItemRef result) {
      this(key, input, result, 1);
    }

    @Override
    public Stonecutting withRequiredLevel(int level) {
      return new Stonecutting(key, input, result, level);
    }
  }

  /**
   * Smithing table recipe. A null template registers the two-slot form (base plus addition) that Adapt's
   * smithing recipes use; item-type ingredients then match the plain item exactly.
   */
  record Smithing(Id key, Ingredient template, Ingredient base, Ingredient addition, ItemRef result, int requiredLevel) implements RecipeSpec {
    public Smithing {
      Objects.requireNonNull(key, "key");
      Objects.requireNonNull(base, "base");
      Objects.requireNonNull(result, "result");
      requiredLevel = level(requiredLevel);
    }

    public Smithing(Id key, Ingredient template, Ingredient base, Ingredient addition, ItemRef result) {
      this(key, template, base, addition, result, 1);
    }

    @Override
    public Smithing withRequiredLevel(int level) {
      return new Smithing(key, template, base, addition, result, level);
    }
  }

  enum CookingStation {
    FURNACE,
    SMOKER,
    BLAST_FURNACE,
    CAMPFIRE
  }
}

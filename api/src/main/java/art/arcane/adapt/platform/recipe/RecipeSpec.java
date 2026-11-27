package art.arcane.adapt.platform.recipe;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Recipe definitions Adapt registers. Keys are recipe ids, for example {@code adapt:elevator}. The required level is
 * the adaptation level a player needs before the recipe enters their recipe book and before a crafting grid lets them
 * take the result; it is at least 1. The factories make recipes with level 1; {@link #withRequiredLevel} changes it.
 */
@ApiStatus.AvailableSince("1.0.0")
public sealed interface RecipeSpec {
  /** The recipe id, for example {@code adapt:elevator}. */
  Id key();

  /** The crafted item. */
  ItemRef result();

  /** The adaptation level the player needs, at least 1. */
  int requiredLevel();

  /** The same recipe with another required level. */
  RecipeSpec withRequiredLevel(int level);

  /**
   * A crafting grid recipe with a fixed pattern. {@code shape} holds 1 to 3 rows; each character names an entry in
   * {@code ingredients}, and a space is an empty cell.
   */
  static Shaped shaped(Id key, List<String> shape, Map<Character, Ingredient> ingredients, ItemRef result) {
    return new Shaped(key, shape, ingredients, result, 1);
  }

  /** A crafting grid recipe that takes 1 to 9 ingredients in any arrangement. */
  static Shapeless shapeless(Id key, List<Ingredient> ingredients, ItemRef result) {
    return new Shapeless(key, ingredients, result, 1);
  }

  /**
   * A furnace, smoker, blast furnace or campfire recipe. {@code experience} is the experience the result gives and
   * {@code cookTicks} the cooking time in ticks.
   */
  static Cooking cooking(Id key, CookingStation station, Ingredient input, ItemRef result, float experience,
                         int cookTicks) {
    return new Cooking(key, station, input, result, experience, cookTicks, 1);
  }

  /** A stonecutter recipe with one input. */
  static Stonecutting stonecutting(Id key, Ingredient input, ItemRef result) {
    return new Stonecutting(key, input, result, 1);
  }

  /**
   * A smithing table recipe. A null template registers the two-slot form (base plus addition) that Adapt's smithing
   * recipes use; item-type ingredients then match the plain item exactly. {@code addition} may be null.
   */
  static Smithing smithing(Id key, Ingredient template, Ingredient base, Ingredient addition, ItemRef result) {
    return new Smithing(key, template, base, addition, result, 1);
  }

  private static int level(int requiredLevel) {
    return Math.max(1, requiredLevel);
  }

  /** A crafting grid recipe with a fixed pattern; see {@link #shaped}. */
  final class Shaped implements RecipeSpec {
    private final Id key;
    private final List<String> shape;
    private final Map<Character, Ingredient> ingredients;
    private final ItemRef result;
    private final int requiredLevel;

    private Shaped(Id key, List<String> shape, Map<Character, Ingredient> ingredients, ItemRef result,
                   int requiredLevel) {
      this.key = Objects.requireNonNull(key, "key");
      this.result = Objects.requireNonNull(result, "result");
      this.shape = List.copyOf(shape);
      this.ingredients = Map.copyOf(ingredients);
      if (this.shape.isEmpty() || this.shape.size() > 3) {
        throw new IllegalArgumentException("shape needs 1..3 rows, " + key + " has " + this.shape.size());
      }
      this.requiredLevel = level(requiredLevel);
    }

    @Override
    public Id key() {
      return key;
    }

    /** The 1 to 3 rows of the pattern. */
    public List<String> shape() {
      return shape;
    }

    /** The ingredient each pattern character names. */
    public Map<Character, Ingredient> ingredients() {
      return ingredients;
    }

    @Override
    public ItemRef result() {
      return result;
    }

    @Override
    public int requiredLevel() {
      return requiredLevel;
    }

    @Override
    public Shaped withRequiredLevel(int level) {
      return new Shaped(key, shape, ingredients, result, level);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Shaped spec && key.equals(spec.key) && shape.equals(spec.shape)
          && ingredients.equals(spec.ingredients) && result.equals(spec.result) && requiredLevel == spec.requiredLevel;
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, shape, ingredients, result, requiredLevel);
    }

    @Override
    public String toString() {
      return "Shaped[key=" + key + ", shape=" + shape + ", ingredients=" + ingredients + ", result=" + result
          + ", requiredLevel=" + requiredLevel + "]";
    }
  }

  /** A crafting grid recipe of 1 to 9 ingredients in any arrangement; see {@link #shapeless}. */
  final class Shapeless implements RecipeSpec {
    private final Id key;
    private final List<Ingredient> ingredients;
    private final ItemRef result;
    private final int requiredLevel;

    private Shapeless(Id key, List<Ingredient> ingredients, ItemRef result, int requiredLevel) {
      this.key = Objects.requireNonNull(key, "key");
      this.result = Objects.requireNonNull(result, "result");
      this.ingredients = List.copyOf(ingredients);
      if (this.ingredients.isEmpty() || this.ingredients.size() > 9) {
        throw new IllegalArgumentException("shapeless recipe needs 1..9 ingredients, " + key + " has "
            + this.ingredients.size());
      }
      this.requiredLevel = level(requiredLevel);
    }

    @Override
    public Id key() {
      return key;
    }

    /** The 1 to 9 ingredients. */
    public List<Ingredient> ingredients() {
      return ingredients;
    }

    @Override
    public ItemRef result() {
      return result;
    }

    @Override
    public int requiredLevel() {
      return requiredLevel;
    }

    @Override
    public Shapeless withRequiredLevel(int level) {
      return new Shapeless(key, ingredients, result, level);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Shapeless spec && key.equals(spec.key) && ingredients.equals(spec.ingredients)
          && result.equals(spec.result) && requiredLevel == spec.requiredLevel;
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, ingredients, result, requiredLevel);
    }

    @Override
    public String toString() {
      return "Shapeless[key=" + key + ", ingredients=" + ingredients + ", result=" + result + ", requiredLevel="
          + requiredLevel + "]";
    }
  }

  /** A furnace, smoker, blast furnace or campfire recipe; see {@link #cooking}. */
  final class Cooking implements RecipeSpec {
    private final Id key;
    private final CookingStation station;
    private final Ingredient input;
    private final ItemRef result;
    private final float experience;
    private final int cookTicks;
    private final int requiredLevel;

    private Cooking(Id key, CookingStation station, Ingredient input, ItemRef result, float experience, int cookTicks,
                    int requiredLevel) {
      this.key = Objects.requireNonNull(key, "key");
      this.station = Objects.requireNonNull(station, "station");
      this.input = Objects.requireNonNull(input, "input");
      this.result = Objects.requireNonNull(result, "result");
      this.experience = experience;
      this.cookTicks = cookTicks;
      this.requiredLevel = level(requiredLevel);
    }

    @Override
    public Id key() {
      return key;
    }

    /** The block that runs the recipe. */
    public CookingStation station() {
      return station;
    }

    /** The cooked input. */
    public Ingredient input() {
      return input;
    }

    @Override
    public ItemRef result() {
      return result;
    }

    /** The experience the result gives. */
    public float experience() {
      return experience;
    }

    /** The cooking time in ticks. */
    public int cookTicks() {
      return cookTicks;
    }

    @Override
    public int requiredLevel() {
      return requiredLevel;
    }

    @Override
    public Cooking withRequiredLevel(int level) {
      return new Cooking(key, station, input, result, experience, cookTicks, level);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Cooking spec && key.equals(spec.key) && station == spec.station
          && input.equals(spec.input) && result.equals(spec.result) && Float.compare(experience, spec.experience) == 0
          && cookTicks == spec.cookTicks && requiredLevel == spec.requiredLevel;
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, station, input, result, experience, cookTicks, requiredLevel);
    }

    @Override
    public String toString() {
      return "Cooking[key=" + key + ", station=" + station + ", input=" + input + ", result=" + result
          + ", experience=" + experience + ", cookTicks=" + cookTicks + ", requiredLevel=" + requiredLevel + "]";
    }
  }

  /** A stonecutter recipe with one input; see {@link #stonecutting}. */
  final class Stonecutting implements RecipeSpec {
    private final Id key;
    private final Ingredient input;
    private final ItemRef result;
    private final int requiredLevel;

    private Stonecutting(Id key, Ingredient input, ItemRef result, int requiredLevel) {
      this.key = Objects.requireNonNull(key, "key");
      this.input = Objects.requireNonNull(input, "input");
      this.result = Objects.requireNonNull(result, "result");
      this.requiredLevel = level(requiredLevel);
    }

    @Override
    public Id key() {
      return key;
    }

    /** The cut input. */
    public Ingredient input() {
      return input;
    }

    @Override
    public ItemRef result() {
      return result;
    }

    @Override
    public int requiredLevel() {
      return requiredLevel;
    }

    @Override
    public Stonecutting withRequiredLevel(int level) {
      return new Stonecutting(key, input, result, level);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Stonecutting spec && key.equals(spec.key) && input.equals(spec.input)
          && result.equals(spec.result) && requiredLevel == spec.requiredLevel;
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, input, result, requiredLevel);
    }

    @Override
    public String toString() {
      return "Stonecutting[key=" + key + ", input=" + input + ", result=" + result + ", requiredLevel="
          + requiredLevel + "]";
    }
  }

  /** A smithing table recipe; see {@link #smithing}. */
  final class Smithing implements RecipeSpec {
    private final Id key;
    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final ItemRef result;
    private final int requiredLevel;

    private Smithing(Id key, Ingredient template, Ingredient base, Ingredient addition, ItemRef result,
                     int requiredLevel) {
      this.key = Objects.requireNonNull(key, "key");
      this.base = Objects.requireNonNull(base, "base");
      this.result = Objects.requireNonNull(result, "result");
      this.template = template;
      this.addition = addition;
      this.requiredLevel = level(requiredLevel);
    }

    @Override
    public Id key() {
      return key;
    }

    /** The template, or null for the two-slot form. */
    public Ingredient template() {
      return template;
    }

    /** The item upgraded. */
    public Ingredient base() {
      return base;
    }

    /** The material added, or null. */
    public Ingredient addition() {
      return addition;
    }

    @Override
    public ItemRef result() {
      return result;
    }

    @Override
    public int requiredLevel() {
      return requiredLevel;
    }

    @Override
    public Smithing withRequiredLevel(int level) {
      return new Smithing(key, template, base, addition, result, level);
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof Smithing spec && key.equals(spec.key) && Objects.equals(template, spec.template)
          && base.equals(spec.base) && Objects.equals(addition, spec.addition) && result.equals(spec.result)
          && requiredLevel == spec.requiredLevel;
    }

    @Override
    public int hashCode() {
      return Objects.hash(key, template, base, addition, result, requiredLevel);
    }

    @Override
    public String toString() {
      return "Smithing[key=" + key + ", template=" + template + ", base=" + base + ", addition=" + addition
          + ", result=" + result + ", requiredLevel=" + requiredLevel + "]";
    }
  }

  /**
   * The block that runs a {@link Cooking} recipe.
   * A {@code switch} over this enum needs a default branch: minor releases add constants.
   */
  enum CookingStation {
    FURNACE,
    SMOKER,
    BLAST_FURNACE,
    CAMPFIRE
  }
}

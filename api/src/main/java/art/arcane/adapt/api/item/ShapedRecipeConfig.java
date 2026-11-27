package art.arcane.adapt.api.item;

import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A shaped crafting recipe an admin can rewrite in the config file. Put one in a skill or adaptation config with its
 * defaults, then register what it parses to:
 *
 * <pre>{@code
 * public ShapedRecipeConfig lensRecipe = new ShapedRecipeConfig()
 *     .rows(" G ", "GAG", " G ").key('G', "GLASS_PANE").key('A', "AMETHYST_SHARD").result("SPYGLASS", 1);
 *
 * getConfig().lensRecipe.toRecipe(Id.of("example", "geode_lens")).ifPresent(this::registerRecipe);
 * }</pre>
 */
@ApiStatus.AvailableSince("1.0.0")
public final class ShapedRecipeConfig {
  /** The grid rows, top to bottom, one symbol per slot. */
  @ConfigDoc(value = "Crafting grid rows from top to bottom.", impact = "Use 1 to 3 rows of the same width, at most 3 characters each. Each character is a symbol from keys; a space leaves that slot empty.")
  public List<String> rows = new ArrayList<>();
  /** The ingredient each symbol stands for: a material name or an item tag starting with {@code #}. */
  @ConfigDoc(value = "Ingredient for each row symbol.", impact = "Accepts a material name such as AMETHYST_SHARD or an item tag such as #planks or #minecraft:planks.")
  public Map<String, String> keys = new LinkedHashMap<>();
  /** The material name of the crafted item. */
  @ConfigDoc(value = "Item this recipe crafts.", impact = "Accepts a material name such as SHIELD.")
  public String result = "";
  /** How many result items one craft yields, 1 to 64. */
  @ConfigDoc(value = "Number of result items one craft yields.", impact = "Accepts 1 to 64.")
  public int amount = 1;
  /** Whether the recipe is registered; when false, {@link #toRecipe(Id)} is empty. */
  @ConfigDoc(value = "Registers this recipe.", impact = "Set to false to remove the recipe from crafting.")
  public boolean enabled = true;

  /** Sets the default grid rows, replacing any set before. */
  public ShapedRecipeConfig rows(String... values) {
    rows = new ArrayList<>(List.of(values));
    return this;
  }

  /** Sets the default ingredient of one row symbol. */
  public ShapedRecipeConfig key(char symbol, String ingredient) {
    keys.put(String.valueOf(symbol), Objects.requireNonNull(ingredient, "ingredient"));
    return this;
  }

  /** Sets the default result item and how many one craft yields. */
  public ShapedRecipeConfig result(String item, int count) {
    result = Objects.requireNonNull(item, "item");
    amount = count;
    return this;
  }

  /**
   * The recipe under {@code key}, or empty when it is disabled or does not parse; each problem is logged as a
   * warning naming the key.
   */
  public Optional<RecipeSpec.Shaped> toRecipe(Id key) {
    Objects.requireNonNull(key, "key");
    return ShapedRecipeParser.platform(ExactItemResolver.NONE).recipe(key, this).map((ShapedRecipeParser.Result parsed) -> parsed.draft().key(key));
  }
}

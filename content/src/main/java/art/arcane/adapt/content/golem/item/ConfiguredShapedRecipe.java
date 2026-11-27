package art.arcane.adapt.content.golem.item;

import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.item.ShapedRecipeConfig;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A Golemancy recipe in a config file; unlike a plain {@link ShapedRecipeConfig}, it may name golem items. */
public class ConfiguredShapedRecipe {
  @ConfigDoc(value = "Crafting grid rows from top to bottom.", impact = "Use 1 to 3 rows of the same width, at most 3 characters each. Each character is a symbol from keys; a space leaves that slot empty.")
  private List<String> rows = new ArrayList<>();
  @ConfigDoc(value = "Ingredient for each row symbol.", impact = "Accepts a material name such as AMETHYST_SHARD, an item tag such as #planks or #minecraft:planks, or a namespaced custom item such as namespace:item.")
  private Map<String, String> keys = new LinkedHashMap<>();
  @ConfigDoc(value = "Item this recipe crafts.", impact = "Accepts a material name such as SHIELD or a namespaced custom item such as namespace:item.")
  private String result = "";
  @ConfigDoc(value = "Number of result items one craft yields.", impact = "Accepts 1 to 64.")
  private int amount = 1;
  @ConfigDoc(value = "Registers this recipe.", impact = "Set to false to remove the recipe from crafting.")
  private boolean enabled = true;

  public List<String> getRows() {
    return rows;
  }

  public Map<String, String> getKeys() {
    return keys;
  }

  public String getResult() {
    return result;
  }

  public int getAmount() {
    return amount;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /** This recipe as the public config type the parser reads. */
  public ShapedRecipeConfig toConfig() {
    ShapedRecipeConfig config = new ShapedRecipeConfig();
    config.rows = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
    config.keys = keys == null ? new LinkedHashMap<>() : new LinkedHashMap<>(keys);
    config.result = result;
    config.amount = amount;
    config.enabled = enabled;
    return config;
  }

  public ConfiguredShapedRecipe rows(String... values) {
    rows = new ArrayList<>(List.of(values));
    return this;
  }

  public ConfiguredShapedRecipe key(char symbol, String ingredient) {
    keys.put(String.valueOf(symbol), ingredient);
    return this;
  }

  public ConfiguredShapedRecipe result(String item, int count) {
    result = item;
    amount = count;
    return this;
  }
}

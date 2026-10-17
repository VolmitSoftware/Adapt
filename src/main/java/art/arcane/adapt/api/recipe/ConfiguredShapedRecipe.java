package art.arcane.adapt.api.recipe;

import art.arcane.adapt.util.config.ConfigDoc;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class ConfiguredShapedRecipe {
  @ConfigDoc(value = "Crafting grid rows from top to bottom.", impact = "Use 1 to 3 rows of the same width, at most 3 characters each. Each character is a symbol from keys; a space leaves that slot empty.")
  private List<String> rows = new ArrayList<>();
  @ConfigDoc(value = "Ingredient for each row symbol.", impact = "Accepts a material name such as AMETHYST_SHARD, an item tag such as #planks or #minecraft:planks, or a namespaced custom item such as namespace:item.")
  private Map<String, String> keys = new LinkedHashMap<>();
  @ConfigDoc(value = "Item this recipe crafts.", impact = "Accepts a material name such as SHIELD or a namespaced custom item such as namespace:item.")
  private String result = "";
  @ConfigDoc(value = "Number of result items one craft yields.", impact = "Accepts 1 to 64.")
  private int amount = 1;
  @ConfigDoc(value = "Experience levels charged for each craft.", impact = "0 makes the recipe free. Players without enough levels see no result, shift-crafting stops at the number of crafts their levels cover, creative players craft free, and crafter blocks cannot craft a recipe with a tithe.")
  private int tithe = 0;
  @ConfigDoc(value = "Registers this recipe.", impact = "Set to false to remove the recipe from crafting.")
  private boolean enabled = true;

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

  public ConfiguredShapedRecipe tithe(int levels) {
    tithe = levels;
    return this;
  }
}

package art.arcane.adapt.api.recipe;

import art.arcane.adapt.Adapt;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ConfiguredRecipeParser {
  private static final int MAX_GRID_SIZE = 3;
  private static final int MAX_AMOUNT = 64;

  private final RecipeItemCatalog catalog;
  private final ExactItemResolver exactItems;

  public ConfiguredRecipeParser(RecipeItemCatalog catalog, ExactItemResolver exactItems) {
    this.catalog = Objects.requireNonNull(catalog);
    this.exactItems = Objects.requireNonNull(exactItems);
  }

  public static ConfiguredRecipeParser bukkit(ExactItemResolver exactItems) {
    return new ConfiguredRecipeParser(new BukkitItemCatalog(), exactItems);
  }

  public Optional<AdaptRecipe.Shaped.ShapedBuilder> builder(String id, ConfiguredShapedRecipe spec) {
    if (!spec.isEnabled()) {
      return Optional.empty();
    }

    Result result = parse(spec);
    for (String problem : result.problems()) {
      Adapt.warn("Skipping configured recipe " + id + ": " + problem);
    }
    return Optional.ofNullable(result.builder());
  }

  public Result parse(ConfiguredShapedRecipe spec) {
    List<String> problems = new ArrayList<>();
    List<String> rows = spec.getRows() == null ? List.of() : spec.getRows();
    Map<String, String> keys = spec.getKeys() == null ? Map.of() : spec.getKeys();
    validateRows(rows, problems);
    Map<Character, MaterialChar> ingredients = resolveKeys(keys, problems);
    validateSymbols(rows, keys, problems);
    ResultItem result = resolveResult(spec.getResult(), problems);
    validateAmount(spec.getAmount(), problems);
    validateTithe(spec.getTithe(), problems);
    if (!problems.isEmpty()) {
      return new Result(null, List.copyOf(problems));
    }

    AdaptRecipe.Shaped.ShapedBuilder builder = AdaptRecipe.shaped()
        .shapes(rows)
        .result(result.create(spec.getAmount()));
    for (char symbol : symbols(rows)) {
      builder.ingredient(ingredients.get(symbol));
    }
    return new Result(builder, List.of());
  }

  private static void validateRows(List<String> rows, List<String> problems) {
    if (rows.isEmpty() || rows.size() > MAX_GRID_SIZE) {
      problems.add("rows must list 1 to 3 rows, found " + rows.size());
      return;
    }

    boolean widthsValid = true;
    for (int i = 0; i < rows.size(); i++) {
      int width = width(rows.get(i));
      if (width < 1 || width > MAX_GRID_SIZE) {
        problems.add("row " + (i + 1) + " must be 1 to 3 characters wide, found " + width);
        widthsValid = false;
      }
    }
    if (!widthsValid) {
      return;
    }

    int firstWidth = width(rows.getFirst());
    boolean uniform = true;
    for (int i = 1; i < rows.size(); i++) {
      int width = width(rows.get(i));
      if (width != firstWidth) {
        problems.add("row " + (i + 1) + " is " + width + " wide but row 1 is " + firstWidth + " wide");
        uniform = false;
      }
    }
    if (uniform && symbols(rows).isEmpty()) {
      problems.add("rows contain no ingredient symbols");
    }
  }

  private Map<Character, MaterialChar> resolveKeys(Map<String, String> keys, List<String> problems) {
    Map<Character, MaterialChar> ingredients = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : keys.entrySet()) {
      String symbolText = entry.getKey();
      if (symbolText == null || symbolText.length() != 1 || symbolText.charAt(0) == ' ') {
        problems.add("key \"" + symbolText + "\" must be a single non-space character");
        continue;
      }

      char symbol = symbolText.charAt(0);
      String ingredient = entry.getValue() == null ? "" : entry.getValue().trim();
      if (ingredient.isEmpty()) {
        problems.add("key '" + symbol + "' has no ingredient");
        continue;
      }

      MaterialChar resolved = resolveIngredient(symbol, ingredient, problems);
      if (resolved != null) {
        ingredients.put(symbol, resolved);
      }
    }
    return ingredients;
  }

  private MaterialChar resolveIngredient(char symbol, String ingredient, List<String> problems) {
    String subject = "key '" + symbol + "'";
    if (ingredient.startsWith("#")) {
      Tag<Material> tag = resolveTag(subject, ingredient, problems);
      return tag == null ? null : new MaterialChar(symbol, tag);
    }
    if (ingredient.indexOf(':') >= 0) {
      ItemStack item = resolveExactItem(subject, ingredient, problems);
      return item == null ? null : new MaterialChar(symbol, item);
    }

    Material material = resolveMaterial(subject, ingredient, problems);
    return material == null ? null : new MaterialChar(symbol, material);
  }

  private static void validateSymbols(List<String> rows, Map<String, String> keys, List<String> problems) {
    for (char symbol : symbols(rows)) {
      if (!keys.containsKey(String.valueOf(symbol))) {
        problems.add("symbol '" + symbol + "' in rows has no key");
      }
    }
  }

  private ResultItem resolveResult(String configured, List<String> problems) {
    String raw = configured == null ? "" : configured.trim();
    if (raw.isEmpty()) {
      problems.add("result is empty");
      return null;
    }
    if (raw.startsWith("#")) {
      problems.add("result \"" + raw + "\" must be a material or a namespaced item, not a tag");
      return null;
    }
    if (raw.indexOf(':') >= 0) {
      ItemStack item = resolveExactItem("result", raw, problems);
      return item == null ? null : new ExactResult(item);
    }

    Material material = resolveMaterial("result", raw, problems);
    return material == null ? null : new MaterialResult(material);
  }

  private Tag<Material> resolveTag(String subject, String raw, List<String> problems) {
    NamespacedKey key = namespacedKey(raw.substring(1));
    if (key == null) {
      problems.add(subject + " tag \"" + raw + "\" is not a valid namespaced id");
      return null;
    }

    Tag<Material> tag = catalog.itemTag(key);
    if (tag == null) {
      problems.add(subject + " tag \"" + raw + "\" is not a known item tag");
      return null;
    }
    if (tag.getValues().isEmpty()) {
      problems.add(subject + " tag \"" + raw + "\" contains no items");
      return null;
    }
    return tag;
  }

  private ItemStack resolveExactItem(String subject, String raw, List<String> problems) {
    NamespacedKey key = namespacedKey(raw);
    if (key == null) {
      problems.add(subject + " item \"" + raw + "\" is not a valid namespaced id");
      return null;
    }

    ItemStack item = exactItems.resolve(key);
    if (item == null) {
      problems.add(subject + " item \"" + raw + "\" is not a known item");
    }
    return item;
  }

  private Material resolveMaterial(String subject, String raw, List<String> problems) {
    Material material = catalog.itemMaterial(raw.toUpperCase(Locale.ROOT));
    if (material == null) {
      problems.add(subject + " material \"" + raw + "\" is not a known item");
    }
    return material;
  }

  private static void validateAmount(int amount, List<String> problems) {
    if (amount < 1 || amount > MAX_AMOUNT) {
      problems.add("amount must be 1 to 64, found " + amount);
    }
  }

  private static void validateTithe(int tithe, List<String> problems) {
    if (tithe < 0) {
      problems.add("tithe must be 0 or more levels, found " + tithe);
    }
  }

  private static Set<Character> symbols(List<String> rows) {
    Set<Character> symbols = new LinkedHashSet<>();
    for (String row : rows) {
      if (row == null) {
        continue;
      }
      for (int i = 0; i < row.length(); i++) {
        char symbol = row.charAt(i);
        if (symbol != ' ') {
          symbols.add(symbol);
        }
      }
    }
    return symbols;
  }

  private static int width(String row) {
    return row == null ? 0 : row.length();
  }

  private static NamespacedKey namespacedKey(String text) {
    try {
      return NamespacedKey.fromString(text.trim().toLowerCase(Locale.ROOT));
    } catch (IllegalArgumentException invalid) {
      return null;
    }
  }

  public record Result(AdaptRecipe.Shaped.ShapedBuilder builder, List<String> problems) {
  }

  private sealed interface ResultItem permits MaterialResult, ExactResult {
    ItemStack create(int amount);
  }

  private record MaterialResult(Material material) implements ResultItem {
    @Override
    public ItemStack create(int amount) {
      return new ItemStack(material, amount);
    }
  }

  private record ExactResult(ItemStack item) implements ResultItem {
    @Override
    public ItemStack create(int amount) {
      ItemStack copy = item.clone();
      copy.setAmount(amount);
      return copy;
    }
  }

  private static final class BukkitItemCatalog implements RecipeItemCatalog {
    @Override
    public Material itemMaterial(String name) {
      Material material = Material.getMaterial(name);
      if (material == null || material.isLegacy() || material.isAir() || !material.isItem()) {
        return null;
      }
      return material;
    }

    @Override
    public Tag<Material> itemTag(NamespacedKey key) {
      return Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
    }
  }
}

package art.arcane.adapt.api.recipe;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.value.MaterialNames;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;

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
  private static final String RECIPE_NAMESPACE = Id.ADAPT;
  private static final String MINECRAFT_PREFIX = "minecraft:";
  private static final int MAX_GRID_SIZE = 3;
  private static final int MAX_AMOUNT = 64;

  private final RecipeItemCatalog catalog;
  private final ExactItemResolver exactItems;
  private final ExactIngredientBook book;

  public ConfiguredRecipeParser(RecipeItemCatalog catalog, ExactItemResolver exactItems, ExactIngredientBook book) {
    this.catalog = Objects.requireNonNull(catalog);
    this.exactItems = Objects.requireNonNull(exactItems);
    this.book = Objects.requireNonNull(book);
  }

  /** A parser that resolves item names, tags and result items through the running platform. */
  public static ConfiguredRecipeParser platform(ExactItemResolver exactItems, ExactIngredientBook book) {
    return new ConfiguredRecipeParser(new PlatformItemCatalog(), exactItems, book);
  }

  public Optional<RecipeSpec.Shaped> builder(Id recipeKey, ConfiguredShapedRecipe spec) {
    Objects.requireNonNull(recipeKey);
    if (!RECIPE_NAMESPACE.equals(recipeKey.namespace())) {
      throw new IllegalArgumentException("Configured recipe " + recipeKey + " must use the " + RECIPE_NAMESPACE + " namespace that Adapt registers recipes under");
    }
    if (!spec.isEnabled()) {
      book.forget(recipeKey);
      return Optional.empty();
    }

    Result result = parse(spec);
    for (String problem : result.problems()) {
      AdaptLog.warn("Skipping configured recipe " + recipeKey + ": " + problem);
    }
    if (result.draft() == null) {
      book.forget(recipeKey);
      return Optional.empty();
    }
    book.set(recipeKey, result.exactIngredients());
    return Optional.of(result.draft().key(recipeKey));
  }

  public Result parse(ConfiguredShapedRecipe spec) {
    List<String> problems = new ArrayList<>();
    List<String> rows = spec.getRows() == null ? List.of() : spec.getRows();
    Map<String, String> keys = spec.getKeys() == null ? Map.of() : spec.getKeys();
    validateRows(rows, problems);
    Map<Character, Parsed> ingredients = resolveKeys(keys, problems);
    validateSymbols(rows, keys, problems);
    ResultItem result = resolveResult(spec.getResult(), problems);
    validateAmount(spec.getAmount(), problems);
    if (result != null && result.maxStack() > 0 && spec.getAmount() > result.maxStack()) {
      problems.add("amount " + spec.getAmount() + " is more than the result stacks to, " + result.maxStack());
    }
    if (!problems.isEmpty()) {
      return new Result(null, List.of(), List.copyOf(problems));
    }

    Map<Character, Ingredient> used = new LinkedHashMap<>();
    for (char symbol : symbols(rows)) {
      used.put(symbol, ingredients.get(symbol).ingredient());
    }
    return new Result(new Draft(rows, used, result.create(spec.getAmount())), exactIngredients(rows, ingredients), List.of());
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

  private Map<Character, Parsed> resolveKeys(Map<String, String> keys, List<String> problems) {
    Map<Character, Parsed> ingredients = new LinkedHashMap<>();
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

      Parsed resolved = resolveIngredient(symbol, ingredient, problems);
      if (resolved != null) {
        ingredients.put(symbol, resolved);
      }
    }
    return ingredients;
  }

  private Parsed resolveIngredient(char symbol, String ingredient, List<String> problems) {
    String subject = "key '" + symbol + "'";
    if (ingredient.startsWith("#")) {
      Set<Id> tag = resolveTag(subject, ingredient, problems);
      return tag == null ? null : new Parsed(new Ingredient.AnyOf(tag), null);
    }
    if (ingredient.indexOf(':') >= 0 && !ingredient.startsWith(MINECRAFT_PREFIX)) {
      return resolveExactIngredient(subject, ingredient, problems);
    }

    Id material = resolveMaterial(subject, withoutMinecraftPrefix(ingredient), problems);
    return material == null ? null : new Parsed(Ingredient.of(material), null);
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
    if (raw.indexOf(':') >= 0 && !raw.startsWith(MINECRAFT_PREFIX)) {
      ItemRef item = resolveExactResult(raw, problems);
      return item == null ? null : new ExactResult(item);
    }

    Id material = resolveMaterial("result", withoutMinecraftPrefix(raw), problems);
    return material == null ? null : new MaterialResult(catalog, material);
  }

  private Set<Id> resolveTag(String subject, String raw, List<String> problems) {
    Id key = namespacedKey(raw.substring(1));
    if (key == null) {
      problems.add(subject + " tag \"" + raw + "\" is not a valid namespaced id");
      return null;
    }

    Set<Id> tag = catalog.itemTag(key);
    if (tag == null) {
      problems.add(subject + " tag \"" + raw + "\" is not a known item tag");
      return null;
    }
    if (tag.isEmpty()) {
      problems.add(subject + " tag \"" + raw + "\" contains no items");
      return null;
    }
    return tag;
  }

  private Parsed resolveExactIngredient(String subject, String raw, List<String> problems) {
    Id key = exactKey(subject, raw, problems);
    if (key == null) {
      return null;
    }

    ExactIngredient ingredient = exactItems.ingredient(key);
    if (ingredient == null) {
      problems.add(subject + " item \"" + raw + "\" is not a known item");
      return null;
    }
    ItemRef example = exactItems.result(key);
    if (example == null || example.isEmpty() || example.type().isAir()) {
      problems.add(subject + " item \"" + raw + "\" has no recipe item");
      return null;
    }
    if (!example.type().equals(ingredient.base())) {
      problems.add(subject + " item \"" + raw + "\" has a recipe item with the wrong material");
      return null;
    }
    ItemRef representative = example.copyWithAmount(1);
    return new Parsed(new Ingredient.Matching(ingredient.base(), representative, ingredient.matches()), ingredient);
  }

  private ItemRef resolveExactResult(String raw, List<String> problems) {
    Id key = exactKey("result", raw, problems);
    if (key == null) {
      return null;
    }

    ItemRef item = exactItems.result(key);
    if (item == null) {
      problems.add("result item \"" + raw + "\" is not a known item");
    }
    return item;
  }

  private static Id exactKey(String subject, String raw, List<String> problems) {
    Id key = namespacedKey(raw);
    if (key == null) {
      problems.add(subject + " item \"" + raw + "\" is not a valid namespaced id");
    }
    return key;
  }

  private static List<ExactIngredient> exactIngredients(List<String> rows, Map<Character, Parsed> ingredients) {
    List<ExactIngredient> exact = new ArrayList<>();
    for (String row : rows) {
      for (int i = 0; i < row.length(); i++) {
        Parsed ingredient = ingredients.get(row.charAt(i));
        if (ingredient != null && ingredient.exact() != null) {
          exact.add(ingredient.exact());
        }
      }
    }
    return exact;
  }

  private static String withoutMinecraftPrefix(String raw) {
    return raw.startsWith(MINECRAFT_PREFIX) ? raw.substring(MINECRAFT_PREFIX.length()) : raw;
  }

  private Id resolveMaterial(String subject, String raw, List<String> problems) {
    Id material = catalog.itemMaterial(raw.toUpperCase(Locale.ROOT));
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

  private static Id namespacedKey(String text) {
    return Id.tryParse(text.trim().toLowerCase(Locale.ROOT));
  }

  public record Result(Draft draft, List<ExactIngredient> exactIngredients, List<String> problems) {
  }

  /** A parsed recipe that still needs its key. */
  public record Draft(List<String> shape, Map<Character, Ingredient> ingredients, ItemRef result) {
    public RecipeSpec.Shaped key(Id key) {
      return new RecipeSpec.Shaped(key, shape, ingredients, result);
    }
  }

  private record Parsed(Ingredient ingredient, ExactIngredient exact) {
  }

  private sealed interface ResultItem permits MaterialResult, ExactResult {
    ItemRef create(int amount);

    int maxStack();
  }

  private record MaterialResult(RecipeItemCatalog catalog, Id material) implements ResultItem {
    @Override
    public ItemRef create(int amount) {
      return catalog.item(material, amount);
    }

    @Override
    public int maxStack() {
      return catalog.maxStackSize(material);
    }
  }

  private record ExactResult(ItemRef item) implements ResultItem {
    @Override
    public ItemRef create(int amount) {
      return item.copyWithAmount(amount);
    }

    @Override
    public int maxStack() {
      return item.maxStackSize();
    }
  }

  private static final class PlatformItemCatalog implements RecipeItemCatalog {
    @Override
    public Id itemMaterial(String name) {
      Id id = MaterialNames.exact(name);
      if (id == null || !id.isMinecraft() || id.isAir() || !AdaptCore.platform().host().registries().exists(RegistryKind.ITEM, id)
          || !AdaptCore.platform().host().items().isItem(id)) {
        return null;
      }
      return id;
    }

    @Override
    public Set<Id> itemTag(Id key) {
      Set<Id> values = AdaptCore.platform().host().registries().tagValues(RegistryKind.ITEM, key);
      return values == null || values.isEmpty() ? null : values;
    }

    @Override
    public ItemRef item(Id type, int amount) {
      return AdaptCore.platform().host().items().create(type, amount);
    }

    @Override
    public int maxStackSize(Id type) {
      return AdaptCore.platform().host().items().maxStackSize(type);
    }
  }
}

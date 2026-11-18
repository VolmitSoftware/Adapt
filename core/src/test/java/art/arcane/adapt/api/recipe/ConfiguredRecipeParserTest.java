package art.arcane.adapt.api.recipe;

import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.util.config.TomlCodec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredRecipeParserTest {
  private static final Id TEST = Id.adapt("test");

  private final Map<String, Id> materials = new HashMap<>();
  private final Map<Id, Integer> maxStacks = new HashMap<>();
  private final Map<Id, Set<Id>> tags = new HashMap<>();
  private final Map<Id, ExactIngredient> exactIngredients = new HashMap<>();
  private final Map<Id, ItemRef> exactResults = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private final ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);

  @Test
  void materialKeysBuildShapedIngredientsAndResult() {
    Id shard = material("AMETHYST_SHARD");
    Id redstone = material("REDSTONE");
    Id pearl = material("ENDER_PEARL");
    Id core = material("HEART_OF_THE_SEA");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("ARA", "RPR", "ARA")
        .key('A', "amethyst_shard")
        .key('R', "REDSTONE")
        .key('P', " ENDER_PEARL ")
        .result("HEART_OF_THE_SEA", 3);

    ConfiguredRecipeParser.Result result = parser.parse(spec);

    assertThat(result.problems()).isEmpty();
    RecipeSpec.Shaped shaped = result.draft().key(TEST);
    assertThat(shaped.shape()).containsExactly("ARA", "RPR", "ARA");
    assertThat(shaped.ingredients().keySet()).containsExactlyInAnyOrder('A', 'R', 'P');
    assertThat(materialChoices(shaped, 'A')).containsExactly(shard);
    assertThat(materialChoices(shaped, 'R')).containsExactly(redstone);
    assertThat(materialChoices(shaped, 'P')).containsExactly(pearl);
    assertThat(shaped.result().type()).isEqualTo(core);
    assertThat(shaped.result().amount()).isEqualTo(3);
  }

  @Test
  void hashKeysResolveItemTagsWithMinecraftAsTheDefaultNamespace() {
    Id oak = material("OAK_PLANKS");
    Id birch = material("BIRCH_PLANKS");
    Set<Id> planks = tag(Id.minecraft("planks"), oak);
    tag(Id.of("example", "woods"), birch);
    material("STICK");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("P", "W")
        .key('P', "#planks")
        .key('W', "#example:woods")
        .result("STICK", 1);

    ConfiguredRecipeParser.Result result = parser.parse(spec);

    assertThat(result.problems()).isEmpty();
    RecipeSpec.Shaped shaped = result.draft().key(TEST);
    assertThat(materialChoices(shaped, 'P')).containsExactlyInAnyOrderElementsOf(planks);
    assertThat(materialChoices(shaped, 'W')).containsExactly(birch);
  }

  @Test
  void namespacedIngredientsKeepTheirCustomStacksAndExactResultsClone() {
    Id star = material("FIREWORK_STAR");
    ExactIngredient blankCore = exactIngredient(Id.of("golemancy", "blank_core"), star);
    ItemRef crook = exactResult(Id.of("golemancy", "crook"), "IRON_HOE");
    material("REDSTONE");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows(" C ", "RCR")
        .key('C', "golemancy:blank_core")
        .key('R', "REDSTONE")
        .result("Golemancy:Crook", 2);

    ConfiguredRecipeParser.Result result = parser.parse(spec);

    assertThat(result.problems()).isEmpty();
    RecipeSpec.Shaped shaped = result.draft().key(TEST);
    assertThat(shaped.ingredients().get('C')).isInstanceOf(Ingredient.Matching.class);
    Ingredient.Matching matching = (Ingredient.Matching) shaped.ingredients().get('C');
    ItemRef representative = matching.example();
    assertThat(representative).isNotSameAs(exactResults.get(Id.of("golemancy", "blank_core")));
    assertThat(representative.type()).isEqualTo(star);
    assertThat(representative.amount()).isEqualTo(1);
    assertThat(matching.base()).isEqualTo(star);
    assertThat(matching.matches()).isSameAs(blankCore.matches());
    assertThat(result.exactIngredients()).containsExactly(blankCore, blankCore);
    assertThat(shaped.result()).isNotSameAs(crook);
    assertThat(shaped.result().type()).isEqualTo(crook.type());
    assertThat(shaped.result().amount()).isEqualTo(2);
  }

  @Test
  void builderRecordsExactIngredientsUnderTheRecipeKeyAndClearsThemWhenDisabled() throws IOException {
    Id recipeKey = Id.adapt("golemancy-guardian-core");
    Id star = material("FIREWORK_STAR");
    ItemRef blank = FakeItem.of("firework_star");
    exactIngredients.put(Id.of("golemancy", "blank_core"), new ExactIngredient(star, item -> item == blank));
    exactResult(Id.of("golemancy", "blank_core"), "FIREWORK_STAR");
    exactResult(Id.of("golemancy", "core/guardian"), "FIREWORK_STAR");
    material("IRON_INGOT");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("IBI")
        .key('B', "golemancy:blank_core")
        .key('I', "IRON_INGOT")
        .result("golemancy:core/guardian", 1);

    Optional<RecipeSpec.Shaped> recipe = parser.builder(recipeKey, spec);

    assertThat(recipe).isPresent();
    assertThat(recipe.get().key()).isEqualTo(Id.adapt("golemancy-guardian-core"));
    assertThat(book.satisfiedBy(recipeKey, List.of(blank))).isTrue();

    ConfiguredShapedRecipe disabled = TomlCodec.fromToml(
        "rows = [\"B\"]\nresult = \"golemancy:core/guardian\"\nenabled = false\n\n[keys]\nB = \"golemancy:blank_core\"\n",
        ConfiguredShapedRecipe.class);
    assertThat(parser.builder(recipeKey, disabled)).isEmpty();
    assertThat(book.satisfiedBy(recipeKey, List.of(blank))).isFalse();
    assertThat(book.satisfiedBy(recipeKey, List.of())).isFalse();
  }

  @Test
  void builderRecordsRecipesWithoutExactIngredientsAsKnown() {
    Id recipeKey = Id.adapt("golemancy-crook");
    material("REDSTONE");
    exactResult(Id.of("golemancy", "crook"), "IRON_HOE");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("R")
        .key('R', "REDSTONE")
        .result("golemancy:crook", 1);

    assertThat(parser.builder(recipeKey, spec)).isPresent();
    assertThat(book.satisfiedBy(recipeKey, List.of())).isTrue();
  }

  @Test
  void builderForgetsInvalidSpecsSoTheirRecipesFailClosed() {
    Id recipeKey = Id.adapt("golemancy-crook");
    material("REDSTONE");
    exactResult(Id.of("golemancy", "crook"), "IRON_HOE");
    parser.builder(recipeKey, new ConfiguredShapedRecipe().rows("R").key('R', "REDSTONE").result("golemancy:crook", 1));

    assertThat(parser.builder(recipeKey, new ConfiguredShapedRecipe().rows("R").key('R', "NOT_A_MATERIAL").result("golemancy:crook", 1))).isEmpty();
    assertThat(book.satisfiedBy(recipeKey, List.of())).isFalse();
  }

  @Test
  void builderRejectsRecipeKeysOutsideTheAdaptNamespace() {
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe().rows("R").key('R', "REDSTONE").result("REDSTONE", 1);

    assertThatThrownBy(() -> parser.builder(Id.of("example", "golemancy-crook"), spec))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("example:golemancy-crook");
  }

  @Test
  void unusedKeysAreNotRegisteredAsIngredients() {
    material("REDSTONE");
    material("STONE");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("RR")
        .key('R', "REDSTONE")
        .key('S', "STONE")
        .result("STONE", 1);

    ConfiguredRecipeParser.Result result = parser.parse(spec);

    assertThat(result.problems()).isEmpty();
    assertThat(result.draft().key(TEST).ingredients().keySet()).containsExactly('R');
  }

  @Test
  void rejectsMissingAndExcessRows() {
    material("STONE");

    assertThat(parser.parse(new ConfiguredShapedRecipe().key('S', "STONE").result("STONE", 1)).problems())
        .containsExactly("rows must list 1 to 3 rows, found 0");
    assertThat(parser.parse(new ConfiguredShapedRecipe().rows("S", "S", "S", "S").key('S', "STONE").result("STONE", 1)).problems())
        .containsExactly("rows must list 1 to 3 rows, found 4");
  }

  @Test
  void rejectsRowsWiderThanThreeOrEmpty() {
    material("STONE");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("SSSS", "")
        .key('S', "STONE")
        .result("STONE", 1);

    assertThat(parser.parse(spec).problems()).containsExactly(
        "row 1 must be 1 to 3 characters wide, found 4",
        "row 2 must be 1 to 3 characters wide, found 0");
  }

  @Test
  void rejectsRowsOfUnequalWidth() {
    material("STONE");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("SSS", "S", "SS")
        .key('S', "STONE")
        .result("STONE", 1);

    assertThat(parser.parse(spec).problems()).containsExactly(
        "row 2 is 1 wide but row 1 is 3 wide",
        "row 3 is 2 wide but row 1 is 3 wide");
  }

  @Test
  void rejectsRowsWithoutIngredients() {
    material("STONE");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("   ")
        .result("STONE", 1);

    assertThat(parser.parse(spec).problems()).containsExactly("rows contain no ingredient symbols");
  }

  @Test
  void rejectsSymbolsWithoutKeysOncePerSymbol() {
    material("STONE");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("SXS", "XYX")
        .key('S', "STONE")
        .result("STONE", 1);

    assertThat(parser.parse(spec).problems()).containsExactly(
        "symbol 'X' in rows has no key",
        "symbol 'Y' in rows has no key");
  }

  @Test
  void rejectsMalformedKeyEntries() {
    material("STONE");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("S")
        .key('S', "STONE")
        .result("STONE", 1);
    spec.getKeys().put("AB", "STONE");
    spec.getKeys().put(" ", "STONE");
    spec.getKeys().put("E", " ");

    assertThat(parser.parse(spec).problems()).containsExactly(
        "key \"AB\" must be a single non-space character",
        "key \" \" must be a single non-space character",
        "key 'E' has no ingredient");
  }

  @Test
  void rejectsUnknownMaterialTagAndExactItemKeys() {
    material("STONE");
    tag(Id.minecraft("empty"));
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("MTE", "NIB")
        .key('M', "NOT_A_MATERIAL")
        .key('T', "#minecraft:unknown")
        .key('E', "#empty")
        .key('N', "golemancy:missing")
        .key('I', "bad id:thing")
        .key('B', "#Bad Tag")
        .result("STONE", 1);

    assertThat(parser.parse(spec).problems()).containsExactly(
        "key 'M' material \"NOT_A_MATERIAL\" is not a known item",
        "key 'T' tag \"#minecraft:unknown\" is not a known item tag",
        "key 'E' tag \"#empty\" contains no items",
        "key 'N' item \"golemancy:missing\" is not a known item",
        "key 'I' item \"bad id:thing\" is not a valid namespaced id",
        "key 'B' tag \"#Bad Tag\" is not a valid namespaced id");
  }

  @Test
  void rejectsUnresolvableResults() {
    material("STONE");

    assertThat(parser.parse(new ConfiguredShapedRecipe().rows("S").key('S', "STONE").result("", 1)).problems())
        .containsExactly("result is empty");
    assertThat(parser.parse(new ConfiguredShapedRecipe().rows("S").key('S', "STONE").result("#planks", 1)).problems())
        .containsExactly("result \"#planks\" must be a material or a namespaced item, not a tag");
    assertThat(parser.parse(new ConfiguredShapedRecipe().rows("S").key('S', "STONE").result("NOT_A_MATERIAL", 1)).problems())
        .containsExactly("result material \"NOT_A_MATERIAL\" is not a known item");
    assertThat(parser.parse(new ConfiguredShapedRecipe().rows("S").key('S', "STONE").result("golemancy:missing", 1)).problems())
        .containsExactly("result item \"golemancy:missing\" is not a known item");
  }

  @Test
  void customIngredientsRequireAnItemRepresentation() {
    Id key = Id.of("golemancy", "blank_core");
    exactIngredient(key, material("FIREWORK_STAR"));
    exactResults.remove(key);
    material("STONE");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe().rows("C").key('C', key.toString()).result("STONE", 1);

    ConfiguredRecipeParser.Result parsed = parser.parse(spec);

    assertThat(parsed.draft()).isNull();
    assertThat(parsed.problems()).containsExactly("key 'C' item \"golemancy:blank_core\" has no recipe item");
  }

  @Test
  void customIngredientRepresentationsMustUseTheirRequiredMaterial() {
    Id key = Id.of("golemancy", "blank_core");
    exactIngredient(key, material("FIREWORK_STAR"));
    exactResult(key, "STONE");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe().rows("C").key('C', key.toString()).result("STONE", 1);

    ConfiguredRecipeParser.Result parsed = parser.parse(spec);

    assertThat(parsed.draft()).isNull();
    assertThat(parsed.problems()).containsExactly("key 'C' item \"golemancy:blank_core\" has a recipe item with the wrong material");
  }

  @Test
  void rejectsAmountsOutsideOneToSixtyFour() {
    material("STONE");

    assertThat(parser.parse(new ConfiguredShapedRecipe().rows("S").key('S', "STONE").result("STONE", 0)).problems())
        .containsExactly("amount must be 1 to 64, found 0");
    assertThat(parser.parse(new ConfiguredShapedRecipe().rows("S").key('S', "STONE").result("STONE", 65)).problems())
        .containsExactly("amount must be 1 to 64, found 65");
    assertThat(parser.parse(new ConfiguredShapedRecipe().rows("S").key('S', "STONE").result("STONE", 64)).problems())
        .isEmpty();
  }

  @Test
  void minecraftPrefixedNamesResolveAsMaterials() {
    Id stone = material("STONE");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe().rows("S").key('S', "minecraft:stone").result("minecraft:stone", 2);

    ConfiguredRecipeParser.Result result = parser.parse(spec);

    assertThat(result.problems()).isEmpty();
    RecipeSpec.Shaped shaped = result.draft().key(TEST);
    assertThat(materialChoices(shaped, 'S')).containsExactly(stone);
    assertThat(shaped.result().type()).isEqualTo(stone);
    assertThat(shaped.result().amount()).isEqualTo(2);
  }

  @Test
  void rejectsAmountsAboveTheResultsStackSize() {
    Id pearl = material("ENDER_PEARL");
    maxStacks.put(pearl, 16);

    assertThat(parser.parse(new ConfiguredShapedRecipe().rows("P").key('P', "ENDER_PEARL").result("ENDER_PEARL", 17)).problems())
        .containsExactly("amount 17 is more than the result stacks to, 16");
    assertThat(parser.parse(new ConfiguredShapedRecipe().rows("P").key('P', "ENDER_PEARL").result("ENDER_PEARL", 16)).problems())
        .isEmpty();
  }

  @Test
  void invalidSpecsProduceNoBuilderAndReportEveryProblem() {
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("XY")
        .result("NOT_A_MATERIAL", 0);

    ConfiguredRecipeParser.Result result = parser.parse(spec);

    assertThat(result.draft()).isNull();
    assertThat(result.problems()).containsExactly(
        "symbol 'X' in rows has no key",
        "symbol 'Y' in rows has no key",
        "result material \"NOT_A_MATERIAL\" is not a known item",
        "amount must be 1 to 64, found 0");
  }

  @Test
  void disabledSpecsAreSkipped() throws IOException {
    material("STONE");
    ConfiguredShapedRecipe spec = TomlCodec.fromToml(
        "rows = [\"S\"]\nresult = \"STONE\"\nenabled = false\n\n[keys]\nS = \"STONE\"\n",
        ConfiguredShapedRecipe.class);

    assertThat(parser.parse(spec).problems()).isEmpty();
    assertThat(parser.builder(Id.adapt("disabled"), spec)).isEmpty();
  }

  @Test
  void configuredRecipesTakeTheirLevelThroughTheSpec() {
    material("REDSTONE");
    Optional<RecipeSpec.Shaped> recipe = parser.builder(Id.adapt("leveled"),
        new ConfiguredShapedRecipe().rows("R").key('R', "REDSTONE").result("REDSTONE", 1));

    assertThat(recipe).isPresent();
    assertThat(recipe.get().requiredLevel()).isEqualTo(1);
    assertThat(recipe.get().withRequiredLevel(3).requiredLevel()).isEqualTo(3);
    assertThat(recipe.get().withRequiredLevel(3).key()).isEqualTo(Id.adapt("leveled"));
  }

  private Id material(String name) {
    Id id = Id.minecraft(name.toLowerCase(Locale.ROOT));
    materials.put(name, id);
    return id;
  }

  private Set<Id> tag(Id key, Id... values) {
    Set<Id> tag = Set.of(values);
    tags.put(key, tag);
    return tag;
  }

  private ExactIngredient exactIngredient(Id key, Id base) {
    ExactIngredient ingredient = new ExactIngredient(base, item -> false);
    exactIngredients.put(key, ingredient);
    exactResult(key, base.path().toUpperCase(Locale.ROOT));
    return ingredient;
  }

  private ItemRef exactResult(Id key, String materialName) {
    Id material = materials.containsKey(materialName) ? materials.get(materialName) : material(materialName);
    ItemRef item = new FakeItem(material, 1);
    exactResults.put(key, item);
    return item;
  }

  private static Set<Id> materialChoices(RecipeSpec.Shaped shaped, char symbol) {
    Ingredient choice = shaped.ingredients().get(symbol);
    assertThat(choice).isInstanceOf(Ingredient.AnyOf.class);
    return ((Ingredient.AnyOf) choice).types();
  }

  private final class FakeResolver implements ExactItemResolver {
    @Override
    public ExactIngredient ingredient(Id id) {
      return exactIngredients.get(id);
    }

    @Override
    public ItemRef result(Id id) {
      return exactResults.get(id);
    }
  }

  private final class FakeCatalog implements RecipeItemCatalog {
    @Override
    public Id itemMaterial(String name) {
      return materials.get(name);
    }

    @Override
    public Set<Id> itemTag(Id key) {
      return tags.get(key);
    }

    @Override
    public ItemRef item(Id type, int amount) {
      return new FakeItem(type, amount);
    }

    @Override
    public int maxStackSize(Id type) {
      return maxStacks.getOrDefault(type, 0);
    }
  }
}

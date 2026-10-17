package art.arcane.adapt.api.recipe;

import art.arcane.adapt.util.config.TomlCodec;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.when;

class ConfiguredRecipeParserTest {
  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, Tag<Material>> tags = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> exactItems = new HashMap<>();
  private ConfiguredRecipeParser parser;
  private MockedConstruction<ItemStack> constructedItems;

  @BeforeEach
  void setUp() {
    parser = new ConfiguredRecipeParser(new FakeCatalog(), exactItems::get);
    constructedItems = mockConstruction(ItemStack.class, (item, context) -> {
      when(item.getType()).thenReturn((Material) context.arguments().getFirst());
      when(item.getAmount()).thenReturn((Integer) context.arguments().get(1));
    });
  }

  @AfterEach
  void tearDown() {
    constructedItems.close();
  }

  @Test
  void materialKeysBuildShapedIngredientsAndResult() {
    Material shard = material("AMETHYST_SHARD");
    Material redstone = material("REDSTONE");
    Material pearl = material("ENDER_PEARL");
    Material core = material("HEART_OF_THE_SEA");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("ARA", "RPR", "ARA")
        .key('A', "amethyst_shard")
        .key('R', "REDSTONE")
        .key('P', " ENDER_PEARL ")
        .result("HEART_OF_THE_SEA", 3)
        .tithe(1);

    ConfiguredRecipeParser.Result result = parser.parse(spec);

    assertThat(result.problems()).isEmpty();
    AdaptRecipe.Shaped shaped = result.builder().key("test").build();
    assertThat(shaped.getShapes()).containsExactly("ARA", "RPR", "ARA");
    assertThat(shaped.getIngredients()).extracting(MaterialChar::getCharacter).containsExactly('A', 'R', 'P');
    assertThat(materialChoices(shaped, 'A')).containsExactly(shard);
    assertThat(materialChoices(shaped, 'R')).containsExactly(redstone);
    assertThat(materialChoices(shaped, 'P')).containsExactly(pearl);
    assertThat(shaped.getResult().getType()).isSameAs(core);
    assertThat(shaped.getResult().getAmount()).isEqualTo(3);
  }

  @Test
  void hashKeysResolveItemTagsWithMinecraftAsTheDefaultNamespace() {
    Material oak = material("OAK_PLANKS");
    Material birch = material("BIRCH_PLANKS");
    Tag<Material> planks = tag(new NamespacedKey("minecraft", "planks"), oak);
    tag(new NamespacedKey("example", "woods"), birch);
    material("STICK");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("P", "W")
        .key('P', "#planks")
        .key('W', "#example:woods")
        .result("STICK", 1);

    ConfiguredRecipeParser.Result result = parser.parse(spec);

    assertThat(result.problems()).isEmpty();
    AdaptRecipe.Shaped shaped = result.builder().key("test").build();
    assertThat(materialChoices(shaped, 'P')).containsExactlyElementsOf(planks.getValues());
    assertThat(materialChoices(shaped, 'W')).containsExactly(birch);
  }

  @Test
  void namespacedKeysResolveExactItemsForIngredientsAndResult() {
    ItemStack blankCore = exactItem(new NamespacedKey("golemancy", "blank_core"), "AMETHYST_SHARD");
    ItemStack crook = exactItem(new NamespacedKey("golemancy", "crook"), "IRON_HOE");
    ItemStack crookCopy = countedItem();
    when(crook.clone()).thenReturn(crookCopy);
    material("REDSTONE");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows(" C ", "RCR")
        .key('C', "golemancy:blank_core")
        .key('R', "REDSTONE")
        .result("Golemancy:Crook", 2);

    ConfiguredRecipeParser.Result result = parser.parse(spec);

    assertThat(result.problems()).isEmpty();
    AdaptRecipe.Shaped shaped = result.builder().key("test").build();
    RecipeChoice choice = choice(shaped, 'C');
    assertThat(choice).isInstanceOf(RecipeChoice.ExactChoice.class);
    assertThat(((RecipeChoice.ExactChoice) choice).getChoices()).containsExactly(blankCore);
    assertThat(shaped.getResult()).isSameAs(crookCopy);
    assertThat(shaped.getResult().getAmount()).isEqualTo(2);
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
    assertThat(result.builder().key("test").build().getIngredients())
        .extracting(MaterialChar::getCharacter)
        .containsExactly('R');
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
    tag(new NamespacedKey("minecraft", "empty"));
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
  void rejectsNegativeTithes() {
    material("STONE");
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("S")
        .key('S', "STONE")
        .result("STONE", 1)
        .tithe(-1);

    assertThat(parser.parse(spec).problems()).containsExactly("tithe must be 0 or more levels, found -1");
  }

  @Test
  void invalidSpecsProduceNoBuilderAndReportEveryProblem() {
    ConfiguredShapedRecipe spec = new ConfiguredShapedRecipe()
        .rows("XY")
        .result("NOT_A_MATERIAL", 0);

    ConfiguredRecipeParser.Result result = parser.parse(spec);

    assertThat(result.builder()).isNull();
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
    assertThat(parser.builder("disabled", spec)).isEmpty();
  }

  private Material material(String name) {
    Material material = mock(Material.class);
    when(material.isItem()).thenReturn(true);
    when(material.name()).thenReturn(name);
    materials.put(name, material);
    return material;
  }

  private static ItemStack countedItem() {
    ItemStack item = mock(ItemStack.class);
    AtomicInteger amount = new AtomicInteger(1);
    doAnswer(invocation -> {
      amount.set(invocation.getArgument(0));
      return null;
    }).when(item).setAmount(anyInt());
    when(item.getAmount()).thenAnswer(invocation -> amount.get());
    return item;
  }

  private Tag<Material> tag(NamespacedKey key, Material... values) {
    Tag<Material> tag = new FakeTag(key, Set.of(values));
    tags.put(key, tag);
    return tag;
  }

  private ItemStack exactItem(NamespacedKey key, String materialName) {
    Material material = material(materialName);
    ItemStack item = mock(ItemStack.class);
    when(item.getType()).thenReturn(material);
    exactItems.put(key, item);
    return item;
  }

  private static RecipeChoice choice(AdaptRecipe.Shaped shaped, char symbol) {
    return shaped.getIngredients().stream()
        .filter(ingredient -> ingredient.getCharacter() == symbol)
        .findFirst()
        .orElseThrow()
        .getChoice();
  }

  private static List<Material> materialChoices(AdaptRecipe.Shaped shaped, char symbol) {
    RecipeChoice choice = choice(shaped, symbol);
    assertThat(choice).isInstanceOf(RecipeChoice.MaterialChoice.class);
    return ((RecipeChoice.MaterialChoice) choice).getChoices();
  }

  private static final class FakeTag implements Tag<Material> {
    private final NamespacedKey key;
    private final Set<Material> values;

    private FakeTag(NamespacedKey key, Set<Material> values) {
      this.key = key;
      this.values = values;
    }

    @Override
    public boolean isTagged(Material item) {
      return values.contains(item);
    }

    @Override
    public Set<Material> getValues() {
      return values;
    }

    @Override
    public NamespacedKey getKey() {
      return key;
    }
  }

  private final class FakeCatalog implements RecipeItemCatalog {
    @Override
    public Material itemMaterial(String name) {
      return materials.get(name);
    }

    @Override
    public Tag<Material> itemTag(NamespacedKey key) {
      return tags.get(key);
    }
  }
}

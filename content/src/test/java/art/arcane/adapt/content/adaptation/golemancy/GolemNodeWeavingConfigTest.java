package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.item.ExactIngredient;
import art.arcane.adapt.api.item.ExactItemResolver;
import art.arcane.adapt.api.item.RecipeItemCatalog;
import art.arcane.adapt.api.item.ShapedRecipeParser;
import art.arcane.adapt.content.golem.item.ExactIngredientBook;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.node.NodeCuboid;
import art.arcane.adapt.content.golem.node.NodeRecord;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GolemNodeWeavingConfigTest {
  private final GolemNodeWeaving.Config config = new GolemNodeWeaving.Config();
  private final Map<String, Id> materials = new HashMap<>();
  private final Map<Id, ItemRef> results = new HashMap<>();
  private final Map<Id, ExactIngredient> ingredients = new HashMap<>();
  private final ExactIngredientBook book = GolemItems.recipeBook();
  private final ShapedRecipeParser parser = new ShapedRecipeParser(new FakeCatalog(), new FakeResolver());

  @Test
  void nodeCapsAreaRadiiAndFilterSlotsFollowTheLevelTable() {
    assertThat(perLevel(level -> GolemNodeWeaving.nodeCap(config, level))).containsExactly(4, 8, 12, 18, 24);
    assertThat(perLevel(level -> GolemNodeWeaving.maxAreaRadius(config, level))).containsExactly(8, 12, 15, 15, 15);
    assertThat(perLevel(level -> GolemNodeWeaving.filterSlots(config, level))).containsExactly(3, 6, 6, 9, 18);
  }

  @Test
  void unlearnedPlayersGetNothingAndLevelsPastTheTableKeepTheLastValue() {
    assertThat(GolemNodeWeaving.nodeCap(config, 0)).isZero();
    assertThat(GolemNodeWeaving.maxAreaRadius(config, 0)).isZero();
    assertThat(GolemNodeWeaving.filterSlots(config, 0)).isZero();
    assertThat(GolemNodeWeaving.nodeCap(config, 9)).isEqualTo(24);
  }

  @Test
  void editedTablesAreClampedToWhatANodeCanHold() {
    config.maxAreaRadiusByLevel = List.of(99);
    config.filterSlotsByLevel = List.of(40);
    config.nodeCapByLevel = List.of(-3);

    assertThat(GolemNodeWeaving.maxAreaRadius(config, 1)).isEqualTo(NodeCuboid.MAX_SPAN - 1);
    assertThat(GolemNodeWeaving.filterSlots(config, 1)).isEqualTo(NodeRecord.MAX_FILTER_ENTRIES);
    assertThat(GolemNodeWeaving.nodeCap(config, 1)).isZero();
  }

  @Test
  void theTopAreaRadiusReachesTheLargestAreaANodeCanHold() {
    assertThat(GolemNodeWeaving.maxAreaRadius(config, 5)).isEqualTo(NodeCuboid.MAX_SPAN - 1).isEqualTo(15);
  }

  @Test
  void configuredAreaRadiiAreClampedFromOneThroughFifteen() {
    config.maxAreaRadiusByLevel = List.of(0, -4, 99, 32);

    assertThat(perLevel(level -> GolemNodeWeaving.maxAreaRadius(config, level))).containsExactly(1, 1, 15, 15, 15);
    assertThat(GolemNodeWeaving.maxAreaRadius(config, 0)).isZero();
  }

  @Test
  void nodeWeavingRequiresAnimusBindingOne() {
    assertThat(config.requires).containsExactly("adapt:golem/animus_binding@1");
  }

  @Test
  void theBlankNodeRecipeParsesToItsPlanShapeAndYieldsFour() {
    Id clay = material("CLAY_BALL");
    Id redstone = material("REDSTONE");
    Id shard = material("AMETHYST_SHARD");
    ItemRef blankNode = result("blank_node");

    ShapedRecipeParser.Result parsed = parser.parse(config.blankNodeRecipe.toConfig());

    assertThat(parsed.problems()).isEmpty();
    RecipeSpec.Shaped shaped = parsed.draft().key(Id.adapt("test"));
    assertThat(shaped.shape()).containsExactly("CRC", "RAR", "CRC");
    assertThat(choice(shaped, 'C')).containsExactly(clay);
    assertThat(choice(shaped, 'R')).containsExactly(redstone);
    assertThat(choice(shaped, 'A')).containsExactly(shard);
    assertThat(shaped.result()).isSameAs(blankNode);
    assertThat(config.blankNodeRecipe.getAmount()).isEqualTo(4);
  }

  @Test
  void theDepositNodeRecipePutsAChestAboveAnExactBlankNode() {
    Id chest = material("CHEST");
    Id clay = material("CLAY_BALL");
    ExactIngredient blankNode = new ExactIngredient(clay, item -> true);
    ingredients.put(Id.of("golemancy", "blank_node"), blankNode);
    ItemRef blank = result("blank_node");
    ItemRef deposit = result("node/deposit");

    ShapedRecipeParser.Result parsed = parser.parse(config.depositNodeRecipe.toConfig());

    assertThat(parsed.problems()).isEmpty();
    RecipeSpec.Shaped shaped = parsed.draft().key(Id.adapt("test"));
    assertThat(shaped.shape()).containsExactly(" X ", " B ", "   ");
    assertThat(choice(shaped, 'X')).containsExactly(chest);
    Ingredient blankChoice = shaped.ingredients().get('B');
    assertThat(blankChoice).isInstanceOf(Ingredient.Matching.class);
    assertThat(List.of(((Ingredient.Matching) blankChoice).example())).containsExactly(blank);
    assertThat(parsed.exactIngredients()).containsExactly(blankNode);
    assertThat(shaped.result()).isSameAs(deposit);
    assertThat(config.depositNodeRecipe.getAmount()).isEqualTo(1);
  }

  private static List<Integer> perLevel(IntUnaryOperator value) {
    return IntStream.rangeClosed(1, 5).map(value).boxed().toList();
  }

  private Id material(String name) {
    Id material = Id.minecraft(name.toLowerCase(Locale.ROOT));
    materials.put(name, material);
    return material;
  }

  private ItemRef result(String id) {
    ItemRef template = mock(ItemRef.class);
    ItemRef copy = mock(ItemRef.class);
    when(template.type()).thenReturn(materials.get("CLAY_BALL"));
    when(copy.type()).thenReturn(materials.get("CLAY_BALL"));
    when(template.copyWithAmount(anyInt())).thenReturn(copy);
    results.put(Id.of("golemancy", id), template);
    return copy;
  }

  private static Set<Id> choice(RecipeSpec.Shaped shaped, char symbol) {
    Ingredient choice = shaped.ingredients().get(symbol);
    assertThat(choice).isInstanceOf(Ingredient.AnyOf.class);
    return ((Ingredient.AnyOf) choice).types();
  }

  private final class FakeCatalog implements RecipeItemCatalog {
    @Override
    public Id itemMaterial(String name) {
      return materials.get(name);
    }

    @Override
    public Set<Id> itemTag(Id key) {
      return null;
    }

    @Override
    public ItemRef item(Id type, int amount) {
      return null;
    }

    @Override
    public int maxStackSize(Id type) {
      return 64;
    }
  }

  private final class FakeResolver implements ExactItemResolver {
    @Override
    public ExactIngredient ingredient(Id id) {
      return ingredients.get(id);
    }

    @Override
    public ItemRef result(Id id) {
      return results.get(id);
    }
  }
}

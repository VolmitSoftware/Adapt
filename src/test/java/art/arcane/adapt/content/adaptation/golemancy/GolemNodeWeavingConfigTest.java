package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GolemNodeWeavingConfigTest {
  private final GolemNodeWeaving.Config config = new GolemNodeWeaving.Config();
  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> results = new HashMap<>();
  private final Map<NamespacedKey, ExactIngredient> ingredients = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private final ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);

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
    assertThat(config.requires).containsExactly("golem-animus-binding:1");
  }

  @Test
  void theBlankNodeRecipeParsesToItsPlanShapeAndYieldsFour() {
    Material clay = material("CLAY_BALL");
    Material redstone = material("REDSTONE");
    Material shard = material("AMETHYST_SHARD");
    ItemStack blankNode = result("blank_node");

    ConfiguredRecipeParser.Result parsed = parser.parse(config.blankNodeRecipe);

    assertThat(parsed.problems()).isEmpty();
    AdaptRecipe.Shaped shaped = parsed.builder().key("test").build();
    assertThat(shaped.getShapes()).containsExactly("CRC", "RAR", "CRC");
    assertThat(choice(shaped, 'C')).containsExactly(clay);
    assertThat(choice(shaped, 'R')).containsExactly(redstone);
    assertThat(choice(shaped, 'A')).containsExactly(shard);
    assertThat(shaped.getResult()).isSameAs(blankNode);
    assertThat(config.blankNodeRecipe.getAmount()).isEqualTo(4);
  }

  @Test
  void theDepositNodeRecipePutsAChestAboveAnExactBlankNode() {
    Material chest = material("CHEST");
    Material clay = material("CLAY_BALL");
    ExactIngredient blankNode = new ExactIngredient(clay, item -> true);
    ingredients.put(new NamespacedKey("golemancy", "blank_node"), blankNode);
    ItemStack blank = result("blank_node");
    ItemStack deposit = result("node/deposit");

    ConfiguredRecipeParser.Result parsed = parser.parse(config.depositNodeRecipe);

    assertThat(parsed.problems()).isEmpty();
    AdaptRecipe.Shaped shaped = parsed.builder().key("test").build();
    assertThat(shaped.getShapes()).containsExactly(" X ", " B ", "   ");
    assertThat(choice(shaped, 'X')).containsExactly(chest);
    RecipeChoice blankChoice = shaped.getIngredients().stream().filter(ingredient -> ingredient.getCharacter() == 'B')
        .findFirst().orElseThrow().getChoice();
    assertThat(blankChoice).isInstanceOf(RecipeChoice.ExactChoice.class);
    assertThat(((RecipeChoice.ExactChoice) blankChoice).getChoices()).containsExactly(blank);
    assertThat(parsed.exactIngredients()).containsExactly(blankNode);
    assertThat(shaped.getResult()).isSameAs(deposit);
    assertThat(config.depositNodeRecipe.getAmount()).isEqualTo(1);
  }

  private static List<Integer> perLevel(IntUnaryOperator value) {
    return IntStream.rangeClosed(1, 5).map(value).boxed().toList();
  }

  private Material material(String name) {
    Material material = mock(Material.class);
    when(material.isItem()).thenReturn(true);
    when(material.name()).thenReturn(name);
    materials.put(name, material);
    return material;
  }

  private ItemStack result(String id) {
    ItemStack template = mock(ItemStack.class);
    ItemStack copy = mock(ItemStack.class);
    when(template.getType()).thenReturn(materials.get("CLAY_BALL"));
    when(copy.getType()).thenReturn(materials.get("CLAY_BALL"));
    when(template.clone()).thenReturn(copy);
    results.put(new NamespacedKey("golemancy", id), template);
    return copy;
  }

  private static List<Material> choice(AdaptRecipe.Shaped shaped, char symbol) {
    RecipeChoice choice = shaped.getIngredients().stream()
        .filter(ingredient -> ingredient.getCharacter() == symbol)
        .findFirst()
        .orElseThrow()
        .getChoice();
    assertThat(choice).isInstanceOf(RecipeChoice.MaterialChoice.class);
    return ((RecipeChoice.MaterialChoice) choice).getChoices();
  }

  private final class FakeCatalog implements RecipeItemCatalog {
    @Override
    public Material itemMaterial(String name) {
      return materials.get(name);
    }

    @Override
    public Tag<Material> itemTag(NamespacedKey key) {
      return null;
    }
  }

  private final class FakeResolver implements ExactItemResolver {
    @Override
    public ExactIngredient ingredient(NamespacedKey id) {
      return ingredients.get(id);
    }

    @Override
    public ItemStack result(NamespacedKey id) {
      return results.get(id);
    }
  }
}

package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.gui.NodePanels;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemSorterCoreExecutionTest extends AdaptTestBase {
  private static final Id CORE_KEY = Id.of("adapt", "golemancy-sorter-core");
  private static final Id INPUT_KEY = Id.of("adapt", "golemancy-input-node");
  private static final Id FILTER_KEY = Id.of("adapt", "golemancy-filter-node");

  private final Map<String, Id> materials = new HashMap<>();
  private final Map<Id, ItemRef> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private GolemSorterCore adaptation;

  @BeforeEach
  void construct() {
    for (String name : List.of("COMPARATOR", "QUARTZ", "COPPER_CHEST", "ITEM_FRAME", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/sorter");
    result("node/input");
    result("node/filter");
    result("blank_core");
    result("blank_node");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.platform(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    adaptation = new GolemSorterCore();
  }

  @AfterEach
  void close() {
    parsers.close();
  }

  @Test
  void theCoreAndInputNodeRecipesUnlockAtLevelOneAndTheFilterNodeAtLevelThree() {
    List<RecipeSpec> recipes = adaptation.getRecipes();

    assertThat(recipes).extracting(RecipeSpec::key).containsExactly(CORE_KEY, INPUT_KEY, FILTER_KEY);
    assertThat(recipes).extracting(RecipeSpec::requiredLevel).containsExactly(1, 1, 3);
    assertThat(((RecipeSpec.Shaped) recipes.get(0)).shape()).containsExactly(" X ", "YBY", " Z ");
    assertThat(((RecipeSpec.Shaped) recipes.get(1)).shape()).containsExactly(" X ", " B ", "   ");
    assertThat(((RecipeSpec.Shaped) recipes.get(2)).shape()).containsExactly(" X ", " B ", "   ");
    assertThat(GolemItems.craftCounter().tracks(CORE_KEY)).isTrue();
    assertThat(GolemItems.craftCounter().tracks(INPUT_KEY)).isTrue();
    assertThat(GolemItems.craftCounter().tracks(FILTER_KEY)).isTrue();
  }

  @Test
  void theDefaultRecipesFollowThePlan() {
    GolemSorterCore.Config config = new GolemSorterCore.Config();

    assertThat(config.sorterCoreRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "COMPARATOR", "Y", "QUARTZ",
        "Z", "COPPER_CHEST", "B", "golemancy:blank_core"));
    assertThat(config.sorterCoreRecipe.getResult()).isEqualTo("golemancy:core/sorter");
    assertThat(config.inputNodeRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "COPPER_CHEST", "B", "golemancy:blank_node"));
    assertThat(config.inputNodeRecipe.getResult()).isEqualTo("golemancy:node/input");
    assertThat(config.filterNodeRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "ITEM_FRAME", "B", "golemancy:blank_node"));
    assertThat(config.filterNodeRecipe.getResult()).isEqualTo("golemancy:node/filter");
  }

  @Test
  void theDefaultsFollowThePlan() {
    GolemSorterCore.Config config = new GolemSorterCore.Config();

    assertThat(config.destinationRadiusByLevel).containsExactly(8, 12, 16, 20, 24);
    assertThat(config.stacksPerTripByLevel).containsExactly(1, 1, 2, 2, 4);
    assertThat(config.groupsLevel).isEqualTo(2);
    assertThat(config.filterLevel).isEqualTo(3);
    assertThat(config.preciseLevel).isEqualTo(4);
    assertThat(config.shulkerBoxLevel).isEqualTo(4);
    assertThat(config.restockLevel).isEqualTo(4);
    assertThat(config.indexLevel).isEqualTo(5);
    assertThat(config.fetchLevel).isEqualTo(5);
    assertThat(config.groups).isEqualTo(SorterRules.DEFAULT_GROUPS);
    assertThat(config.indexRefreshSeconds).isEqualTo(30);
    assertThat(config.maxIndexedContainers).isEqualTo(1024);
    assertThat(config.fetchTimeoutSeconds).isEqualTo(120);
    assertThat(config.maxServedContainers).isEqualTo(128);
    assertThat(config.workXpPerStack).isEqualTo(0.3D);
  }

  @Test
  void configurationLoadsAndEveryLevelHasAPositiveCostAndProgress() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-sorter-core");
    assertThat(adaptation.getMaxLevel()).isEqualTo(5);
    assertThat(adaptation.getConfig().requires).containsExactly("golem-node-weaving:1");
    double previous = 0D;
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
      assertThat(adaptation.getLevelPercent(level)).isBetween(previous, 1D);
      previous = adaptation.getLevelPercent(level);
    }
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
  }

  @Test
  void everyLevelShowsItsRadiusTripAndUnlocks() {
    String groups = "+ Sorts by Item Groups";
    String filters = "+ Filter Nodes and an Overflow";

    assertThat(lore(1)).containsExactly("+ 8 Block Destination Radius", "+ 1 Stacks per Trip");
    assertThat(lore(2)).containsExactly("+ 12 Block Destination Radius", "+ 1 Stacks per Trip", groups);
    assertThat(lore(3)).containsExactly("+ 16 Block Destination Radius", "+ 2 Stacks per Trip", groups, filters);
    assertThat(lore(4)).containsExactly("+ 20 Block Destination Radius", "+ 2 Stacks per Trip", groups, filters,
        "+ Matches Enchantments and Potions", "+ Moves Shulker Boxes", "+ Restock Targets on Filter Nodes");
    assertThat(lore(5)).containsExactly("+ 24 Block Destination Radius", "+ 4 Stacks per Trip", groups, filters,
        "+ Matches Enchantments and Potions", "+ Moves Shulker Boxes", "+ Restock Targets on Filter Nodes",
        "+ Index of Container Contents", "+ Fetch From the Input Node Menu");
  }

  @Test
  void aDisabledReloadTurnsTheAdaptationOff() {
    assertThat(adaptation.reloadConfigSnapshot("enabled = false\nmaxLevel = 1\n", dataFolder.toPath().resolve("disabled.toml").toFile(), false)).isTrue();
    assertThat(adaptation.isEnabled()).isFalse();
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
  }

  @Test
  void loadingClampsTheWorkXpIndexAndFetchSettings() {
    assertThat(adaptation.reloadConfigSnapshot("workXpPerStack = -2.0\nindexRefreshSeconds = 0\nmaxIndexedContainers = -5\nfetchTimeoutSeconds = 1\nmaxServedContainers = 5000\n",
        dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    GolemSorterCore.Config config = adaptation.getConfig();
    assertThat(config.workXpPerStack).isZero();
    assertThat(config.indexRefreshSeconds).isEqualTo(1);
    assertThat(config.maxIndexedContainers).isEqualTo(1);
    assertThat(config.fetchTimeoutSeconds).isEqualTo(10);
    assertThat(config.maxServedContainers).isEqualTo(1024);
  }

  @Test
  void aReloadedGroupListIsParsedAgainWithItsItemTags() {
    RegistryLookup registries = mock(RegistryLookup.class);
    lenient().when(registries.exists(any(RegistryKind.class), any(Id.class))).thenReturn(true);
    lenient().when(registries.tagValues(RegistryKind.ITEM, Id.minecraft("logs"))).thenReturn(Set.of(Id.minecraft("oak_log")));
    lenient().when(AdaptCore.platform().host().registries()).thenReturn(registries);
    assertThat(adaptation.groups()).containsEntry(Id.minecraft("oak_log"), "logs").containsEntry(Id.minecraft("cobblestone"), "stone");

    assertThat(adaptation.reloadConfigSnapshot("groups = [\"wood=#logs,STICK\"]\n", dataFolder.toPath().resolve("groups.toml").toFile(), false))
        .isTrue();

    assertThat(adaptation.groups()).containsOnly(Map.entry(Id.minecraft("oak_log"), "wood"), Map.entry(Id.minecraft("stick"), "wood"));
    assertThat(adaptation.groups()).isSameAs(adaptation.groups());
  }

  @Test
  void activatingTheRuntimeInstallsTheInputAndFilterNodesUntilUnregistered() {
    assertThat(NodeBehaviors.of(NodeType.INPUT)).isEmpty();
    assertThat(NodeBehaviors.of(NodeType.FILTER)).isEmpty();

    adaptation.onRuntimeActivated();

    assertThat(NodeBehaviors.of(NodeType.INPUT)).hasValueSatisfying(behavior -> assertThat(behavior.policy()).isSameAs(adaptation));
    assertThat(NodeBehaviors.of(NodeType.FILTER)).hasValueSatisfying(behavior -> assertThat(behavior.policy()).isSameAs(adaptation));
    assertThat(NodePanels.of(NodeType.INPUT)).isPresent();
    assertThat(NodePanels.of(NodeType.FILTER)).isPresent();

    adaptation.unregister();

    assertThat(NodeBehaviors.of(NodeType.INPUT)).isEmpty();
    assertThat(NodeBehaviors.of(NodeType.FILTER)).isEmpty();
    assertThat(NodePanels.of(NodeType.INPUT)).isEmpty();
    assertThat(NodePanels.of(NodeType.FILTER)).isEmpty();
  }

  private List<String> lore(int level) {
    StatLines stats = new StatLines();
    adaptation.addStats(level, stats);
    List<String> lines = new ArrayList<>();
    for (String line : stats.lines()) {
      lines.add(line.replaceAll("§[0-9a-fk-orx]", ""));
    }
    return lines;
  }

  private void material(String name) {
    materials.put(name, Id.minecraft(name.toLowerCase(Locale.ROOT)));
  }

  private void result(String id) {
    Id material = id.equals("blank_node") ? materials.get("CLAY_BALL")
        : id.startsWith("node/") ? Id.minecraft("brick") : materials.get("FIREWORK_STAR");
    results.put(Id.of("golemancy", id), new FakeItem(material, 1));
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
      return new FakeItem(type, amount);
    }

    @Override
    public int maxStackSize(Id type) {
      return 64;
    }
  }

  private final class FakeResolver implements ExactItemResolver {
    @Override
    public ExactIngredient ingredient(Id id) {
      return new ExactIngredient(materials.get(id.path().equals("blank_core") ? "FIREWORK_STAR" : "CLAY_BALL"), item -> true);
    }

    @Override
    public ItemRef result(Id id) {
      return results.get(id);
    }
  }
}

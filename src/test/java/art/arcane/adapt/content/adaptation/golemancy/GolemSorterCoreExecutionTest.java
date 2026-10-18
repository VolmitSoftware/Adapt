package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.gui.NodePanels;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemSorterCoreExecutionTest extends AdaptTestBase {
  private static final NamespacedKey CORE_KEY = new NamespacedKey("adapt", "golemancy-sorter-core");
  private static final NamespacedKey INPUT_KEY = new NamespacedKey("adapt", "golemancy-input-node");
  private static final NamespacedKey FILTER_KEY = new NamespacedKey("adapt", "golemancy-filter-node");

  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private GolemSorterCore adaptation;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    for (String name : List.of("COMPARATOR", "QUARTZ", "COPPER_CHEST", "ITEM_FRAME", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/sorter");
    result("node/input");
    result("node/filter");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    adaptation = new GolemSorterCore();
  }

  @AfterEach
  void close() {
    parsers.close();
  }

  @Test
  void theCoreAndInputNodeRecipesUnlockAtLevelOneAndTheFilterNodeAtLevelThree() {
    List<AdaptRecipe> recipes = adaptation.getRecipes();

    assertThat(recipes).extracting(AdaptRecipe::getNSKey).containsExactly(CORE_KEY, INPUT_KEY, FILTER_KEY);
    assertThat(recipes).extracting(AdaptRecipe::getRequiredLevel).containsExactly(1, 1, 3);
    assertThat(((AdaptRecipe.Shaped) recipes.get(0)).getShapes()).containsExactly(" X ", "YBY", " Z ");
    assertThat(((AdaptRecipe.Shaped) recipes.get(1)).getShapes()).containsExactly(" X ", " B ", "   ");
    assertThat(((AdaptRecipe.Shaped) recipes.get(2)).getShapes()).containsExactly(" X ", " B ", "   ");
    assertThat(GolemItems.recipeTithe().levels(keyed(CORE_KEY))).isEqualTo(2);
    assertThat(GolemItems.recipeTithe().levels(keyed(INPUT_KEY))).isZero();
    assertThat(GolemItems.recipeTithe().levels(keyed(FILTER_KEY))).isZero();
  }

  @Test
  void theDefaultRecipesFollowThePlan() {
    GolemSorterCore.Config config = new GolemSorterCore.Config();

    assertThat(config.sorterCoreRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "COMPARATOR", "Y", "QUARTZ",
        "Z", "COPPER_CHEST", "B", "golemancy:blank_core"));
    assertThat(config.sorterCoreRecipe.getResult()).isEqualTo("golemancy:core/sorter");
    assertThat(config.sorterCoreRecipe.getTithe()).isEqualTo(2);
    assertThat(config.inputNodeRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "COPPER_CHEST", "B", "golemancy:blank_node"));
    assertThat(config.inputNodeRecipe.getResult()).isEqualTo("golemancy:node/input");
    assertThat(config.filterNodeRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "ITEM_FRAME", "B", "golemancy:blank_node"));
    assertThat(config.filterNodeRecipe.getResult()).isEqualTo("golemancy:node/filter");
    assertThat(config.filterNodeRecipe.getTithe()).isZero();
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
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
      assertThat(adaptation.isEnabled()).isFalse();
    }
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
  }

  @Test
  void loadingClampsTheWorkXpIndexAndFetchSettings() {
    assertThat(adaptation.reloadConfigSnapshot("workXpPerStack = -2.0\nindexRefreshSeconds = 0\nmaxIndexedContainers = -5\nfetchTimeoutSeconds = 1\n",
        dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    GolemSorterCore.Config config = adaptation.getConfig();
    assertThat(config.workXpPerStack).isZero();
    assertThat(config.indexRefreshSeconds).isEqualTo(1);
    assertThat(config.maxIndexedContainers).isEqualTo(1);
    assertThat(config.fetchTimeoutSeconds).isEqualTo(10);
  }

  @Test
  void aReloadedGroupListIsParsedAgainWithItsItemTags() {
    Tag<Material> logs = new FakeTag(NamespacedKey.minecraft("logs"), Set.of(Material.OAK_LOG));
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft("logs"), Material.class)).thenReturn(logs);
      assertThat(adaptation.groups()).containsEntry(Material.OAK_LOG, "logs").containsEntry(Material.COBBLESTONE, "stone");

      assertThat(adaptation.reloadConfigSnapshot("groups = [\"wood=#logs,STICK\"]\n", dataFolder.toPath().resolve("groups.toml").toFile(), false))
          .isTrue();

      assertThat(adaptation.groups()).containsOnly(Map.entry(Material.OAK_LOG, "wood"), Map.entry(Material.STICK, "wood"));
      assertThat(adaptation.groups()).isSameAs(adaptation.groups());
    }
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
    List<String> lines = new ArrayList<>();
    Element stats = mock(Element.class, RETURNS_SELF);
    when(stats.addLore(anyString())).thenAnswer(invocation -> {
      lines.add(invocation.<String>getArgument(0).replaceAll("§[0-9a-fk-orx]", ""));
      return stats;
    });
    adaptation.addStats(level, stats);
    return lines;
  }

  private static ShapedRecipe keyed(NamespacedKey key) {
    ShapedRecipe recipe = mock(ShapedRecipe.class);
    when(recipe.getKey()).thenReturn(key);
    return recipe;
  }

  private void material(String name) {
    Material material = mock(Material.class);
    when(material.isItem()).thenReturn(true);
    when(material.name()).thenReturn(name);
    materials.put(name, material);
  }

  private void result(String id) {
    ItemStack template = mock(ItemStack.class);
    ItemStack copy = mock(ItemStack.class);
    when(template.clone()).thenReturn(copy);
    results.put(new NamespacedKey("golemancy", id), template);
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
      return new ExactIngredient(materials.get(id.getKey().equals("blank_core") ? "FIREWORK_STAR" : "CLAY_BALL"), item -> true);
    }

    @Override
    public ItemStack result(NamespacedKey id) {
      return results.get(id);
    }
  }

  private record FakeTag(NamespacedKey key, Set<Material> values) implements Tag<Material> {
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
}

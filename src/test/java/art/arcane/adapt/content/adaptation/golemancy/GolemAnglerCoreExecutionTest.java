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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemAnglerCoreExecutionTest extends AdaptTestBase {
  private static final NamespacedKey CORE_KEY = new NamespacedKey("adapt", "golemancy-angler-core");
  private static final NamespacedKey NODE_KEY = new NamespacedKey("adapt", "golemancy-fishing-node");

  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private GolemAnglerCore adaptation;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    for (String name : List.of("FISHING_ROD", "PRISMARINE_SHARD", "DRIED_KELP", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/angler");
    result("node/fishing");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    adaptation = new GolemAnglerCore();
  }

  @AfterEach
  void close() {
    parsers.close();
  }

  @Test
  void theCoreAndNodeRecipesRegisterAtLevelOneUnderGolemancyKeys() {
    List<AdaptRecipe> recipes = adaptation.getRecipes();

    assertThat(recipes).extracting(AdaptRecipe::getNSKey).containsExactly(CORE_KEY, NODE_KEY);
    assertThat(recipes).allMatch(recipe -> recipe.getRequiredLevel() == 1);
    assertThat(((AdaptRecipe.Shaped) recipes.get(0)).getShapes()).containsExactly(" X ", "YBY", " Z ");
    assertThat(((AdaptRecipe.Shaped) recipes.get(1)).getShapes()).containsExactly(" X ", " B ", "   ");
    assertThat(book.satisfiedBy(CORE_KEY, List.of(mock(ItemStack.class)))).isTrue();
    assertThat(book.satisfiedBy(NODE_KEY, List.of(mock(ItemStack.class)))).isTrue();
    assertThat(GolemItems.craftCounter().tracks(keyed(CORE_KEY))).isTrue();
    assertThat(GolemItems.craftCounter().tracks(keyed(NODE_KEY))).isTrue();
  }

  @Test
  void theDefaultRecipesFollowThePlan() {
    GolemAnglerCore.Config config = new GolemAnglerCore.Config();

    assertThat(config.anglerCoreRecipe.getRows()).containsExactly(" X ", "YBY", " Z ");
    assertThat(config.anglerCoreRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "FISHING_ROD", "Y", "PRISMARINE_SHARD",
        "Z", "DRIED_KELP", "B", "golemancy:blank_core"));
    assertThat(config.anglerCoreRecipe.getResult()).isEqualTo("golemancy:core/angler");
    assertThat(config.anglerCoreRecipe.getAmount()).isEqualTo(1);
    assertThat(config.fishingNodeRecipe.getRows()).containsExactly(" X ", " B ", "   ");
    assertThat(config.fishingNodeRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "FISHING_ROD", "B", "golemancy:blank_node"));
    assertThat(config.fishingNodeRecipe.getResult()).isEqualTo("golemancy:node/fishing");
  }

  @Test
  void theDefaultsFollowThePlan() {
    GolemAnglerCore.Config config = new GolemAnglerCore.Config();

    assertThat(config.catchIntervalSecondsByLevel).containsExactly(60, 50, 40, 32, 25);
    assertThat(config.luckByLevel).containsExactly(0, 0, 1, 1, 2);
    assertThat(config.junkFilterLevel).isEqualTo(3);
    assertThat(config.rainLevel).isEqualTo(4);
    assertThat(config.rainIntervalMultiplier).isEqualTo(0.75D);
    assertThat(config.doubleCatchLevel).isEqualTo(5);
    assertThat(config.doubleCatchChance).isEqualTo(0.1D);
    assertThat(config.maxAnglersPerChunk).isEqualTo(2);
    assertThat(List.of(config.fishWeight, config.fishLuckShift, config.junkWeight, config.junkLuckShift, config.treasureWeight, config.treasureLuckShift))
        .containsExactly(85, -1, 10, -2, 5, 2);
    assertThat(config.fish).containsExactlyElementsOf(AnglerLoot.DEFAULT_FISH);
    assertThat(config.junk).containsExactlyElementsOf(AnglerLoot.DEFAULT_JUNK);
    assertThat(config.treasure).containsExactlyElementsOf(AnglerLoot.DEFAULT_TREASURE);
    assertThat(config.treasureEnchantLevels).isEqualTo(30);
    assertThat(config.workXpPerCatch).isEqualTo(6.0D);
  }

  @Test
  void configurationLoadsAndEveryLevelHasAPositiveCostAndProgress() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-angler-core");
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
  void everyLevelShowsItsCatchTimeLuckAndUnlocks() {
    String junk = "+ Junk Filter Toggle on Fishing Nodes";
    String rain = "+ 25% Shorter Catch Time in Rain";

    assertThat(lore(1)).containsExactly("* 60 Seconds per Catch");
    assertThat(lore(2)).containsExactly("* 50 Seconds per Catch");
    assertThat(lore(3)).containsExactly("* 40 Seconds per Catch", "+ 1 Fishing Luck", junk);
    assertThat(lore(4)).containsExactly("* 32 Seconds per Catch", "+ 1 Fishing Luck", junk, rain);
    assertThat(lore(5)).containsExactly("* 25 Seconds per Catch", "+ 2 Fishing Luck", junk, rain, "+ 10% Chance of a Double Catch");
  }

  @Test
  void aDisabledReloadTurnsTheAdaptationOff() {
    assertThat(adaptation.reloadConfigSnapshot("enabled = false\nmaxLevel = 1\n", dataFolder.toPath().resolve("disabled.toml").toFile(), false)).isTrue();
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
      assertThat(adaptation.isEnabled()).isFalse();
    }
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
    assertThat(adaptation.getLevelPercent(2)).isEqualTo(1D);
  }

  @Test
  void loadingClampsChancesRatesAndCaps() {
    assertThat(adaptation.reloadConfigSnapshot("""
        workXpPerCatch = -2.0
        doubleCatchChance = 3.0
        rainIntervalMultiplier = 0.0
        maxAnglersPerChunk = 0
        treasureEnchantLevels = 0
        """, dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    GolemAnglerCore.Config config = adaptation.getConfig();
    assertThat(config.workXpPerCatch).isZero();
    assertThat(config.doubleCatchChance).isEqualTo(1D);
    assertThat(config.rainIntervalMultiplier).isEqualTo(0.05D);
    assertThat(config.maxAnglersPerChunk).isEqualTo(1);
    assertThat(config.treasureEnchantLevels).isEqualTo(1);

    assertThat(adaptation.reloadConfigSnapshot("treasureEnchantLevels = 99\n", dataFolder.toPath().resolve("enchant.toml").toFile(), false)).isTrue();

    assertThat(adaptation.getConfig().treasureEnchantLevels).isEqualTo(30);
  }

  @Test
  void reloadedLootTablesAreParsedAgainAndCachedBetweenReloads() {
    assertThat(adaptation.reloadConfigSnapshot("fish = []\njunk = []\ntreasure = []\n", dataFolder.toPath().resolve("empty.toml").toFile(), false))
        .isTrue();
    AnglerLoot.Tables empty = adaptation.loot();

    assertThat(adaptation.loot()).isSameAs(empty);
    assertThat(empty.fish().isEmpty() && empty.junk().isEmpty() && empty.treasure().isEmpty()).isTrue();

    assertThat(adaptation.reloadConfigSnapshot("fish = [\"NOT_A_FISH:4\", \"COD\"]\njunk = []\ntreasure = []\n",
        dataFolder.toPath().resolve("loot.toml").toFile(), false)).isTrue();

    assertThat(adaptation.loot()).isNotSameAs(empty).isEqualTo(empty);
    assertThat(adaptation.getConfig().fish).containsExactly("NOT_A_FISH:4", "COD");
  }

  @Test
  void activatingTheRuntimeInstallsTheFishingNodeAndPanelUntilUnregistered() {
    assertThat(NodeBehaviors.of(NodeType.FISHING)).isEmpty();
    assertThat(NodePanels.of(NodeType.FISHING)).isEmpty();

    adaptation.onRuntimeActivated();

    assertThat(NodeBehaviors.of(NodeType.FISHING)).hasValueSatisfying(behavior -> assertThat(behavior.policy()).isSameAs(adaptation));
    assertThat(NodePanels.of(NodeType.FISHING)).isPresent();

    adaptation.unregister();

    assertThat(NodeBehaviors.of(NodeType.FISHING)).isEmpty();
    assertThat(NodePanels.of(NodeType.FISHING)).isEmpty();
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
}

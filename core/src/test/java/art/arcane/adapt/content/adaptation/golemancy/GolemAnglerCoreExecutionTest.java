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
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemAnglerCoreExecutionTest extends AdaptTestBase {
  private static final Id CORE_KEY = Id.adapt("golemancy-angler-core");
  private static final Id NODE_KEY = Id.adapt("golemancy-fishing-node");

  private final Map<String, Id> materials = new HashMap<>();
  private final Map<Id, ItemRef> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private final ItemOps items = mock(ItemOps.class);
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private GolemAnglerCore adaptation;

  @BeforeEach
  void construct() {
    lenient().when(AdaptCore.platform().host().items()).thenReturn(items);
    lenient().when(items.isItem(any(Id.class))).thenAnswer(invocation -> !Id.minecraft("not_a_fish").equals(invocation.getArgument(0)));
    for (String name : List.of("FISHING_ROD", "PRISMARINE_SHARD", "DRIED_KELP", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/angler");
    result("node/fishing");
    result("blank_core");
    result("blank_node");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.platform(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    adaptation = new GolemAnglerCore();
  }

  @AfterEach
  void close() {
    parsers.close();
  }

  @Test
  void theCoreAndNodeRecipesRegisterAtLevelOneUnderGolemancyKeys() {
    List<RecipeSpec> recipes = adaptation.getRecipes();

    assertThat(recipes).extracting(RecipeSpec::key).containsExactly(CORE_KEY, NODE_KEY);
    assertThat(recipes).allMatch(recipe -> recipe.requiredLevel() == 1);
    assertThat(((RecipeSpec.Shaped) recipes.get(0)).shape()).containsExactly(" X ", "YBY", " Z ");
    assertThat(((RecipeSpec.Shaped) recipes.get(1)).shape()).containsExactly(" X ", " B ", "   ");
    assertThat(book.satisfiedBy(CORE_KEY, List.of(mock(ItemRef.class)))).isTrue();
    assertThat(book.satisfiedBy(NODE_KEY, List.of(mock(ItemRef.class)))).isTrue();
    assertThat(GolemItems.craftCounter().tracks(CORE_KEY)).isTrue();
    assertThat(GolemItems.craftCounter().tracks(NODE_KEY)).isTrue();
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
    String rod = "+ Fishes with a Fishing Rod from the Home Chest";

    assertThat(lore(1)).containsExactly("* 60 Seconds per Catch", rod);
    assertThat(lore(2)).containsExactly("* 50 Seconds per Catch", rod);
    assertThat(lore(3)).containsExactly("* 40 Seconds per Catch", "+ 1 Fishing Luck", junk, rod);
    assertThat(lore(4)).containsExactly("* 32 Seconds per Catch", "+ 1 Fishing Luck", junk, rain, rod);
    assertThat(lore(5)).containsExactly("* 25 Seconds per Catch", "+ 2 Fishing Luck", junk, rain, "+ 10% Chance of a Double Catch", rod);
  }

  @Test
  void aDisabledReloadTurnsTheAdaptationOff() {
    assertThat(adaptation.reloadConfigSnapshot("enabled = false\nmaxLevel = 1\n", dataFolder.toPath().resolve("disabled.toml").toFile(), false)).isTrue();
    assertThat(adaptation.isEnabled()).isFalse();
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
    Id type = id.equals("blank_node") ? materials.get("CLAY_BALL")
        : id.startsWith("node/") ? Id.minecraft("brick") : materials.get("FIREWORK_STAR");
    results.put(Id.of("golemancy", id), item(type, 1));
  }

  private static ItemRef item(Id type, int amount) {
    ItemRef item = mock(ItemRef.class);
    when(item.type()).thenReturn(type);
    when(item.amount()).thenReturn(amount);
    when(item.maxStackSize()).thenReturn(64);
    when(item.copyWithAmount(anyInt())).thenAnswer(invocation -> item(type, invocation.<Integer>getArgument(0)));
    return item;
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
      return GolemAnglerCoreExecutionTest.item(type, amount);
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

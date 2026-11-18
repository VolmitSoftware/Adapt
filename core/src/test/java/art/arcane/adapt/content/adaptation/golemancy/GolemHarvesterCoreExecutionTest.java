package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.golem.GolemCoreBehaviors;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
import art.arcane.adapt.api.world.AdaptServer;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemHarvesterCoreExecutionTest extends AdaptTestBase {
  private static final Id CORE_KEY = Id.of("adapt", "golemancy-harvester-core");
  private static final Id NODE_KEY = Id.of("adapt", "golemancy-harvest-node");
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-0000000022e1");

  private final Map<String, Id> materials = new HashMap<>();
  private final Map<Id, ItemRef> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private GolemHarvesterCore adaptation;

  @BeforeEach
  void construct() {
    for (String name : List.of("IRON_HOE", "WHEAT", "COMPOSTER", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/harvester");
    result("node/harvest");
    result("blank_core");
    result("blank_node");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.platform(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    adaptation = new GolemHarvesterCore();
  }

  @AfterEach
  void close() {
    parsers.close();
  }

  @Test
  void tendingAndTillingControlsAppearOnceTheirLevelsUnlock() {
    assertThat(adaptation.getPlayerPreferences())
        .containsExactly(CommonPreferences.ENABLED, GolemancyPreferences.TENDING, GolemancyPreferences.TILLING);
    GolemancyPreferenceAssertions.assertVisibleFrom(adaptation, GolemancyPreferences.TENDING, 3);
    GolemancyPreferenceAssertions.assertVisibleFrom(adaptation, GolemancyPreferences.TILLING, 4);
  }

  @Test
  void tendingAndTillingDefaultOnAndFollowTheServerDefault() {
    UUID owner = UUID.randomUUID();
    when(core.getAdaptServer()).thenReturn(mock(AdaptServer.class));

    assertThat(adaptation.tends(owner)).isTrue();
    assertThat(adaptation.tills(owner)).isTrue();

    GolemancyPreferenceAssertions.serverDefault(adaptation, GolemancyPreferences.TENDING, "OFF");
    GolemancyPreferenceAssertions.serverDefault(adaptation, GolemancyPreferences.TILLING, "OFF");
    assertThat(adaptation.tends(owner)).isFalse();
    assertThat(adaptation.tills(owner)).isFalse();
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
    GolemHarvesterCore.Config config = new GolemHarvesterCore.Config();

    assertThat(config.harvesterCoreRecipe.getRows()).containsExactly(" X ", "YBY", " Z ");
    assertThat(config.harvesterCoreRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "IRON_HOE", "Y", "WHEAT",
        "Z", "COMPOSTER", "B", "golemancy:blank_core"));
    assertThat(config.harvesterCoreRecipe.getResult()).isEqualTo("golemancy:core/harvester");
    assertThat(config.harvesterCoreRecipe.getAmount()).isEqualTo(1);
    assertThat(config.harvestNodeRecipe.getRows()).containsExactly(" X ", " B ", "   ");
    assertThat(config.harvestNodeRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "IRON_HOE", "B", "golemancy:blank_node"));
    assertThat(config.harvestNodeRecipe.getResult()).isEqualTo("golemancy:node/harvest");
  }

  @Test
  void theDefaultsFollowThePlan() {
    GolemHarvesterCore.Config config = new GolemHarvesterCore.Config();

    assertThat(config.actionIntervalTicksByLevel).containsExactly(40, 30, 24, 18, 12);
    assertThat(config.cropsPerActionByLevel).containsExactly(1, 1, 2, 3, 4);
    assertThat(config.crops).containsExactly("WHEAT", "CARROTS", "POTATOES", "BEETROOTS", "NETHER_WART", "COCOA", "SWEET_BERRY_BUSH",
        "CAVE_VINES", "TORCHFLOWER_CROP", "PITCHER_CROP", "MELON", "PUMPKIN", "SUGAR_CANE", "BAMBOO", "CACTUS");
    assertThat(config.tendingIntervalMillis).isEqualTo(10_000L);
    assertThat(config.bountifulChance).isEqualTo(0.1D);
    assertThat(config.workXpPerCrop).isEqualTo(0.4D);
  }

  @Test
  void configurationLoadsAndEveryLevelHasAPositiveCostAndProgress() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-harvester-core");
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
  void everyLevelShowsItsPaceAndTheUnlocksItHasReached() {
    String gourds = "+ Harvests Melons, Pumpkins, Sugar Cane, Bamboo and Cactus";
    String tending = "+ Tending: Bone Meal from the Home Chest on Young Crops";
    String tilling = "+ Tilling: Re-tills Dirt and Plants Empty Farmland";

    assertThat(lore(1)).containsExactly("+ 1 Crops per Harvest Action", "* 2 Seconds per Harvest Action");
    assertThat(lore(2)).containsExactly("+ 1 Crops per Harvest Action", "* 1.5 Seconds per Harvest Action", gourds);
    assertThat(lore(3)).containsExactly("+ 2 Crops per Harvest Action", "* 1.2 Seconds per Harvest Action", gourds, tending);
    assertThat(lore(4)).containsExactly("+ 3 Crops per Harvest Action", "* 0.9 Seconds per Harvest Action", gourds, tending, tilling);
    assertThat(lore(5)).containsExactly("+ 4 Crops per Harvest Action", "* 0.6 Seconds per Harvest Action", gourds, tending, tilling,
        "+ 10% Chance of an Extra Drop Roll");
  }

  @Test
  void aDisabledReloadTurnsTheAdaptationOff() {
    assertThat(adaptation.reloadConfigSnapshot("enabled = false\nmaxLevel = 1\n", dataFolder.toPath().resolve("disabled.toml").toFile(), false)).isTrue();
    assertThat(adaptation.isEnabled()).isFalse();
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
    assertThat(adaptation.getLevelPercent(2)).isEqualTo(1D);
  }

  @Test
  void loadingClampsTheChanceXpAndTendingInterval() {
    assertThat(adaptation.reloadConfigSnapshot("bountifulChance = 1.5\nworkXpPerCrop = -2.0\ntendingIntervalMillis = 20\n",
        dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    assertThat(adaptation.getConfig().bountifulChance).isEqualTo(1D);
    assertThat(adaptation.getConfig().workXpPerCrop).isZero();
    assertThat(adaptation.getConfig().tendingIntervalMillis).isEqualTo(1_000L);
  }

  @Test
  void aReloadedCropListIsParsedAgain() {
    assertThat(adaptation.crops().kind(Id.minecraft("carrots"))).isEqualTo(HarvestCrops.Kind.CROP);

    assertThat(adaptation.reloadConfigSnapshot("crops = [\"WHEAT\"]\n", dataFolder.toPath().resolve("crops.toml").toFile(), false)).isTrue();

    assertThat(adaptation.crops().kind(Id.minecraft("carrots"))).isEqualTo(HarvestCrops.Kind.NONE);
    assertThat(adaptation.crops().kind(Id.minecraft("wheat"))).isEqualTo(HarvestCrops.Kind.CROP);
  }

  @Test
  void eachGolemTendsOncePerTendingInterval() {
    assertThat(adaptation.tendReady(GOLEM, 1_000L)).isTrue();

    adaptation.tended(GOLEM, 1_000L);

    assertThat(adaptation.tendReady(GOLEM, 10_999L)).isFalse();
    assertThat(adaptation.tendReady(GOLEM, 11_000L)).isTrue();
    assertThat(adaptation.hasTickDemand()).isTrue();
    adaptation.sweep(11_000L);
    assertThat(adaptation.hasTickDemand()).isFalse();
  }

  @Test
  void activatingTheRuntimeInstallsTheHarvestNodeAndHarvesterWorkUntilUnregistered() {
    assertThat(NodeBehaviors.of(NodeType.HARVEST)).isEmpty();
    assertThat(GolemCoreBehaviors.of(GolemCoreType.HARVESTER)).isEmpty();

    adaptation.onRuntimeActivated();

    assertThat(NodeBehaviors.of(NodeType.HARVEST)).hasValueSatisfying(behavior -> assertThat(behavior.policy()).isSameAs(adaptation));
    assertThat(GolemCoreBehaviors.of(GolemCoreType.HARVESTER)).hasValueSatisfying(behavior -> assertThat(behavior.policy()).isSameAs(adaptation));

    adaptation.unregister();

    assertThat(NodeBehaviors.of(NodeType.HARVEST)).isEmpty();
    assertThat(GolemCoreBehaviors.of(GolemCoreType.HARVESTER)).isEmpty();
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

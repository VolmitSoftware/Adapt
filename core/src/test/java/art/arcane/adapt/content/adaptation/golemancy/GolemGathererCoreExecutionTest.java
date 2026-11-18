package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.golem.GolemCapacity;
import art.arcane.adapt.api.golem.GolemCoreBehaviors;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemGathererCoreExecutionTest extends AdaptTestBase {
  private static final Id CORE_KEY = Id.adapt("golemancy-gatherer-core");
  private static final Id NODE_KEY = Id.adapt("golemancy-collection-node");

  private final Map<String, Id> materials = new HashMap<>();
  private final Map<Id, ItemRef> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private GolemRegistrations<GolemCapacity.Source> carrySources;
  private GolemGathererCore adaptation;

  @BeforeEach
  void construct() {
    for (String name : List.of("BUNDLE", "STRING", "HOPPER", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/gatherer");
    result("node/collection");
    result("blank_core");
    result("blank_node");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.platform(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    carrySources = GolemRegistrations.carry();
    adaptation = new GolemGathererCore();
  }

  @AfterEach
  void close() {
    adaptation.unregister();
    carrySources.close();
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
    GolemGathererCore.Config config = new GolemGathererCore.Config();

    assertThat(config.gathererCoreRecipe.getRows()).containsExactly(" X ", "YBY", " Z ");
    assertThat(config.gathererCoreRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "BUNDLE", "Y", "STRING",
        "Z", "HOPPER", "B", "golemancy:blank_core"));
    assertThat(config.gathererCoreRecipe.getResult()).isEqualTo("golemancy:core/gatherer");
    assertThat(config.gathererCoreRecipe.getAmount()).isEqualTo(1);
    assertThat(config.collectionNodeRecipe.getRows()).containsExactly(" X ", " B ", "   ");
    assertThat(config.collectionNodeRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "BUNDLE", "B", "golemancy:blank_node"));
    assertThat(config.collectionNodeRecipe.getResult()).isEqualTo("golemancy:node/collection");
  }

  @Test
  void theDefaultsFollowThePlan() {
    GolemGathererCore.Config config = new GolemGathererCore.Config();

    assertThat(config.pickupRadiusByLevel).containsExactly(4, 6, 8, 10, 12);
    assertThat(config.essenceCapByLevel).containsExactly(0, 500, 1500, 1500, 1500);
    assertThat(config.magnetLevel).isEqualTo(4);
    assertThat(config.magnetRadius).isEqualTo(3D);
    assertThat(config.instantPickupLevel).isEqualTo(5);
    assertThat(config.instantPickupRadius).isEqualTo(1.5D);
    assertThat(config.carryBonusLevel).isEqualTo(5);
    assertThat(config.carryBonusFraction).isEqualTo(0.5D);
    assertThat(config.foreignItemSeconds).isEqualTo(30);
    assertThat(config.deathSiteRadius).isEqualTo(8D);
    assertThat(config.deathSiteSeconds).isEqualTo(300);
    assertThat(config.workXpPerStack).isEqualTo(0.5D);
  }

  @Test
  void configurationLoadsAndEveryLevelHasAPositiveCostAndProgress() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-gatherer-core");
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
  void everyLevelShowsItsPickupRadiusAndTheUnlocksItHasReached() {
    assertThat(lore(1)).containsExactly("+ 4 Block Pickup Radius");
    assertThat(lore(2)).containsExactly("+ 6 Block Pickup Radius", "+ 500 Experience Points of Essence Storage");
    assertThat(lore(3)).containsExactly("+ 8 Block Pickup Radius", "+ 1500 Experience Points of Essence Storage");
    assertThat(lore(4)).containsExactly("+ 10 Block Pickup Radius", "+ 1500 Experience Points of Essence Storage", "+ 3 Block Item Magnet");
    assertThat(lore(5)).containsExactly("+ 12 Block Pickup Radius", "+ 1500 Experience Points of Essence Storage", "+ 3 Block Item Magnet",
        "+ 1.5 Block Instant Pickup", "+ 50% Carry Capacity");
  }

  @Test
  void aDisabledReloadTurnsTheAdaptationOff() {
    assertThat(adaptation.reloadConfigSnapshot("enabled = false\nmaxLevel = 1\n", dataFolder.toPath().resolve("disabled.toml").toFile(), false)).isTrue();
    assertThat(adaptation.isEnabled()).isFalse();
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
    assertThat(adaptation.getLevelPercent(2)).isEqualTo(1D);
  }

  @Test
  void loadingClampsRadiiTimesAndFractions() {
    assertThat(adaptation.reloadConfigSnapshot("magnetRadius = 90.0\ninstantPickupRadius = -2.0\ndeathSiteRadius = 500.0\ndeathSiteSeconds = -9\n"
        + "foreignItemSeconds = 99999\ncarryBonusFraction = -1.0\nworkXpPerStack = -3.0\n", dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    GolemGathererCore.Config config = adaptation.getConfig();
    assertThat(config.magnetRadius).isEqualTo(GathererRules.MAX_MAGNET_RADIUS);
    assertThat(config.instantPickupRadius).isZero();
    assertThat(config.deathSiteRadius).isEqualTo(GathererRules.MAX_DEATH_SITE_RADIUS);
    assertThat(config.deathSiteSeconds).isZero();
    assertThat(config.foreignItemSeconds).isEqualTo(GathererRules.MAX_FOREIGN_ITEM_SECONDS);
    assertThat(config.carryBonusFraction).isZero();
    assertThat(config.workXpPerStack).isZero();
  }

  @Test
  void activatingTheRuntimeInstallsTheGathererWorkCollectionNodesAndCarryBonusUntilUnregistered() {
    assertThat(GolemCoreBehaviors.of(GolemCoreType.GATHERER)).isEmpty();
    assertThat(NodeBehaviors.of(NodeType.COLLECTION)).isEmpty();

    adaptation.onRuntimeActivated();

    assertThat(GolemCoreBehaviors.of(GolemCoreType.GATHERER)).hasValueSatisfying(behavior -> assertThat(behavior.policy()).isSameAs(adaptation));
    assertThat(NodeBehaviors.of(NodeType.COLLECTION)).hasValueSatisfying(behavior -> assertThat(behavior.policy()).isSameAs(adaptation));
    assertThat(carrySources.registered()).extracting(GolemCapacity.Source::id).contains(GolemGathererCore.CARRY_SOURCE);

    adaptation.unregister();

    assertThat(GolemCoreBehaviors.of(GolemCoreType.GATHERER)).isEmpty();
    assertThat(NodeBehaviors.of(NodeType.COLLECTION)).isEmpty();
    assertThat(carrySources.unregistered()).isEqualTo(carrySources.registered());
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
      return GolemGathererCoreExecutionTest.item(type, amount);
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

package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.golem.GolemBrainSettings;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemancyRuntime;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemGuardianCoreExecutionTest extends AdaptTestBase {
  private static final Id CORE_KEY = Id.adapt("golemancy-guardian-core");
  private static final Id NODE_KEY = Id.adapt("golemancy-guard-node");

  private final Map<String, Id> materials = new HashMap<>();
  private final Map<Id, ItemRef> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private MockedStatic<GolemancyRuntime> runtime;
  private GolemGuardianCore adaptation;

  @BeforeEach
  void construct() {
    for (String name : List.of("IRON_SWORD", "IRON_INGOT", "SHIELD", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/guardian");
    result("node/guard");
    result("blank_core");
    result("blank_node");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.platform(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::brainSettings).thenReturn(GolemBrainSettings.defaults());
    adaptation = new GolemGuardianCore();
  }

  @AfterEach
  void close() {
    runtime.close();
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
    GolemGuardianCore.Config config = new GolemGuardianCore.Config();

    assertThat(config.guardianCoreRecipe.getRows()).containsExactly(" X ", "YBY", " Z ");
    assertThat(config.guardianCoreRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "IRON_SWORD", "Y", "IRON_INGOT",
        "Z", "SHIELD", "B", "golemancy:blank_core"));
    assertThat(config.guardianCoreRecipe.getResult()).isEqualTo("golemancy:core/guardian");
    assertThat(config.guardianCoreRecipe.getAmount()).isEqualTo(1);
    assertThat(config.guardNodeRecipe.getRows()).containsExactly(" X ", " B ", "   ");
    assertThat(config.guardNodeRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "IRON_SWORD", "B", "golemancy:blank_node"));
    assertThat(config.guardNodeRecipe.getResult()).isEqualTo("golemancy:node/guard");
  }

  @Test
  void configurationLoadsAndEveryLevelHasAPositiveCostAndProgress() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-guardian-core");
    assertThat(adaptation.getMaxLevel()).isEqualTo(5);
    assertThat(adaptation.getConfig().requires).containsExactly("golem-animus-binding:1");
    double previous = 0D;
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
      assertThat(adaptation.getLevelPercent(level)).isBetween(previous, 1D);
      previous = adaptation.getLevelPercent(level);
    }
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
  }

  @Test
  void everyLevelShowsItsGuardRadiusAndTheUnlocksItHasReached() {
    assertThat(lore(1)).containsExactly("+ 8 Block Guard Radius");
    assertThat(lore(2)).containsExactly("+ 12 Block Guard Radius", "+ Intercept: Mobs Chasing You Turn on Your Guardians");
    assertThat(lore(3)).containsExactly("+ 16 Block Guard Radius", "+ Intercept: Mobs Chasing You Turn on Your Guardians",
        "+ Retaliate: Guardians Attack Whatever Hurt You");
    assertThat(lore(4)).containsExactly("+ 20 Block Guard Radius", "+ Intercept: Mobs Chasing You Turn on Your Guardians",
        "+ Retaliate: Guardians Attack Whatever Hurt You", "+ 15% Less Damage Near Your Guardians");
    assertThat(lore(5)).containsExactly("+ 24 Block Guard Radius", "+ Intercept: Mobs Chasing You Turn on Your Guardians",
        "+ Retaliate: Guardians Attack Whatever Hurt You", "+ 15% Less Damage Near Your Guardians",
        "+ 5 Seconds of Last Stand Resistance");
  }

  @Test
  void theLoreShowsTheGuardRadiusClampedToTheLeash() {
    runtime.when(GolemancyRuntime::brainSettings).thenReturn(new GolemBrainSettings(5, 32D, 18D, 8, 10));

    assertThat(lore(5)).first().isEqualTo("+ 18 Block Guard Radius");
  }

  @Test
  void aDisabledReloadTurnsTheAdaptationOff() {
    assertThat(adaptation.reloadConfigSnapshot("enabled = false\nmaxLevel = 1\n", dataFolder.toPath().resolve("disabled.toml").toFile(), false)).isTrue();
    assertThat(adaptation.isEnabled()).isFalse();
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
    assertThat(adaptation.getLevelPercent(2)).isEqualTo(1D);
  }

  @Test
  void loadingClampsRangesAndFractions() {
    assertThat(adaptation.reloadConfigSnapshot("sentryRange = 90.0\nbulwarkRadius = -3.0\nbulwarkDamageReduction = 1.5\nlastStandHealthFraction = -0.2\n"
        + "interceptIntervalMillis = 50\n", dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    assertThat(adaptation.getConfig().sentryRange).isEqualTo(10D);
    assertThat(adaptation.getConfig().interceptIntervalMillis).isEqualTo(500L);
    assertThat(adaptation.getConfig().bulwarkRadius).isZero();
    assertThat(adaptation.getConfig().bulwarkDamageReduction).isEqualTo(1D);
    assertThat(adaptation.getConfig().lastStandHealthFraction).isZero();
  }

  @Test
  void activatingTheRuntimeInstallsTheGuardianPolicyAndGuardNodesUntilUnregistered() {
    assertThat(NodeBehaviors.of(NodeType.GUARD)).isEmpty();

    adaptation.onRuntimeActivated();

    runtime.verify(() -> GolemancyRuntime.setTargetPolicy(eq(GolemCoreType.GUARDIAN), any(GuardianTargetPolicy.class)));
    assertThat(NodeBehaviors.of(NodeType.GUARD)).hasValueSatisfying(behavior -> assertThat(behavior.policy()).isSameAs(adaptation));

    adaptation.unregister();

    runtime.verify(() -> GolemancyRuntime.clearTargetPolicy(eq(GolemCoreType.GUARDIAN), any(GuardianTargetPolicy.class)));
    assertThat(NodeBehaviors.of(NodeType.GUARD)).isEmpty();
  }

  private List<String> lore(int level) {
    List<String> lines = new ArrayList<>();
    StatLines stats = new StatLines();
    adaptation.addStats(level, stats);
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
      return GolemGuardianCoreExecutionTest.item(type, amount);
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

package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemBrainSettings;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.NodeType;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemGuardianCoreExecutionTest extends AdaptTestBase {
  private static final NamespacedKey CORE_KEY = new NamespacedKey("adapt", "golemancy-guardian-core");
  private static final NamespacedKey NODE_KEY = new NamespacedKey("adapt", "golemancy-guard-node");

  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private MockedStatic<GolemancyRuntime> runtime;
  private GolemGuardianCore adaptation;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    for (String name : List.of("IRON_SWORD", "IRON_INGOT", "SHIELD", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/guardian");
    result("node/guard");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
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
  void theCoreAndNodeRecipesRegisterAtLevelOneUnderGolemancyKeysWithTheirTithes() {
    List<AdaptRecipe> recipes = adaptation.getRecipes();

    assertThat(recipes).extracting(AdaptRecipe::getNSKey).containsExactly(CORE_KEY, NODE_KEY);
    assertThat(recipes).allMatch(recipe -> recipe.getRequiredLevel() == 1);
    assertThat(((AdaptRecipe.Shaped) recipes.get(0)).getShapes()).containsExactly(" X ", "YBY", " Z ");
    assertThat(((AdaptRecipe.Shaped) recipes.get(1)).getShapes()).containsExactly(" X ", " B ", "   ");
    assertThat(book.satisfiedBy(CORE_KEY, List.of(mock(ItemStack.class)))).isTrue();
    assertThat(book.satisfiedBy(NODE_KEY, List.of(mock(ItemStack.class)))).isTrue();
    assertThat(GolemItems.recipeTithe().levels(keyed(CORE_KEY))).isEqualTo(2);
    assertThat(GolemItems.recipeTithe().levels(keyed(NODE_KEY))).isZero();
  }

  @Test
  void theDefaultRecipesFollowThePlan() {
    GolemGuardianCore.Config config = new GolemGuardianCore.Config();

    assertThat(config.guardianCoreRecipe.getRows()).containsExactly(" X ", "YBY", " Z ");
    assertThat(config.guardianCoreRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "IRON_SWORD", "Y", "IRON_INGOT",
        "Z", "SHIELD", "B", "golemancy:blank_core"));
    assertThat(config.guardianCoreRecipe.getResult()).isEqualTo("golemancy:core/guardian");
    assertThat(config.guardianCoreRecipe.getAmount()).isEqualTo(1);
    assertThat(config.guardianCoreRecipe.getTithe()).isEqualTo(2);
    assertThat(config.guardNodeRecipe.getRows()).containsExactly(" X ", " B ", "   ");
    assertThat(config.guardNodeRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "IRON_SWORD", "B", "golemancy:blank_node"));
    assertThat(config.guardNodeRecipe.getResult()).isEqualTo("golemancy:node/guard");
    assertThat(config.guardNodeRecipe.getTithe()).isZero();
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
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
      assertThat(adaptation.isEnabled()).isFalse();
    }
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

package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemCapacity;
import art.arcane.adapt.api.golem.GolemCoreBehaviors;
import art.arcane.adapt.api.golem.GolemCoreType;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemGathererCoreExecutionTest extends AdaptTestBase {
  private static final NamespacedKey CORE_KEY = new NamespacedKey("adapt", "golemancy-gatherer-core");
  private static final NamespacedKey NODE_KEY = new NamespacedKey("adapt", "golemancy-collection-node");

  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private GolemRegistrations<GolemCapacity.Source> carrySources;
  private GolemGathererCore adaptation;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    for (String name : List.of("BUNDLE", "STRING", "HOPPER", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/gatherer");
    result("node/collection");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
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
    GolemGathererCore.Config config = new GolemGathererCore.Config();

    assertThat(config.gathererCoreRecipe.getRows()).containsExactly(" X ", "YBY", " Z ");
    assertThat(config.gathererCoreRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "BUNDLE", "Y", "STRING",
        "Z", "HOPPER", "B", "golemancy:blank_core"));
    assertThat(config.gathererCoreRecipe.getResult()).isEqualTo("golemancy:core/gatherer");
    assertThat(config.gathererCoreRecipe.getAmount()).isEqualTo(1);
    assertThat(config.gathererCoreRecipe.getTithe()).isEqualTo(2);
    assertThat(config.collectionNodeRecipe.getRows()).containsExactly(" X ", " B ", "   ");
    assertThat(config.collectionNodeRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "BUNDLE", "B", "golemancy:blank_node"));
    assertThat(config.collectionNodeRecipe.getResult()).isEqualTo("golemancy:node/collection");
    assertThat(config.collectionNodeRecipe.getTithe()).isZero();
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
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
      assertThat(adaptation.isEnabled()).isFalse();
    }
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

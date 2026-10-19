package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemHarvesterCoreExecutionTest extends AdaptTestBase {
  private static final NamespacedKey CORE_KEY = new NamespacedKey("adapt", "golemancy-harvester-core");
  private static final NamespacedKey NODE_KEY = new NamespacedKey("adapt", "golemancy-harvest-node");
  private static final UUID GOLEM = UUID.fromString("00000000-0000-0000-0000-0000000022e1");

  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private GolemHarvesterCore adaptation;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    for (String name : List.of("IRON_HOE", "WHEAT", "COMPOSTER", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/harvester");
    result("node/harvest");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    adaptation = new GolemHarvesterCore();
  }

  @AfterEach
  void close() {
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
    GolemHarvesterCore.Config config = new GolemHarvesterCore.Config();

    assertThat(config.harvesterCoreRecipe.getRows()).containsExactly(" X ", "YBY", " Z ");
    assertThat(config.harvesterCoreRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "IRON_HOE", "Y", "WHEAT",
        "Z", "COMPOSTER", "B", "golemancy:blank_core"));
    assertThat(config.harvesterCoreRecipe.getResult()).isEqualTo("golemancy:core/harvester");
    assertThat(config.harvesterCoreRecipe.getAmount()).isEqualTo(1);
    assertThat(config.harvesterCoreRecipe.getTithe()).isEqualTo(2);
    assertThat(config.harvestNodeRecipe.getRows()).containsExactly(" X ", " B ", "   ");
    assertThat(config.harvestNodeRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "IRON_HOE", "B", "golemancy:blank_node"));
    assertThat(config.harvestNodeRecipe.getResult()).isEqualTo("golemancy:node/harvest");
    assertThat(config.harvestNodeRecipe.getTithe()).isZero();
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
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
      assertThat(adaptation.isEnabled()).isFalse();
    }
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
    assertThat(adaptation.crops().kind(Material.CARROTS)).isEqualTo(HarvestCrops.Kind.CROP);

    assertThat(adaptation.reloadConfigSnapshot("crops = [\"WHEAT\"]\n", dataFolder.toPath().resolve("crops.toml").toFile(), false)).isTrue();

    assertThat(adaptation.crops().kind(Material.CARROTS)).isEqualTo(HarvestCrops.Kind.NONE);
    assertThat(adaptation.crops().kind(Material.WHEAT)).isEqualTo(HarvestCrops.Kind.CROP);
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

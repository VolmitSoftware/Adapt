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
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class GolemBreakerCoreExecutionTest extends AdaptTestBase {
  private static final NamespacedKey CORE_KEY = new NamespacedKey("adapt", "golemancy-breaker-core");
  private static final NamespacedKey NODE_KEY = new NamespacedKey("adapt", "golemancy-breaker-node");

  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private GolemBreakerCore adaptation;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    for (String name : List.of("IRON_PICKAXE", "REDSTONE", "PISTON", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/breaker");
    result("node/breaker");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    adaptation = new GolemBreakerCore();
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
    GolemBreakerCore.Config config = new GolemBreakerCore.Config();

    assertThat(config.breakerCoreRecipe.getRows()).containsExactly(" X ", "YBY", " Z ");
    assertThat(config.breakerCoreRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "IRON_PICKAXE", "Y", "REDSTONE",
        "Z", "PISTON", "B", "golemancy:blank_core"));
    assertThat(config.breakerCoreRecipe.getResult()).isEqualTo("golemancy:core/breaker");
    assertThat(config.breakerCoreRecipe.getAmount()).isEqualTo(1);
    assertThat(config.breakerNodeRecipe.getRows()).containsExactly(" X ", " B ", "   ");
    assertThat(config.breakerNodeRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "IRON_PICKAXE", "B", "golemancy:blank_node"));
    assertThat(config.breakerNodeRecipe.getResult()).isEqualTo("golemancy:node/breaker");
  }

  @Test
  void theDefaultsFollowThePlan() {
    GolemBreakerCore.Config config = new GolemBreakerCore.Config();

    assertThat(config.intervalTicksByLevel).containsExactly(40, 30, 20, 15, 10);
    assertThat(config.maxHardnessByLevel).containsExactly(2.0D, 3.0D, 5.0D, 10.0D, 50.0D);
    assertThat(config.toolByLevel).containsExactly("STONE_PICKAXE", "IRON_PICKAXE", "IRON_PICKAXE", "DIAMOND_PICKAXE", "NETHERITE_PICKAXE");
    assertThat(config.silkTouchLevel).isEqualTo(3);
    assertThat(config.fortuneLevel).isEqualTo(5);
    assertThat(config.blacklist).containsExactly("BEDROCK", "BARRIER", "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK",
        "STRUCTURE_BLOCK", "STRUCTURE_VOID", "JIGSAW", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "END_PORTAL_FRAME", "REINFORCED_DEEPSLATE",
        "BUDDING_AMETHYST", "#beds", "#doors");
    assertThat(config.workXpPerBlock).isEqualTo(0.4D);
  }

  @Test
  void configurationLoadsAndEveryLevelHasAPositiveCostAndProgress() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-breaker-core");
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
  void everyLevelShowsItsHardnessPaceToolAndUnlocks() {
    String silk = "+ Silk Touch Toggle on Breaker Nodes";
    String fortune = "+ Fortune I Toggle on Breaker Nodes";

    try (MockedStatic<Bukkit> bukkit = pickaxes()) {
      assertThat(lore(1)).containsExactly("+ 2 Max Block Hardness", "* 2 Seconds per Block", "+ Stone Pickaxe Mining Tool");
      assertThat(lore(2)).containsExactly("+ 3 Max Block Hardness", "* 1.5 Seconds per Block", "+ Iron Pickaxe Mining Tool");
      assertThat(lore(3)).containsExactly("+ 5 Max Block Hardness", "* 1 Seconds per Block", "+ Iron Pickaxe Mining Tool", silk);
      assertThat(lore(4)).containsExactly("+ 10 Max Block Hardness", "* 0.75 Seconds per Block", "+ Diamond Pickaxe Mining Tool", silk);
      assertThat(lore(5)).containsExactly("+ 50 Max Block Hardness", "* 0.5 Seconds per Block", "+ Netherite Pickaxe Mining Tool", silk, fortune);
    }
  }

  @Test
  void configuredToolsThatAreNotPickaxesUseTheLevelDefaultAndReloadsParseAgain() {
    try (MockedStatic<Bukkit> bukkit = pickaxes()) {
      assertThat(adaptation.pickaxe(4)).isEqualTo(Material.DIAMOND_PICKAXE);

      assertThat(adaptation.reloadConfigSnapshot("toolByLevel = [\"DIAMOND_SHOVEL\", \"GOLDEN_PICKAXE\", \"STICK\"]\n",
          dataFolder.toPath().resolve("tools.toml").toFile(), false)).isTrue();

      assertThat(adaptation.pickaxe(1)).isEqualTo(Material.STONE_PICKAXE);
      assertThat(adaptation.pickaxe(2)).isEqualTo(Material.GOLDEN_PICKAXE);
      assertThat(adaptation.pickaxe(3)).isEqualTo(Material.IRON_PICKAXE);
      assertThat(adaptation.pickaxe(5)).isEqualTo(Material.IRON_PICKAXE);
    }
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
  void loadingClampsTheWorkXp() {
    assertThat(adaptation.reloadConfigSnapshot("workXpPerBlock = -2.0\n", dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    assertThat(adaptation.getConfig().workXpPerBlock).isZero();
  }

  @Test
  void aReloadedBlacklistIsParsedAgainWithItsBlockTags() {
    Tag<Material> slabs = new FakeTag(NamespacedKey.minecraft("slabs"), Set.of(Material.STONE_SLAB));
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft("slabs"), Material.class)).thenReturn(slabs);
      assertThat(adaptation.blacklist()).contains(Material.BEDROCK).doesNotContain(Material.STONE);

      assertThat(adaptation.reloadConfigSnapshot("blacklist = [\"STONE\", \"#slabs\", \"#minecraft:missing\"]\n",
          dataFolder.toPath().resolve("blacklist.toml").toFile(), false)).isTrue();

      assertThat(adaptation.blacklist()).containsExactlyInAnyOrder(Material.STONE, Material.STONE_SLAB);
      assertThat(adaptation.blacklist()).isSameAs(adaptation.blacklist());
    }
  }

  @Test
  void blockEntityTypesAreRecognizedFromTheirTypeOnce() {
    BlockData chest = blockData(mock(TileState.class));
    BlockData stone = blockData(mock(BlockState.class));
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.createBlockData(Material.CHEST)).thenReturn(chest);
      bukkit.when(() -> Bukkit.createBlockData(Material.STONE)).thenReturn(stone);

      assertThat(adaptation.tileEntity(Material.CHEST)).isTrue();
      assertThat(adaptation.tileEntity(Material.CHEST)).isTrue();
      assertThat(adaptation.tileEntity(Material.STONE)).isFalse();
      bukkit.verify(() -> Bukkit.createBlockData(Material.CHEST), times(1));
    }
  }

  @Test
  void activatingTheRuntimeInstallsTheBreakerNodeAndPanelUntilUnregistered() {
    assertThat(NodeBehaviors.of(NodeType.BREAKER)).isEmpty();
    assertThat(NodePanels.of(NodeType.BREAKER)).isEmpty();

    adaptation.onRuntimeActivated();

    assertThat(NodeBehaviors.of(NodeType.BREAKER)).hasValueSatisfying(behavior -> assertThat(behavior.policy()).isSameAs(adaptation));
    assertThat(NodePanels.of(NodeType.BREAKER)).isPresent();

    adaptation.unregister();

    assertThat(NodeBehaviors.of(NodeType.BREAKER)).isEmpty();
    assertThat(NodePanels.of(NodeType.BREAKER)).isEmpty();
  }


  private static BlockData blockData(BlockState state) {
    BlockData data = mock(BlockData.class);
    when(data.createBlockState()).thenReturn(state);
    return data;
  }

  private static MockedStatic<Bukkit> pickaxes() {
    Tag<Material> tag = new FakeTag(NamespacedKey.minecraft("pickaxes"), Set.of(Material.STONE_PICKAXE, Material.IRON_PICKAXE,
        Material.GOLDEN_PICKAXE, Material.DIAMOND_PICKAXE, Material.NETHERITE_PICKAXE));
    MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    bukkit.when(() -> Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft("pickaxes"), Material.class)).thenReturn(tag);
    return bukkit;
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

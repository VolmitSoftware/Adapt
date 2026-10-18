package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
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
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemLumberCoreExecutionTest extends AdaptTestBase {
  private static final NamespacedKey CORE_KEY = new NamespacedKey("adapt", "golemancy-lumber-core");
  private static final NamespacedKey NODE_KEY = new NamespacedKey("adapt", "golemancy-lumber-node");
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-0000000023e1");

  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private GolemLumberCore adaptation;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    for (String name : List.of("IRON_AXE", "OAK_LOG", "OAK_SAPLING", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/lumber");
    result("node/lumber");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    adaptation = new GolemLumberCore();
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
    assertThat(book.required(CORE_KEY)).hasSize(1);
    assertThat(book.required(NODE_KEY)).hasSize(1);
    assertThat(GolemItems.recipeTithe().levels(keyed(CORE_KEY))).isEqualTo(2);
    assertThat(GolemItems.recipeTithe().levels(keyed(NODE_KEY))).isZero();
  }

  @Test
  void theDefaultRecipesFollowThePlan() {
    GolemLumberCore.Config config = new GolemLumberCore.Config();

    assertThat(config.lumberCoreRecipe.getRows()).containsExactly(" X ", "YBY", " Z ");
    assertThat(config.lumberCoreRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "IRON_AXE", "Y", "#logs",
        "Z", "#saplings", "B", "golemancy:blank_core"));
    assertThat(config.lumberCoreRecipe.getResult()).isEqualTo("golemancy:core/lumber");
    assertThat(config.lumberCoreRecipe.getAmount()).isEqualTo(1);
    assertThat(config.lumberCoreRecipe.getTithe()).isEqualTo(2);
    assertThat(config.lumberNodeRecipe.getRows()).containsExactly(" X ", " B ", "   ");
    assertThat(config.lumberNodeRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "IRON_AXE", "B", "golemancy:blank_node"));
    assertThat(config.lumberNodeRecipe.getResult()).isEqualTo("golemancy:node/lumber");
    assertThat(config.lumberNodeRecipe.getTithe()).isZero();
  }

  @Test
  void theDefaultsFollowThePlan() {
    GolemLumberCore.Config config = new GolemLumberCore.Config();

    assertThat(config.logsPerSecondByLevel).containsExactly(1.0D, 1.5D, 2.0D, 3.0D, 4.0D);
    assertThat(config.copperTreeCapByLevel).containsExactly(24, 32, 48, 64, 96);
    assertThat(config.ironTreeCapByLevel).containsExactly(64, 96, 128, 192, 256);
    assertThat(List.of(config.replantLevel, config.leafSweepLevel, config.megaTreeLevel, config.netherLevel)).containsExactly(2, 3, 4, 5);
    assertThat(config.workXpPerLog).isEqualTo(0.6D);
  }

  @Test
  void configurationLoadsAndEveryLevelHasAPositiveCostAndProgress() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-lumber-core");
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
  void everyLevelShowsItsPaceCapsAndTheUnlocksItHasReached() {
    String replant = "+ Replants Saplings After Felling";
    String sweep = "+ Leaf Sweep: Breaks Loose Leaves and Collects Their Drops";
    String mega = "+ Fells 2x2 Mega Trees";
    String nether = "+ Fells Crimson and Warped Fungi and Replants Fungus";

    assertThat(lore(1)).containsExactly("+ 1 Logs Felled per Second", "+ 24 Logs per Tree for Copper Golems", "+ 64 Logs per Tree for Iron Golems");
    assertThat(lore(2)).containsExactly("+ 1.5 Logs Felled per Second", "+ 32 Logs per Tree for Copper Golems", "+ 96 Logs per Tree for Iron Golems",
        replant);
    assertThat(lore(3)).containsExactly("+ 2 Logs Felled per Second", "+ 48 Logs per Tree for Copper Golems", "+ 128 Logs per Tree for Iron Golems",
        replant, sweep);
    assertThat(lore(4)).containsExactly("+ 3 Logs Felled per Second", "+ 64 Logs per Tree for Copper Golems", "+ 192 Logs per Tree for Iron Golems",
        replant, sweep, mega);
    assertThat(lore(5)).containsExactly("+ 4 Logs Felled per Second", "+ 96 Logs per Tree for Copper Golems", "+ 256 Logs per Tree for Iron Golems",
        replant, sweep, mega, nether);
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
    assertThat(adaptation.reloadConfigSnapshot("workXpPerLog = -2.0\n", dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    assertThat(adaptation.getConfig().workXpPerLog).isZero();
  }

  @Test
  void fellingsAreSweptOnTickUntilNoneAreLeft() {
    LumberTrees.Tree tree = LumberTrees.survey(LumberTreesTest.oak(), 0, 64, 0, new LumberTrees.Reach(false, false, 64)).tree();
    assertThat(adaptation.hasTickDemand()).isFalse();

    adaptation.fellings().start(WORLD, tree, 1_000L);

    assertThat(adaptation.hasTickDemand()).isTrue();
    adaptation.sweep(60_999L);
    assertThat(adaptation.hasTickDemand()).isTrue();
    adaptation.sweep(61_000L);
    assertThat(adaptation.hasTickDemand()).isFalse();
  }

  @Test
  void activatingTheRuntimeInstallsTheLumberNodeUntilUnregistered() {
    assertThat(NodeBehaviors.of(NodeType.LUMBER)).isEmpty();
    LumberTrees.Tree tree = LumberTrees.survey(LumberTreesTest.oak(), 0, 64, 0, new LumberTrees.Reach(false, false, 64)).tree();

    adaptation.onRuntimeActivated();
    adaptation.fellings().start(WORLD, tree, 1_000L);

    assertThat(NodeBehaviors.of(NodeType.LUMBER)).hasValueSatisfying(behavior -> assertThat(behavior.policy()).isSameAs(adaptation));
    assertThat(GolemCoreType.LUMBER.id()).isEqualTo("lumber");

    adaptation.unregister();

    assertThat(NodeBehaviors.of(NodeType.LUMBER)).isEmpty();
    assertThat(adaptation.hasTickDemand()).isFalse();
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
      return switch (key.getKey()) {
        case "logs" -> new FakeTag(key, materials.get("OAK_LOG"));
        case "saplings" -> new FakeTag(key, materials.get("OAK_SAPLING"));
        default -> null;
      };
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

  private record FakeTag(NamespacedKey id, Material value) implements Tag<Material> {
    @Override
    public boolean isTagged(Material item) {
      return item == value;
    }

    @Override
    public Set<Material> getValues() {
      return Set.of(value);
    }

    @Override
    public NamespacedKey getKey() {
      return id;
    }
  }
}

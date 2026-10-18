package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemActResult;
import art.arcane.adapt.api.golem.GolemBody;
import art.arcane.adapt.api.golem.GolemContext;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemTarget;
import art.arcane.adapt.api.golem.GolemTask;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeBehavior;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.golem.node.NodeContext;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.ScanBudget;
import art.arcane.adapt.api.recipe.AdaptRecipe;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
import art.arcane.adapt.api.xp.XpProvenance;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GolemLumberCoreExecutionTest extends AdaptTestBase {
  private static final NamespacedKey CORE_KEY = new NamespacedKey("adapt", "golemancy-lumber-core");
  private static final NamespacedKey NODE_KEY = new NamespacedKey("adapt", "golemancy-lumber-node");
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000023e2");
  private static final NodeKey KEY = new NodeKey(CropField.WORLD, 0, 63, 0, BlockFace.UP);
  private static final long NOW = 40_000L;

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
  void aTreeStartedAtTheLumberNodeKeepsTheTickBusyUntilItGoesStale() {
    GolemLumberCore core = levelFive();
    core.onRuntimeActivated();
    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class)) {
      NodeBehavior lumber = NodeBehaviors.of(NodeType.LUMBER).orElseThrow();
      assertThat(core.hasTickDemand()).isFalse();

      assertThat(lumber.act(golem(oakField()), fellTask())).isEqualTo(GolemActResult.WORKING);

      assertThat(core.hasTickDemand()).isTrue();
      core.sweep(NOW + LumberFellings.STALE_MILLIS - 1L);
      assertThat(core.hasTickDemand()).isTrue();
      core.sweep(NOW + LumberFellings.STALE_MILLIS);
      assertThat(core.hasTickDemand()).isFalse();
    } finally {
      core.unregister();
    }
  }

  @Test
  void aTreeTheLumberNodeTurnsDownKeepsTheTickBusyUntilItIsLookedAtAgain() {
    GolemLumberCore core = levelFive();
    core.onRuntimeActivated();
    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class)) {
      CropField field = oakField();
      field.set(1, 65, 0, Material.OAK_PLANKS);
      NodeBehaviors.of(NodeType.LUMBER).orElseThrow().scan(node(field), budget());

      assertThat(core.hasTickDemand()).isTrue();
      core.sweep(NOW + LumberRejections.REJECT_MILLIS);
      assertThat(core.hasTickDemand()).isFalse();
    } finally {
      core.unregister();
    }
  }

  @Test
  void activatingTheRuntimeInstallsTheLumberNodeUntilUnregistered() {
    GolemLumberCore core = levelFive();
    assertThat(NodeBehaviors.of(NodeType.LUMBER)).isEmpty();

    core.onRuntimeActivated();
    NodeBehavior lumber = NodeBehaviors.of(NodeType.LUMBER).orElseThrow();
    try (MockedStatic<XpProvenance> provenance = mockStatic(XpProvenance.class)) {
      lumber.act(golem(oakField()), fellTask());
    }

    assertThat(lumber.policy()).isSameAs(core);
    assertThat(GolemCoreType.LUMBER.id()).isEqualTo("lumber");
    assertThat(core.hasTickDemand()).isTrue();

    core.unregister();

    assertThat(NodeBehaviors.of(NodeType.LUMBER)).isEmpty();
    assertThat(core.hasTickDemand()).isFalse();
  }

  private static GolemLumberCore levelFive() {
    return new GolemLumberCore() {
      @Override
      int ownerLevel(UUID owner) {
        return OWNER.equals(owner) ? 5 : 0;
      }
    };
  }

  private static CropField oakField() {
    CropField field = new CropField();
    field.set(0, 63, 0, Material.GRASS_BLOCK);
    for (int y = 64; y <= 68; y++) {
      field.set(0, y, 0, Material.OAK_LOG);
    }
    for (int x = -1; x <= 1; x++) {
      for (int z = -1; z <= 1; z++) {
        if (field.type(x, 69, z) == Material.AIR) {
          field.set(x, 69, z, Material.OAK_LEAVES);
        }
      }
    }
    return field;
  }

  private static NodeContext node(CropField field) {
    NodeRecord record = NodeRecord.builder(OWNER, NodeType.LUMBER, BlockFace.UP).radii(2, 2, 2).build();
    NodeContext node = mock(NodeContext.class);
    when(node.owner()).thenReturn(OWNER);
    when(node.key()).thenReturn(KEY);
    when(node.record()).thenReturn(record);
    when(node.world()).thenReturn(field.world);
    when(node.nowMillis()).thenReturn(NOW);
    when(node.owns(any(Block.class))).thenReturn(true);
    when(node.block(anyInt())).thenAnswer(invocation -> {
      int[] at = NodeArea.position(KEY, record, invocation.getArgument(0));
      return Optional.of(field.block(at[0], at[1], at[2]));
    });
    return node;
  }

  private static ScanBudget budget() {
    ScanBudget budget = mock(ScanBudget.class);
    when(budget.start()).thenReturn(0);
    when(budget.end()).thenReturn(125);
    when(budget.hasTaskRoom()).thenReturn(true);
    return budget;
  }

  private static GolemTask fellTask() {
    return GolemTask.builder(KEY, NodeType.LUMBER, LumberNodeBehavior.FELL, GolemTarget.block(CropField.WORLD, 0, 64, 0)).build();
  }

  private static GolemContext golem(CropField field) {
    GolemContext golem = mock(GolemContext.class);
    Player owner = mock(Player.class);
    when(owner.getUniqueId()).thenReturn(OWNER);
    GolemHandle handle = mock(GolemHandle.class);
    when(handle.body()).thenReturn(GolemBody.IRON);
    when(golem.owner()).thenReturn(owner);
    when(golem.handle()).thenReturn(handle);
    when(golem.node()).thenReturn(Optional.of(NodeRecord.builder(OWNER, NodeType.LUMBER, BlockFace.UP).radii(2, 2, 2).build()));
    when(golem.world()).thenReturn(field.world);
    when(golem.owns(any(Location.class))).thenReturn(true);
    when(golem.nowMillis()).thenReturn(NOW);
    when(golem.actIndex()).thenReturn(0);
    return golem;
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

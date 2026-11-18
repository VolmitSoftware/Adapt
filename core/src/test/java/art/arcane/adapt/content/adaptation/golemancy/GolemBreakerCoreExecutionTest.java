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
import art.arcane.adapt.platform.RegistryKind;
import art.arcane.adapt.platform.RegistryLookup;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemBreakerCoreExecutionTest extends AdaptTestBase {
  private static final Id CORE_KEY = Id.adapt("golemancy-breaker-core");
  private static final Id NODE_KEY = Id.adapt("golemancy-breaker-node");

  private final Map<String, Id> materials = new HashMap<>();
  private final Map<Id, ItemRef> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private final ItemOps items = mock(ItemOps.class);
  private final RegistryLookup registries = mock(RegistryLookup.class);
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private GolemBreakerCore adaptation;

  @BeforeEach
  void construct() {
    lenient().when(AdaptCore.platform().host().items()).thenReturn(items);
    lenient().when(AdaptCore.platform().host().registries()).thenReturn(registries);
    lenient().when(registries.exists(eq(RegistryKind.BLOCK), any(Id.class))).thenReturn(true);
    for (String name : List.of("IRON_PICKAXE", "REDSTONE", "PISTON", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/breaker");
    result("node/breaker");
    result("blank_core");
    result("blank_node");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.platform(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    adaptation = new GolemBreakerCore();
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
    String fortune = "+ Fortune Toggle on Breaker Nodes";
    String tiers = "+ Breaks Blocks Above Iron Tier with a Matching Pickaxe from the Home Chest";

    pickaxes();
    assertThat(lore(1)).containsExactly("+ 2 Max Block Hardness", "* 2 Seconds per Block", "+ Stone Pickaxe Mining Tool");
    assertThat(lore(2)).containsExactly("+ 3 Max Block Hardness", "* 1.5 Seconds per Block", "+ Iron Pickaxe Mining Tool");
    assertThat(lore(3)).containsExactly("+ 5 Max Block Hardness", "* 1 Seconds per Block", "+ Iron Pickaxe Mining Tool", silk);
    assertThat(lore(4)).containsExactly("+ 10 Max Block Hardness", "* 0.75 Seconds per Block", "+ Diamond Pickaxe Mining Tool", silk, tiers);
    assertThat(lore(5)).containsExactly("+ 50 Max Block Hardness", "* 0.5 Seconds per Block", "+ Netherite Pickaxe Mining Tool", silk, fortune, tiers);
  }

  @Test
  void configuredToolsThatAreNotPickaxesUseTheLevelDefaultAndReloadsParseAgain() {
    pickaxes();
    assertThat(adaptation.pickaxe(4)).isEqualTo(Id.minecraft("diamond_pickaxe"));

    assertThat(adaptation.reloadConfigSnapshot("toolByLevel = [\"DIAMOND_SHOVEL\", \"GOLDEN_PICKAXE\", \"STICK\"]\n",
        dataFolder.toPath().resolve("tools.toml").toFile(), false)).isTrue();

    assertThat(adaptation.pickaxe(1)).isEqualTo(Id.minecraft("stone_pickaxe"));
    assertThat(adaptation.pickaxe(2)).isEqualTo(Id.minecraft("golden_pickaxe"));
    assertThat(adaptation.pickaxe(3)).isEqualTo(Id.minecraft("iron_pickaxe"));
    assertThat(adaptation.pickaxe(5)).isEqualTo(Id.minecraft("iron_pickaxe"));
  }

  @Test
  void aDisabledReloadTurnsTheAdaptationOff() {
    assertThat(adaptation.reloadConfigSnapshot("enabled = false\nmaxLevel = 1\n", dataFolder.toPath().resolve("disabled.toml").toFile(), false)).isTrue();
    assertThat(adaptation.isEnabled()).isFalse();
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
    when(registries.tagValues(RegistryKind.BLOCK, Id.minecraft("slabs"))).thenReturn(Set.of(Id.minecraft("stone_slab")));
    assertThat(adaptation.blacklist()).contains(Id.minecraft("bedrock")).doesNotContain(Id.minecraft("stone"));

    assertThat(adaptation.reloadConfigSnapshot("blacklist = [\"STONE\", \"#slabs\", \"#minecraft:missing\"]\n",
        dataFolder.toPath().resolve("blacklist.toml").toFile(), false)).isTrue();

    assertThat(adaptation.blacklist()).containsExactlyInAnyOrder(Id.minecraft("stone"), Id.minecraft("stone_slab"));
    assertThat(adaptation.blacklist()).isSameAs(adaptation.blacklist());
  }

  @Test
  void blockEntityTypesAreRecognizedFromTheirTypeOnce() {
    when(items.hasBlockEntity(Id.minecraft("chest"))).thenReturn(true);
    when(items.hasBlockEntity(Id.minecraft("stone"))).thenReturn(false);

    assertThat(adaptation.tileEntity(Id.minecraft("chest"))).isTrue();
    assertThat(adaptation.tileEntity(Id.minecraft("chest"))).isTrue();
    assertThat(adaptation.tileEntity(Id.minecraft("stone"))).isFalse();
    verify(items, times(1)).hasBlockEntity(Id.minecraft("chest"));
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


  private void pickaxes() {
    Set<Id> tag = Set.of(Id.minecraft("stone_pickaxe"), Id.minecraft("iron_pickaxe"), Id.minecraft("golden_pickaxe"), Id.minecraft("diamond_pickaxe"),
        Id.minecraft("netherite_pickaxe"));
    when(registries.isTagged(eq(RegistryKind.ITEM), eq(Id.minecraft("pickaxes")), any(Id.class)))
        .thenAnswer(invocation -> tag.contains(invocation.<Id>getArgument(2)));
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
      return GolemBreakerCoreExecutionTest.item(type, amount);
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

package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemCoreBehaviors;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemTestItems;
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
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.entity.Cow;
import org.bukkit.entity.EntityType;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemRancherCoreExecutionTest extends AdaptTestBase {
  private static final NamespacedKey CORE_KEY = new NamespacedKey("adapt", "golemancy-rancher-core");
  private static final NamespacedKey NODE_KEY = new NamespacedKey("adapt", "golemancy-pasture-node");

  private final Map<String, Material> materials = new HashMap<>();
  private final Map<NamespacedKey, ItemStack> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private GolemRancherCore adaptation;

  @BeforeEach
  void construct() {
    when(plugin.namespace()).thenReturn("adapt");
    for (String name : List.of("SHEARS", "WHEAT", "LEAD", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/rancher");
    result("node/pasture");
    result("blank_core");
    result("blank_node");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.bukkit(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    adaptation = new GolemRancherCore();
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
    GolemRancherCore.Config config = new GolemRancherCore.Config();

    assertThat(config.rancherCoreRecipe.getRows()).containsExactly(" X ", "YBY", " Z ");
    assertThat(config.rancherCoreRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "SHEARS", "Y", "WHEAT",
        "Z", "LEAD", "B", "golemancy:blank_core"));
    assertThat(config.rancherCoreRecipe.getResult()).isEqualTo("golemancy:core/rancher");
    assertThat(config.rancherCoreRecipe.getAmount()).isEqualTo(1);
    assertThat(config.pastureNodeRecipe.getRows()).containsExactly(" X ", " B ", "   ");
    assertThat(config.pastureNodeRecipe.getKeys()).containsExactlyInAnyOrderEntriesOf(Map.of("X", "LEAD", "B", "golemancy:blank_node"));
    assertThat(config.pastureNodeRecipe.getResult()).isEqualTo("golemancy:node/pasture");
  }

  @Test
  void theDefaultsFollowThePlan() {
    GolemRancherCore.Config config = new GolemRancherCore.Config();

    assertThat(config.species).containsExactly("COW", "MOOSHROOM", "SHEEP", "PIG", "CHICKEN", "GOAT", "RABBIT");
    assertThat(config.defaultKeep).isEqualTo(8);
    assertThat(config.actionIntervalTicks).isEqualTo(40);
    assertThat(List.of(config.shearLevel, config.milkLevel, config.cullLevel, config.husbandryLevel)).containsExactly(2, 3, 4, 5);
    assertThat(config.husbandryCooldownMultiplier).isEqualTo(0.5D);
    assertThat(config.husbandryExtraLootRolls).isEqualTo(1);
    assertThat(List.of(config.workXpPerPair, config.workXpPerShear, config.workXpPerMilk, config.workXpPerCull)).containsExactly(4D, 2D, 1D, 1D);
  }

  @Test
  void configurationLoadsAndEveryLevelHasAPositiveCostAndProgress() {
    assertThat(adaptation.reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getName()).isEqualTo("golem-rancher-core");
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
  void everyLevelShowsTheActionsItHasUnlocked() {
    String breed = "+ Breeds Pairs up to the Pasture Node's Keep Count";
    String shear = "+ Shears Sheep";
    String milk = "+ Milks Cows, Goats and Mooshrooms with Buckets or Bowls from the Home Chest";
    String cull = "+ Culls Adults Above the Keep Count (Iron Golems Only)";

    assertThat(lore(1)).containsExactly(breed);
    assertThat(lore(2)).containsExactly(breed, shear);
    assertThat(lore(3)).containsExactly(breed, shear, milk);
    assertThat(lore(4)).containsExactly(breed, shear, milk, cull);
    assertThat(lore(5)).containsExactly(breed, shear, milk, cull, "+ 50% Shorter Breeding Cooldown", "+ 1 Extra Loot Roll per Cull");
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
  void loadingClampsTheTunables() {
    String snapshot = """
        defaultKeep = 100
        actionIntervalTicks = 0
        husbandryCooldownMultiplier = 3.0
        husbandryExtraLootRolls = -2
        workXpPerPair = -1.0
        workXpPerShear = -1.0
        workXpPerMilk = -1.0
        workXpPerCull = -1.0
        """;
    assertThat(adaptation.reloadConfigSnapshot(snapshot, dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    GolemRancherCore.Config config = adaptation.getConfig();
    assertThat(config.defaultKeep).isEqualTo(64);
    assertThat(config.actionIntervalTicks).isEqualTo(1);
    assertThat(config.husbandryCooldownMultiplier).isEqualTo(1D);
    assertThat(config.husbandryExtraLootRolls).isZero();
    assertThat(List.of(config.workXpPerPair, config.workXpPerShear, config.workXpPerMilk, config.workXpPerCull)).containsOnly(0D);
  }

  @Test
  void theTendedSpeciesFollowTheConfigList() {
    assertThat(adaptation.species()).containsExactly(EntityType.COW, EntityType.MOOSHROOM, EntityType.SHEEP, EntityType.PIG,
        EntityType.CHICKEN, EntityType.GOAT, EntityType.RABBIT);

    assertThat(adaptation.reloadConfigSnapshot("species = [\"SHEEP\", \"ZOMBIE\"]\n", dataFolder.toPath().resolve("species.toml").toFile(), false))
        .isTrue();

    assertThat(adaptation.species()).containsExactly(EntityType.SHEEP);
  }

  @Test
  void activatingTheRuntimeInstallsThePastureNodeRancherWorkAndPanelUntilUnregistered() {
    assertThat(NodeBehaviors.of(NodeType.PASTURE)).isEmpty();

    adaptation.onRuntimeActivated();

    assertThat(NodeBehaviors.of(NodeType.PASTURE)).hasValueSatisfying(behavior -> assertThat(behavior.policy()).isSameAs(adaptation));
    assertThat(GolemCoreBehaviors.of(GolemCoreType.RANCHER)).hasValueSatisfying(behavior -> assertThat(behavior.policy()).isSameAs(adaptation));
    assertThat(NodePanels.of(NodeType.PASTURE)).isPresent();

    adaptation.unregister();

    assertThat(NodeBehaviors.of(NodeType.PASTURE)).isEmpty();
    assertThat(GolemCoreBehaviors.of(GolemCoreType.RANCHER)).isEmpty();
    assertThat(NodePanels.of(NodeType.PASTURE)).isEmpty();
  }

  @Test
  void birthsFromARancherFedParentGiveNoExperience() {
    Cow fed = cow();
    Cow other = cow();
    adaptation.markFed(fed.getUniqueId(), M.ms(), false);
    EntityBreedEvent rancherBirth = breeding(other, fed);
    EntityBreedEvent wildBirth = breeding(cow(), cow());

    adaptation.on(rancherBirth);
    adaptation.on(wildBirth);

    verify(rancherBirth).setExperience(0);
    verify(wildBirth, never()).setExperience(anyInt());
  }

  @Test
  void aParentFedWithinItsLoveModeGivesNoExperienceEvenWithoutAClockMark() {
    Cow fed = cow();
    when(fed.getTicksLived()).thenReturn(1_500);
    when(fed.getPersistentDataContainer().get(GolemRancherCore.FED_TICK_KEY, PersistentDataType.INTEGER)).thenReturn(1_000);
    EntityBreedEvent birth = breeding(fed, cow());

    adaptation.on(birth);
    adaptation.afterBreeding(birth);

    verify(birth).setExperience(0);
    verify(fed.getPersistentDataContainer()).remove(GolemRancherCore.FED_TICK_KEY);
  }

  @Test
  void aFeedingOlderThanLoveModeLeavesTheExperienceAlone() {
    Cow fed = cow();
    when(fed.getTicksLived()).thenReturn(2_000);
    when(fed.getPersistentDataContainer().get(GolemRancherCore.FED_TICK_KEY, PersistentDataType.INTEGER)).thenReturn(1_000);
    EntityBreedEvent birth = breeding(fed, cow());

    adaptation.on(birth);

    verify(birth, never()).setExperience(anyInt());
  }

  @Test
  void feedingStampsTheAnimalWithItsCurrentTick() {
    Cow fed = cow();
    when(fed.getTicksLived()).thenReturn(777);

    GolemRancherCore.markFedTick(fed);

    verify(fed.getPersistentDataContainer()).set(GolemRancherCore.FED_TICK_KEY, PersistentDataType.INTEGER, 777);
  }

  @Test
  void aStaleFeedingMarkLeavesTheExperienceAndCooldownAlone() {
    Cow fed = cow();
    adaptation.markFed(fed.getUniqueId(), M.ms() - GolemRancherCore.FED_MARK_MILLIS, true);
    EntityBreedEvent event = breeding(fed, cow());
    when(fed.getAge()).thenReturn(5999);

    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      adaptation.on(event);
      adaptation.afterBreeding(event);
      scheduler.verify(() -> J.runEntity(any(), any(Runnable.class), anyInt()), never());
    }

    verify(event, never()).setExperience(anyInt());
  }

  @Test
  void husbandryHalvesTheBreedingCooldownOfParentsARancherFed() {
    Cow fed = cow();
    Cow other = cow();
    Cow plain = cow();
    adaptation.markFed(fed.getUniqueId(), M.ms(), true);
    adaptation.markFed(plain.getUniqueId(), M.ms(), false);
    assertThat(adaptation.hasTickDemand()).isTrue();
    EntityBreedEvent event = breeding(fed, other);
    when(fed.getAge()).thenReturn(5999);

    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      ArgumentCaptor<Runnable> later = ArgumentCaptor.forClass(Runnable.class);
      adaptation.afterBreeding(event);
      adaptation.afterBreeding(breeding(plain, cow()));
      scheduler.verify(() -> J.runEntity(eq(fed), later.capture(), eq(1)));
      scheduler.verify(() -> J.runEntity(eq(other), any(Runnable.class), anyInt()), never());
      scheduler.verify(() -> J.runEntity(eq(plain), any(Runnable.class), anyInt()), never());
      later.getValue().run();
    }

    verify(fed).setAge(2999);
    verify(other, never()).setAge(anyInt());
    assertThat(adaptation.hasTickDemand()).isFalse();
  }

  @Test
  void feedingMarksExpireOnTick() {
    adaptation.markFed(UUID.randomUUID(), 1_000L, false);

    adaptation.sweep(1_000L + GolemRancherCore.FED_MARK_MILLIS - 1L);
    assertThat(adaptation.hasTickDemand()).isTrue();
    adaptation.sweep(1_000L + GolemRancherCore.FED_MARK_MILLIS);
    assertThat(adaptation.hasTickDemand()).isFalse();
  }

  @Test
  void theCullDropListenerRidesAlongAndLeavesOtherDeathsAlone() {
    List<Listener> companions = adaptation.createCompanionListeners();
    assertThat(companions).singleElement().isInstanceOf(RancherActions.class);
    Cow cow = cow();
    List<ItemStack> drops = new ArrayList<>(List.of(GolemTestItems.stack(Material.BEEF, 2)));
    EntityDeathEvent event = mock(EntityDeathEvent.class);
    when(event.getEntity()).thenReturn(cow);
    when(event.getDrops()).thenReturn(drops);

    ((RancherActions) companions.getFirst()).on(event);

    assertThat(drops).hasSize(1);
  }

  private static EntityBreedEvent breeding(Cow mother, Cow father) {
    EntityBreedEvent event = mock(EntityBreedEvent.class);
    when(event.getMother()).thenReturn(mother);
    when(event.getFather()).thenReturn(father);
    return event;
  }

  private Cow cow() {
    Cow cow = mock(Cow.class);
    UUID id = UUID.randomUUID();
    when(cow.getUniqueId()).thenReturn(id);
    lenient().when(cow.getPersistentDataContainer()).thenReturn(mock(PersistentDataContainer.class));
    return cow;
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
    Material material = id.equals("blank_node") ? materials.get("CLAY_BALL")
        : id.startsWith("node/") ? Material.BRICK : materials.get("FIREWORK_STAR");
    when(template.getType()).thenReturn(material);
    when(copy.getType()).thenReturn(material);
    when(template.getAmount()).thenReturn(1);
    when(copy.getAmount()).thenReturn(1);
    when(template.clone()).thenReturn(copy);
    when(copy.clone()).thenReturn(copy);
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

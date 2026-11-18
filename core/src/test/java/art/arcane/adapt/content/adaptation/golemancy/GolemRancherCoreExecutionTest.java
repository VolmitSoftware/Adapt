package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.golem.GolemCoreBehaviors;
import art.arcane.adapt.api.golem.GolemCoreType;
import art.arcane.adapt.api.golem.GolemTestItems;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.gui.NodePanels;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.node.NodeBehaviors;
import art.arcane.adapt.api.recipe.ConfiguredRecipeParser;
import art.arcane.adapt.api.recipe.ExactIngredient;
import art.arcane.adapt.api.recipe.ExactIngredientBook;
import art.arcane.adapt.api.recipe.ExactItemResolver;
import art.arcane.adapt.api.recipe.RecipeItemCatalog;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.event.entity.EntityBreedEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.volmlib.util.math.M;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemRancherCoreExecutionTest extends AdaptTestBase {
  private static final Id CORE_KEY = Id.of("adapt", "golemancy-rancher-core");
  private static final Id NODE_KEY = Id.of("adapt", "golemancy-pasture-node");

  private final Map<String, Id> materials = new HashMap<>();
  private final Map<Id, ItemRef> results = new HashMap<>();
  private final ExactIngredientBook book = new ExactIngredientBook();
  private final AnimalOps animals = mock(AnimalOps.class);
  private MockedStatic<ConfiguredRecipeParser> parsers;
  private GolemRancherCore adaptation;

  @BeforeEach
  void construct() {
    lenient().when(animals.isPeacefulAnimalType(any())).thenAnswer(invocation -> PeacefulAnimals.isPeaceful(invocation.getArgument(0)));
    lenient().when(AdaptCore.platform().host().animals()).thenReturn(animals);
    for (String name : List.of("SHEARS", "WHEAT", "LEAD", "FIREWORK_STAR", "CLAY_BALL")) {
      material(name);
    }
    result("core/rancher");
    result("node/pasture");
    result("blank_core");
    result("blank_node");
    ConfiguredRecipeParser parser = new ConfiguredRecipeParser(new FakeCatalog(), new FakeResolver(), book);
    parsers = mockStatic(ConfiguredRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ConfiguredRecipeParser.platform(GolemItems.resolver(), GolemItems.recipeBook())).thenReturn(parser);
    adaptation = new GolemRancherCore();
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
    assertThat(adaptation.isEnabled()).isFalse();
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
    assertThat(adaptation.species()).containsExactly(Id.minecraft("cow"), Id.minecraft("mooshroom"), Id.minecraft("sheep"), Id.minecraft("pig"),
        Id.minecraft("chicken"), Id.minecraft("goat"), Id.minecraft("rabbit"));

    assertThat(adaptation.reloadConfigSnapshot("species = [\"SHEEP\", \"ZOMBIE\"]\n", dataFolder.toPath().resolve("species.toml").toFile(), false))
        .isTrue();

    assertThat(adaptation.species()).containsExactly(Id.minecraft("sheep"));
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
    MobRef fed = cow();
    MobRef other = cow();
    adaptation.markFed(fed.uuid(), M.ms(), false);
    EntityBreedEvent rancherBirth = breeding(other, fed);
    EntityBreedEvent wildBirth = breeding(cow(), cow());

    adaptation.on(rancherBirth);
    adaptation.on(wildBirth);

    verify(rancherBirth).setExperience(0);
    verify(wildBirth, never()).setExperience(anyInt());
  }

  @Test
  void aParentFedWithinItsLoveModeGivesNoExperienceEvenWithoutAClockMark() {
    MobRef fed = cow();
    when(fed.ticksLived()).thenReturn(1_500);
    when(fed.data().get(GolemRancherCore.FED_TICK_KEY)).thenReturn(1_000);
    EntityBreedEvent birth = breeding(fed, cow());

    adaptation.on(birth);
    adaptation.afterBreeding(birth);

    verify(birth).setExperience(0);
    verify(fed.data()).remove(GolemRancherCore.FED_TICK_KEY);
  }

  @Test
  void aFeedingOlderThanLoveModeLeavesTheExperienceAlone() {
    MobRef fed = cow();
    when(fed.ticksLived()).thenReturn(2_000);
    when(fed.data().get(GolemRancherCore.FED_TICK_KEY)).thenReturn(1_000);
    EntityBreedEvent birth = breeding(fed, cow());

    adaptation.on(birth);

    verify(birth, never()).setExperience(anyInt());
  }

  @Test
  void feedingStampsTheAnimalWithItsCurrentTick() {
    MobRef fed = cow();
    when(fed.ticksLived()).thenReturn(777);

    GolemRancherCore.markFedTick(fed);

    verify(fed.data()).set(GolemRancherCore.FED_TICK_KEY, 777);
  }

  @Test
  void aStaleFeedingMarkLeavesTheExperienceAndCooldownAlone() {
    MobRef fed = cow();
    adaptation.markFed(fed.uuid(), M.ms() - GolemRancherCore.FED_MARK_MILLIS, true);
    EntityBreedEvent event = breeding(fed, cow());
    when(animals.age(fed)).thenReturn(5999);

    try (MockedStatic<Tasks> scheduler = mockStatic(Tasks.class)) {
      adaptation.on(event);
      adaptation.afterBreeding(event);
      scheduler.verify(() -> Tasks.runEntity(any(), any(Runnable.class), anyInt()), never());
    }

    verify(event, never()).setExperience(anyInt());
  }

  @Test
  void husbandryHalvesTheBreedingCooldownOfParentsARancherFed() {
    MobRef fed = cow();
    MobRef other = cow();
    MobRef plain = cow();
    adaptation.markFed(fed.uuid(), M.ms(), true);
    adaptation.markFed(plain.uuid(), M.ms(), false);
    assertThat(adaptation.hasTickDemand()).isTrue();
    EntityBreedEvent event = breeding(fed, other);
    when(animals.age(fed)).thenReturn(5999);

    try (MockedStatic<Tasks> scheduler = mockStatic(Tasks.class)) {
      ArgumentCaptor<Runnable> later = ArgumentCaptor.forClass(Runnable.class);
      adaptation.afterBreeding(event);
      adaptation.afterBreeding(breeding(plain, cow()));
      scheduler.verify(() -> Tasks.runEntity(eq(fed), later.capture(), eq(1)));
      scheduler.verify(() -> Tasks.runEntity(eq(other), any(Runnable.class), anyInt()), never());
      scheduler.verify(() -> Tasks.runEntity(eq(plain), any(Runnable.class), anyInt()), never());
      later.getValue().run();
    }

    verify(animals).setAge(fed, 2999);
    verify(animals, never()).setAge(eq(other), anyInt());
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
    List<?> companions = adaptation.createCompanionListeners();
    assertThat(companions).singleElement().isInstanceOf(RancherActions.class);
    MobRef cow = cow();
    List<ItemRef> drops = new ArrayList<>(List.of(GolemTestItems.stack(Id.minecraft("beef"), 2)));
    EntityDeathEvent event = mock(EntityDeathEvent.class);
    when(event.entity()).thenReturn(cow);
    when(event.drops()).thenReturn(drops);

    ((RancherActions) companions.getFirst()).on(event);

    assertThat(drops).hasSize(1);
  }

  private static EntityBreedEvent breeding(MobRef mother, MobRef father) {
    EntityBreedEvent event = mock(EntityBreedEvent.class);
    when(event.mother()).thenReturn(mother);
    when(event.father()).thenReturn(father);
    return event;
  }

  private MobRef cow() {
    MobRef cow = mock(MobRef.class);
    UUID id = UUID.randomUUID();
    when(cow.uuid()).thenReturn(id);
    lenient().when(cow.data()).thenReturn(mock(DataHolder.class));
    return cow;
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
    Id material = id.equals("blank_node") ? materials.get("CLAY_BALL")
        : id.startsWith("node/") ? Id.minecraft("brick") : materials.get("FIREWORK_STAR");
    results.put(Id.of("golemancy", id), new FakeItem(material, 1));
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
      return new FakeItem(type, amount);
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

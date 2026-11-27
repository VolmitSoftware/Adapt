package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.item.ExactIngredient;
import art.arcane.adapt.api.item.ExactItemResolver;
import art.arcane.adapt.api.item.RecipeItemCatalog;
import art.arcane.adapt.api.item.ShapedRecipeParser;
import art.arcane.adapt.content.golem.item.ExactIngredientBook;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.testkit.PlacedAdaptations;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.FxTimeline;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.content.golem.GolemBindListener;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemScale;
import art.arcane.adapt.content.golem.GolemSettings;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.ServantFrame;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.item.OwnerBoundDrop;
import art.arcane.adapt.content.golem.reveal.GolemLabelPolicy;
import art.arcane.adapt.engine.world.AdaptServer;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.fx.SoundCategory;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.MemoryData;
import art.arcane.adapt.util.common.format.C;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemAnimusBindingExecutionTest extends AdaptTestBase {
  private static final Id CROOK_KEY = Id.adapt("golemancy-crook");
  private static final Id BLANK_CORE_KEY = Id.adapt("golemancy-blank-core");
  private static final DataKey<String> SERVANT_KEY = DataKey.of(Id.parse("adapt:tragoul_servant_owner"), DataCodecs.STRING);

  private final Map<String, Id> materials = new HashMap<>();
  private final Map<Id, ItemRef> results = new HashMap<>();
  private final ExactIngredientBook book = GolemItems.recipeBook();
  private MockedStatic<ShapedRecipeParser> parsers;
  private MockedStatic<GolemancyRuntime> runtime;
  private GolemAnimusBinding adaptation;

  @BeforeEach
  void construct() {
    for (String name : List.of("IRON_HOE", "AMETHYST_SHARD", "REDSTONE", "COPPER_INGOT", "ENDER_PEARL")) {
      material(name);
    }
    result("crook");
    result("blank_core");
    ShapedRecipeParser parser = new ShapedRecipeParser(new FakeCatalog(), new FakeResolver());
    parsers = mockStatic(ShapedRecipeParser.class, CALLS_REAL_METHODS);
    parsers.when(() -> ShapedRecipeParser.platform(GolemItems.resolver())).thenReturn(parser);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::settings).thenReturn(GolemSettings.defaults());
    adaptation = PlacedAdaptations.on(SkillGolemancy.ID, GolemAnimusBinding::new);
  }

  @AfterEach
  void close() {
    runtime.close();
    parsers.close();
  }

  @Test
  void theAdaptationRegistersItselfAsTheStatusLabelPolicy() {
    runtime.verify(() -> GolemancyRuntime.setLabelPolicy(adaptation));
    assertThat(adaptation).isInstanceOf(GolemLabelPolicy.class);
  }

  @Test
  void hiddenStatusLabelsAreAPerOwnerChoiceThatDefaultsToShown() {
    assertThat(adaptation.getPlayerPreferences()).containsExactly(CommonPreferences.ENABLED, GolemancyPreferences.STATUS_LABELS);
    assertThat(GolemancyPreferences.STATUS_LABELS.defaultValue()).isEqualTo(GolemancyPreferences.Labels.SHOWN);
    GolemancyPreferenceAssertions.assertVisibleFrom(adaptation, GolemancyPreferences.STATUS_LABELS, 0);
    PlayerRef owner = mock(PlayerRef.class);
    PlayerRef other = mock(PlayerRef.class);
    GolemAnimusBinding learned = spy(adaptation);
    doReturn(GolemancyPreferences.Labels.HIDDEN).when(learned).preference(owner, GolemancyPreferences.STATUS_LABELS);
    doReturn(GolemancyPreferences.Labels.SHOWN).when(learned).preference(other, GolemancyPreferences.STATUS_LABELS);

    assertThat(learned.hidesLabels(owner)).isTrue();
    assertThat(learned.hidesLabels(other)).isFalse();
  }

  @Test
  void bothRecipesRegisterAtLevelOneUnderGolemancyKeys() {
    List<RecipeSpec> recipes = adaptation.getRecipes();

    assertThat(recipes).extracting(RecipeSpec::key).containsExactly(CROOK_KEY, BLANK_CORE_KEY);
    assertThat(recipes).allMatch(recipe -> recipe.requiredLevel() == 1);
    assertThat(book.satisfiedBy(CROOK_KEY, List.of())).isTrue();
    assertThat(book.satisfiedBy(BLANK_CORE_KEY, List.of())).isTrue();
    assertThat(GolemItems.craftCounter().tracks(CROOK_KEY)).isTrue();
    assertThat(GolemItems.craftCounter().tracks(BLANK_CORE_KEY)).isTrue();
  }

  @Test
  void configurationLoadsAndEveryLevelHasAPositiveCostAndProgress() {
    assertThat(ComponentHost.of(adaptation).reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getConfig()).isNotNull();
    assertThat(adaptation.getMaxLevel()).isEqualTo(5);
    assertThat(adaptation.getLevelPercent(0)).isZero();
    assertThat(adaptation.getLevelPercent(Integer.MAX_VALUE)).isEqualTo(1D);
    double previous = 0D;
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
      assertThat(adaptation.getLevelPercent(level)).isBetween(previous, 1D);
      previous = adaptation.getLevelPercent(level);
    }
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
  }

  @Test
  void everyLevelShowsItsCapRitualAndTithes() {
    assertThat(lore(1)).containsExactly("+ 1 Golems You Can Own", "* 4 Seconds per Binding Ritual",
        "- 1 Levels to Bind a Frost Golem", "- 2 Levels to Bind a Copper Golem", "- 3 Levels to Bind an Iron Golem");
    assertThat(lore(2)).containsExactly("+ 2 Golems You Can Own", "* 3.5 Seconds per Binding Ritual",
        "- 1 Levels to Bind a Frost Golem", "- 2 Levels to Bind a Copper Golem", "- 3 Levels to Bind an Iron Golem");
    assertThat(lore(3)).containsExactly("+ 3 Golems You Can Own", "* 3 Seconds per Binding Ritual",
        "- 1 Levels to Bind a Frost Golem", "- 1 Levels to Bind a Copper Golem", "- 2 Levels to Bind an Iron Golem");
    assertThat(lore(4)).containsExactly("+ 5 Golems You Can Own", "* 2.5 Seconds per Binding Ritual",
        "- 1 Levels to Bind a Frost Golem", "- 1 Levels to Bind a Copper Golem", "- 2 Levels to Bind an Iron Golem");
    assertThat(lore(5)).containsExactly("+ 7 Golems You Can Own", "* 2 Seconds per Binding Ritual",
        "- 0 Levels to Bind a Frost Golem", "- 1 Levels to Bind a Copper Golem", "- 1 Levels to Bind an Iron Golem");
  }

  @Test
  void aDisabledReloadTurnsTheAdaptationOff() {
    assertThat(ComponentHost.of(adaptation).reloadConfigSnapshot("enabled = false\nmaxLevel = 1\n", dataFolder.toPath().resolve("disabled.toml").toFile(), false)).isTrue();
    assertThat(adaptation.isEnabled()).isFalse();
    assertThat(adaptation.getMaxLevel()).isEqualTo(1);
    assertThat(adaptation.getLevelPercent(2)).isEqualTo(1D);
  }

  @Test
  void ritualSecondsAreClampedWhenTheConfigLoads() {
    assertThat(ComponentHost.of(adaptation).reloadConfigSnapshot("ritualSecondsByLevel = [0.1, 99.0]\n", dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    assertThat(adaptation.getConfig().ritualSecondsByLevel).containsExactly(0.5D, 30D);
    assertThat(GolemAnimusBinding.ritualTicks(adaptation.getConfig(), 1)).isEqualTo(10);
    assertThat(GolemAnimusBinding.ritualTicks(adaptation.getConfig(), 2)).isEqualTo(600);
  }

  @Test
  void sneakClickingAWildSkeletonWithACoreIsLeftAlone() {
    PlayerInteractEntityEvent skeleton = coreClick(typed("skeleton"));
    PlayerInteractEntityEvent snowGolem = coreClick(typed("snow_golem"));

    verify(skeleton, never()).setCancelled(anyBoolean());
    verify(snowGolem).setCancelled(true);
  }

  @Test
  void theCoreLeavesTheHandTheMomentTheRitualStarts() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");

      ritual.click(golem);

      verify(ritual.held).setAmount(2);
      assertThat(ritual.pulses).hasSize(1);
      verify(ritual.registry, never()).bind(any(), any(), any(), any());
      verify(ritual.inventory, never()).addItem(any(ItemRef.class));
    }
  }

  @Test
  void aFinishedRitualBindsTheGolemAndKeepsTheCoreEvenWhenTheHandChanged() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");
      ritual.click(golem);
      ritual.pulse(5);
      when(ritual.inventory.mainHand()).thenReturn(mock(ItemRef.class));
      when(ritual.inventory.heldSlot()).thenReturn(7);

      ritual.finish();

      verify(ritual.registry).bind(ritual.owner, golem, GolemBody.FROST, GolemCoreType.BLANK);
      verify(ritual.inventory, never()).addItem(any(ItemRef.class));
      verify(ritual.held, times(1)).setAmount(anyInt());
      assertThat(ritual.shown).containsExactly("Your Frost golem is bound. Right-click it with your Crook to manage it");
    }
  }

  @Test
  void walkingAwayReturnsTheCore() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");
      ritual.click(golem);
      ritual.pulse(3);
      when(ritual.owner.location()).thenReturn(new Loc(ritual.world, 0D, 64D, 12D));

      ritual.finish();

      ritual.assertReturnedOnce();
      verify(ritual.registry, never()).bind(any(), any(), any(), any());
      assertThat(ritual.shown).containsExactly("The binding was interrupted");
    }
  }

  @Test
  void anAttackOnTheGolemReturnsTheCore() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");
      ritual.click(golem);
      ritual.pulse(2);
      EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
      when(hit.entity()).thenReturn(golem);
      when(hit.cause()).thenReturn(DamageCause.ENTITY_ATTACK);

      ritual.learned.on(hit);
      ritual.finish();

      ritual.assertReturnedOnce();
      verify(ritual.registry, never()).bind(any(), any(), any(), any());
    }
  }

  @Test
  void loggingOutReturnsTheCoreToTheInventoryBeforeTheSave() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");
      ritual.click(golem);
      ritual.pulse(2);

      ritual.learned.on(new PlayerQuitEvent(ritual.owner));
      ritual.finish();

      ritual.assertReturnedOnce();
      verify(golem).setAware(true);
      verify(ritual.registry, never()).bind(any(), any(), any(), any());
    }
  }

  @Test
  void aBindingWithoutEnoughLevelsIsRefusedInRedWithTheCostYourLevelsAndADenySound() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");
      doReturn(1).when(ritual.learned).getActiveLevel(ritual.owner);
      when(ritual.owner.level()).thenReturn(0);

      ritual.click(golem);

      String refusal = "Not enough experience: binding this golem costs 1 levels and you have 0";
      assertThat(ritual.shown).containsExactly(refusal);
      assertThat(ritual.shownRaw).containsExactly(C.RED + refusal);
      verify(ritual.owner).playSound(any(Loc.class), eq(Sounds.ENTITY_VILLAGER_NO), any(SoundCategory.class), anyFloat(), anyFloat());
      assertThat(ritual.pulses).isEmpty();
      verify(ritual.held, never()).setAmount(anyInt());
      verify(ritual.owner, never()).giveExpLevels(anyInt());
    }
  }

  @Test
  void bindingLevelsAreTakenOnlyWhenTheRitualCompletes() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");
      doReturn(1).when(ritual.learned).getActiveLevel(ritual.owner);

      ritual.click(golem);
      ritual.pulse(2);

      verify(ritual.owner, never()).giveExpLevels(anyInt());
      ritual.finish();

      verify(ritual.owner).giveExpLevels(-1);
      assertThat(ritual.shown).containsExactly("Your Frost golem is bound. Right-click it with your Crook to manage it");
    }
  }

  @Test
  void levelsLostDuringTheRitualRefuseAtTheEndReturnTheCoreAndChargeNothing() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");
      doReturn(1).when(ritual.learned).getActiveLevel(ritual.owner);
      ritual.click(golem);
      ritual.pulse(2);
      when(ritual.owner.level()).thenReturn(0);

      ritual.finish();

      assertThat(ritual.shown).containsExactly("Not enough experience: binding this golem costs 1 levels and you have 0");
      verify(ritual.owner).playSound(any(Loc.class), eq(Sounds.ENTITY_VILLAGER_NO), any(SoundCategory.class), anyFloat(), anyFloat());
      ritual.assertReturnedOnce();
      verify(ritual.owner, never()).giveExpLevels(anyInt());
      verify(ritual.registry, never()).bind(any(), any(), any(), any());
    }
  }

  @Test
  void theTithesTheLoreShowsAreTheTithesABindingCharges() {
    GolemAnimusBinding.Config config = adaptation.getConfig();

    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(lore(level)).as("level %s", level).contains(
          "- " + GolemAnimusBinding.tithe(config, GolemBody.FROST, level) + " Levels to Bind a Frost Golem",
          "- " + GolemAnimusBinding.tithe(config, GolemBody.COPPER, level) + " Levels to Bind a Copper Golem",
          "- " + GolemAnimusBinding.tithe(config, GolemBody.IRON, level) + " Levels to Bind an Iron Golem");
    }
  }

  @Test
  void aGolemThatDiesMidRitualReturnsTheCore() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");
      ritual.click(golem);
      ritual.pulse(2);
      when(golem.isDead()).thenReturn(true);

      ritual.finish();

      ritual.assertReturnedOnce();
    }
  }

  @Test
  void aServerStopMidRitualReturnsTheCore() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");
      ritual.click(golem);
      ritual.pulse(2);

      ritual.learned.unregister();
      ritual.finish();

      ritual.assertReturnedOnce();
      verify(ritual.registry, never()).bind(any(), any(), any(), any());
    }
  }

  @Test
  void aCoreReturnedToAFullInventoryDropsAtTheOwnersFeetForThemAlone() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");
      ritual.click(golem);
      List<ItemRef> leftover = new ArrayList<>();
      leftover.add(ritual.escrow);
      when(ritual.inventory.addItem(any(ItemRef.class))).thenReturn(leftover);
      when(ritual.owner.location()).thenReturn(new Loc(ritual.world, 0D, 64D, 12D));

      ritual.finish();

      ritual.drops.verify(() -> OwnerBoundDrop.drop(any(Loc.class), eq(ritual.escrow), eq(ritual.ownerId)));
    }
  }

  @Test
  void aBindingRefusedAtTheEndReturnsTheCore() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");
      ritual.click(golem);
      when(ritual.registry.bindBlock(any(), any(), any(), any())).thenReturn(Optional.of(GolemRegistry.BindBlock.CHUNK_CAP));

      ritual.finish();

      ritual.assertReturnedOnce();
      verify(ritual.registry, never()).bind(any(), any(), any(), any());
    }
  }

  @Test
  void aBindThatFailsBeforeTheGolemIsBoundReturnsTheCore() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");
      ritual.click(golem);
      when(ritual.registry.bind(any(), any(), any(), any())).thenThrow(new IllegalStateException("refused"));

      ritual.finish();

      ritual.assertReturnedOnce();
    }
  }

  @Test
  void anAbortRacingACompletedRitualNeverReturnsTheSpentCore() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");
      ritual.click(golem);
      ritual.finish();
      EntityDamageByEntityEvent hit = mock(EntityDamageByEntityEvent.class);
      when(hit.entity()).thenReturn(golem);
      when(hit.cause()).thenReturn(DamageCause.ENTITY_ATTACK);

      ritual.learned.on(hit);
      ritual.learned.on(new PlayerQuitEvent(ritual.owner));
      ritual.learned.unregister();

      verify(ritual.registry, times(1)).bind(any(), any(), any(), any());
      verify(ritual.inventory, never()).addItem(any(ItemRef.class));
    }
  }

  @Test
  void aPulseRacingAnAbortedRitualNeverBindsOrReturnsTheCoreTwice() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef golem = ritual.golem("snow_golem");
      ritual.click(golem);
      ritual.pulse(38);
      Runnable pending = ritual.pulses.getFirst();

      ritual.learned.on(new PlayerQuitEvent(ritual.owner));
      pending.run();
      pending.run();
      ritual.finish();

      ritual.assertReturnedOnce();
      verify(ritual.registry, never()).bind(any(), any(), any(), any());
    }
  }

  @Test
  void yourOwnServantBindsAsABoneCompanionAndLeavesTragoulTracking() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    ServantFrame.Frame frame = mock(ServantFrame.Frame.class);
    ServantFrame.register(frame);
    try (ritual) {
      MobRef servant = ritual.servant(ritual.ownerId);
      when(frame.surrender(servant)).thenReturn(true);

      ritual.click(servant);
      verify(ritual.held).setAmount(2);
      ritual.finish();

      verify(frame).surrender(servant);
      verify(ritual.registry).bind(ritual.owner, servant, GolemBody.BONE, GolemCoreType.BLANK);
      verify(ritual.inventory, never()).addItem(any(ItemRef.class));
      assertThat(ritual.shown).containsExactly("Your Bone golem is bound. Right-click it with your Crook to manage it");
    } finally {
      ServantFrame.unregister(frame);
    }
  }

  @Test
  void theGuardianCoreBindsYourServantAsABoneGuardian() {
    Ritual ritual = ritual(GolemCoreType.GUARDIAN);
    ServantFrame.Frame frame = mock(ServantFrame.Frame.class);
    ServantFrame.register(frame);
    try (ritual) {
      MobRef servant = ritual.servant(ritual.ownerId);
      when(frame.surrender(servant)).thenReturn(true);

      ritual.click(servant);
      ritual.finish();

      verify(ritual.registry).bind(ritual.owner, servant, GolemBody.BONE, GolemCoreType.GUARDIAN);
    } finally {
      ServantFrame.unregister(frame);
    }
  }

  @Test
  void someoneElsesServantIsRefusedAndKeepsYourCore() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef servant = ritual.servant(UUID.randomUUID());

      PlayerInteractEntityEvent click = ritual.click(servant);

      verify(click).setCancelled(true);
      verify(ritual.held, never()).setAmount(anyInt());
      assertThat(ritual.pulses).isEmpty();
      assertThat(ritual.shown).containsExactly("That skeletal servant belongs to another player");
    }
  }

  @Test
  void bindingYourServantNeedsBoneFrame() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    try (ritual) {
      MobRef servant = ritual.servant(ritual.ownerId);
      doReturn(0).when(ritual.learned).getActiveSiblingLevel(ritual.owner, Id.adapt("golem/bone_frame"));

      ritual.click(servant);

      verify(ritual.held, never()).setAmount(anyInt());
      assertThat(ritual.pulses).isEmpty();
      assertThat(ritual.shown).containsExactly("You can't bind Bone golems yet");
    }
  }

  @Test
  void aWorkCoreIsRefusedForYourServantWithTheCoreRefusalMessage() {
    Ritual ritual = ritual(GolemCoreType.GATHERER);
    try (ritual) {
      MobRef servant = ritual.servant(ritual.ownerId);
      when(ritual.registry.bindBlock(ritual.owner, servant, GolemBody.BONE, GolemCoreType.GATHERER))
          .thenReturn(Optional.of(GolemRegistry.BindBlock.CORE_NOT_ALLOWED));

      ritual.click(servant);

      verify(ritual.held, never()).setAmount(anyInt());
      assertThat(ritual.shown).containsExactly("A Bone golem can't take a Gatherer core");
    }
  }

  @Test
  void aServantTragoulWillNotHandOverIsReturnedWithItsCore() {
    Ritual ritual = ritual(GolemCoreType.BLANK);
    ServantFrame.Frame frame = mock(ServantFrame.Frame.class);
    ServantFrame.register(frame);
    try (ritual) {
      MobRef servant = ritual.servant(ritual.ownerId);

      ritual.click(servant);
      ritual.finish();

      ritual.assertReturnedOnce();
      verify(ritual.registry, never()).bind(any(), any(), any(), any());
    } finally {
      ServantFrame.unregister(frame);
    }
  }

  private Ritual ritual(GolemCoreType core) {
    return new Ritual(spy(adaptation), core);
  }

  private final class Ritual implements AutoCloseable {
    private final UUID ownerId = UUID.randomUUID();
    private final WorldRef world = mock(WorldRef.class);
    private final PlayerRef owner = mock(PlayerRef.class);
    private final PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    private final ItemRef held = mock(ItemRef.class);
    private final ItemRef escrow = mock(ItemRef.class);
    private final GolemRegistry registry = mock(GolemRegistry.class);
    private final List<Runnable> pulses = new ArrayList<>();
    private final List<String> shown = new ArrayList<>();
    private final List<String> shownRaw = new ArrayList<>();
    private final GolemAnimusBinding learned;
    private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
    private final MockedStatic<AdaptApi> scheduling;
    private final MockedStatic<GolemItems> items;
    private final MockedStatic<Fx> effects;
    private final MockedStatic<FxTimeline> timelines;
    private final MockedStatic<OwnerBoundDrop> drops;
    private final MockedStatic<HudStatus> hud;
    private final MockedStatic<GolemScale> scales;

    private Ritual(GolemAnimusBinding learned, GolemCoreType core) {
      this.learned = learned;
      when(held.copyWithAmount(1)).thenReturn(escrow);
      when(held.amount()).thenReturn(3);
      when(owner.uuid()).thenReturn(ownerId);
      when(owner.name()).thenReturn("Alex");
      when(owner.isSneaking()).thenReturn(true);
      when(owner.isOnline()).thenReturn(true);
      when(owner.isValid()).thenReturn(true);
      when(owner.inventory()).thenReturn(inventory);
      when(owner.gameMode()).thenReturn(GameMode.SURVIVAL);
      when(owner.level()).thenReturn(30);
      when(owner.location()).thenReturn(new Loc(world, 0D, 64D, 2D));
      when(inventory.mainHand()).thenReturn(held);
      when(inventory.addItem(any(ItemRef.class))).thenReturn(new ArrayList<>());
      when(registry.bindBlock(any(), any(), any(), any())).thenReturn(Optional.empty());
      when(registry.bind(any(), any(), any(), any())).thenReturn(mock(GolemHandle.class));
      runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
      runtime.when(GolemancyRuntime::bindListener).thenReturn(mock(GolemBindListener.class));
      AdaptServer server = mock(AdaptServer.class);
      when(GolemAnimusBindingExecutionTest.this.core.getAdaptServer()).thenReturn(server);
      scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
      scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
      when(adaptScheduler.owns(any(EntityRef.class))).thenReturn(true);
      when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class), anyLong(), any(Runnable.class))).thenAnswer(invocation -> pulses.add(invocation.getArgument(1)));
      when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class))).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(1).run();
        return true;
      });
      when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class), any(Runnable.class))).thenAnswer(invocation -> {
        invocation.<Runnable>getArgument(1).run();
        return true;
      });
      items = mockStatic(GolemItems.class, CALLS_REAL_METHODS);
      items.when(() -> GolemItems.coreOf(held)).thenReturn(Optional.of(core));
      effects = mockStatic(Fx.class);
      effects.when(() -> Fx.now(any(Adaptation.class), any(EntityRef.class), any(FxPriority.class))).thenReturn(mock(FxEmitter.class, RETURNS_SELF));
      timelines = mockStatic(FxTimeline.class);
      timelines.when(() -> FxTimeline.follow(any(Adaptation.class), any(EntityRef.class))).thenReturn(mock(FxTimeline.class, RETURNS_SELF));
      drops = mockStatic(OwnerBoundDrop.class);
      scales = mockStatic(GolemScale.class);
      hud = mockStatic(HudStatus.class);
      hud.when(() -> HudStatus.show(eq(owner), anyString())).thenAnswer(invocation -> {
        shownRaw.add(invocation.getArgument(1));
        return shown.add(C.stripColor(invocation.getArgument(1)));
      });
      doReturn(5).when(learned).getActiveLevel(owner);
      doReturn(1).when(learned).getActiveSiblingLevel(eq(owner), any(Id.class));
      doReturn(true).when(learned).canInteract(any(PlayerRef.class), any(Loc.class));
      doReturn(true).when(learned).canBlockPlace(any(PlayerRef.class), any(Loc.class));
      doNothing().when(learned).boundFx(any(MobRef.class), any(GolemBody.class));
      doNothing().when(learned).fizzle(any(MobRef.class));
    }

    private MobRef golem(String type) {
      MobRef golem = mock(MobRef.class);
      lenient().when(golem.type()).thenReturn(Id.minecraft(type));
      MemoryData data = mapBacked(new HashMap<>());
      when(golem.data()).thenReturn(data);
      when(golem.uuid()).thenReturn(UUID.randomUUID());
      when(golem.location()).thenAnswer(invocation -> new Loc(world, 0D, 64D, 0D));
      when(golem.velocity()).thenAnswer(invocation -> new Vec3(0D, 0D, 0D));
      when(golem.isValid()).thenReturn(true);
      when(golem.isAware()).thenReturn(true);
      when(golem.height()).thenReturn(1.9D);
      return golem;
    }

    private MobRef servant(UUID servantOwner) {
      MobRef servant = golem("skeleton");
      servant.data().set(SERVANT_KEY, servantOwner.toString());
      return servant;
    }

    private PlayerInteractEntityEvent click(MobRef target) {
      PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
      when(event.hand()).thenReturn(Hand.MAIN_HAND);
      when(event.player()).thenReturn(owner);
      when(event.clicked()).thenReturn(target);
      learned.on(event);
      return event;
    }

    private void pulse(int count) {
      for (int index = 0; index < count; index++) {
        pulses.removeFirst().run();
      }
    }

    private void finish() {
      while (!pulses.isEmpty()) {
        pulses.removeFirst().run();
      }
    }

    private void assertReturnedOnce() {
      verify(inventory, times(1)).addItem(any(ItemRef.class));
      verify(inventory).addItem(escrow);
      verify(held, times(1)).setAmount(anyInt());
    }

    @Override
    public void close() {
      scales.close();
      hud.close();
      drops.close();
      timelines.close();
      effects.close();
      items.close();
      scheduling.close();
    }
  }

  private static MemoryData mapBacked(Map<Id, Object> values) {
    return new MemoryData();
  }

  private PlayerInteractEntityEvent coreClick(MobRef target) {
    PlayerRef player = mock(PlayerRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    ItemRef core = mock(ItemRef.class);
    when(player.isSneaking()).thenReturn(true);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(target.uuid()).thenReturn(UUID.randomUUID());
    MemoryData data = mapBacked(new HashMap<>());
    when(target.data()).thenReturn(data);
    when(player.inventory()).thenReturn(inventory);
    when(inventory.mainHand()).thenReturn(core);
    PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
    when(event.hand()).thenReturn(Hand.MAIN_HAND);
    when(event.player()).thenReturn(player);
    when(event.clicked()).thenReturn(target);
    GolemAnimusBinding learned = spy(adaptation);
    doReturn(3).when(learned).getActiveLevel(player);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.empty());
    AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
    try (MockedStatic<AdaptApi> scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
         MockedStatic<GolemItems> items = mockStatic(GolemItems.class, CALLS_REAL_METHODS)) {
      scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
      when(adaptScheduler.owns(any(EntityRef.class))).thenReturn(true);
      items.when(() -> GolemItems.coreOf(core)).thenReturn(Optional.of(GolemCoreType.BLANK));
      learned.on(event);
    }
    return event;
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
    results.put(Id.of("golemancy", id), item(null, 1));
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
      return GolemAnimusBindingExecutionTest.item(type, amount);
    }

    @Override
    public int maxStackSize(Id type) {
      return 64;
    }
  }

  private final class FakeResolver implements ExactItemResolver {
    @Override
    public ExactIngredient ingredient(Id id) {
      return null;
    }

    @Override
    public ItemRef result(Id id) {
      return results.get(id);
    }
  }

  private static MobRef typed(String type) {
    MobRef entity = mock(MobRef.class);
    lenient().when(entity.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(entity.type()).thenReturn(Id.minecraft(type));
    return entity;
  }
}

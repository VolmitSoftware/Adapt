package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.testkit.PlacedAdaptations;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.content.golem.GolemAnchor;
import art.arcane.adapt.content.golem.GolemBody;
import art.arcane.adapt.content.golem.GolemCoreType;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemMend;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemRepairXp;
import art.arcane.adapt.content.golem.GolemRoster;
import art.arcane.adapt.content.golem.GolemSettings;
import art.arcane.adapt.content.golem.GolemSnapshot;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.content.golem.GolemVitals;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.item.GolemItemKind;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.item.PackedGolem;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityRegainHealthEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.item.ItemOps;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.FakeItem;
import art.arcane.adapt.testkit.MemoryData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemMendingTest extends AdaptTestBase {
  private static final UUID WORLD = UUID.fromString("00000000-0000-0000-0000-00000000b1a1");
  private static final double MAX_HEALTH = 20D;

  private static final ItemRef EMPTY = FakeItem.empty();
  private static final DataKey<String> SERVANT_KEY = DataKey.adapt("tragoul_servant_owner", DataCodecs.STRING);

  private final List<Double> repairs = new ArrayList<>();
  private GolemMending adaptation;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<AdaptApi> scheduling;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
  private MockedStatic<GolemVitals> vitals;
  private MockedStatic<GolemItems> items;
  private GolemRegistrations<GolemMend.Mender> menders;
  private WorldRef world;
  private PlayerRef player;
  private PlayerInventoryRef inventory;

  @BeforeEach
  void construct() {
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(() -> GolemancyRuntime.handle(any())).thenReturn(Optional.empty());
    runtime.when(GolemancyRuntime::settings).thenReturn(GolemSettings.defaults());
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.empty());
    scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    when(adaptScheduler.owns(any(EntityRef.class))).thenReturn(true);
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class))).thenReturn(true);
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class), anyLong())).thenReturn(true);
    vitals = mockStatic(GolemVitals.class);
    vitals.when(() -> GolemVitals.maxHealth(any())).thenReturn(MAX_HEALTH);
    vitals.when(() -> GolemVitals.heal(any(), anyDouble(), any())).thenCallRealMethod();
    vitals.when(() -> GolemVitals.fullHealth(any())).thenCallRealMethod();
    ItemOps itemOps = mock(ItemOps.class);
    lenient().when(itemOps.empty()).thenReturn(EMPTY);
    lenient().when(AdaptCore.platform().host().items()).thenReturn(itemOps);
    items = mockStatic(GolemItems.class, CALLS_REAL_METHODS);
    menders = GolemRegistrations.menders();
    adaptation = PlacedAdaptations.on(SkillGolemancy.ID, GolemMending::new);
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(WORLD);
    player = mock(PlayerRef.class);
    inventory = mock(PlayerInventoryRef.class);
    lenient().when(player.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(player.name()).thenReturn("Alex");
    lenient().when(player.inventory()).thenReturn(inventory);
    lenient().when(player.gameMode()).thenReturn(GameMode.SURVIVAL);
    lenient().when(player.eyeLocation()).thenReturn(new Loc(world, 0.5D, 65.6D, 2D));
    GolemRepairXp.setSink((healer, golem, healed) -> repairs.add(healed));
  }

  @AfterEach
  void close() {
    GolemRepairXp.setSink(GolemRepairXp.Sink.NONE);
    menders.close();
    items.close();
    vitals.close();
    scheduling.close();
    runtime.close();
  }

  @Test
  void defaultsMatchTheBrief() {
    GolemMending.Config config = new GolemMending.Config();

    assertThat(config.healBonusByLevel).containsExactly(0.2D, 0.4D, 0.6D, 0.8D, 1.0D);
    assertThat(config.copperIngotHeal).isEqualTo(4D);
    assertThat(config.snowBlockHeal).isEqualTo(2D);
    assertThat(config.boneMealHeal).isEqualTo(2D);
    assertThat(config.ownerRegenLevel).isEqualTo(2);
    assertThat(config.ownerRegenAmount).isEqualTo(0.5D);
    assertThat(config.ownerRegenRadius).isEqualTo(16D);
    assertThat(config.homeRegenLevel).isEqualTo(3);
    assertThat(config.homeRegenAmount).isEqualTo(1D);
    assertThat(config.homeRegenRadius).isEqualTo(4D);
    assertThat(config.regenIntervalSeconds).isEqualTo(5);
    assertThat(config.shatterLevel).isEqualTo(4);
    assertThat(config.rebornLevel).isEqualTo(5);
    assertThat(config.rebornResistanceSeconds).isEqualTo(10);
    assertThat(config.rebornResistanceAmplifier).isEqualTo(1);
    assertThat(config.requires).containsExactly("adapt:golem/animus_binding@1");
    assertThat(config.maxLevel).isEqualTo(5);
  }

  @Test
  void configurationLoadsWithFiveLevelsAndRegistersItsMenderOnlyAtRuntime() {
    assertThat(ComponentHost.of(adaptation).reloadConfigFromDisk(false)).isTrue();
    assertThat(adaptation.getId().toString()).isEqualTo("adapt:golem/mending");
    assertThat(adaptation.getMaxLevel()).isEqualTo(5);
    for (int level = 1; level <= adaptation.getMaxLevel(); level++) {
      assertThat(adaptation.getCostFor(level)).as("cost at level %s", level).isPositive();
    }
    assertThat(adaptation.isRuntimeRegistered()).isFalse();
    assertThat(menders.registered()).isEmpty();

    adaptation.onRuntimeActivated();
    try {
      assertThat(menders.registered()).singleElement().satisfies(mender -> assertThat(mender.regenIntervalMillis()).isEqualTo(5_000L));
    } finally {
      adaptation.unregister();
    }
    assertThat(menders.unregistered()).isEqualTo(menders.registered());
  }

  @Test
  void loadingClampsNegativeAmountsTheIntervalAndTheAmplifier() {
    String toml = "copperIngotHeal = -3.0\nownerRegenRadius = -1.0\nregenIntervalSeconds = 0\nrebornResistanceSeconds = -4\n"
        + "rebornResistanceAmplifier = 900\n";
    assertThat(ComponentHost.of(adaptation).reloadConfigSnapshot(toml, dataFolder.toPath().resolve("clamp.toml").toFile(), false)).isTrue();

    GolemMending.Config config = adaptation.getConfig();
    assertThat(config.copperIngotHeal).isZero();
    assertThat(config.ownerRegenRadius).isZero();
    assertThat(config.regenIntervalSeconds).isEqualTo(1);
    assertThat(config.rebornResistanceSeconds).isZero();
    assertThat(config.rebornResistanceAmplifier).isEqualTo(255);
  }

  @Test
  void everyLevelShowsItsHealingRegenAndUnlocks() {
    String heals = "+ Copper Ingots, Snow Blocks and Bone Meal Heal Your Golems";
    assertThat(lore(1)).containsExactly("+ 20% Bonus Healing from Repairs", heals);
    assertThat(lore(2)).containsExactly("+ 40% Bonus Healing from Repairs", heals, "+ 0.5 Health Regenerated near You",
        "* 5 Seconds Between Regeneration");
    assertThat(lore(3)).containsExactly("+ 60% Bonus Healing from Repairs", heals, "+ 0.5 Health Regenerated near You",
        "+ 1 Health Regenerated While Idle at Home", "* 5 Seconds Between Regeneration");
    assertThat(lore(4)).containsExactly("+ 80% Bonus Healing from Repairs", heals, "+ 0.5 Health Regenerated near You",
        "+ 1 Health Regenerated While Idle at Home", "* 5 Seconds Between Regeneration", "+ Shattered Core: Rebuild Fallen Golems");
    assertThat(lore(5)).containsExactly("+ 100% Bonus Healing from Repairs", heals, "+ 0.5 Health Regenerated near You",
        "+ 1 Health Regenerated While Idle at Home", "* 5 Seconds Between Regeneration", "+ Shattered Core: Rebuild Fallen Golems",
        "+ 10 Seconds of Resistance for Reborn Golems", "+ Reborn Golems Keep Their Copper State");
  }

  @Test
  void aCopperIngotHealsYourBoundCopperGolemByTheLevelBonusAndFeedsRepairXp() {
    AtomicReference<Double> health = new AtomicReference<>(5D);
    MobRef golem = living("copper_golem", health);
    bound(golem, GolemBody.COPPER, player.uuid());
    ItemRef ingots = held(Id.minecraft("copper_ingot"), 3, Hand.MAIN_HAND);
    GolemMending learned = learned(1);
    PlayerInteractEntityEvent click = click(golem, Hand.MAIN_HAND);

    learned.on(click);

    assertThat(health.get()).isCloseTo(9.8D, offset(1e-9));
    assertThat(click.isCancelled()).isTrue();
    verify(ingots).setAmount(2);
    verify(learned).mendFx(golem, MendingRules.Remedy.COPPER_INGOT);
    assertThat(repairs).singleElement().satisfies(healed -> assertThat(healed).isCloseTo(4.8D, offset(1e-9)));
  }

  @Test
  void aSnowBlockHealsYourFrostGolemAndHealingStopsAtFullHealth() {
    AtomicReference<Double> health = new AtomicReference<>(18D);
    MobRef golem = living("snow_golem", health);
    bound(golem, GolemBody.FROST, player.uuid());
    ItemRef snow = held(Id.minecraft("snow_block"), 1, Hand.OFF_HAND);
    GolemMending learned = learned(5);

    learned.on(click(golem, Hand.OFF_HAND));

    assertThat(health.get()).isEqualTo(MAX_HEALTH);
    verify(inventory).setHand(Hand.OFF_HAND, EMPTY);
    assertThat(repairs).singleElement().satisfies(healed -> assertThat(healed).isCloseTo(2D, offset(1e-9)));
  }

  @Test
  void aDormantFrostGolemIsNeverSpentAsASnowBlockRemedy() {
    AtomicReference<Double> health = new AtomicReference<>(10D);
    MobRef golem = living("snow_golem", health);
    bound(golem, GolemBody.FROST, player.uuid());
    ItemRef dormant = held(Id.minecraft("snow_block"), 1, Hand.MAIN_HAND);
    items.when(() -> GolemItems.isGolemItem(dormant)).thenReturn(true);
    GolemMending learned = learned(5);
    PlayerInteractEntityEvent click = click(golem, Hand.MAIN_HAND);

    learned.on(click);

    assertThat(health.get()).isEqualTo(10D);
    assertThat(click.isCancelled()).isFalse();
    verify(dormant, never()).setAmount(anyInt());
    verify(inventory, never()).setHand(any(Hand.class), any());
    assertThat(repairs).isEmpty();
  }

  @Test
  void aGolemAtFullHealthRefusesTheItem() {
    AtomicReference<Double> health = new AtomicReference<>(MAX_HEALTH);
    MobRef golem = living("copper_golem", health);
    bound(golem, GolemBody.COPPER, player.uuid());
    ItemRef ingots = held(Id.minecraft("copper_ingot"), 3, Hand.MAIN_HAND);
    GolemMending learned = learned(3);
    PlayerInteractEntityEvent click = click(golem, Hand.MAIN_HAND);

    learned.on(click);

    assertThat(health.get()).isEqualTo(MAX_HEALTH);
    verify(ingots, never()).setAmount(anyInt());
    verify(learned, never()).mendFx(any(), any());
    assertThat(repairs).isEmpty();
    assertThat(click.isCancelled()).isTrue();
  }

  @Test
  void creativePlayersKeepTheirItem() {
    AtomicReference<Double> health = new AtomicReference<>(5D);
    MobRef golem = living("copper_golem", health);
    bound(golem, GolemBody.COPPER, player.uuid());
    ItemRef ingots = held(Id.minecraft("copper_ingot"), 3, Hand.MAIN_HAND);
    when(player.gameMode()).thenReturn(GameMode.CREATIVE);

    learned(1).on(click(golem, Hand.MAIN_HAND));

    assertThat(health.get()).isGreaterThan(5D);
    verify(ingots, never()).setAmount(anyInt());
  }

  @Test
  void itemsOnlyHealYourOwnGolemOfTheirBodyAndNeedTheAdaptation() {
    AtomicReference<Double> health = new AtomicReference<>(5D);
    MobRef someoneElses = living("copper_golem", health);
    bound(someoneElses, GolemBody.COPPER, UUID.randomUUID());
    MobRef frost = living("snow_golem", health);
    bound(frost, GolemBody.FROST, player.uuid());
    MobRef wild = living("copper_golem", health);
    MobRef mine = living("copper_golem", health);
    bound(mine, GolemBody.COPPER, player.uuid());
    held(Id.minecraft("copper_ingot"), 3, Hand.MAIN_HAND);
    GolemMending learned = learned(5);
    List<PlayerInteractEntityEvent> clicks = List.of(click(someoneElses, Hand.MAIN_HAND), click(frost, Hand.MAIN_HAND),
        click(wild, Hand.MAIN_HAND));

    for (PlayerInteractEntityEvent click : clicks) {
      learned.on(click);
    }
    PlayerInteractEntityEvent unlearned = click(mine, Hand.MAIN_HAND);
    learned(0).on(unlearned);

    assertThat(health.get()).isEqualTo(5D);
    assertThat(clicks).noneMatch(PlayerInteractEntityEvent::isCancelled);
    assertThat(unlearned.isCancelled()).isFalse();
  }

  @Test
  void boneMealHealsYourOwnTragoulServantWithoutRepairXp() {
    AtomicReference<Double> health = new AtomicReference<>(4D);
    MobRef servant = living("skeleton", health);
    servantOf(servant, player.uuid());
    MobRef strangers = living("skeleton", new AtomicReference<>(4D));
    servantOf(strangers, UUID.randomUUID());
    held(Id.minecraft("bone_meal"), 5, Hand.MAIN_HAND);
    GolemMending learned = learned(1);

    learned.on(click(servant, Hand.MAIN_HAND));
    learned.on(click(strangers, Hand.MAIN_HAND));

    assertThat(health.get()).isCloseTo(6.4D, offset(1e-9));
    assertThat(strangers.health()).isEqualTo(4D);
    assertThat(repairs).isEmpty();
  }

  @Test
  void boneMealAlsoHealsYourOwnBoneGolemButNotSomeoneElses() {
    AtomicReference<Double> health = new AtomicReference<>(4D);
    MobRef mine = living("skeleton", health);
    bound(mine, GolemBody.BONE, player.uuid());
    MobRef strangers = living("skeleton", new AtomicReference<>(4D));
    bound(strangers, GolemBody.BONE, UUID.randomUUID());
    held(Id.minecraft("bone_meal"), 5, Hand.MAIN_HAND);
    GolemMending learned = learned(1);

    learned.on(click(mine, Hand.MAIN_HAND));
    learned.on(click(strangers, Hand.MAIN_HAND));

    assertThat(health.get()).isCloseTo(6.4D, offset(1e-9));
    assertThat(strangers.health()).isEqualTo(4D);
    assertThat(repairs).isEmpty();
  }

  @Test
  void aShatterLevelMenderNeverShattersABoneGolem() {
    GolemHandle handle = mock(GolemHandle.class);
    when(handle.body()).thenReturn(GolemBody.BONE);
    when(handle.isValidated()).thenReturn(true);
    GolemMending learned = learned(5);
    doReturn(5).when(learned).ownerLevel(handle);
    doReturn(true).when(learned).ownerOnline(handle);
    learned.onRuntimeActivated();
    try {
      assertThat(menders.registered().getFirst().shatters(handle)).isFalse();
    } finally {
      learned.unregister();
    }
  }

  @Test
  void anIronIngotRepairOfABoundIronGolemGainsTheBonusOfTheVanillaTwentyFive() {
    MobRef golem = living("iron_golem", new AtomicReference<>(50D));
    bound(golem, GolemBody.IRON, UUID.randomUUID());
    held(Id.minecraft("iron_ingot"), 1, Hand.MAIN_HAND);
    GolemMending learned = learned(1);
    doReturn(3).when(learned).ownerLevel(any(GolemHandle.class));

    learned.onIngotRepair(click(golem, Hand.MAIN_HAND));
    EntityRegainHealthEvent repair = new EntityRegainHealthEvent(golem, 25D, EntityRegainHealthEvent.Reason.CUSTOM);
    learned.on(repair);
    EntityRegainHealthEvent later = new EntityRegainHealthEvent(golem, 25D, EntityRegainHealthEvent.Reason.CUSTOM);
    learned.on(later);

    assertThat(repair.amount()).isCloseTo(40D, offset(1e-9));
    assertThat(later.amount()).isEqualTo(25D);
    assertThat(learned.hasTickDemand()).isFalse();
  }

  @Test
  void theMenderShattersOnlyValidatedGolemsOfAnOnlineOwnerAtTheShatterLevel() {
    GolemMending learned = learned(0);
    GolemHandle handle = mock(GolemHandle.class);
    when(handle.isValidated()).thenReturn(true);
    doReturn(true).when(learned).ownerOnline(handle);
    doReturn(4).when(learned).ownerLevel(handle);
    learned.onRuntimeActivated();
    try {
      GolemMend.Mender mender = menders.registered().getFirst();
      assertThat(mender.shatters(handle)).isTrue();
      doReturn(3).when(learned).ownerLevel(handle);
      assertThat(mender.shatters(handle)).isFalse();
      doReturn(5).when(learned).ownerLevel(handle);
      doReturn(false).when(learned).ownerOnline(handle);
      assertThat(mender.shatters(handle)).isFalse();
      doReturn(true).when(learned).ownerOnline(handle);
      when(handle.isValidated()).thenReturn(false);
      assertThat(mender.shatters(handle)).isFalse();
      assertThat(mender.regen(handle, new GolemMend.RegenSenses(2D, 2D, true))).isEqualTo(1D);
    } finally {
      learned.unregister();
    }
  }

  @Test
  void aReadyRebuildRestoresThenConsumesTheCoreThenSavesThePlayer() {
    Rebuild rebuild = rebuild(4);

    rebuild.adaptation.on(rebuild.click);

    assertThat(rebuild.click.isCancelled()).isTrue();
    InOrder order = inOrder(rebuild.registry, inventory, player);
    order.verify(rebuild.registry).restore(player, rebuild.golem, new GolemRegistry.Restore(rebuild.item, GolemRoster.Status.SHATTERED));
    order.verify(inventory).setMainHand(null);
    order.verify(player).saveData();
    verify(rebuild.handle).restoreFullHealth();
    verify(rebuild.adaptation, never()).applyRebornBoon(any(), any());
    verify(rebuild.adaptation).rebornFx(rebuild.golem);
  }

  @Test
  void levelFiveRebornGolemsKeepTheirCopperAndGainResistance() {
    Rebuild rebuild = rebuild(5);

    rebuild.adaptation.on(rebuild.click);

    verify(rebuild.adaptation).applyRebornBoon(rebuild.golem, rebuild.snapshot);
    verify(player).saveData();
  }

  @Test
  void aCoreWhoseGolemWasReleasedCrumblesWithoutRebuilding() {
    Rebuild rebuild = rebuild(5);
    rebuild.roster.remove(rebuild.snapshot.id());

    rebuild.adaptation.on(rebuild.click);

    verify(rebuild.registry, never()).restore(any(), any(), any());
    verify(inventory).setMainHand(null);
    verify(rebuild.adaptation).crumbleFx(player);
    verify(player, never()).saveData();
  }

  @Test
  void anotherPlayersCoreIsRefusedAndKept() {
    Rebuild rebuild = rebuild(5);
    PackedGolem foreign = new PackedGolem(rebuild.snapshot.id(), UUID.randomUUID(), rebuild.snapshot.toJson());
    ItemRef core = rebuild.core;
    items.when(() -> GolemItems.packedGolemOf(core)).thenReturn(Optional.of(foreign));

    rebuild.adaptation.on(rebuild.click);

    verify(rebuild.registry, never()).restore(any(), any(), any());
    verify(inventory, never()).setMainHand(any());
  }

  @Test
  void rebuildingNeedsTheShatterLevelAndABodyInReach() {
    Rebuild locked = rebuild(3);
    locked.adaptation.on(locked.click);
    verify(locked.registry, never()).restore(any(), any(), any());

    Rebuild far = rebuild(5);
    when(player.eyeLocation()).thenReturn(new Loc(world, 0.5D, 65.6D, 9D));
    far.adaptation.on(far.click);
    verify(far.registry, never()).restore(any(), any(), any());
    verify(inventory, never()).setMainHand(any());
  }

  @Test
  void sneakClickingAMobThatIsNoGolemBodyWithAShatteredCoreIsLeftAlone() {
    Rebuild rebuild = rebuild(5);
    PlayerInteractEntityEvent cow = click(living("cow", new AtomicReference<>(10D)), Hand.MAIN_HAND);
    PlayerInteractEntityEvent skeleton = click(living("skeleton", new AtomicReference<>(10D)), Hand.MAIN_HAND);

    rebuild.adaptation.on(cow);
    rebuild.adaptation.on(skeleton);

    assertThat(cow.isCancelled()).isFalse();
    assertThat(skeleton.isCancelled()).isFalse();
    verify(rebuild.registry, never()).restore(any(), any(), any());
    verify(inventory, never()).setMainHand(any());
  }

  @Test
  void aCoreWhoseEntryChangedBeforeTheSwapCrumbles() {
    Rebuild rebuild = rebuild(5);
    when(rebuild.registry.restore(any(), any(), any())).thenReturn(Optional.empty());

    rebuild.adaptation.on(rebuild.click);

    verify(inventory).setMainHand(null);
    verify(player, never()).saveData();
    verify(rebuild.handle, never()).restoreFullHealth();
  }

  @Test
  void anOlderCopyOfTheCoreCrumblesBecauseTheEntryIsSealedToTheNewestOne() {
    Rebuild rebuild = rebuild(5);
    rebuild.roster.unstow(rebuild.snapshot.id(), GolemRoster.Status.SHATTERED, rebuild.item.state());
    rebuild.roster.stow(rebuild.snapshot.id(), GolemRoster.Status.SHATTERED, "{\"newer\":true}");

    rebuild.adaptation.on(rebuild.click);

    verify(rebuild.registry, never()).restore(any(), any(), any());
    verify(inventory).setMainHand(null);
    verify(rebuild.adaptation).crumbleFx(player);
  }

  @Test
  void aFailedRestoreKeepsTheCore() {
    Rebuild rebuild = rebuild(5);
    doThrow(new IllegalStateException("boom")).when(rebuild.registry).restore(any(), any(), any());

    rebuild.adaptation.on(rebuild.click);

    verify(inventory, never()).setMainHand(any());
    verify(player, never()).saveData();
  }

  private Rebuild rebuild(int level) {
    UUID golemId = UUID.randomUUID();
    GolemState state = GolemState.create(new GolemState.Seed(golemId, player.uuid(), GolemBody.IRON, GolemCoreType.GUARDIAN,
        GolemAnchor.of(WORLD, 0, 64, 0)));
    GolemSnapshot snapshot = GolemSnapshot.capture(state, new GolemSnapshot.Traits(null, 0D, 0, false, true), 1L);
    GolemRoster roster = new GolemRoster();
    roster.add(new GolemRoster.Binding(golemId, GolemBody.IRON, GolemCoreType.GUARDIAN, WORLD, 0L, 0L));
    PackedGolem packed = new PackedGolem(golemId, player.uuid(), snapshot.toJson());
    roster.stow(golemId, GolemRoster.Status.SHATTERED, packed.state());
    MobRef golem = living("iron_golem", new AtomicReference<>(100D));
    when(golem.isPlayerCreated()).thenReturn(true);
    when(golem.boundingBox()).thenReturn(new Box(0D, 64D, 0D, 1D, 65D, 1D));
    ItemRef core = held(Id.minecraft("firework_star"), 1, Hand.MAIN_HAND);
    when(inventory.mainHand()).thenReturn(core);
    when(player.isSneaking()).thenReturn(true);
    items.when(() -> GolemItems.kindOf(core)).thenReturn(Optional.of(GolemItemKind.SHATTERED_CORE));
    items.when(() -> GolemItems.packedGolemOf(core)).thenReturn(Optional.of(packed));
    GolemRegistry registry = mock(GolemRegistry.class);
    GolemHandle handle = mock(GolemHandle.class);
    when(registry.restoreBlock(golem, snapshot)).thenReturn(Optional.empty());
    lenient().when(registry.restore(any(), any(), any())).thenReturn(Optional.of(handle));
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    GolemAnimusBinding binding = mock(GolemAnimusBinding.class);
    when(binding.bodyUnlocked(player, GolemBody.IRON)).thenReturn(true);
    GolemMending learned = learned(level);
    doReturn(Optional.of(binding)).when(learned).binding();
    doReturn(Optional.of(roster)).when(learned).roster(player);
    lenient().doNothing().when(learned).applyRebornBoon(any(), any());
    return new Rebuild(learned, registry, roster, golem, core, packed, snapshot, handle, click(golem, Hand.MAIN_HAND));
  }

  private GolemMending learned(int level) {
    GolemMending learned = spy(adaptation);
    lenient().doReturn(level).when(learned).getActiveLevel(player);
    lenient().doReturn(true).when(learned).canInteract(any(PlayerRef.class), any(Loc.class));
    lenient().doReturn(true).when(learned).canBlockPlace(any(PlayerRef.class), any(Loc.class));
    lenient().doNothing().when(learned).mendFx(any(), any());
    lenient().doNothing().when(learned).rebornFx(any());
    lenient().doNothing().when(learned).crumbleFx(any());
    return learned;
  }

  private void bound(MobRef golem, GolemBody body, UUID owner) {
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.body()).thenReturn(body);
    lenient().when(handle.owner()).thenReturn(owner);
    lenient().when(handle.entity()).thenReturn(golem);
    runtime.when(() -> GolemancyRuntime.handle(golem)).thenReturn(Optional.of(handle));
  }

  private void servantOf(MobRef servant, UUID owner) {
    servant.data().set(SERVANT_KEY, owner.toString());
  }

  private MobRef living(String type, AtomicReference<Double> health) {
    MobRef entity = mock(MobRef.class);
    lenient().when(entity.type()).thenReturn(Id.minecraft(type));
    lenient().when(entity.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(entity.location()).thenReturn(new Loc(world, 0.5D, 64D, 0.5D));
    lenient().when(entity.world()).thenReturn(world);
    lenient().when(entity.data()).thenReturn(new MemoryData());
    lenient().when(entity.health()).thenAnswer(invocation -> health.get());
    lenient().doAnswer(invocation -> {
      health.set(invocation.getArgument(0));
      return null;
    }).when(entity).setHealth(anyDouble());
    lenient().when(entity.regainHealth(anyDouble(), any())).thenAnswer(invocation -> {
      double amount = invocation.getArgument(0);
      double current = health.get();
      if (!Double.isFinite(amount) || amount <= 0D || current <= 0D || current >= MAX_HEALTH) {
        return 0D;
      }
      double next = Math.min(MAX_HEALTH, current + amount);
      health.set(next);
      return next - current;
    });
    return entity;
  }

  private ItemRef held(Id material, int amount, Hand hand) {
    ItemRef item = mock(ItemRef.class);
    lenient().when(item.type()).thenReturn(material);
    lenient().when(item.amount()).thenReturn(amount);
    lenient().when(inventory.hand(hand)).thenReturn(item);
    if (hand == Hand.MAIN_HAND) {
      lenient().when(inventory.mainHand()).thenReturn(item);
    } else {
      lenient().when(inventory.offHand()).thenReturn(item);
    }
    return item;
  }

  private PlayerInteractEntityEvent click(EntityRef target, Hand hand) {
    return new PlayerInteractEntityEvent(player, target, hand, null);
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

  private record Rebuild(GolemMending adaptation, GolemRegistry registry, GolemRoster roster, MobRef golem, ItemRef core, PackedGolem item,
                         GolemSnapshot snapshot, GolemHandle handle, PlayerInteractEntityEvent click) {
  }

}

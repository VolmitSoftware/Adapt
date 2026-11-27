package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.content.skill.SkillGolemancy;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.testkit.PlacedAdaptations;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.content.golem.GolemFormation;
import art.arcane.adapt.content.golem.GolemHandle;
import art.arcane.adapt.content.golem.GolemMode;
import art.arcane.adapt.content.golem.GolemRegistry;
import art.arcane.adapt.content.golem.GolemState;
import art.arcane.adapt.content.golem.GolemancyRuntime;
import art.arcane.adapt.content.golem.item.GolemItems;
import art.arcane.adapt.content.golem.overseer.GolemSignal;
import art.arcane.adapt.content.golem.overseer.Overseer;
import art.arcane.adapt.content.golem.overseer.OverseerReceiver;
import art.arcane.adapt.content.golem.overseer.OverseerSignal;
import art.arcane.adapt.engine.minion.MinionIdentity;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.PlayerChangedWorldEvent;
import art.arcane.adapt.platform.event.player.PlayerDeathEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.DyeColor;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.WorldRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GolemOverseerTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000a01");

  private final AtomicLong now = new AtomicLong(100_000L);
  private final List<OverseerSignal> heard = new ArrayList<>();
  private final OverseerReceiver receiver = heard::add;
  private final List<GolemHandle> loaded = new ArrayList<>();
  private MockedStatic<AdaptApi> scheduling;
  private final AdaptScheduler adaptScheduler = mock(AdaptScheduler.class);
  private MockedStatic<OverseerFeedback> feedback;
  private MockedStatic<OverseerArmor> armor;
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<MinionIdentity> minions;
  private WorldRef world;
  private PlayerRef player;
  private ItemRef crook;
  private GolemOverseer adaptation;

  @BeforeEach
  void setUp() {
    world = mock(WorldRef.class);
    lenient().when(world.uid()).thenReturn(UUID.randomUUID());
    crook = mock(ItemRef.class);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    lenient().when(inventory.mainHand()).thenReturn(crook);
    player = mock(PlayerRef.class);
    lenient().when(player.uuid()).thenReturn(OWNER);
    lenient().when(player.inventory()).thenReturn(inventory);
    lenient().when(player.world()).thenReturn(world);
    lenient().when(player.isOnline()).thenReturn(true);
    lenient().when(player.isOnGround()).thenReturn(true);
    lenient().when(player.location()).thenAnswer(call -> new Loc(world, 0D, 64D, 0D));
    lenient().when(player.eyeLocation()).thenAnswer(call -> new Loc(world, 0D, 65.6D, 0D));
    scheduling = mockStatic(AdaptApi.class, CALLS_REAL_METHODS);
    scheduling.when(AdaptApi::scheduler).thenReturn(adaptScheduler);
    when(adaptScheduler.runEntity(any(EntityRef.class), any(Runnable.class))).thenReturn(true);
    when(adaptScheduler.owns(any(EntityRef.class))).thenReturn(true);
    feedback = mockStatic(OverseerFeedback.class);
    armor = mockStatic(OverseerArmor.class);
    items = mockStatic(GolemItems.class);
    items.when(() -> GolemItems.crookChannel(any())).thenReturn(Optional.empty());
    GolemRegistry registry = mock(GolemRegistry.class);
    lenient().when(registry.loadedOf(OWNER)).thenReturn(loaded);
    runtime = mockStatic(GolemancyRuntime.class);
    runtime.when(GolemancyRuntime::registry).thenReturn(Optional.of(registry));
    minions = mockStatic(MinionIdentity.class);
    Overseer.register(receiver);
    adaptation = spy(PlacedAdaptations.on(SkillGolemancy.ID, () -> new GolemOverseer(now::get)));
    signalFeedback(GolemancyPreferences.SignalFeedback.CHIME_AND_PARTICLES);
  }

  @AfterEach
  void tearDown() {
    if (adaptation != null) {
      adaptation.unregister();
    }
    Overseer.unregister(receiver);
    minions.close();
    runtime.close();
    items.close();
    armor.close();
    feedback.close();
    scheduling.close();
  }

  @Test
  void withoutOverseerTheCrookSignalsNothing() {
    level(0);

    adaptation.cycle(player);
    adaptation.send(player);

    assertThat(heard).isEmpty();
    feedback.verifyNoInteractions();
  }

  @Test
  void aSendCarriesTheOwnerPositionAndLevelRadiusAndStartsTheCooldown() {
    level(3);

    adaptation.send(player);
    adaptation.send(player);

    assertThat(heard).singleElement().satisfies(signal -> {
      assertThat(signal.owner()).isEqualTo(OWNER);
      assertThat(signal.signal()).isEqualTo(GolemSignal.FOLLOW);
      assertThat(signal.radius()).isEqualTo(32D);
      assertThat(signal.channel()).isNull();
      assertThat(signal.origin()).isEqualTo(new Loc(world, 0D, 64D, 0D));
      assertThat(signal.target()).isNull();
    });
    feedback.verify(() -> OverseerFeedback.sent(adaptation, player, GolemSignal.FOLLOW));

    now.addAndGet(1_000L);
    adaptation.send(player);
    assertThat(heard).hasSize(2);
  }

  @Test
  void actionBarOnlyFeedbackSendsTheSignalWithoutTheChimeOrThePulse() {
    level(3);
    signalFeedback(GolemancyPreferences.SignalFeedback.ACTION_BAR_ONLY);
    EntityRef golem = mock(EntityRef.class);

    adaptation.send(player);

    assertThat(heard).singleElement().satisfies(signal -> signal.reached(golem));
    feedback.verify(() -> OverseerFeedback.sent(any(), any(), any()), never());
    feedback.verify(() -> OverseerFeedback.pulse(any(), any()), never());
  }

  @Test
  void actionBarOnlyFeedbackCyclesTheSignalWithoutTheClick() {
    level(3);
    signalFeedback(GolemancyPreferences.SignalFeedback.ACTION_BAR_ONLY);

    adaptation.cycle(player);
    adaptation.send(player);

    assertThat(heard).singleElement().satisfies(signal -> assertThat(signal.signal()).isEqualTo(GolemSignal.HOLD));
    feedback.verify(() -> OverseerFeedback.cycled(any(), any()), never());
  }

  @Test
  void chimeAndParticlesFeedbackPulsesEachGolemTheSignalReaches() {
    level(3);
    EntityRef golem = mock(EntityRef.class);

    adaptation.send(player);

    assertThat(heard).singleElement().satisfies(signal -> signal.reached(golem));
    feedback.verify(() -> OverseerFeedback.sent(adaptation, player, GolemSignal.FOLLOW));
    feedback.verify(() -> OverseerFeedback.pulse(any(), eq(golem)));
  }

  @Test
  void theSignalFeedbackControlIsListedAndDefaultsToChimeAndParticles() {
    GolemOverseer fresh = PlacedAdaptations.on(SkillGolemancy.ID, () -> new GolemOverseer(now::get));

    assertThat(fresh.getPlayerPreferences()).containsExactly(CommonPreferences.ENABLED, GolemancyPreferences.SIGNAL_FEEDBACK);
    assertThat(GolemancyPreferences.SIGNAL_FEEDBACK.defaultValue()).isEqualTo(GolemancyPreferences.SignalFeedback.CHIME_AND_PARTICLES);
    GolemancyPreferenceAssertions.assertVisibleFrom(fresh, GolemancyPreferences.SIGNAL_FEEDBACK, 0);
  }

  @Test
  void cyclingPicksTheSignalTheNextSendCarries() {
    level(1);

    adaptation.cycle(player);
    adaptation.send(player);

    assertThat(heard).singleElement().satisfies(signal -> assertThat(signal.signal()).isEqualTo(GolemSignal.HOLD));
    feedback.verify(() -> OverseerFeedback.cycled(adaptation, player));
  }

  @Test
  void theCrookChannelRidesAlongFromTheFilterLevel() {
    items.when(() -> GolemItems.crookChannel(crook)).thenReturn(Optional.of(DyeColor.RED));
    level(2);
    adaptation.send(player);
    now.addAndGet(1_000L);
    level(3);
    adaptation.send(player);

    assertThat(heard).extracting(OverseerSignal::channel).containsExactly(null, DyeColor.RED);
    assertThat(adaptation.filtersChannels(player)).isTrue();
    level(2);
    assertThat(adaptation.filtersChannels(player)).isFalse();
  }

  @Test
  void onlyFollowCarriesARallyAndOnlyFromTheRallyLevel() {
    level(3);
    adaptation.send(player);
    now.addAndGet(1_000L);
    level(4);
    adaptation.send(player);
    now.addAndGet(1_000L);
    adaptation.cycle(player);
    adaptation.send(player);

    assertThat(heard).extracting(signal -> signal.rally() != null).containsExactly(false, true, false);
  }

  @Test
  void anAirborneFollowCarriesNoRallyAndLeavesTheCooldownForTheNextGroundedOne() {
    level(4);
    when(player.isOnGround()).thenReturn(false);
    adaptation.send(player);
    now.addAndGet(1_000L);
    when(player.isOnGround()).thenReturn(true);
    adaptation.send(player);

    assertThat(heard).extracting(signal -> signal.rally() != null).containsExactly(false, true);
    assertThat(heard.get(1).claimRally()).isTrue();
  }

  @Test
  void withoutGuardianCoreFrostGolemsUseTheDefaultSentryRange() {
    assertThat(adaptation.sentryRange()).isEqualTo(10D);
  }

  @Test
  void theRallyCooldownSpansFollowSignals() {
    level(4);
    adaptation.send(player);
    now.addAndGet(1_000L);
    adaptation.send(player);
    now.addAndGet(60_000L);
    adaptation.send(player);

    assertThat(heard).extracting(OverseerSignal::claimRally).containsExactly(true, false, true);
  }

  @Test
  void sicSendsTheTargetInSightWithItsHoldDeadline() {
    MobRef zombie = living(MobRef.class);
    sight(zombie);
    doReturn(true).when(adaptation).canPVE(any(PlayerRef.class), any(EntityRef.class));
    level(2);
    cycleToSic();

    adaptation.send(player);

    assertThat(heard).singleElement().satisfies(signal -> {
      assertThat(signal.signal()).isEqualTo(GolemSignal.SIC);
      assertThat(signal.target()).isSameAs(zombie);
      assertThat(signal.commandUntilMillis()).isEqualTo(120_000L);
      assertThat(signal.radius()).isEqualTo(24D);
    });
  }

  @Test
  void sicWithNothingInSightSendsNothingAndLeavesTheCooldownFree() {
    sight(null);
    level(2);
    cycleToSic();

    adaptation.send(player);
    assertThat(heard).isEmpty();

    MobRef zombie = living(MobRef.class);
    sight(zombie);
    doReturn(true).when(adaptation).canPVE(any(PlayerRef.class), any(EntityRef.class));
    adaptation.send(player);

    assertThat(heard).singleElement().satisfies(signal -> assertThat(signal.target()).isSameAs(zombie));
  }

  @Test
  void sicNeverTargetsTheOwnerTheirPetsOrWhatProtectionForbids() {
    MobRef pet = living(MobRef.class);
    when(pet.tameOwner()).thenReturn(OWNER);
    PlayerRef rival = living(PlayerRef.class);
    MobRef zombie = living(MobRef.class);
    MobRef dead = living(MobRef.class);
    when(dead.isDead()).thenReturn(true);
    doReturn(false).when(adaptation).canPVP(any(PlayerRef.class), any(Loc.class));
    doReturn(true).when(adaptation).canPVE(any(PlayerRef.class), any(EntityRef.class));

    assertThat(adaptation.canSic(player, pet)).isFalse();
    assertThat(adaptation.canSic(player, player)).isFalse();
    assertThat(adaptation.canSic(player, rival)).isFalse();
    assertThat(adaptation.canSic(player, dead)).isFalse();
    assertThat(adaptation.canSic(player, zombie)).isTrue();

    doReturn(true).when(adaptation).canPVP(any(PlayerRef.class), any(Loc.class));
    doReturn(false).when(adaptation).canPVE(any(PlayerRef.class), any(EntityRef.class));
    assertThat(adaptation.canSic(player, rival)).isTrue();
    assertThat(adaptation.canSic(player, zombie)).isFalse();
  }

  @Test
  void quittingForgetsTheSignalCycleAndRemovesTheFormationArmor() {
    level(1);
    adaptation.cycle(player);
    PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
    when(quit.player()).thenReturn(player);

    adaptation.on(quit);
    adaptation.send(player);

    assertThat(heard).singleElement().satisfies(signal -> assertThat(signal.signal()).isEqualTo(GolemSignal.FOLLOW));
    armor.verify(() -> OverseerArmor.remove(adaptation, player));
  }

  @Test
  void formationRingsFollowingGolemsAndArmsTheOwnerForThoseNearby() {
    adaptation.onRuntimeActivated();
    GolemHandle near = golem(GolemMode.FOLLOW, 3D, false);
    GolemHandle close = golem(GolemMode.FOLLOW, 5D, false);
    GolemHandle trailing = golem(GolemMode.FOLLOW, 20D, false);
    GolemHandle holding = golem(GolemMode.STAY, 2D, false);
    GolemHandle asleep = golem(GolemMode.FOLLOW, 2D, true);
    level(5);

    adaptation.reconcile(player);

    armor.verify(() -> OverseerArmor.settle(adaptation, player, 2D));
    Loc ownerAt = new Loc(world, 0D, 64D, 0D);
    assertThat(GolemFormation.slot(near, ownerAt)).isNotNull();
    assertThat(GolemFormation.slot(close, ownerAt)).isNotNull();
    assertThat(GolemFormation.slot(trailing, ownerAt)).isNotNull();
    assertThat(GolemFormation.slot(holding, ownerAt)).isNull();
    assertThat(GolemFormation.slot(asleep, ownerAt)).isNull();
  }

  @Test
  void belowTheFormationLevelTheRingBreaksAndTheArmorGoes() {
    adaptation.onRuntimeActivated();
    GolemHandle near = golem(GolemMode.FOLLOW, 3D, false);
    level(5);
    adaptation.reconcile(player);

    level(4);
    adaptation.reconcile(player);

    armor.verify(() -> OverseerArmor.settle(adaptation, player, 0D));
    assertThat(GolemFormation.slot(near, new Loc(world, 0D, 64D, 0D))).isNull();
  }

  @Test
  void deathAndWorldChangesRemoveTheFormationArmorAndRing() {
    adaptation.onRuntimeActivated();
    GolemHandle near = golem(GolemMode.FOLLOW, 3D, false);
    level(5);
    adaptation.reconcile(player);
    PlayerDeathEvent death = mock(PlayerDeathEvent.class);
    when(death.player()).thenReturn(player);

    adaptation.on(death);

    assertThat(GolemFormation.slot(near, new Loc(world, 0D, 64D, 0D))).isNull();
    adaptation.reconcile(player);
    PlayerChangedWorldEvent travel = mock(PlayerChangedWorldEvent.class);
    when(travel.player()).thenReturn(player);

    adaptation.on(travel);

    assertThat(GolemFormation.slot(near, new Loc(world, 0D, 64D, 0D))).isNull();
    armor.verify(() -> OverseerArmor.remove(adaptation, player), times(2));
  }

  private void level(int level) {
    doReturn(level).when(adaptation).getActiveLevel(player);
  }

  private void signalFeedback(GolemancyPreferences.SignalFeedback value) {
    lenient().doReturn(value).when(adaptation).preference(player, GolemancyPreferences.SIGNAL_FEEDBACK);
  }

  private void cycleToSic() {
    for (int step = 0; step < 4; step++) {
      adaptation.cycle(player);
    }
  }

  private void sight(LivingRef target) {
    lenient().when(world.rayTraceBlock(any(Vec3.class), any(Vec3.class), anyDouble(), eq(false))).thenReturn(null);
    lenient().when(world.rayTraceEntity(any(Vec3.class), any(Vec3.class), anyDouble(), anyDouble(), any())).thenReturn(target);
  }

  private <T extends LivingRef> T living(Class<T> type) {
    T entity = mock(type);
    lenient().when(entity.isValid()).thenReturn(true);
    lenient().when(entity.uuid()).thenReturn(UUID.randomUUID());
    lenient().when(entity.world()).thenReturn(world);
    lenient().when(entity.location()).thenAnswer(call -> new Loc(world, 0D, 64D, 4D));
    if (entity instanceof PlayerRef rival) {
      lenient().when(rival.gameMode()).thenReturn(GameMode.SURVIVAL);
      lenient().when(AdaptCore.platform().viewers().canSee(player, rival)).thenReturn(true);
    }
    return entity;
  }

  private GolemHandle golem(GolemMode mode, double distance, boolean dormant) {
    MobRef body = mock(MobRef.class);
    lenient().when(body.isValid()).thenReturn(true);
    lenient().when(body.world()).thenReturn(world);
    lenient().when(body.location()).thenAnswer(call -> new Loc(world, distance, 64D, 0D));
    GolemState state = mock(GolemState.class);
    lenient().when(state.mode()).thenReturn(mode);
    GolemHandle handle = mock(GolemHandle.class);
    lenient().when(handle.id()).thenReturn(UUID.randomUUID());
    lenient().when(handle.owner()).thenReturn(OWNER);
    lenient().when(handle.entity()).thenReturn(body);
    lenient().when(handle.state()).thenReturn(state);
    lenient().when(handle.isDormant()).thenReturn(dormant);
    loaded.add(handle);
    return handle;
  }
}

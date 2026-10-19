package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.golem.GolemFormation;
import art.arcane.adapt.api.golem.GolemHandle;
import art.arcane.adapt.api.golem.GolemMode;
import art.arcane.adapt.api.golem.GolemRegistry;
import art.arcane.adapt.api.golem.GolemState;
import art.arcane.adapt.api.golem.GolemancyRuntime;
import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.golem.overseer.GolemSignal;
import art.arcane.adapt.api.golem.overseer.Overseer;
import art.arcane.adapt.api.golem.overseer.OverseerReceiver;
import art.arcane.adapt.api.golem.overseer.OverseerSignal;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.util.common.scheduling.J;
import org.bukkit.DyeColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class GolemOverseerTest extends AdaptTestBase {
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000a01");

  private final AtomicLong now = new AtomicLong(100_000L);
  private final List<OverseerSignal> heard = new ArrayList<>();
  private final OverseerReceiver receiver = heard::add;
  private final List<GolemHandle> loaded = new ArrayList<>();
  private MockedStatic<J> scheduling;
  private MockedStatic<OverseerFeedback> feedback;
  private MockedStatic<OverseerArmor> armor;
  private MockedStatic<GolemItems> items;
  private MockedStatic<GolemancyRuntime> runtime;
  private MockedStatic<MinionIdentity> minions;
  private World world;
  private Player player;
  private ItemStack crook;
  private GolemOverseer adaptation;

  @BeforeEach
  void setUp() {
    world = mock(World.class);
    crook = mock(ItemStack.class);
    PlayerInventory inventory = mock(PlayerInventory.class);
    lenient().when(inventory.getItemInMainHand()).thenReturn(crook);
    player = mock(Player.class);
    lenient().when(player.getUniqueId()).thenReturn(OWNER);
    lenient().when(player.getInventory()).thenReturn(inventory);
    lenient().when(player.getWorld()).thenReturn(world);
    lenient().when(player.isOnline()).thenReturn(true);
    lenient().when(player.isOnGround()).thenReturn(true);
    lenient().when(player.getLocation()).thenAnswer(call -> new Location(world, 0D, 64D, 0D));
    lenient().when(player.getEyeLocation()).thenAnswer(call -> new Location(world, 0D, 65.6D, 0D));
    scheduling = mockStatic(J.class);
    scheduling.when(() -> J.runEntity(any(Entity.class), any(Runnable.class))).thenReturn(true);
    scheduling.when(() -> J.isOwnedByCurrentRegion(any(Entity.class))).thenReturn(true);
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
    adaptation = spy(new GolemOverseer(now::get));
    signalFeedback(GolemancyPreferences.SignalFeedback.CHIME_AND_PARTICLES);
  }

  @AfterEach
  void tearDown() {
    adaptation.unregister();
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
      assertThat(signal.origin()).isEqualTo(new Location(world, 0D, 64D, 0D));
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
    Entity golem = mock(Entity.class);

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
    Entity golem = mock(Entity.class);

    adaptation.send(player);

    assertThat(heard).singleElement().satisfies(signal -> signal.reached(golem));
    feedback.verify(() -> OverseerFeedback.sent(adaptation, player, GolemSignal.FOLLOW));
    feedback.verify(() -> OverseerFeedback.pulse(any(), eq(golem)));
  }

  @Test
  void theSignalFeedbackControlIsListedAndDefaultsToChimeAndParticles() {
    GolemOverseer fresh = new GolemOverseer(now::get);

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
    Zombie zombie = living(Zombie.class);
    sight(zombie);
    doReturn(true).when(adaptation).canPVE(any(Player.class), any(Entity.class));
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

    Zombie zombie = living(Zombie.class);
    sight(zombie);
    doReturn(true).when(adaptation).canPVE(any(Player.class), any(Entity.class));
    adaptation.send(player);

    assertThat(heard).singleElement().satisfies(signal -> assertThat(signal.target()).isSameAs(zombie));
  }

  @Test
  void sicNeverTargetsTheOwnerTheirPetsOrWhatProtectionForbids() {
    Wolf pet = living(Wolf.class);
    when(pet.isTamed()).thenReturn(true);
    when(pet.getOwner()).thenReturn(player);
    Player rival = living(Player.class);
    Zombie zombie = living(Zombie.class);
    Zombie dead = living(Zombie.class);
    when(dead.isDead()).thenReturn(true);
    doReturn(false).when(adaptation).canPVP(any(Player.class), any(Location.class));
    doReturn(true).when(adaptation).canPVE(any(Player.class), any(Entity.class));

    assertThat(adaptation.canSic(player, pet)).isFalse();
    assertThat(adaptation.canSic(player, player)).isFalse();
    assertThat(adaptation.canSic(player, rival)).isFalse();
    assertThat(adaptation.canSic(player, dead)).isFalse();
    assertThat(adaptation.canSic(player, zombie)).isTrue();

    doReturn(true).when(adaptation).canPVP(any(Player.class), any(Location.class));
    doReturn(false).when(adaptation).canPVE(any(Player.class), any(Entity.class));
    assertThat(adaptation.canSic(player, rival)).isTrue();
    assertThat(adaptation.canSic(player, zombie)).isFalse();
  }

  @Test
  void quittingForgetsTheSignalCycleAndRemovesTheFormationArmor() {
    level(1);
    adaptation.cycle(player);
    PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
    when(quit.getPlayer()).thenReturn(player);

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
    Location ownerAt = new Location(world, 0D, 64D, 0D);
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
    assertThat(GolemFormation.slot(near, new Location(world, 0D, 64D, 0D))).isNull();
  }

  @Test
  void deathAndWorldChangesRemoveTheFormationArmorAndRing() {
    adaptation.onRuntimeActivated();
    GolemHandle near = golem(GolemMode.FOLLOW, 3D, false);
    level(5);
    adaptation.reconcile(player);
    PlayerDeathEvent death = mock(PlayerDeathEvent.class);
    when(death.getEntity()).thenReturn(player);

    adaptation.on(death);

    assertThat(GolemFormation.slot(near, new Location(world, 0D, 64D, 0D))).isNull();
    adaptation.reconcile(player);
    PlayerChangedWorldEvent travel = mock(PlayerChangedWorldEvent.class);
    when(travel.getPlayer()).thenReturn(player);

    adaptation.on(travel);

    assertThat(GolemFormation.slot(near, new Location(world, 0D, 64D, 0D))).isNull();
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

  private void sight(LivingEntity target) {
    RayTraceResult hit = target == null ? null : new RayTraceResult(new Vector(0D, 65D, 4D), target, null);
    lenient().when(world.rayTrace(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER), anyBoolean(), anyDouble(), any()))
        .thenReturn(hit);
  }

  private <T extends LivingEntity> T living(Class<T> type) {
    T entity = mock(type);
    lenient().when(entity.isValid()).thenReturn(true);
    lenient().when(entity.getUniqueId()).thenReturn(UUID.randomUUID());
    lenient().when(entity.getWorld()).thenReturn(world);
    lenient().when(entity.getLocation()).thenAnswer(call -> new Location(world, 0D, 64D, 4D));
    if (entity instanceof Player rival) {
      lenient().when(rival.getGameMode()).thenReturn(GameMode.SURVIVAL);
      lenient().when(player.canSee(rival)).thenReturn(true);
    }
    return entity;
  }

  private GolemHandle golem(GolemMode mode, double distance, boolean dormant) {
    Mob body = mock(IronGolem.class);
    lenient().when(body.isValid()).thenReturn(true);
    lenient().when(body.getWorld()).thenReturn(world);
    lenient().when(body.getLocation()).thenAnswer(call -> new Location(world, distance, 64D, 0D));
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

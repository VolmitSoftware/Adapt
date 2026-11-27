package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.Skill;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.adaptation.AdaptationTeleportPort;
import art.arcane.adapt.engine.adaptation.PlayerStateRegistry;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.preference.PlayerPreferenceData;
import art.arcane.adapt.engine.preference.PlayerPreferences;
import art.arcane.adapt.engine.world.AdaptPlayer;
import art.arcane.adapt.engine.world.PlayerData;
import art.arcane.adapt.engine.world.PlayerDataAccess;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.PlayerTeleportEvent;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.testkit.RuntimeSpy;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.testkit.TestPlatform;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.Mockito.lenient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RiftBlinkPreferencesTest {
  @Test
  void personalLandingAndMomentumRemainWithinServerBounds() {
    assertThat(RiftBlink.landingSnapDepth(1, RiftBlink.Landing.NEAR)).isEqualTo(1);
    assertThat(RiftBlink.landingSnapDepth(20, RiftBlink.Landing.NEAR)).isEqualTo(2);
    assertThat(RiftBlink.landingSnapDepth(20, RiftBlink.Landing.NONE)).isZero();
    assertThat(RiftBlink.landingSnapDepth(100, RiftBlink.Landing.SERVER)).isEqualTo(32);
    assertThat(RiftBlink.momentumMultiplier(RiftBlink.Momentum.HALF)).isEqualTo(0.5D);
    assertThat(RiftBlink.momentumMultiplier(RiftBlink.Momentum.STOP)).isZero();
  }

  @Test
  void automaticPhasingUsesAimIntentAndCannotOverrideServerDenial() {
    assertThat(RiftBlink.phaseForDirection(true, RiftBlink.Phasing.AUTO, false, 0.4D)).isFalse();
    assertThat(RiftBlink.phaseForDirection(true, RiftBlink.Phasing.AUTO, false, 0D)).isTrue();
    assertThat(RiftBlink.phaseForDirection(true, RiftBlink.Phasing.AUTO, true, -0.7D)).isTrue();
    assertThat(RiftBlink.phaseForDirection(false, RiftBlink.Phasing.AUTO, true, -0.7D)).isFalse();
  }

  @Test
  void successfulBlinkUsesPersonalExitMomentum() {
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("adapt:rift/blink", "momentum", "STOP");
      when(fixture.player.teleport(any(Loc.class), eq(TeleportCause.PLUGIN)))
          .thenReturn(CompletableFuture.completedFuture(true));
      fixture.blink.on(fixture.attack());
      verify(fixture.player).setVelocity(argThat((Vec3 velocity) -> sameVector(velocity, Vec3.ZERO)));
    }
  }

  @Test
  void reactiveModeAndItsDirectionsUnlockAtLevelTwo() {
    assertThat(RiftBlink.ACTIVATION.choice(RiftBlink.Activation.MANUAL).minimumLevel()).isEqualTo(1);
    assertThat(RiftBlink.ACTIVATION.choice(RiftBlink.Activation.REACTIVE).minimumLevel()).isEqualTo(2);
    assertThat(RiftBlink.REACTIVE_DIRECTION.defaultValue()).isEqualTo(RiftBlink.ReactiveDirection.LOOK);
    assertThat(RiftBlink.REACTIVE_DIRECTION.choices()).allMatch(choice -> choice.minimumLevel() == 2);
  }

  @Test
  void reactiveDirectionIsVisibleOnlyAtLevelTwoInEffectiveReactiveMode() {
    try (Fixture fixture = new Fixture()) {
      PlayerProfile owner = fixture.blink.getPlayer(fixture.player);
      fixture.level(2);
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.REACTIVE_DIRECTION)).isTrue();
      fixture.preferences.set("adapt:rift/blink", "reactive-direction", "AWAY_FROM_ATTACKER");
      fixture.preferences.set("adapt:rift/blink", "activation", "MANUAL");
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.REACTIVE_DIRECTION)).isFalse();
      assertThat(fixture.preferences.get("adapt:rift/blink", "reactive-direction")).isEqualTo("AWAY_FROM_ATTACKER");
      fixture.preferences.set("adapt:rift/blink", "activation", "REACTIVE");
      fixture.level(1);
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.REACTIVE_DIRECTION)).isFalse();
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.ACTIVATION)).isTrue();
      fixture.level(2);
      PreferencePolicy policy = PreferencePolicy.defaults(RiftBlink.ACTIVATION);
      policy.playerEditable = false;
      fixture.blink.getConfig().playerPreferences = Map.of("activation", policy);
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.REACTIVE_DIRECTION)).isFalse();
    }
  }

  @Test
  void manualDirectionDefaultsToLookAndBothChoicesUnlockAtLevelOne() {
    assertThat(RiftBlink.DIRECTION.defaultValue()).isEqualTo(RiftBlink.Direction.LOOK);
    assertThat(RiftBlink.DIRECTION.choices()).allMatch(choice -> choice.minimumLevel() == 1);
    try (Fixture fixture = new Fixture()) {
      PlayerProfile owner = fixture.blink.getPlayer(fixture.player);
      fixture.preferences.set("adapt:rift/blink", "activation", "MANUAL");
      fixture.level(1);
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.DIRECTION)).isTrue();
      assertThat(PlayerPreferences.resolve(fixture.blink, fixture.data, 1, RiftBlink.DIRECTION))
          .isEqualTo(RiftBlink.Direction.LOOK);
      fixture.preferences.set("adapt:rift/blink", "direction", "MOMENTUM");
      assertThat(PlayerPreferences.resolve(fixture.blink, fixture.data, 1, RiftBlink.DIRECTION))
          .isEqualTo(RiftBlink.Direction.MOMENTUM);
      fixture.level(2);
      fixture.preferences.set("adapt:rift/blink", "activation", "REACTIVE");
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.DIRECTION)).isFalse();
      assertThat(fixture.preferences.get("adapt:rift/blink", "direction")).isEqualTo("MOMENTUM");
      PreferencePolicy policy = PreferencePolicy.defaults(RiftBlink.ACTIVATION);
      policy.playerEditable = false;
      fixture.blink.getConfig().playerPreferences = Map.of("activation", policy);
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.DIRECTION)).isTrue();
    }
  }

  @Test
  void serverCanLockOrRestrictManualDirectionWithoutDiscardingTheSavedChoice() {
    try (Fixture fixture = new Fixture()) {
      PlayerProfile owner = fixture.blink.getPlayer(fixture.player);
      fixture.preferences.set("adapt:rift/blink", "direction", "MOMENTUM");
      PreferencePolicy policy = PreferencePolicy.defaults(RiftBlink.DIRECTION);
      policy.playerEditable = false;
      fixture.blink.getConfig().playerPreferences = Map.of("direction", policy);
      assertThat(PlayerPreferences.resolve(fixture.blink, fixture.data, 1, RiftBlink.DIRECTION))
          .isEqualTo(RiftBlink.Direction.LOOK);
      fixture.blink.getConfig().playerPreferences = Map.of("direction", lookOnly());
      assertThat(PlayerPreferences.resolve(fixture.blink, fixture.data, 1, RiftBlink.DIRECTION))
          .isEqualTo(RiftBlink.Direction.LOOK);
      assertThat(fixture.preferences.get("adapt:rift/blink", "direction")).isEqualTo("MOMENTUM");
    }
  }

  @Test
  void reactiveMovementUsesFullMotionInsteadOfLookOrBukkitVelocity() {
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("adapt:rift/blink", "reactive-direction", "MOMENTUM");
      fixture.preferences.set("adapt:rift/blink", "landing", "NONE");
      when(fixture.player.velocity()).thenReturn(new Vec3(-1D, 0D, 0D));
      fixture.move(new Vec3(0D, 3D, 4D));
      Vec3 displacement = fixture.reactiveDestination().position().subtract(fixture.player.location().position());
      assertThat(displacement.x()).isCloseTo(0D, offset(0.000001D));
      assertThat(displacement.y()).isPositive();
      assertThat(displacement.z()).isPositive();
      assertThat(displacement.normalize().y()).isCloseTo(0.6D, offset(0.000001D));
      assertThat(displacement.normalize().z()).isCloseTo(0.8D, offset(0.000001D));
      assertThat(displacement.length()).isBetween(7D, 8D);
    }
  }

  @Test
  void movementSpeedDoesNotIncreaseBlinkRange() {
    Vec3 slowDestination;
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("adapt:rift/blink", "reactive-direction", "MOMENTUM");
      fixture.preferences.set("adapt:rift/blink", "landing", "NONE");
      fixture.move(new Vec3(0D, 0D, 0.05D));
      slowDestination = fixture.reactiveDestination().position();
    }
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("adapt:rift/blink", "reactive-direction", "MOMENTUM");
      fixture.preferences.set("adapt:rift/blink", "landing", "NONE");
      fixture.move(new Vec3(0D, 0D, 20D));
      Loc destination = fixture.reactiveDestination();
      assertThat(destination.position()).isEqualTo(slowDestination);
      assertThat(destination.distance(fixture.player.location())).isCloseTo(8D, offset(0.000001D));
    }
  }

  @Test
  void missingZeroAndNonFiniteMotionFallBackToLooking() {
    for (Vec3 motion : new Vec3[]{Vec3.ZERO, new Vec3(0D, 0D, 0.00001D),
        new Vec3(Double.NaN, 0D, 0D), new Vec3(0D, Double.POSITIVE_INFINITY, 0D)}) {
      try (Fixture fixture = new Fixture()) {
        fixture.preferences.set("adapt:rift/blink", "reactive-direction", "MOMENTUM");
        fixture.move(motion);
        fixture.assertReactiveLooksForward();
      }
    }
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("adapt:rift/blink", "reactive-direction", "MOMENTUM");
      fixture.assertReactiveLooksForward();
    }
  }

  @Test
  void staleOrFutureMotionFallsBackToLooking() {
    for (int currentTick : new int[]{104, 99}) {
      try (Fixture fixture = new Fixture()) {
        fixture.preferences.set("adapt:rift/blink", "reactive-direction", "MOMENTUM");
        when(fixture.player.ticksLived()).thenReturn(100);
        fixture.move(new Vec3(0D, 0D, 1D));
        when(fixture.player.ticksLived()).thenReturn(currentTick);
        fixture.assertReactiveLooksForward();
      }
    }
  }

  @Test
  void teleportClearsMovementBeforeTheNextReactiveBlink() {
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("adapt:rift/blink", "reactive-direction", "MOMENTUM");
      fixture.move(new Vec3(0D, 0D, 1D));
      fixture.blink.on(new PlayerTeleportEvent(fixture.player, fixture.player.location(),
          fixture.player.location().add(0D, 0D, 100D), TeleportCause.PLUGIN));
      fixture.assertReactiveLooksForward();
    }
  }

  @Test
  void reactiveServerDirectionLockOverridesPersonalMovement() {
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("adapt:rift/blink", "reactive-direction", "MOMENTUM");
      fixture.move(new Vec3(0D, 0D, 1D));
      PreferencePolicy policy = PreferencePolicy.defaults(RiftBlink.REACTIVE_DIRECTION);
      policy.playerEditable = false;
      fixture.blink.getConfig().playerPreferences = Map.of("reactive-direction", policy);
      fixture.assertReactiveLooksForward();
      assertThat(fixture.preferences.get("adapt:rift/blink", "reactive-direction")).isEqualTo("MOMENTUM");
    }
  }

  @Test
  void reactiveModeOnlyDodgesActualMeleeAndProjectileDamage() {
    for (DamageCause cause : DamageCause.values()) {
      boolean attack = cause == DamageCause.ENTITY_ATTACK
          || cause == DamageCause.ENTITY_SWEEP_ATTACK
          || cause == DamageCause.PROJECTILE;
      assertThat(RiftBlink.isReactiveAttack(cause, 4D)).isEqualTo(attack);
    }
    assertThat(RiftBlink.isReactiveAttack(DamageCause.PROJECTILE, 0D)).isFalse();
    assertThat(RiftBlink.isReactiveAttack(DamageCause.ENTITY_ATTACK, Double.NaN)).isFalse();
  }

  @Test
  void verticalityPrefersHeightWhileDistancePrefersReach() {
    WorldRef world = mock(WorldRef.class);
    Loc origin = new Loc(world, 0D, 64D, 0D);
    Loc far = new Loc(world, 15D, 64D, 0D);
    Loc high = new Loc(world, 6D, 68D, 0D);
    Loc higherFar = new Loc(world, 9D, 68D, 0D);
    assertThat(RiftBlink.betterCandidate(origin, far, high, RiftBlink.Targeting.DISTANCE)).isSameAs(far);
    assertThat(RiftBlink.betterCandidate(origin, far, high, RiftBlink.Targeting.VERTICALITY)).isSameAs(high);
    assertThat(RiftBlink.betterCandidate(origin, high, higherFar, RiftBlink.Targeting.VERTICALITY)).isSameAs(higherFar);
  }

  @Test
  void awayDirectionUsesTheAttackAndFallsBackToLookWhenUnavailable() {
    WorldRef world = mock(WorldRef.class);
    Loc origin = new Loc(world, 0D, 64D, 0D);
    Vec3 look = new Vec3(0D, 0D, 1D);
    assertThat(RiftBlink.awayDirection(origin, look, new Loc(world, 5D, 68D, 0D)))
        .isEqualTo(new Vec3(-1D, 0D, 0D));
    assertThat(RiftBlink.awayDirection(origin, look, null)).isEqualTo(look).isNotSameAs(look);
    assertThat(RiftBlink.awayDirection(origin, look, origin)).isEqualTo(look);
  }

  @Test
  void failedReactiveTeleportReplaysTheOriginalDamageWithoutAnotherDodge() {
    try (Fixture fixture = new Fixture()) {
      CompletableFuture<Boolean> teleport = new CompletableFuture<>();
      when(fixture.player.teleport(any(Loc.class), eq(TeleportCause.PLUGIN))).thenReturn(teleport);
      EntityDamageByEntityEvent attack = fixture.attack();
      doAnswer(call -> {
        fixture.blink.on(fixture.attack());
        return null;
      }).when(attack).replay(fixture.player, 4D);

      fixture.blink.on(attack);
      verify(attack).setCancelled(true);
      EntityDamageByEntityEvent second = fixture.attack();
      fixture.blink.on(second);
      verify(second, never()).setCancelled(true);
      teleport.complete(false);

      verify(attack).replay(fixture.player, 4D);
      verify(fixture.player).teleport(any(Loc.class), eq(TeleportCause.PLUGIN));
    }
  }

  @Test
  void successfulReactiveTeleportSharesCooldownAcrossPreferenceChanges() {
    try (Fixture fixture = new Fixture()) {
      when(fixture.player.teleport(any(Loc.class), eq(TeleportCause.PLUGIN))).thenReturn(CompletableFuture.completedFuture(true));
      EntityDamageByEntityEvent first = fixture.attack();
      fixture.blink.on(first);
      verify(first).setCancelled(true);

      fixture.preferences.set("adapt:rift/blink", "enabled", "OFF");
      fixture.preferences.set("adapt:rift/blink", "activation", "MANUAL");
      fixture.preferences.set("adapt:rift/blink", "activation", "REACTIVE");
      fixture.preferences.set("adapt:rift/blink", "enabled", "ON");
      EntityDamageByEntityEvent second = fixture.attack();
      fixture.blink.on(second);

      verify(second, never()).setCancelled(true);
      verify(first, never()).replay(any(PlayerRef.class), anyDouble());
    }
  }

  @Test
  void disabledAndUnlearnedReactiveModesLeaveDamageUntouched() {
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("adapt:rift/blink", "enabled", "OFF");
      EntityDamageByEntityEvent disabled = fixture.attack();
      fixture.blink.on(disabled);
      verify(disabled, never()).setCancelled(true);
      fixture.preferences.set("adapt:rift/blink", "enabled", "ON");
      fixture.level(1);
      EntityDamageByEntityEvent locked = fixture.attack();
      fixture.blink.on(locked);
      verify(locked, never()).setCancelled(true);
      verify(fixture.player, never()).teleport(any(Loc.class), any(TeleportCause.class));
    }
  }

  @Test
  void protectionDenialDoesNotCancelTheAttackOrStartTeleport() {
    try (Fixture fixture = new Fixture()) {
      doReturn(false).when(fixture.blink).canInteract(eq(fixture.player), any(Loc.class));
      EntityDamageByEntityEvent denied = fixture.attack();
      fixture.blink.on(denied);
      verify(denied, never()).setCancelled(true);
      verify(fixture.player, never()).teleport(any(Loc.class), any(TeleportCause.class));
    }
  }

  @Test
  void cancelledAdaptationTeleportDoesNotConsumeTheAttack() {
    try (Fixture fixture = new Fixture()) {
      AdaptPorts.install(AdaptationTeleportPort.class, new AdaptationTeleportPort() {
        @Override
        public Loc announce(PlayerRef player, Adaptation<?> adaptation, Loc from, Loc to) {
          return null;
        }
      });
      EntityDamageByEntityEvent denied = fixture.attack();
      fixture.blink.on(denied);
      verify(denied, never()).setCancelled(true);
      verify(fixture.player, never()).teleport(any(Loc.class), any(TeleportCause.class));
    }
  }

  @Test
  void foliaUnownedDestinationsAreRejectedWithoutBlockReads() {
    try (Fixture fixture = new Fixture()) {
      fixture.scheduling.when(Tasks::isFoliaThreading).thenReturn(true);
      fixture.scheduling.when(() -> Tasks.isOwnedByCurrentRegion(any(Loc.class), anyDouble(), anyDouble())).thenReturn(false);
      EntityDamageByEntityEvent attack = fixture.attack();
      fixture.blink.on(attack);
      verify(attack, never()).setCancelled(true);
      verify(fixture.world, never()).blockAt(any(Loc.class));
      verify(fixture.world, never()).blockAt(anyInt(), anyInt(), anyInt());
      verify(fixture.player, never()).teleport(any(Loc.class), any(TeleportCause.class));
    }
  }

  @Test
  void quittingWithAPendingDodgeReplaysDamageOnlyOnce() {
    try (Fixture fixture = new Fixture()) {
      CompletableFuture<Boolean> teleport = new CompletableFuture<>();
      when(fixture.player.teleport(any(Loc.class), eq(TeleportCause.PLUGIN))).thenReturn(teleport);
      EntityDamageByEntityEvent attack = fixture.attack();
      fixture.blink.on(attack);
      PlayerQuitEvent quit = new PlayerQuitEvent(fixture.player);
      fixture.blink.on(quit);
      teleport.complete(false);
      verify(attack).replay(fixture.player, 4D);
    }
  }

  @Test
  void clearingPlayerStateDoesNotDiscardAPendingFailedDodge() {
    try (Fixture fixture = new Fixture()) {
      CompletableFuture<Boolean> teleport = new CompletableFuture<>();
      when(fixture.player.teleport(any(Loc.class), eq(TeleportCause.PLUGIN))).thenReturn(teleport);
      EntityDamageByEntityEvent attack = fixture.attack();
      fixture.blink.on(attack);
      verify(attack).setCancelled(true);

      PlayerStateRegistry.clearPlayer(fixture.player.uuid());
      EntityDamageByEntityEvent whilePending = fixture.attack();
      fixture.blink.on(whilePending);
      verify(whilePending, never()).setCancelled(true);
      teleport.complete(false);

      verify(attack).replay(fixture.player, 4D);
    }
  }

  @Test
  void clearingPlayerStateDoesNotDiscardAPendingSuccessfulBlinkCostOrCooldown() {
    try (Fixture fixture = new Fixture()) {
      fixture.blink.getConfig().pearlDamageBase = 5D;
      fixture.blink.getConfig().minimumPearlDamage = 1D;
      CompletableFuture<Boolean> teleport = new CompletableFuture<>();
      when(fixture.player.teleport(any(Loc.class), eq(TeleportCause.PLUGIN))).thenReturn(teleport);
      EntityDamageByEntityEvent attack = fixture.attack();
      fixture.blink.on(attack);

      PlayerStateRegistry.clearPlayer(fixture.player.uuid());
      teleport.complete(true);
      EntityDamageByEntityEvent onCooldown = fixture.attack();
      fixture.blink.on(onCooldown);

      verify(fixture.player).damage(4D, Id.minecraft("ender_pearl"));
      verify(attack, never()).replay(any(PlayerRef.class), anyDouble());
      verify(onCooldown, never()).setCancelled(true);
    }
  }

  @Test
  void quittingAfterPlayerStateClearStillSettlesPendingDamageOnce() {
    try (Fixture fixture = new Fixture()) {
      CompletableFuture<Boolean> teleport = new CompletableFuture<>();
      when(fixture.player.teleport(any(Loc.class), eq(TeleportCause.PLUGIN))).thenReturn(teleport);
      EntityDamageByEntityEvent attack = fixture.attack();
      fixture.blink.on(attack);
      PlayerStateRegistry.clearPlayer(fixture.player.uuid());
      PlayerQuitEvent quit = new PlayerQuitEvent(fixture.player);

      fixture.blink.on(quit);
      teleport.complete(false);

      verify(attack).replay(fixture.player, 4D);
    }
  }

  @Test
  void oldCompletionCannotClearThePendingBlinkAfterRejoining() {
    try (Fixture fixture = new Fixture()) {
      CompletableFuture<Boolean> oldTeleport = new CompletableFuture<>();
      CompletableFuture<Boolean> newTeleport = new CompletableFuture<>();
      when(fixture.player.teleport(any(Loc.class), eq(TeleportCause.PLUGIN))).thenReturn(oldTeleport, newTeleport);
      fixture.blink.on(fixture.attack());
      PlayerQuitEvent quit = new PlayerQuitEvent(fixture.player);
      fixture.blink.on(quit);

      PlayerStateRegistry.clearPlayer(fixture.player.uuid());
      PlayerRef rejoined = mock(PlayerRef.class, delegatesTo(fixture.player));
      fixture.bind(rejoined);
      EntityDamageByEntityEvent newAttack = fixture.attack();
      when(newAttack.entity()).thenReturn(rejoined);
      fixture.blink.on(newAttack);
      verify(newAttack).setCancelled(true);

      oldTeleport.complete(true);
      EntityDamageByEntityEvent whilePending = fixture.attack();
      when(whilePending.entity()).thenReturn(rejoined);
      fixture.blink.on(whilePending);
      verify(whilePending, never()).setCancelled(true);

      newTeleport.complete(false);
      verify(newAttack).replay(rejoined, 4D);
    }
  }

  /** Bukkit Vector equality: components equal within 1e-6, so -0.0 and 0.0 match. */
  private static boolean sameVector(Vec3 actual, Vec3 expected) {
    return Math.abs(actual.x() - expected.x()) < 1.0E-6D
        && Math.abs(actual.y() - expected.y()) < 1.0E-6D
        && Math.abs(actual.z() - expected.z()) < 1.0E-6D;
  }

  private static final class Fixture implements AutoCloseable {
    private final TestAdapt adapt;
    private final WorldRef world = mock(WorldRef.class);
    private final PlayerRef player = mock(PlayerRef.class);
    private final PlayerPreferenceData preferences = new PlayerPreferenceData();
    private final PlayerData data = mock(PlayerData.class);
    private final AdaptPlayer owner = mock(AdaptPlayer.class);
    private final FxEmitter effects = mock(FxEmitter.class, RETURNS_SELF);
    private final RiftBlink blink;
    private final AdaptationHost runtime;
    private final MockedStatic<Tasks> scheduling;
    private final MockedStatic<PlayerDataAccess> playerData;

    private Fixture() {
      try {
        adapt = TestAdapt.start(TestPlatform.builder(Files.createTempDirectory("rift-blink")).build(), List.of());
      } catch (IOException failure) {
        throw new IllegalStateException(failure);
      }
      RiftBlink original = TestContent.adaptation(Id.adapt("rift"), RiftBlink::new);
      RiftBlink.Config config = new RiftBlink.Config();
      config.baseDistance = 8D;
      config.distanceFactor = 0D;
      config.pearlDamageBase = 0D;
      config.minimumPearlDamage = 0D;
      config.cooldownMillis = 60000;
      ComponentHost.of(original).useFixedConfig(config);
      runtime = RuntimeSpy.install(original, original);
      blink = spy(original);
      scheduling = SchedulerForwarding.mockTasks(CALLS_REAL_METHODS);
      playerData = mockStatic(PlayerDataAccess.class, CALLS_REAL_METHODS);
      playerData.when(() -> PlayerDataAccess.data(any(PlayerRef.class))).thenReturn(data);
      playerData.when(() -> PlayerDataAccess.player(any(PlayerRef.class))).thenReturn(owner);
      when(owner.getData()).thenReturn(data);
      when(owner.isRuntimeReady()).thenReturn(true);
      when(data.getPreferences()).thenReturn(preferences);
      Skill<?> skill = mock(Skill.class);
      lenient().when(skill.getId()).thenReturn(Id.adapt("rift"));
      lenient().when(skill.getColor()).thenReturn(Rgb.of(0x7C5CC8));
      doReturn(skill).when(runtime).skill();
      doReturn(effects).when(runtime).fx(any(Loc.class), any(FxPriority.class));
      doReturn(effects).when(runtime).fx(any(EntityRef.class), any(FxPriority.class));
      Loc origin = new Loc(world, 0.5D, 64D, 0.5D, -90F, 0F);
      when(player.location()).thenAnswer(call -> origin);
      when(player.eyeLocation()).thenAnswer(call -> origin.add(0D, 1.62D, 0D));
      when(player.height()).thenReturn(1.8D);
      when(player.uuid()).thenReturn(UUID.randomUUID());
      when(player.gameMode()).thenReturn(GameMode.SURVIVAL);
      when(player.isOnline()).thenReturn(true);
      when(player.isValid()).thenReturn(true);
      when(player.world()).thenReturn(world);
      when(world.minHeight()).thenReturn(-64);
      when(world.maxHeight()).thenReturn(320);
      when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
      when(world.isInsideBorder(any(Vec3.class))).thenReturn(true);
      when(world.blockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> block(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
      when(world.blockAt(any(Loc.class))).thenAnswer(call -> {
        Loc location = call.getArgument(0);
        return block(location.blockX(), location.blockY(), location.blockZ());
      });
      preferences.set("adapt:rift/blink", "activation", "REACTIVE");
      bind(player);
    }

    private void bind(PlayerRef actor) {
      PlayerProfile profile = mock(PlayerProfile.class);
      when(profile.player()).thenReturn(actor);
      when(profile.uuid()).thenAnswer(call -> actor.uuid());
      when(profile.isReady()).thenReturn(true);
      doReturn(profile).when(runtime).profile(actor);
      doReturn(owner).when(runtime).readyPlayer(actor);
      doReturn(2).when(runtime).level(actor);
      doReturn(true).when(blink).hasActiveAdaptation(actor);
      doReturn(true).when(blink).canInteract(eq(actor), any(Loc.class));
      doReturn(true).when(blink).checkRegion(eq(actor), any(Loc.class));
      doReturn(0).when(blink).getActiveSiblingLevel(actor, Id.adapt("rift/resist"));
    }

    private void level(int level) {
      doReturn(level).when(runtime).level(player);
    }

    private void move(Vec3 displacement) {
      Loc from = player.location();
      blink.on(new PlayerMoveEvent(player, from, from.add(displacement)));
    }

    private Loc reactiveDestination() {
      when(player.teleport(any(Loc.class), eq(TeleportCause.PLUGIN))).thenReturn(new CompletableFuture<>());
      EntityDamageByEntityEvent attack = attack();
      blink.on(attack);
      verify(attack).setCancelled(true);
      ArgumentCaptor<Loc> destination = ArgumentCaptor.forClass(Loc.class);
      verify(player).teleport(destination.capture(), eq(TeleportCause.PLUGIN));
      return destination.getValue();
    }

    private void assertReactiveLooksForward() {
      Loc destination = reactiveDestination();
      assertThat(destination.x()).isGreaterThan(player.location().x());
      assertThat(destination.z()).isCloseTo(player.location().z(), offset(0.000001D));
    }

    private BlockRef block(int x, int y, int z) {
      BlockRef block = mock(BlockRef.class);
      when(block.location()).thenReturn(new Loc(world, x, y, z));
      when(block.y()).thenReturn(y);
      when(block.type()).thenReturn(y < 64 ? Id.minecraft("stone") : Id.minecraft("air"));
      when(block.isPassable()).thenReturn(y >= 64);
      when(block.boundingBox()).thenReturn(new Box(x, y, z, x + 1D, y + 1D, z + 1D));
      when(block.relative(any(BlockFace.class))).thenAnswer(call -> {
        BlockFace face = call.getArgument(0);
        return block(x + face.dx(), y + face.dy(), z + face.dz());
      });
      return block;
    }

    private EntityDamageByEntityEvent attack() {
      EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
      when(event.entity()).thenReturn(player);
      when(event.cause()).thenReturn(DamageCause.PROJECTILE);
      when(event.finalDamage()).thenReturn(3D);
      when(event.damage()).thenReturn(6D);
      when(event.originalDamage()).thenReturn(4D);
      return event;
    }

    @Override
    public void close() {
      AdaptPorts.uninstall(AdaptationTeleportPort.class);
      playerData.close();
      scheduling.close();
      adapt.close();
    }
  }

  private static PreferencePolicy lookOnly() {
    PreferencePolicy policy = new PreferencePolicy();
    policy.defaultValue = RiftBlink.Direction.LOOK.name();
    policy.allowedValues = List.of(RiftBlink.Direction.LOOK.name());
    return policy;
  }
}

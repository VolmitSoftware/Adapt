package art.arcane.adapt.content.adaptation.rift;

import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.preference.PlayerPreferenceData;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.preference.PreferencePolicy;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.content.event.AdaptAdaptationTeleportEvent;
import art.arcane.adapt.util.common.compat.PaperCompat;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.registries.RegistryUtil;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.UnsafeValues;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.mockito.MockedStatic;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.AdditionalAnswers.delegatesTo;

class RiftBlinkPreferencesTest {
  @BeforeAll
  static void initializeRegistries() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<Sound> sounds = Registry.SOUNDS;
      doAnswer(call -> {
        Key key = call.getArgument(0);
        Sound sound = mock(Sound.class);
        when(sound.getKey()).thenReturn(new NamespacedKey(key.namespace(), key.value()));
        return sound;
      }).when(sounds).getOrThrow(any(Key.class));
      assertThat(Sound.ENTITY_ENDERMAN_TELEPORT).isNotNull();
      Registry<DamageType> damageTypes = access.getRegistry(RegistryKey.DAMAGE_TYPE);
      doAnswer(call -> mock(DamageType.class)).when(damageTypes).getOrThrow(any(Key.class));
      assertThat(DamageType.ENDER_PEARL).isNotNull();
    }
  }

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
      fixture.preferences.set("rift-blink", "momentum", "STOP");
      fixture.teleports.when(() -> PaperCompat.teleportAsync(eq(fixture.player), any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)))
          .thenReturn(CompletableFuture.completedFuture(true));
      fixture.blink.on(fixture.attack());
      verify(fixture.player).setVelocity(new Vector());
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
      AdaptPlayer owner = fixture.blink.getPlayer(fixture.player);
      doReturn(2).when(fixture.blink).getLevel(owner);
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.REACTIVE_DIRECTION)).isTrue();
      fixture.preferences.set("rift-blink", "reactive-direction", "AWAY_FROM_ATTACKER");
      fixture.preferences.set("rift-blink", "activation", "MANUAL");
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.REACTIVE_DIRECTION)).isFalse();
      assertThat(fixture.preferences.get("rift-blink", "reactive-direction")).isEqualTo("AWAY_FROM_ATTACKER");
      fixture.preferences.set("rift-blink", "activation", "REACTIVE");
      doReturn(1).when(fixture.blink).getLevel(owner);
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.REACTIVE_DIRECTION)).isFalse();
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.ACTIVATION)).isTrue();
      doReturn(2).when(fixture.blink).getLevel(owner);
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
      AdaptPlayer owner = fixture.blink.getPlayer(fixture.player);
      fixture.preferences.set("rift-blink", "activation", "MANUAL");
      doReturn(1).when(fixture.blink).getLevel(owner);
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.DIRECTION)).isTrue();
      assertThat(PlayerPreferences.resolve(fixture.blink, owner.getData(), 1, RiftBlink.DIRECTION))
          .isEqualTo(RiftBlink.Direction.LOOK);
      fixture.preferences.set("rift-blink", "direction", "MOMENTUM");
      assertThat(PlayerPreferences.resolve(fixture.blink, owner.getData(), 1, RiftBlink.DIRECTION))
          .isEqualTo(RiftBlink.Direction.MOMENTUM);
      doReturn(2).when(fixture.blink).getLevel(owner);
      fixture.preferences.set("rift-blink", "activation", "REACTIVE");
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.DIRECTION)).isFalse();
      assertThat(fixture.preferences.get("rift-blink", "direction")).isEqualTo("MOMENTUM");
      PreferencePolicy policy = PreferencePolicy.defaults(RiftBlink.ACTIVATION);
      policy.playerEditable = false;
      fixture.blink.getConfig().playerPreferences = Map.of("activation", policy);
      assertThat(fixture.blink.isPlayerPreferenceVisible(owner, RiftBlink.DIRECTION)).isTrue();
    }
  }

  @Test
  void serverCanLockOrRestrictManualDirectionWithoutDiscardingTheSavedChoice() {
    try (Fixture fixture = new Fixture()) {
      AdaptPlayer owner = fixture.blink.getPlayer(fixture.player);
      fixture.preferences.set("rift-blink", "direction", "MOMENTUM");
      PreferencePolicy policy = PreferencePolicy.defaults(RiftBlink.DIRECTION);
      policy.playerEditable = false;
      fixture.blink.getConfig().playerPreferences = Map.of("direction", policy);
      assertThat(PlayerPreferences.resolve(fixture.blink, owner.getData(), 1, RiftBlink.DIRECTION))
          .isEqualTo(RiftBlink.Direction.LOOK);
      fixture.blink.getConfig().playerPreferences = Map.of("direction", PreferencePolicy.of(RiftBlink.Direction.LOOK, RiftBlink.Direction.LOOK));
      assertThat(PlayerPreferences.resolve(fixture.blink, owner.getData(), 1, RiftBlink.DIRECTION))
          .isEqualTo(RiftBlink.Direction.LOOK);
      assertThat(fixture.preferences.get("rift-blink", "direction")).isEqualTo("MOMENTUM");
    }
  }

  @Test
  void reactiveMovementUsesFullMotionInsteadOfLookOrBukkitVelocity() {
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("rift-blink", "reactive-direction", "MOMENTUM");
      fixture.preferences.set("rift-blink", "landing", "NONE");
      when(fixture.player.getVelocity()).thenReturn(new Vector(-1D, 0D, 0D));
      fixture.move(new Vector(0D, 3D, 4D));
      Vector displacement = fixture.reactiveDestination().toVector().subtract(fixture.player.getLocation().toVector());
      assertThat(displacement.getX()).isCloseTo(0D, offset(0.000001D));
      assertThat(displacement.getY()).isPositive();
      assertThat(displacement.getZ()).isPositive();
      assertThat(displacement.clone().normalize().getY()).isCloseTo(0.6D, offset(0.000001D));
      assertThat(displacement.clone().normalize().getZ()).isCloseTo(0.8D, offset(0.000001D));
      assertThat(displacement.length()).isBetween(7D, 8D);
    }
  }

  @Test
  void movementSpeedDoesNotIncreaseBlinkRange() {
    Vector slowDestination;
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("rift-blink", "reactive-direction", "MOMENTUM");
      fixture.preferences.set("rift-blink", "landing", "NONE");
      fixture.move(new Vector(0D, 0D, 0.05D));
      slowDestination = fixture.reactiveDestination().toVector();
    }
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("rift-blink", "reactive-direction", "MOMENTUM");
      fixture.preferences.set("rift-blink", "landing", "NONE");
      fixture.move(new Vector(0D, 0D, 20D));
      Location destination = fixture.reactiveDestination();
      assertThat(destination.toVector()).isEqualTo(slowDestination);
      assertThat(destination.distance(fixture.player.getLocation())).isCloseTo(8D, offset(0.000001D));
    }
  }

  @Test
  void missingZeroAndNonFiniteMotionFallBackToLooking() {
    for (Vector motion : new Vector[]{new Vector(), new Vector(0D, 0D, 0.00001D),
        new Vector(Double.NaN, 0D, 0D), new Vector(0D, Double.POSITIVE_INFINITY, 0D)}) {
      try (Fixture fixture = new Fixture()) {
        fixture.preferences.set("rift-blink", "reactive-direction", "MOMENTUM");
        fixture.move(motion);
        fixture.assertReactiveLooksForward();
      }
    }
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("rift-blink", "reactive-direction", "MOMENTUM");
      fixture.assertReactiveLooksForward();
    }
  }

  @Test
  void staleOrFutureMotionFallsBackToLooking() {
    for (int currentTick : new int[]{104, 99}) {
      try (Fixture fixture = new Fixture()) {
        fixture.preferences.set("rift-blink", "reactive-direction", "MOMENTUM");
        when(fixture.player.getTicksLived()).thenReturn(100);
        fixture.move(new Vector(0D, 0D, 1D));
        when(fixture.player.getTicksLived()).thenReturn(currentTick);
        fixture.assertReactiveLooksForward();
      }
    }
  }

  @Test
  void teleportClearsMovementBeforeTheNextReactiveBlink() {
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("rift-blink", "reactive-direction", "MOMENTUM");
      fixture.move(new Vector(0D, 0D, 1D));
      fixture.blink.on(new PlayerTeleportEvent(fixture.player, fixture.player.getLocation(),
          fixture.player.getLocation().add(0D, 0D, 100D), PlayerTeleportEvent.TeleportCause.PLUGIN));
      fixture.assertReactiveLooksForward();
    }
  }

  @Test
  void reactiveServerDirectionLockOverridesPersonalMovement() {
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("rift-blink", "reactive-direction", "MOMENTUM");
      fixture.move(new Vector(0D, 0D, 1D));
      PreferencePolicy policy = PreferencePolicy.defaults(RiftBlink.REACTIVE_DIRECTION);
      policy.playerEditable = false;
      fixture.blink.getConfig().playerPreferences = Map.of("reactive-direction", policy);
      fixture.assertReactiveLooksForward();
      assertThat(fixture.preferences.get("rift-blink", "reactive-direction")).isEqualTo("MOMENTUM");
    }
  }

  @Test
  void reactiveModeOnlyDodgesActualMeleeAndProjectileDamage() {
    for (EntityDamageEvent.DamageCause cause : EntityDamageEvent.DamageCause.values()) {
      boolean attack = cause == EntityDamageEvent.DamageCause.ENTITY_ATTACK
          || cause == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK
          || cause == EntityDamageEvent.DamageCause.PROJECTILE;
      assertThat(RiftBlink.isReactiveAttack(cause, 4D)).isEqualTo(attack);
    }
    assertThat(RiftBlink.isReactiveAttack(EntityDamageEvent.DamageCause.PROJECTILE, 0D)).isFalse();
    assertThat(RiftBlink.isReactiveAttack(EntityDamageEvent.DamageCause.ENTITY_ATTACK, Double.NaN)).isFalse();
  }

  @Test
  void verticalityPrefersHeightWhileDistancePrefersReach() {
    World world = mock(World.class);
    Location origin = new Location(world, 0D, 64D, 0D);
    Location far = new Location(world, 15D, 64D, 0D);
    Location high = new Location(world, 6D, 68D, 0D);
    Location higherFar = new Location(world, 9D, 68D, 0D);
    assertThat(RiftBlink.betterCandidate(origin, far, high, RiftBlink.Targeting.DISTANCE)).isSameAs(far);
    assertThat(RiftBlink.betterCandidate(origin, far, high, RiftBlink.Targeting.VERTICALITY)).isSameAs(high);
    assertThat(RiftBlink.betterCandidate(origin, high, higherFar, RiftBlink.Targeting.VERTICALITY)).isSameAs(higherFar);
  }

  @Test
  void awayDirectionUsesTheAttackAndFallsBackToLookWhenUnavailable() {
    World world = mock(World.class);
    Location origin = new Location(world, 0D, 64D, 0D);
    Vector look = new Vector(0D, 0D, 1D);
    assertThat(RiftBlink.awayDirection(origin, look, new Location(world, 5D, 68D, 0D)))
        .isEqualTo(new Vector(-1D, 0D, 0D));
    assertThat(RiftBlink.awayDirection(origin, look, null)).isEqualTo(look).isNotSameAs(look);
    assertThat(RiftBlink.awayDirection(origin, look, origin)).isEqualTo(look);
  }

  @Test
  void failedReactiveTeleportReplaysTheOriginalDamageWithoutAnotherDodge() {
    try (Fixture fixture = new Fixture()) {
      CompletableFuture<Boolean> teleport = new CompletableFuture<>();
      fixture.teleports.when(() -> PaperCompat.teleportAsync(eq(fixture.player), any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)))
          .thenReturn(teleport);
      doAnswer(call -> {
        fixture.blink.on(fixture.attack());
        return null;
      }).when(fixture.player).damage(4D, fixture.source);
      EntityDamageByEntityEvent attack = fixture.attack();

      fixture.blink.on(attack);
      verify(attack).setCancelled(true);
      EntityDamageByEntityEvent second = fixture.attack();
      fixture.blink.on(second);
      verify(second, never()).setCancelled(true);
      teleport.complete(false);

      verify(fixture.player).damage(4D, fixture.source);
      fixture.teleports.verify(() -> PaperCompat.teleportAsync(eq(fixture.player), any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)));
    }
  }

  @Test
  void successfulReactiveTeleportSharesCooldownAcrossPreferenceChanges() {
    try (Fixture fixture = new Fixture()) {
      fixture.teleports.when(() -> PaperCompat.teleportAsync(eq(fixture.player), any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)))
          .thenReturn(CompletableFuture.completedFuture(true));
      EntityDamageByEntityEvent first = fixture.attack();
      fixture.blink.on(first);
      verify(first).setCancelled(true);

      fixture.preferences.set("rift-blink", "enabled", "OFF");
      fixture.preferences.set("rift-blink", "activation", "MANUAL");
      fixture.preferences.set("rift-blink", "activation", "REACTIVE");
      fixture.preferences.set("rift-blink", "enabled", "ON");
      EntityDamageByEntityEvent second = fixture.attack();
      fixture.blink.on(second);

      verify(second, never()).setCancelled(true);
      verify(fixture.player, never()).damage(anyDouble(), eq(fixture.source));
    }
  }

  @Test
  void disabledAndUnlearnedReactiveModesLeaveDamageUntouched() {
    try (Fixture fixture = new Fixture()) {
      fixture.preferences.set("rift-blink", "enabled", "OFF");
      EntityDamageByEntityEvent disabled = fixture.attack();
      fixture.blink.on(disabled);
      verify(disabled, never()).setCancelled(true);
      fixture.preferences.set("rift-blink", "enabled", "ON");
      doReturn(1).when(fixture.blink).getLevel(fixture.player);
      EntityDamageByEntityEvent locked = fixture.attack();
      fixture.blink.on(locked);
      verify(locked, never()).setCancelled(true);
      fixture.teleports.verifyNoInteractions();
    }
  }

  @Test
  void protectionDenialDoesNotCancelTheAttackOrStartTeleport() {
    try (Fixture fixture = new Fixture()) {
      doReturn(false).when(fixture.blink).canInteract(eq(fixture.player), any(Location.class));
      EntityDamageByEntityEvent denied = fixture.attack();
      fixture.blink.on(denied);
      verify(denied, never()).setCancelled(true);
      fixture.teleports.verifyNoInteractions();
    }
  }

  @Test
  void cancelledAdaptationTeleportDoesNotConsumeTheAttack() {
    try (Fixture fixture = new Fixture()) {
      PluginManager manager = Bukkit.getPluginManager();
      doAnswer(call -> {
        call.getArgument(0, AdaptAdaptationTeleportEvent.class).setCancelled(true);
        return null;
      }).when(manager).callEvent(any(AdaptAdaptationTeleportEvent.class));
      EntityDamageByEntityEvent denied = fixture.attack();
      fixture.blink.on(denied);
      verify(denied, never()).setCancelled(true);
      fixture.teleports.verifyNoInteractions();
    }
  }

  @Test
  void foliaUnownedDestinationsAreRejectedWithoutBlockReads() {
    try (Fixture fixture = new Fixture()) {
      fixture.scheduling.when(J::isFoliaThreading).thenReturn(true);
      EntityDamageByEntityEvent attack = fixture.attack();
      fixture.blink.on(attack);
      verify(attack, never()).setCancelled(true);
      verify(fixture.world, never()).getBlockAt(any(Location.class));
      verify(fixture.world, never()).getBlockAt(anyInt(), anyInt(), anyInt());
      fixture.teleports.verifyNoInteractions();
    }
  }

  @Test
  void quittingWithAPendingDodgeReplaysDamageOnlyOnce() {
    try (Fixture fixture = new Fixture()) {
      CompletableFuture<Boolean> teleport = new CompletableFuture<>();
      fixture.teleports.when(() -> PaperCompat.teleportAsync(eq(fixture.player), any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)))
          .thenReturn(teleport);
      fixture.blink.on(fixture.attack());
      PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
      when(quit.getPlayer()).thenReturn(fixture.player);
      fixture.blink.on(quit);
      teleport.complete(false);
      verify(fixture.player).damage(4D, fixture.source);
    }
  }

  @Test
  void clearingPlayerStateDoesNotDiscardAPendingFailedDodge() {
    try (Fixture fixture = new Fixture()) {
      CompletableFuture<Boolean> teleport = new CompletableFuture<>();
      fixture.teleports.when(() -> PaperCompat.teleportAsync(eq(fixture.player), any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)))
          .thenReturn(teleport);
      EntityDamageByEntityEvent attack = fixture.attack();
      fixture.blink.on(attack);
      verify(attack).setCancelled(true);

      PlayerStateRegistry.clearPlayer(fixture.player.getUniqueId());
      EntityDamageByEntityEvent whilePending = fixture.attack();
      fixture.blink.on(whilePending);
      verify(whilePending, never()).setCancelled(true);
      teleport.complete(false);

      verify(fixture.player).damage(4D, fixture.source);
    }
  }

  @Test
  void clearingPlayerStateDoesNotDiscardAPendingSuccessfulBlinkCostOrCooldown() {
    try (Fixture fixture = new Fixture()) {
      DamageSource pearlSource = mock(DamageSource.class);
      DamageSource.Builder builder = mock(DamageSource.Builder.class);
      UnsafeValues unsafe = mock(UnsafeValues.class);
      fixture.bukkit.when(Bukkit::getUnsafe).thenReturn(unsafe);
      when(unsafe.createDamageSourceBuilder(any(DamageType.class))).thenReturn(builder);
      when(builder.build()).thenReturn(pearlSource);
      fixture.blink.getConfig().pearlDamageBase = 5D;
      fixture.blink.getConfig().minimumPearlDamage = 1D;
      CompletableFuture<Boolean> teleport = new CompletableFuture<>();
      fixture.teleports.when(() -> PaperCompat.teleportAsync(eq(fixture.player), any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)))
          .thenReturn(teleport);
      fixture.blink.on(fixture.attack());

      PlayerStateRegistry.clearPlayer(fixture.player.getUniqueId());
      teleport.complete(true);
      EntityDamageByEntityEvent onCooldown = fixture.attack();
      fixture.blink.on(onCooldown);

      verify(fixture.player).damage(4D, pearlSource);
      verify(fixture.player, never()).damage(anyDouble(), eq(fixture.source));
      verify(onCooldown, never()).setCancelled(true);
    }
  }

  @Test
  void quittingAfterPlayerStateClearStillSettlesPendingDamageOnce() {
    try (Fixture fixture = new Fixture()) {
      CompletableFuture<Boolean> teleport = new CompletableFuture<>();
      fixture.teleports.when(() -> PaperCompat.teleportAsync(eq(fixture.player), any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)))
          .thenReturn(teleport);
      fixture.blink.on(fixture.attack());
      PlayerStateRegistry.clearPlayer(fixture.player.getUniqueId());
      PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
      when(quit.getPlayer()).thenReturn(fixture.player);

      fixture.blink.on(quit);
      teleport.complete(false);

      verify(fixture.player).damage(4D, fixture.source);
    }
  }

  @Test
  void oldCompletionCannotClearThePendingBlinkAfterRejoining() {
    try (Fixture fixture = new Fixture()) {
      CompletableFuture<Boolean> oldTeleport = new CompletableFuture<>();
      CompletableFuture<Boolean> newTeleport = new CompletableFuture<>();
      fixture.teleports.when(() -> PaperCompat.teleportAsync(any(Player.class), any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)))
          .thenReturn(oldTeleport, newTeleport);
      fixture.blink.on(fixture.attack());
      PlayerQuitEvent quit = mock(PlayerQuitEvent.class);
      when(quit.getPlayer()).thenReturn(fixture.player);
      fixture.blink.on(quit);

      PlayerStateRegistry.clearPlayer(fixture.player.getUniqueId());
      Player rejoined = mock(Player.class, delegatesTo(fixture.player));
      fixture.bind(rejoined);
      EntityDamageByEntityEvent newAttack = fixture.attack();
      when(newAttack.getEntity()).thenReturn(rejoined);
      fixture.blink.on(newAttack);
      verify(newAttack).setCancelled(true);

      oldTeleport.complete(true);
      EntityDamageByEntityEvent whilePending = fixture.attack();
      when(whilePending.getEntity()).thenReturn(rejoined);
      fixture.blink.on(whilePending);
      verify(whilePending, never()).setCancelled(true);

      newTeleport.complete(false);
      verify(rejoined).damage(4D, fixture.source);
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final World world = mock(World.class);
    private final Player player = mock(Player.class);
    private final DamageSource source = mock(DamageSource.class);
    private final PlayerPreferenceData preferences = new PlayerPreferenceData();
    private final TestBlink blink = spy(new TestBlink());
    private final MockedStatic<J> scheduling = mockStatic(J.class);
    private final MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
    private final MockedStatic<PaperCompat> teleports = mockStatic(PaperCompat.class);
    private final MockedStatic<RegistryUtil> registries = mockStatic(RegistryUtil.class);

    private Fixture() {
      registries.when(() -> RegistryUtil.find(eq(Particle.class), any(String[].class))).thenAnswer(call -> {
        String[] keys = (String[]) call.getRawArguments()[1];
        for (String key : keys) {
          for (Particle particle : Particle.values()) {
            if (particle.getKey().getKey().equals(key)) {
              return particle;
            }
          }
        }
        throw new IllegalArgumentException("Unknown particle");
      });
      RiftBlink.Config config = new RiftBlink.Config();
      config.baseDistance = 8D;
      config.distanceFactor = 0D;
      config.pearlDamageBase = 0D;
      config.minimumPearlDamage = 0D;
      config.cooldownMillis = 60000;
      blink.setConfig(config);
      Skill<?> skill = mock(Skill.class);
      when(skill.getName()).thenReturn("rift");
      blink.setSkill(skill);
      Location origin = new Location(world, 0.5D, 64D, 0.5D, -90F, 0F);
      when(player.getLocation()).thenAnswer(call -> origin.clone());
      when(player.getEyeLocation()).thenAnswer(call -> origin.clone().add(0D, 1.62D, 0D));
      when(player.getEyeHeight()).thenReturn(1.62D);
      when(player.getHeight()).thenReturn(1.8D);
      when(player.getUniqueId()).thenReturn(UUID.randomUUID());
      when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
      when(player.isOnline()).thenReturn(true);
      when(world.getMinHeight()).thenReturn(-64);
      when(world.getMaxHeight()).thenReturn(320);
      when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
      WorldBorder border = mock(WorldBorder.class);
      when(border.isInside(any(Location.class))).thenReturn(true);
      when(world.getWorldBorder()).thenReturn(border);
      when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(call -> block(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
      when(world.getBlockAt(any(Location.class))).thenAnswer(call -> {
        Location location = call.getArgument(0);
        return block(location.getBlockX(), location.getBlockY(), location.getBlockZ());
      });
      preferences.set("rift-blink", "activation", "REACTIVE");
      bind(player);
      bukkit.when(Bukkit::getPluginManager).thenReturn(mock(PluginManager.class));
      bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
    }

    private void bind(Player actor) {
      AdaptPlayer owner = mock(AdaptPlayer.class);
      PlayerData data = mock(PlayerData.class);
      when(owner.getData()).thenReturn(data);
      when(owner.isRuntimeReady()).thenReturn(true);
      when(owner.getPlayer()).thenReturn(actor);
      when(data.getPreferences()).thenReturn(preferences);
      doReturn(owner).when(blink).getPlayer(actor);
      doReturn(2).when(blink).getLevel(actor);
      doReturn(2).when(blink).getLevel(owner);
      doReturn(true).when(blink).hasActiveAdaptation(actor);
      doReturn(true).when(blink).canInteract(eq(actor), any(Location.class));
      doReturn(true).when(blink).checkRegion(eq(actor), any(Location.class));
      doReturn(0).when(blink).getActiveSiblingLevel(actor, "rift-resist");
      scheduling.when(() -> J.runEntity(eq(actor), any(Runnable.class))).thenAnswer(call -> {
        call.getArgument(1, Runnable.class).run();
        return true;
      });
    }

    private void move(Vector displacement) {
      Location from = player.getLocation();
      blink.on(new PlayerMoveEvent(player, from, from.clone().add(displacement)));
    }

    private Location reactiveDestination() {
      teleports.when(() -> PaperCompat.teleportAsync(eq(player), any(Location.class), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)))
          .thenReturn(new CompletableFuture<>());
      EntityDamageByEntityEvent attack = attack();
      blink.on(attack);
      verify(attack).setCancelled(true);
      ArgumentCaptor<Location> destination = ArgumentCaptor.forClass(Location.class);
      teleports.verify(() -> PaperCompat.teleportAsync(eq(player), destination.capture(), eq(PlayerTeleportEvent.TeleportCause.PLUGIN)));
      return destination.getValue();
    }

    private void assertReactiveLooksForward() {
      Location destination = reactiveDestination();
      assertThat(destination.getX()).isGreaterThan(player.getLocation().getX());
      assertThat(destination.getZ()).isCloseTo(player.getLocation().getZ(), offset(0.000001D));
    }

    private Block block(int x, int y, int z) {
      Block block = mock(Block.class);
      when(block.getLocation()).thenReturn(new Location(world, x, y, z));
      when(block.getY()).thenReturn(y);
      when(block.getType()).thenReturn(y < 64 ? Material.STONE : Material.AIR);
      when(block.isPassable()).thenReturn(y >= 64);
      when(block.getBoundingBox()).thenReturn(new BoundingBox(x, y, z, x + 1D, y + 1D, z + 1D));
      when(block.getRelative(any(BlockFace.class))).thenAnswer(call -> {
        BlockFace face = call.getArgument(0);
        return block(x + face.getModX(), y + face.getModY(), z + face.getModZ());
      });
      return block;
    }

    private EntityDamageByEntityEvent attack() {
      EntityDamageByEntityEvent event = mock(EntityDamageByEntityEvent.class);
      when(event.getEntity()).thenReturn(player);
      when(event.getCause()).thenReturn(EntityDamageEvent.DamageCause.PROJECTILE);
      when(event.getFinalDamage()).thenReturn(3D);
      when(event.getDamage()).thenReturn(6D);
      when(event.getOriginalDamage(EntityDamageEvent.DamageModifier.BASE)).thenReturn(4D);
      when(event.getDamageSource()).thenReturn(source);
      return event;
    }

    @Override
    public void close() {
      registries.close();
      teleports.close();
      bukkit.close();
      scheduling.close();
    }
  }

  private static class TestBlink extends RiftBlink {
    private final FxEmitter effects = mock(FxEmitter.class, RETURNS_SELF);

    @Override
    protected void addStat(Player player, String stat, double amount) {
    }

    @Override
    protected FxEmitter fx(Location location, FxPriority priority) {
      return effects;
    }

    @Override
    protected FxEmitter fx(Entity entity, FxPriority priority) {
      return effects;
    }
  }
}

package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.fx.Fx;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.projectile.ProjectileClaims;
import art.arcane.adapt.content.adaptation.rift.RiftPearlRebound;
import art.arcane.adapt.util.common.scheduling.J;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectileModifierCompatibilityTest extends AdaptTestBase {
  private static final NamespacedKey REBOUND_LEVEL = new NamespacedKey("adapt", "rift_pearl_rebound_level");
  private static final NamespacedKey REBOUNDED = new NamespacedKey("adapt", "rift_pearl_rebounded");
  private static final NamespacedKey TAGLOCK_TARGET = new NamespacedKey("adapt", "rift_taglock_target_uuid");

  private final Map<PersistentDataContainer, Map<NamespacedKey, Object>> containers = new IdentityHashMap<>();
  private final Map<NamespacedKey, Object> stored = new HashMap<>();
  private Player player;
  private World world;
  private EnderPearl pearl;
  private RiftPearlRebound rebound;
  private List<Consumer<ProjectileLaunchEvent>> modifiers;

  @BeforeAll
  static void initializeSoundRegistry() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registry = mockStatic(RegistryAccess.class)) {
      registry.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<Sound> sounds = Registry.SOUNDS;
      doAnswer(call -> {
        Key key = call.getArgument(0);
        Sound sound = mock(Sound.class);
        when(sound.getKey()).thenReturn(new NamespacedKey(key.namespace(), key.value()));
        return sound;
      }).when(sounds).getOrThrow(any(Key.class));
      assertThat(Sound.UI_BUTTON_CLICK).isNotNull();
      Registry<Particle> particles = Registry.PARTICLE_TYPE;
      doAnswer(call -> {
        NamespacedKey key = call.getArgument(0);
        for (Particle particle : Particle.values()) {
          if (particle.getKey().equals(key)) {
            return particle;
          }
        }
        return null;
      }).when(particles).get(any(NamespacedKey.class));
    }
  }

  @BeforeEach
  void prepareProjectile() {
    when(plugin.getName()).thenReturn("Adapt");
    when(plugin.namespace()).thenReturn("adapt");
    player = mock(Player.class);
    world = mock(World.class);
    pearl = mock(EnderPearl.class);
    PersistentDataContainer data = dataContainer(stored);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(pearl.getShooter()).thenReturn(player);
    when(pearl.getPersistentDataContainer()).thenReturn(data);
    when(pearl.getVelocity()).thenReturn(new Vector(1D, 0D, 0D));
    when(pearl.getLocation()).thenReturn(new Location(world, 0D, 64D, 0D));


    RangedFetchShot fetch = spy(new RangedFetchShot());
    doReturn(2).when(fetch).getActiveLevel(player);
    RangedFloaters floaters = spy(new RangedFloaters());
    doReturn(2).when(floaters).getActiveLevel(player);
    doReturn(false).when(floaters).preferenceEnabled(player, RangedPreferences.SNEAK);
    doReturn(RangedPreferences.Targets.ALL).when(floaters).preference(player, RangedPreferences.TARGETS);
    RangedPinningShot pinning = spy(new RangedPinningShot());
    doReturn(2).when(pinning).getActiveLevel(player);
    doReturn(false).when(pinning).preferenceEnabled(player, RangedPreferences.SNEAK);
    doReturn(true).when(pinning).preferenceEnabled(player, RangedPreferences.DAMPEN);
    modifiers = List.of(fetch::on, floaters::on, pinning::on);

    rebound = spy(new RiftPearlRebound());
    doReturn(2).when(rebound).getActiveLevel(player);
    doReturn(true).when(rebound).preferenceEnabled(player, RiftPearlRebound.REBOUND);
    doReturn(false).when(rebound).preferenceEnabled(player, RiftPearlRebound.SNEAK);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void cooperativeLaunchModifiersAllowReboundInEitherListenerOrder(boolean reboundFirst) {
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
      scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class))).thenReturn(true);
      ProjectileLaunchEvent launch = new ProjectileLaunchEvent(pearl);
      if (reboundFirst) {
        rebound.on(launch);
      }
      for (Consumer<ProjectileLaunchEvent> modifier : modifiers) {
        modifier.accept(launch);
      }
      if (!reboundFirst) {
        rebound.on(launch);
      }

      assertThat(stored).hasSize(8).containsEntry(REBOUND_LEVEL, 2);
      assertThat(ProjectileClaims.isUnclaimed(pearl, REBOUND_LEVEL, REBOUNDED)).isTrue();
      assertThat(ProjectileClaims.isUnclaimed(pearl)).isFalse();
      ProjectileHitEvent hit = hitBlock();
      rebound.on(hit);

      assertThat(hit.isCancelled()).isTrue();
      verify(pearl).remove();
      scheduler.verify(() -> J.runEntity(eq(player), any(Runnable.class)));
    }
  }

  @Test
  void replacementPearlRetainsDetachedModifierDataAndCannotReboundAgain() {
    launchModifiers();
    Map<NamespacedKey, Object> expectedModifiers = new HashMap<>(stored);
    rebound.on(new ProjectileLaunchEvent(pearl));
    when(player.isOnline()).thenReturn(true);
    Location launchLocation = pearl.getLocation();
    when(player.getEyeLocation()).thenReturn(launchLocation);
    doReturn(true).when(rebound).isRuntimeRegistered();
    EnderPearl replacement = mock(EnderPearl.class);
    Map<NamespacedKey, Object> replacementData = new HashMap<>();
    PersistentDataContainer replacementContainer = dataContainer(replacementData);
    when(replacement.getPersistentDataContainer()).thenReturn(replacementContainer);
    when(replacement.getShooter()).thenReturn(player);
    when(pearl.getWorld()).thenReturn(world);
    when(world.spawn(any(Location.class), eq(EnderPearl.class))).thenReturn(replacement);
    List<Runnable> playerTasks = new ArrayList<>();
    FxEmitter emitter = mock(FxEmitter.class, RETURNS_SELF);
    try (MockedStatic<J> scheduler = mockStatic(J.class);
         MockedStatic<Fx> effects = mockStatic(Fx.class)) {
      scheduler.when(() -> J.runEntity(eq(player), any(Runnable.class))).thenAnswer(call -> {
        playerTasks.add(call.getArgument(1));
        return true;
      });
      scheduler.when(() -> J.runAt(any(Location.class), any(Runnable.class))).thenAnswer(call -> {
        Runnable task = call.getArgument(1);
        task.run();
        return true;
      });
      effects.when(() -> Fx.now(eq(rebound), any(Location.class), eq(FxPriority.TRAIL))).thenReturn(emitter);
      ProjectileHitEvent firstHit = hitBlock();

      rebound.on(firstHit);
      stored.clear();
      assertThat(playerTasks).hasSize(1);
      playerTasks.removeFirst().run();

      assertThat(firstHit.isCancelled()).isTrue();
      assertThat(replacementData).hasSize(8).containsAllEntriesOf(expectedModifiers)
          .containsEntry(REBOUNDED, (byte) 1).doesNotContainKey(REBOUND_LEVEL);
      verify(replacement).setShooter(player);
      verify(replacement).setVelocity(any(Vector.class));
      assertThat(playerTasks).hasSize(1);
      ProjectileHitEvent secondHit = new ProjectileHitEvent(replacement, null, mock(Block.class), BlockFace.EAST);
      rebound.on(secondHit);
      assertThat(secondHit.isCancelled()).isFalse();
      verify(replacement, never()).remove();
    }
  }

  @Test
  void detachedModifierSnapshotExcludesOwnershipAndUnknownPayloads() {
    launchModifiers();
    Map<NamespacedKey, Object> expectedModifiers = new HashMap<>(stored);
    stored.put(REBOUND_LEVEL, 2);
    stored.put(TAGLOCK_TARGET, UUID.randomUUID().toString());

    PersistentDataContainer snapshot = ProjectileClaims.snapshotCooperativeData(pearl.getPersistentDataContainer());

    assertThat(containers.get(snapshot)).isEqualTo(expectedModifiers);
    assertThat(stored).hasSize(9).containsKey(TAGLOCK_TARGET).containsKey(REBOUND_LEVEL);
    stored.clear();
    assertThat(containers.get(snapshot)).isEqualTo(expectedModifiers);
  }

  @Test
  void eachModifierKeyIsCooperativeAndDoesNotHideAnExclusivePayload() {
    launchModifiers();
    assertThat(stored).hasSize(7);
    Set<NamespacedKey> modifierKeys = new HashSet<>(stored.keySet());
    for (NamespacedKey modifier : modifierKeys) {
      stored.clear();
      stored.put(modifier, "metadata");
      assertThat(ProjectileClaims.isUnclaimed(pearl)).as(modifier.toString()).isTrue();
      stored.put(TAGLOCK_TARGET, UUID.randomUUID().toString());
      assertThat(ProjectileClaims.isUnclaimed(pearl)).as(modifier.toString()).isFalse();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "adapt:rift_taglock_target_uuid",
      "adapt:rift_conduit_taglock_loc",
      "adapt:rift_conduit_taglock_id",
      "adapt:unregistered_payload",
      "foreign:ranged_floaters_level",
      "foreign:fetch-launch-sneak"
  })
  void cooperativeModifiersDoNotAllowForeignPayloadsAtLaunch(String payloadKey) {
    launchModifiers();
    stored.put(NamespacedKey.fromString(payloadKey), "payload");

    rebound.on(new ProjectileLaunchEvent(pearl));

    assertThat(stored).doesNotContainKey(REBOUND_LEVEL);
    assertThat(ProjectileClaims.isUnclaimed(pearl, REBOUND_LEVEL, REBOUNDED)).isFalse();
  }

  @Test
  void payloadAddedAfterLaunchStillPreventsRebound() {
    rebound.on(new ProjectileLaunchEvent(pearl));
    launchModifiers();
    stored.put(TAGLOCK_TARGET, UUID.randomUUID().toString());
    ProjectileHitEvent hit = hitBlock();

    rebound.on(hit);

    assertThat(hit.isCancelled()).isFalse();
    verify(pearl, never()).remove();
  }

  @Test
  void cooperativeMetadataDoesNotAllowASecondRebound() {
    rebound.on(new ProjectileLaunchEvent(pearl));
    launchModifiers();
    stored.put(REBOUNDED, (byte) 1);
    ProjectileHitEvent hit = hitBlock();

    rebound.on(hit);

    assertThat(hit.isCancelled()).isFalse();
    verify(pearl, never()).remove();
  }

  @Test
  void recreatingModifiersKeepsExistingProjectileMetadataCooperative() {
    launchModifiers();

    new RangedFetchShot();
    new RangedFloaters();
    new RangedPinningShot();

    assertThat(stored).hasSize(7);
    assertThat(ProjectileClaims.isUnclaimed(pearl)).isTrue();
  }

  private PersistentDataContainer dataContainer(Map<NamespacedKey, Object> values) {
    PersistentDataContainer data = mock(PersistentDataContainer.class);
    containers.put(data, values);
    PersistentDataAdapterContext adapter = mock(PersistentDataAdapterContext.class);
    when(data.getAdapterContext()).thenReturn(adapter);
    when(adapter.newPersistentDataContainer()).thenAnswer(call -> dataContainer(new HashMap<>()));
    when(data.getKeys()).thenAnswer(call -> new HashSet<>(values.keySet()));
    when(data.has(any(NamespacedKey.class))).thenAnswer(call -> values.containsKey(call.getArgument(0)));
    when(data.get(any(NamespacedKey.class), eq(PersistentDataType.INTEGER)))
        .thenAnswer(call -> values.get(call.getArgument(0)));
    doAnswer(call -> {
      values.put(call.getArgument(0), call.getArgument(2));
      return null;
    }).when(data).set(any(NamespacedKey.class), any(), any());
    doAnswer(call -> {
      values.remove(call.getArgument(0));
      return null;
    }).when(data).remove(any(NamespacedKey.class));
    doAnswer(call -> {
      containers.get(call.<PersistentDataContainer>getArgument(0)).putAll(values);
      return null;
    }).when(data).copyTo(any(PersistentDataContainer.class), eq(true));
    return data;
  }

  private void launchModifiers() {
    try (MockedStatic<J> scheduler = mockStatic(J.class)) {
      scheduler.when(() -> J.isOwnedByCurrentRegion(player)).thenReturn(true);
      ProjectileLaunchEvent launch = new ProjectileLaunchEvent(pearl);
      for (Consumer<ProjectileLaunchEvent> modifier : modifiers) {
        modifier.accept(launch);
      }
    }
  }

  private ProjectileHitEvent hitBlock() {
    return new ProjectileHitEvent(pearl, null, mock(Block.class), BlockFace.WEST);
  }
}

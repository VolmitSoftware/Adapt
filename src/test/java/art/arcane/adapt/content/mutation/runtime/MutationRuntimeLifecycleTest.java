package art.arcane.adapt.content.mutation.runtime;

import art.arcane.adapt.api.mutation.MutationLimits;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MutationRuntimeLifecycleTest {
  @Test
  void removedPlayersReceiveAUniqueLoadoutGeneration() {
    MutationRuntimeStore store = new MutationRuntimeStore();
    UUID playerId = UUID.randomUUID();

    MutationRuntimeStore.PlayerRuntimeState first = store.player(playerId);
    store.remove(playerId);
    MutationRuntimeStore.PlayerRuntimeState second = store.player(playerId);

    assertThat(second).isNotSameAs(first);
    assertThat(second.loadoutGeneration).isGreaterThan(first.loadoutGeneration);
  }

  @Test
  void existingLookupDoesNotRecreatePlayerState() {
    MutationRuntimeStore store = new MutationRuntimeStore();
    UUID playerId = UUID.randomUUID();

    assertThat(store.existing(playerId)).isNull();
    assertThat(store.players).doesNotContainKey(playerId);
  }

  @Test
  void latticePlacementAccountingCannotUnderflow() {
    MutationRuntimeStore.LatticeStructure structure = new MutationRuntimeStore.LatticeStructure(7L, 1_000L, 2);

    assertThat(structure.completePlacement(0, true)).isTrue();
    assertThat(structure.completePlacement(1, false)).isTrue();
    assertThat(structure.completePlacement(1, false)).isFalse();

    assertThat(structure.pendingPlacements).isZero();
    assertThat(structure.placedBlocks).isEqualTo(1);
    assertThat(structure.isPlacementComplete()).isTrue();
  }

  @Test
  void latticeExpiryReleasesEachPendingReservationExactlyOnce() {
    MutationRuntimeStore.LatticeStructure structure = new MutationRuntimeStore.LatticeStructure(7L, 1_000L, 3);
    assertThat(structure.completePlacement(1, false)).isTrue();

    assertThat(structure.expireReservations()).isEqualTo(2);
    assertThat(structure.expireReservations()).isZero();
    assertThat(structure.completePlacement(0, true)).isFalse();
    assertThat(structure.pendingPlacements).isZero();
    assertThat(structure.placedBlocks).isZero();
  }

  @Test
  void latticeCleanupClearsPendingResourceRefunds() {
    MutationRuntimeStore.LatticeState lattice = new MutationRuntimeStore.LatticeState();
    lattice.pendingRootRefunds = 2;
    lattice.reservedBlocks = 3;

    lattice.clearTransient();

    assertThat(lattice.pendingRootRefunds).isZero();
    assertThat(lattice.reservedBlocks).isZero();
  }

  @Test
  void resourceSaveRequestsCoalesceAndImmediateFlushInvalidatesScheduledWork() {
    MutationRuntimeStore.PlayerRuntimeState runtime = new MutationRuntimeStore.PlayerRuntimeState(1L);

    long scheduledGeneration = runtime.requestResourceSave();
    assertThat(scheduledGeneration).isPositive();
    assertThat(runtime.requestResourceSave()).isZero();
    assertThat(runtime.claimImmediateResourceSave()).isTrue();
    assertThat(runtime.claimResourceSave(scheduledGeneration)).isFalse();

    long nextGeneration = runtime.requestResourceSave();
    assertThat(nextGeneration).isGreaterThan(scheduledGeneration);
    assertThat(runtime.claimResourceSave(nextGeneration)).isTrue();
    assertThat(runtime.claimImmediateResourceSave()).isFalse();
  }

  @Test
  void deathMutationGrantsCarryImmutableOwnerAndSourceGenerations() {
    UUID ownerId = UUID.randomUUID();
    UUID sourceEntityId = UUID.randomUUID();
    MutationRuntimeStore.DeathMutationGrant grant = new MutationRuntimeStore.DeathMutationGrant(
        ownerId,
        sourceEntityId,
        11L,
        true,
        true,
        5_000L
    );

    assertThat(grant.active(5_000L)).isTrue();
    assertThat(grant.active(5_001L)).isFalse();
    assertThat(grant.ownerId()).isEqualTo(ownerId);
    assertThat(grant.sourceEntityId()).isEqualTo(sourceEntityId);
    assertThat(grant.loadoutGeneration()).isEqualTo(11L);
  }

  @Test
  void worldExpiryDeadlinesUseBoundedRescheduling() {
    long now = 1_000L;

    assertThat(MutationWorldRuntime.boundedDelayTicks(now - 1L, now)).isEqualTo(1);
    assertThat(MutationWorldRuntime.boundedDelayTicks(now + 1_000L, now)).isEqualTo(20);
    assertThat(MutationWorldRuntime.boundedDelayTicks(Long.MAX_VALUE, now))
        .isEqualTo(MutationLimits.MAX_DELAY_TICKS);
  }

  @Test
  void creativeAndSpectatorModesAreRejected() {
    assertThat(MutationWorldRuntime.eligibleGameMode(GameMode.SURVIVAL)).isTrue();
    assertThat(MutationWorldRuntime.eligibleGameMode(GameMode.ADVENTURE)).isTrue();
    assertThat(MutationWorldRuntime.eligibleGameMode(GameMode.CREATIVE)).isFalse();
    assertThat(MutationWorldRuntime.eligibleGameMode(GameMode.SPECTATOR)).isFalse();
    assertThat(MutationWorldRuntime.eligibleGameMode(null)).isFalse();
  }

  @Test
  void globallyDisabledRuntimeRejectsOtherwiseEligibleGameplay() {
    assertThat(MutationRuntimeRouter.gameplayEligible(false, true, GameMode.SURVIVAL)).isFalse();
    assertThat(MutationRuntimeRouter.gameplayEligible(true, true, GameMode.SURVIVAL)).isTrue();
    assertThat(MutationRuntimeRouter.gameplayEligible(true, false, GameMode.SURVIVAL)).isFalse();
    assertThat(MutationRuntimeRouter.gameplayEligible(true, true, GameMode.CREATIVE)).isFalse();
    assertThat(MutationRuntimeRouter.recoveryEligible(true, GameMode.SURVIVAL)).isTrue();
    assertThat(MutationRuntimeRouter.recoveryEligible(true, GameMode.CREATIVE)).isFalse();
  }

  @Test
  void blockBreakMutationRewardsRunOnlyAtMonitor() {
    Method successful = Arrays.stream(MutationRuntimeRouter.class.getDeclaredMethods())
        .filter(method -> method.getName().equals("onSuccessful"))
        .filter(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[]{BlockBreakEvent.class}))
        .findFirst()
        .orElseThrow();
    EventHandler handler = successful.getAnnotation(EventHandler.class);

    assertThat(handler).isNotNull();
    assertThat(handler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(handler.ignoreCancelled()).isTrue();
  }

  @Test
  void bastionMovementControlRunsBeforeMonitor() {
    Method control = Arrays.stream(MutationRuntimeRouter.class.getDeclaredMethods())
        .filter(method -> method.getName().equals("onMovementControl"))
        .filter(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[]{PlayerMoveEvent.class}))
        .findFirst()
        .orElseThrow();
    EventHandler handler = control.getAnnotation(EventHandler.class);

    assertThat(handler).isNotNull();
    assertThat(handler.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(handler.ignoreCancelled()).isTrue();
  }

  @Test
  void blockedHeldItemsCancelDamageBeforeAdaptationHandlers() {
    Method preflight = Arrays.stream(MutationRuntimeRouter.class.getDeclaredMethods())
        .filter(method -> method.getName().equals("onHeldItemDamagePreflight"))
        .filter(method -> Arrays.equals(method.getParameterTypes(), new Class<?>[]{EntityDamageByEntityEvent.class}))
        .findFirst()
        .orElseThrow();
    EventHandler handler = preflight.getAnnotation(EventHandler.class);

    assertThat(handler).isNotNull();
    assertThat(handler.priority()).isEqualTo(EventPriority.LOWEST);
    assertThat(handler.ignoreCancelled()).isTrue();
  }
}

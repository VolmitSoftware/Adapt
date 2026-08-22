package art.arcane.adapt.content.mutation.runtime;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MutationRuntimeStoreTest {
  @Test
  void utilityEchoClaimsAreLimitedToOneWindow() {
    MutationRuntimeStore.PlayerRuntimeState runtime = new MutationRuntimeStore.PlayerRuntimeState(1L);

    assertThat(runtime.tryClaimUtilityEcho(1_000L)).isTrue();
    assertThat(runtime.tryClaimUtilityEcho(1_049L)).isFalse();
    assertThat(runtime.tryClaimUtilityEcho(1_050L)).isTrue();
  }

  @Test
  void transientCleanupReleasesUtilityEchoArbitration() {
    MutationRuntimeStore.PlayerRuntimeState runtime = new MutationRuntimeStore.PlayerRuntimeState(1L);
    assertThat(runtime.tryClaimUtilityEcho(1_000L)).isTrue();

    runtime.clearTransient();

    assertThat(runtime.tryClaimUtilityEcho(1_001L)).isTrue();
    assertThat(runtime.trophyClearConfirmUntil).isZero();
  }

  @Test
  void galeCleanupInvalidatesPendingTasksAndProjectileReservations() {
    MutationRuntimeStore.GaleState gale = new MutationRuntimeStore.GaleState();
    gale.momentum = 100D;
    gale.reservedProjectile = UUID.randomUUID();
    gale.reservedProjectileExpiresAt = 2_000L;
    gale.ventScheduled = true;
    long generation = gale.ventGeneration;

    gale.clear();

    assertThat(gale.momentum).isZero();
    assertThat(gale.reservedProjectile).isNull();
    assertThat(gale.reservedProjectileExpiresAt).isZero();
    assertThat(gale.ventScheduled).isFalse();
    assertThat(gale.ventGeneration).isGreaterThan(generation);
  }

  @Test
  void packCleanupInvalidatesExpiryAndControlCallbacks() {
    MutationRuntimeStore.PackState pack = new MutationRuntimeStore.PackState();
    pack.quarryId = UUID.randomUUID();
    pack.members.put(UUID.randomUUID(), 1_000L);
    pack.tempo = 5;
    long generation = pack.generation;

    pack.clear();

    assertThat(pack.quarryId).isNull();
    assertThat(pack.members).isEmpty();
    assertThat(pack.tempo).isZero();
    assertThat(pack.generation).isGreaterThan(generation);
  }

  @Test
  void umbralCleanupRemovesTrackedExposureViewers() {
    MutationRuntimeStore.UmbralState umbral = new MutationRuntimeStore.UmbralState();
    umbral.exposedViewers.put(UUID.randomUUID(), 4L);

    umbral.clear();

    assertThat(umbral.exposedViewers).isEmpty();
  }

  @Test
  void bastionCleanupInvalidatesAnchorSessions() {
    MutationRuntimeStore.BastionState bastion = new MutationRuntimeStore.BastionState();
    bastion.anchored = true;
    bastion.anchorScheduled = true;
    long generation = bastion.anchorGeneration;

    bastion.clear();

    assertThat(bastion.anchored).isFalse();
    assertThat(bastion.anchorScheduled).isFalse();
    assertThat(bastion.anchorGeneration).isGreaterThan(generation);
  }

  @Test
  void trophyReservationsAreExclusiveAndGenerationGuarded() {
    MutationRuntimeStore.TrophyState trophy = new MutationRuntimeStore.TrophyState();

    long first = trophy.reserve("undead", 2_000L);

    assertThat(first).isPositive();
    assertThat(trophy.reserve("beast", 3_000L)).isZero();
    assertThat(trophy.matches(first, "undead", 2_000L)).isTrue();
    assertThat(trophy.committed(first, "undead", 2_000L)).isFalse();
    assertThat(trophy.commit(first, "undead", 2_000L)).isTrue();
    assertThat(trophy.commit(first, "undead", 2_000L)).isFalse();
    assertThat(trophy.committed(first, "undead", 2_000L)).isTrue();

    trophy.release(first);
    long second = trophy.reserve("beast", 3_000L);
    trophy.release(first);

    assertThat(second).isGreaterThan(first);
    assertThat(trophy.matches(second, "beast", 3_000L)).isTrue();
  }

  @Test
  void trophyCleanupInvalidatesReservationsAndRecognitionCadence() {
    MutationRuntimeStore.TrophyState trophy = new MutationRuntimeStore.TrophyState();
    long generation = trophy.reserve("slime", 5_000L);
    assertThat(trophy.commit(generation, "slime", 5_000L)).isTrue();
    trophy.nextRecognitionAt = 4_000L;

    trophy.clear();

    assertThat(trophy.matches(generation, "slime", 5_000L)).isFalse();
    assertThat(trophy.committed(generation, "slime", 5_000L)).isFalse();
    assertThat(trophy.nextRecognitionAt).isZero();
    assertThat(trophy.reservedFamily).isNull();
  }

  @Test
  void removedPlayersCannotReuseCommittedTrophyCapabilities() {
    MutationRuntimeStore store = new MutationRuntimeStore();
    UUID playerId = UUID.randomUUID();
    MutationRuntimeStore.PlayerRuntimeState original = store.player(playerId);
    long generation = original.trophy.reserve("construct", 8_000L);
    assertThat(original.trophy.commit(generation, "construct", 8_000L)).isTrue();

    store.remove(playerId);
    MutationRuntimeStore.PlayerRuntimeState replacement = store.player(playerId);

    assertThat(replacement).isNotSameAs(original);
    assertThat(replacement.trophy.committed(generation, "construct", 8_000L)).isFalse();
    assertThat(replacement.loadoutGeneration).isGreaterThan(original.loadoutGeneration);
  }
}

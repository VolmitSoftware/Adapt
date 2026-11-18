package art.arcane.adapt.api.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdaptServerPersistenceCoordinationTest {
  @Test
  void completedPreloginClaimIsRemovedBeforeTheNextPreloginStarts() {
    UUID playerId = UUID.randomUUID();
    ConcurrentMap<UUID, CompletableFuture<LoadedPlayerData>> activeClaims =
        new ConcurrentHashMap<>();
    AtomicBoolean acceptingClaims = new AtomicBoolean(true);
    AtomicInteger loadCount = new AtomicInteger();
    Function<UUID, LoadedPlayerData> loader = ignored -> {
      int epoch = loadCount.incrementAndGet();
      return LoadedPlayerData.owned(new PlayerData(), UUID.randomUUID(), epoch, 1L);
    };

    CompletableFuture<LoadedPlayerData> first = AdaptServer.beginPlayerDataClaim(
        playerId, activeClaims, Runnable::run, acceptingClaims, loader);
    CompletableFuture<LoadedPlayerData> second = AdaptServer.beginPlayerDataClaim(
        playerId, activeClaims, Runnable::run, acceptingClaims, loader);

    assertThat(first).isNotSameAs(second);
    assertThat(first.join().epoch()).isEqualTo(1L);
    assertThat(second.join().epoch()).isEqualTo(2L);
    assertThat(loadCount).hasValue(2);
    assertThat(activeClaims).isEmpty();
  }

  @Test
  void simultaneousPreloginClaimsAreCoalescedUntilTheSharedLoadCompletes() {
    UUID playerId = UUID.randomUUID();
    ConcurrentMap<UUID, CompletableFuture<LoadedPlayerData>> activeClaims =
        new ConcurrentHashMap<>();
    AtomicBoolean acceptingClaims = new AtomicBoolean(true);
    AtomicInteger loadCount = new AtomicInteger();
    AtomicBoolean absentAtCompletion = new AtomicBoolean();
    Deque<Runnable> scheduled = new ArrayDeque<>();
    Function<UUID, LoadedPlayerData> loader = ignored -> {
      loadCount.incrementAndGet();
      return LoadedPlayerData.owned(new PlayerData(), UUID.randomUUID(), 1L, 1L);
    };

    CompletableFuture<LoadedPlayerData> first = AdaptServer.beginPlayerDataClaim(
        playerId, activeClaims, scheduled::addLast, acceptingClaims, loader);
    CompletableFuture<LoadedPlayerData> second = AdaptServer.beginPlayerDataClaim(
        playerId, activeClaims, scheduled::addLast, acceptingClaims, loader);
    first.whenComplete((loaded, failure) ->
        absentAtCompletion.set(!activeClaims.containsKey(playerId)));

    assertThat(second).isSameAs(first);
    assertThat(scheduled).hasSize(1);
    assertThat(loadCount).hasValue(0);

    scheduled.removeFirst().run();

    assertThat(first.join().isOwned()).isTrue();
    assertThat(loadCount).hasValue(1);
    assertThat(absentAtCompletion).isTrue();
    assertThat(activeClaims).isEmpty();
  }

  @Test
  void shutdownRejectsAndRemovesEveryOutstandingClaim() {
    UUID firstPlayer = UUID.randomUUID();
    UUID secondPlayer = UUID.randomUUID();
    ConcurrentMap<UUID, CompletableFuture<LoadedPlayerData>> activeClaims =
        new ConcurrentHashMap<>();
    CompletableFuture<LoadedPlayerData> first = new CompletableFuture<>();
    CompletableFuture<LoadedPlayerData> second = new CompletableFuture<>();
    activeClaims.put(firstPlayer, first);
    activeClaims.put(secondPlayer, second);

    AdaptServer.rejectPlayerDataClaims(
        activeClaims,
        new IllegalStateException("Adapt is shutting down")
    );

    assertThat(activeClaims).isEmpty();
    assertThat(first).isCompletedExceptionally();
    assertThat(second).isCompletedExceptionally();
    assertThatThrownBy(first::join).hasCauseInstanceOf(IllegalStateException.class);
    assertThatThrownBy(second::join).hasCauseInstanceOf(IllegalStateException.class);
  }

  @Test
  void prefetchedSelectionKeepsTheNewestFenceAndSequence() {
    UUID owner = UUID.randomUUID();
    LoadedPlayerData olderEpoch = LoadedPlayerData.owned(
        new PlayerData(), UUID.randomUUID(), 4L, 20L);
    LoadedPlayerData newerEpoch = LoadedPlayerData.owned(
        new PlayerData(), owner, 5L, 1L);
    LoadedPlayerData newerSequence = LoadedPlayerData.owned(
        new PlayerData(), owner, 5L, 2L);

    assertThat(AdaptServer.selectPrefetchedPlayerData(olderEpoch, newerEpoch))
        .isSameAs(newerEpoch);
    assertThat(AdaptServer.selectPrefetchedPlayerData(newerEpoch, olderEpoch))
        .isSameAs(newerEpoch);
    assertThat(AdaptServer.selectPrefetchedPlayerData(newerEpoch, newerSequence))
        .isSameAs(newerSequence);
    assertThat(AdaptServer.selectPrefetchedPlayerData(newerSequence, newerEpoch))
        .isSameAs(newerSequence);
    assertThatThrownBy(() -> AdaptServer.selectPrefetchedPlayerData(
        newerEpoch,
        LoadedPlayerData.owned(new PlayerData(), UUID.randomUUID(), 5L, 1L)
    )).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void resetWatermarkRejectsOlderCompletionAndYieldsToTheNextFreshClaim() {
    UUID playerId = UUID.randomUUID();
    ConcurrentMap<UUID, LoadedPlayerData> prefetchedData = new ConcurrentHashMap<>();
    ConcurrentMap<UUID, Long> resetEpochs = new ConcurrentHashMap<>();
    LoadedPlayerData staleClaim = LoadedPlayerData.owned(
        new PlayerData(), UUID.randomUUID(), 8L, 1L);
    LoadedPlayerData freshClaim = LoadedPlayerData.owned(
        new PlayerData(), UUID.randomUUID(), 10L, 1L);
    prefetchedData.put(playerId, staleClaim);

    AdaptServer.cacheResetFenceEpoch(prefetchedData, resetEpochs, playerId, 9L);
    AdaptServer.cacheResetFenceEpoch(prefetchedData, resetEpochs, playerId, 7L);

    assertThat(prefetchedData).doesNotContainKey(playerId);
    assertThat(resetEpochs.get(playerId)).isEqualTo(9L);
    assertThat(AdaptServer.selectClaimAfterResetEpoch(null, staleClaim, 9L, true)).isNull();
    assertThat(AdaptServer.selectClaimAfterResetEpoch(null, freshClaim, 9L, true))
        .isSameAs(freshClaim);
  }

  @Test
  void localClaimIsAcceptedWithoutAnSqlFence() {
    LoadedPlayerData local = LoadedPlayerData.inspected(new PlayerData());

    assertThat(AdaptServer.selectClaimAfterResetEpoch(null, local, null, false))
        .isSameAs(local);
  }

  @Test
  void persistenceModeRejectsCrossModeClaimsAndWatermarks() {
    LoadedPlayerData local = LoadedPlayerData.inspected(new PlayerData());
    LoadedPlayerData sql = LoadedPlayerData.owned(
        new PlayerData(), UUID.randomUUID(), 1L, 0L);

    assertThatThrownBy(() -> AdaptServer.selectClaimAfterResetEpoch(null, local, null, true))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must own");
    assertThatThrownBy(() -> AdaptServer.selectClaimAfterResetEpoch(null, sql, null, false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not own");
    assertThatThrownBy(() -> AdaptServer.selectClaimAfterResetEpoch(null, local, 1L, false))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SQL reset fence");
    assertThat(AdaptServer.selectClaimAfterResetEpoch(local, sql, null, true))
        .isSameAs(sql);
    assertThat(AdaptServer.selectClaimAfterResetEpoch(sql, local, null, false))
        .isSameAs(local);
  }

  @Test
  void recoveryBackoffIsBoundedAndDeterministicallyJittered() {
    UUID playerId = UUID.randomUUID();

    int first = AdaptServer.onlineProfileRecoveryDelayTicks(playerId, 1);
    int second = AdaptServer.onlineProfileRecoveryDelayTicks(playerId, 2);
    int capped = AdaptServer.onlineProfileRecoveryDelayTicks(playerId, 99);

    assertThat(first).isBetween(40, 59);
    assertThat(second).isBetween(80, 99);
    assertThat(capped).isBetween(160, 179);
  }

  @Test
  void profileReadyMessageIdentifiesTheActiveStorageMode() {
    UUID playerId = UUID.fromString("85ff4989-a64f-4db6-93f1-00c85bfc4e75");

    assertThat(AdaptServer.profileReadyMessage("Magic_Psycho", playerId, false))
        .isEqualTo("Player profile ready for Magic_Psycho (" + playerId
            + ") using local JSON storage.");
    assertThat(AdaptServer.profileReadyMessage("Magic_Psycho", playerId, true))
        .isEqualTo("Player profile ready for Magic_Psycho (" + playerId
            + ") using SQL storage.");
  }
}

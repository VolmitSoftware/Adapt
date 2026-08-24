package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.advancement.AdvancementManager;
import art.arcane.adapt.util.common.io.SQLManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerDataPersistenceQueueTest extends AdaptTestBase {
  @Test
  void snapshotReplacementLeavesNoPartialWriteFile() throws Exception {
    File target = new File(dataFolder, "atomic/player.json");
    PlayerDataPersistenceQueue.writeSnapshot(target, "first");
    PlayerDataPersistenceQueue.writeSnapshot(target, "second");

    assertThat(Files.readString(target.toPath())).isEqualTo("second");
    try (Stream<Path> files = Files.list(target.getParentFile().toPath())) {
      assertThat(files.map(path -> path.getFileName().toString()).toList()).containsExactly("player.json");
    }
  }

  @Test
  void coalescesQueuedSavesAndPersistsOnlyLatestSnapshot() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "player.json");

    for (int index = 0; index < 100; index++) {
      queue.queueSave(playerId, "snapshot-" + index, target);
    }

    assertThat(queue.getPendingSave(playerId)).isEqualTo("snapshot-99");
    assertThat(executor.pendingTaskCount()).isEqualTo(1);
    executor.runNext();

    assertThat(queue.getPendingSave(playerId)).isNull();
    assertThat(Files.readString(target.toPath()).trim()).isEqualTo("snapshot-99");
  }

  @Test
  void saveKeepsBackendCapturedAtEnqueueTime() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "captured-local.json");
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    when(sqlSettings.isEnabled()).thenReturn(false);
    when(plugin.getSqlManager()).thenReturn(sqlManager);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      queue.queueSave(playerId, "local", target);
      when(sqlSettings.isEnabled()).thenReturn(true);
      executor.runNext();
    }

    assertThat(Files.readString(target.toPath()).trim()).isEqualTo("local");
    verify(sqlManager, never()).updateFencedDataBatch(any());
  }

  @Test
  void failedSaveRetriesLatestSnapshotWithNonBlockingBackoff() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    List<Long> retryDelays = new ArrayList<>();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor, (task, delayMillis) -> {
      retryDelays.add(delayMillis);
      executor.execute(task);
    });
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "retry-target");
    assertThat(target.mkdir()).isTrue();

    queue.queueSave(playerId, "first", target);
    executor.runNext();
    queue.queueSave(playerId, "second", target);
    Files.delete(target.toPath());
    executor.runNext();

    assertThat(retryDelays).hasSize(1);
    assertThat(retryDelays.getFirst()).isPositive();
    assertThat(executor.pendingTaskCount()).isZero();
    assertThat(queue.getPendingSave(playerId)).isNull();
    assertThat(Files.readString(target.toPath()).trim()).isEqualTo("second");
  }

  @Test
  void fencedSqlFailureIsRetriedThroughCapturedManager() {
    CapturingExecutor executor = new CapturingExecutor();
    List<Long> retryDelays = new ArrayList<>();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor, (task, delayMillis) -> {
      retryDelays.add(delayMillis);
      executor.execute(task);
    });
    UUID playerId = UUID.randomUUID();
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    UUID ownerToken = UUID.randomUUID();
    FencedPlayerSnapshot snapshot = new FencedPlayerSnapshot(
        playerId, ownerToken, 7L, 12L, "sql");
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(sqlManager.updateFencedDataBatch(any())).thenAnswer(invocation ->
        fencedResults(invocation.getArgument(0), SQLManager.FencedWriteStatus.FAILED))
        .thenAnswer(invocation ->
            fencedResults(invocation.getArgument(0), SQLManager.FencedWriteStatus.COMMITTED));

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      queue.queueSave(snapshot, new File(dataFolder, "unused.json"),
          PlayerDataPurgeGuard.generation(playerId));
      executor.runAll();
    }

    assertThat(retryDelays).containsExactly(50L);
    assertThat(queue.getPendingSave(playerId)).isNull();
    verify(sqlManager, times(2)).updateFencedDataBatch(any());
  }

  @Test
  void retryCountIsBoundedAndFailedLatestSnapshotRemainsPending() {
    CapturingExecutor executor = new CapturingExecutor();
    List<Long> retryDelays = new ArrayList<>();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor, (task, delayMillis) -> {
      retryDelays.add(delayMillis);
      executor.execute(task);
    });
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "permanent-directory-target");
    assertThat(target.mkdir()).isTrue();

    queue.queueSave(playerId, "latest", target);
    executor.runAll();

    assertThat(retryDelays).containsExactly(50L, 250L, 1_000L);
    assertThat(executor.pendingTaskCount()).isZero();
    assertThat(queue.getPendingSave(playerId)).isEqualTo("latest");
  }

  @Test
  void shutdownFallbackPersistsOnlyLatestSafeSnapshot() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "shutdown.json");

    queue.queueSave(playerId, "old", target);
    queue.queueSave(playerId, "latest", target);
    queue.flushAndShutdown(0L);

    assertThat(queue.getPendingSave(playerId)).isNull();
    assertThat(Files.readString(target.toPath()).trim()).isEqualTo("latest");
  }

  @Test
  void shutdownFallbackJournalsLatestFencedSqlSnapshotWithoutBlockingOnTheDatabase() {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    File localFile = new File(dataFolder, "unused.json");
    UUID ownerToken = UUID.randomUUID();
    FencedPlayerSnapshot old = new FencedPlayerSnapshot(
        playerId, ownerToken, 3L, 8L, "old");
    FencedPlayerSnapshot latest = new FencedPlayerSnapshot(
        playerId, ownerToken, 3L, 9L, "latest");

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      queue.queueSave(old, localFile, PlayerDataPurgeGuard.generation(playerId));
      queue.queueSave(latest, localFile, PlayerDataPurgeGuard.generation(playerId));
      queue.flushAndShutdown(0L);
    }

    assertThat(queue.getPendingSave(playerId)).isNull();
    assertThat(PlayerDataPersistenceQueue.readSqlRecovery(
        PlayerDataPersistenceQueue.sqlRecoveryFile(localFile)).snapshot()).isEqualTo(latest);
    verify(sqlManager, never()).updateFencedDataBatch(any());
  }

  @Test
  void deleteImmediatelyHidesPendingSaveAndRunsAfterIt() {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "player.json");

    queue.queueSave(playerId, "snapshot", target);
    queue.queueDelete(playerId, target);

    assertThat(queue.getPendingSave(playerId)).isNull();
    executor.runAll();
    assertThat(target).doesNotExist();
  }

  @Test
  void saveQueuedAfterDeletePersistsAfterTheDeleteBarrier() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "ordered-delete.json");

    queue.queueSave(playerId, "old", target);
    queue.queueDelete(playerId, target);
    queue.queueSave(playerId, "fresh", target);
    executor.runAll();

    assertThat(Files.readString(target.toPath())).isEqualTo("fresh");
    assertThat(PlayerDataPersistenceQueue.deleteMarkerFile(target)).doesNotExist();
    assertThat(queue.pendingCount()).isZero();
  }

  @Test
  void fencedResetRetriesAndCompletesOnlyAfterTokenRotationCommits() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    List<Long> retryDelays = new ArrayList<>();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor, (task, delayMillis) -> {
      retryDelays.add(delayMillis);
      executor.execute(task);
    });
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "sql-reset.json");
    Files.writeString(target.toPath(), "stale-local");
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    UUID newOwnerToken = UUID.randomUUID();
    SQLManager.TokenMutationResult failed = new SQLManager.TokenMutationResult(
        playerId, SQLManager.TokenMutationStatus.FAILED, null);
    SQLManager.TokenMutationResult committed = new SQLManager.TokenMutationResult(
        playerId,
        SQLManager.TokenMutationStatus.COMMITTED,
        new SQLManager.SqlToken(newOwnerToken, 9L)
    );
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(sqlManager.resetFencedData(playerId, "fresh")).thenReturn(failed, committed);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      CompletableFuture<SQLManager.TokenMutationResult> completion =
          queue.resetFencedData(
              playerId,
              "fresh",
              target,
              PlayerDataPurgeGuard.generation(playerId)
          );

      assertThat(completion).isNotDone();
      executor.runNext();
      assertThat(completion).isNotDone();
      executor.runAll();

      assertThat(completion).isCompletedWithValue(committed);
    }

    assertThat(retryDelays).containsExactly(50L);
    assertThat(target).doesNotExist();
    verify(sqlManager, times(2)).resetFencedData(playerId, "fresh");
  }

  @Test
  void fencedPurgeDropsQueuedSaveFromRetiredOwner() {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    UUID retiredOwnerToken = UUID.randomUUID();
    UUID newOwnerToken = UUID.randomUUID();
    File target = new File(dataFolder, "sql-purge.json");
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    SQLManager.TokenMutationResult committed = new SQLManager.TokenMutationResult(
        playerId,
        SQLManager.TokenMutationStatus.COMMITTED,
        new SQLManager.SqlToken(newOwnerToken, 10L)
    );
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(sqlManager.purgeFencedData(playerId)).thenReturn(committed);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      CompletableFuture<SQLManager.TokenMutationResult> completion =
          queue.purgeFencedData(playerId, target);
      queue.queueSave(
          new FencedPlayerSnapshot(playerId, retiredOwnerToken, 9L, 17L, "stale"),
          target,
          PlayerDataPurgeGuard.generation(playerId)
      );

      executor.runAll();

      assertThat(completion).isCompletedWithValue(committed);
    }

    assertThat(queue.getPendingSave(playerId)).isNull();
    verify(sqlManager, never()).updateFencedDataBatch(any());
  }

  @Test
  void failedFencedPurgeStopsAfterBoundedRetries() {
    CapturingExecutor executor = new CapturingExecutor();
    List<Long> retryDelays = new ArrayList<>();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor, (task, delayMillis) -> {
      retryDelays.add(delayMillis);
      executor.execute(task);
    });
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "failed-sql-purge.json");
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    SQLManager.TokenMutationResult failed = new SQLManager.TokenMutationResult(
        playerId, SQLManager.TokenMutationStatus.FAILED, null);
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(sqlManager.purgeFencedData(playerId)).thenReturn(failed);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      CompletableFuture<SQLManager.TokenMutationResult> completion =
          queue.purgeFencedData(playerId, target);
      executor.runAll();

      assertThat(completion).isCompletedExceptionally();
    }

    assertThat(retryDelays).containsExactly(50L, 250L, 1_000L);
    verify(sqlManager, times(4)).purgeFencedData(playerId);
  }

  @Test
  void shutdownFailsDelayedFencedMutationInsteadOfLeavingFuturePending() {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "shutdown-sql-purge.json");
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    SQLManager.TokenMutationResult failed = new SQLManager.TokenMutationResult(
        playerId, SQLManager.TokenMutationStatus.FAILED, null);
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(sqlManager.purgeFencedData(playerId)).thenReturn(failed);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      CompletableFuture<SQLManager.TokenMutationResult> completion =
          queue.purgeFencedData(playerId, target);
      executor.runNext();

      assertThat(completion).isNotDone();
      assertThat(queue.pendingCount()).isOne();
      queue.flushAndShutdown(0L);

      assertThat(completion).isCompletedExceptionally();
      assertThat(queue.pendingCount()).isZero();
    }
  }

  @Test
  void shutdownFallbackCompletesQueuedDeleteAfterExecutorCancellation() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "shutdown-delete.json");
    Files.writeString(target.toPath(), "stale");

    queue.queueDelete(playerId, target);
    queue.flushAndShutdown(0L);

    assertThat(target).doesNotExist();
    assertThat(PlayerDataPersistenceQueue.deleteMarkerFile(target)).doesNotExist();
    assertThat(queue.pendingCount()).isZero();
  }

  @Test
  void deleteThenSaveJournalTakesPrecedenceWhenRecoveryPayloadAlsoExists() throws Exception {
    CapturingExecutor originalExecutor = new CapturingExecutor();
    PlayerDataPersistenceQueue originalQueue = new PlayerDataPersistenceQueue(originalExecutor);
    CapturingExecutor restartExecutor = new CapturingExecutor();
    PlayerDataPersistenceQueue restartQueue = new PlayerDataPersistenceQueue(restartExecutor);
    UUID playerId = UUID.randomUUID();
    File playerDirectory = new File(dataFolder, "journal-precedence");
    assertThat(playerDirectory.mkdirs()).isTrue();
    File target = new File(playerDirectory, playerId + ".json");
    PlayerData fresh = new PlayerData();
    fresh.addStat("fresh", 42D);
    String freshJson = fresh.toJson(false);
    PlayerData staleRecovery = new PlayerData();
    staleRecovery.addStat("stale", 99D);
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    when(sqlSettings.isEnabled()).thenReturn(false);
    when(plugin.getPlayerDataPersistenceQueue()).thenReturn(restartQueue);
    when(plugin.getDataFolder(any(String[].class))).thenReturn(playerDirectory);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      assertThat(originalQueue.queueReset(playerId, freshJson, target,
          PlayerDataPurgeGuard.generation(playerId))).isTrue();
      Files.writeString(PlayerDataPersistenceQueue.sqlRecoveryFile(target).toPath(), staleRecovery.toJson(false));

      PlayerData loaded = AdaptPlayer.loadPlayerData(playerId);

      assertThat(loaded.getStat("fresh")).isEqualTo(42D);
      assertThat(loaded.getStat("stale")).isZero();
      assertThat(restartQueue.getPendingSave(playerId)).isEqualTo(freshJson);
    }

    assertThat(originalExecutor.pendingTaskCount()).isOne();
    assertThat(restartExecutor.pendingTaskCount()).isOne();
  }

  @Test
  void staleJournalReplayCannotReplaceNewerDeleteSuccessorRevision() {
    CapturingExecutor originalExecutor = new CapturingExecutor();
    PlayerDataPersistenceQueue originalQueue = new PlayerDataPersistenceQueue(originalExecutor);
    CapturingExecutor currentExecutor = new CapturingExecutor();
    PlayerDataPersistenceQueue currentQueue = new PlayerDataPersistenceQueue(currentExecutor);
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "stale-journal-replay.json");

    assertThat(originalQueue.queueReset(playerId, "first", target,
        PlayerDataPurgeGuard.generation(playerId))).isTrue();
    PlayerDataPersistenceQueue.DeleteJournal stale = PlayerDataPersistenceQueue.readDeleteJournal(
        PlayerDataPersistenceQueue.deleteMarkerFile(target));
    assertThat(currentQueue.queueReset(playerId, "latest", target,
        PlayerDataPurgeGuard.generation(playerId))).isTrue();
    int pendingTasks = currentExecutor.pendingTaskCount();

    assertThat(currentQueue.resumeDeleteThenSave(playerId, target, stale)).isTrue();

    PlayerDataPersistenceQueue.DeleteJournal retained = PlayerDataPersistenceQueue.readDeleteJournal(
        PlayerDataPersistenceQueue.deleteMarkerFile(target));
    assertThat(retained.valid()).isTrue();
    assertThat(retained.revision()).isGreaterThan(stale.revision());
    assertThat(retained.successorJson()).isEqualTo("latest");
    assertThat(currentQueue.getPendingSave(playerId)).isEqualTo("latest");
    assertThat(originalExecutor.pendingTaskCount()).isOne();
    assertThat(currentExecutor.pendingTaskCount()).isEqualTo(pendingTasks);
  }

  @Test
  void journalAwarePrefetchedConstructorCannotReviveStaleProfile() throws Exception {
    CapturingExecutor originalExecutor = new CapturingExecutor();
    PlayerDataPersistenceQueue originalQueue = new PlayerDataPersistenceQueue(originalExecutor);
    CapturingExecutor restartExecutor = new CapturingExecutor();
    PlayerDataPersistenceQueue restartQueue = new PlayerDataPersistenceQueue(restartExecutor);
    UUID playerId = UUID.randomUUID();
    File playerDirectory = new File(dataFolder, "peek-journal-precedence");
    assertThat(playerDirectory.mkdirs()).isTrue();
    File target = new File(playerDirectory, playerId + ".json");
    PlayerData stale = new PlayerData();
    stale.addStat("stale", 99D);
    PlayerData fresh = new PlayerData();
    fresh.addStat("fresh", 42D);
    String freshJson = fresh.toJson(false);
    Files.writeString(target.toPath(), stale.toJson(false));
    when(plugin.getPlayerDataPersistenceQueue()).thenReturn(restartQueue);
    when(plugin.getDataFolder(any(String[].class))).thenReturn(playerDirectory);
    when(plugin.getManager()).thenReturn(mock(AdvancementManager.class));

    assertThat(originalQueue.queueReset(playerId, freshJson, target,
        PlayerDataPurgeGuard.generation(playerId))).isTrue();

    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(playerId);
    AdaptPlayer constructed = new AdaptPlayer(player, stale);

    assertThat(constructed.getData().getStat("fresh")).isEqualTo(42D);
    assertThat(constructed.getData().getStat("stale")).isZero();
    assertThat(restartQueue.getPendingSave(playerId)).isEqualTo(freshJson);
    assertThat(originalExecutor.pendingTaskCount()).isOne();
    assertThat(restartExecutor.pendingTaskCount()).isOne();
  }

  @Test
  void staleLoadGenerationCannotQueueSaveAfterPurgeWasConsumed() {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "purge-generation.json");
    long staleGeneration = PlayerDataPurgeGuard.generation(playerId);
    long freshGeneration = PlayerDataPurgeGuard.mark(playerId);
    PlayerDataPurgeGuard.clear(playerId);

    queue.queueSave(playerId, "stale", target, staleGeneration);
    queue.queueSave(playerId, "fresh", target, freshGeneration);

    assertThat(queue.getPendingSave(playerId)).isEqualTo("fresh");
    assertThat(executor.pendingTaskCount()).isOne();
  }

  @Test
  void coalescesFiveThousandBurstSavesIntoOneWritePerThousandPlayers() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    List<UUID> playerIds = new ArrayList<>(1_000);
    for (int index = 0; index < 1_000; index++) {
      UUID playerId = UUID.randomUUID();
      playerIds.add(playerId);
      File target = new File(dataFolder, "burst/" + playerId + ".json");
      for (int revision = 0; revision < 5; revision++) {
        queue.queueSave(playerId, "snapshot-" + revision, target);
      }
    }

    assertThat(queue.pendingCount()).isEqualTo(1_000);
    assertThat(executor.pendingTaskCount()).isEqualTo(1_000);
    executor.runAll();

    assertThat(queue.pendingCount()).isZero();
    for (UUID playerId : playerIds) {
      File target = new File(dataFolder, "burst/" + playerId + ".json");
      assertThat(Files.readString(target.toPath())).isEqualTo("snapshot-4");
    }
  }

  @Test
  void fencedCoalescingRejectsOlderAndConflictingEqualSequences() {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    UUID ownerToken = UUID.randomUUID();
    File target = new File(dataFolder, "fenced-coalescing.json");
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    FencedPlayerSnapshot initial = new FencedPlayerSnapshot(
        playerId, ownerToken, 6L, 10L, "initial");
    FencedPlayerSnapshot older = new FencedPlayerSnapshot(
        playerId, ownerToken, 6L, 9L, "older");
    FencedPlayerSnapshot conflicting = new FencedPlayerSnapshot(
        playerId, ownerToken, 6L, 10L, "conflicting");
    FencedPlayerSnapshot latest = new FencedPlayerSnapshot(
        playerId, ownerToken, 6L, 11L, "latest");
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(sqlManager.updateFencedDataBatch(any())).thenAnswer(invocation ->
        fencedResults(invocation.getArgument(0), SQLManager.FencedWriteStatus.COMMITTED));

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      long generation = PlayerDataPurgeGuard.generation(playerId);
      queue.queueSave(initial, target, generation);
      queue.queueSave(older, target, generation);
      queue.queueSave(conflicting, target, generation);
      queue.queueSave(latest, target, generation);

      assertThat(queue.getPendingFencedSnapshot(playerId)).isEqualTo(latest);
      executor.runAll();
    }

    verify(sqlManager).updateFencedDataBatch(List.of(new SQLManager.FencedDataUpdate(
        playerId, ownerToken.toString(), 6L, 11L, "latest")));
    assertThat(queue.pendingCount()).isZero();
  }

  @Test
  void drainsThousandFencedSqlProfilesInEightBoundedBatchCalls() {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    List<List<SQLManager.FencedDataUpdate>> committedBatches = new ArrayList<>();
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(sqlManager.updateFencedDataBatch(any())).thenAnswer(invocation -> {
      List<SQLManager.FencedDataUpdate> updates = invocation.getArgument(0);
      committedBatches.add(List.copyOf(updates));
      return fencedResults(updates, SQLManager.FencedWriteStatus.COMMITTED);
    });

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      for (int index = 0; index < 1_000; index++) {
        UUID playerId = new UUID(0L, index + 1L);
        UUID ownerToken = new UUID(1L, index + 1L);
        File target = new File(dataFolder, "sql-burst/" + playerId + ".json");
        for (int revision = 0; revision < 5; revision++) {
          queue.queueSave(
              new FencedPlayerSnapshot(
                  playerId, ownerToken, 4L, revision + 1L, "snapshot-" + revision),
              target,
              PlayerDataPurgeGuard.generation(playerId)
          );
        }
      }

      assertThat(queue.pendingCount()).isEqualTo(1_000);
      assertThat(executor.pendingTaskCount()).isOne();
      executor.runAll();
    }

    List<SQLManager.FencedDataUpdate> committedUpdates = new ArrayList<>(1_000);
    for (List<SQLManager.FencedDataUpdate> batch : committedBatches) {
      assertThat(batch).hasSizeLessThanOrEqualTo(128);
      committedUpdates.addAll(batch);
    }
    assertThat(committedBatches).hasSize(8);
    assertThat(committedUpdates).hasSize(1_000);
    assertThat(committedUpdates).allSatisfy(update -> assertThat(update.data()).isEqualTo("snapshot-4"));
    assertThat(committedUpdates).allSatisfy(update -> assertThat(update.sequence()).isEqualTo(5L));
    assertThat(queue.pendingCount()).isZero();
  }

  @Test
  void replacementQueuedDuringCommittedBatchIsWrittenByFollowingBatch() {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "sql-replacement.json");
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    AtomicInteger batchCalls = new AtomicInteger();
    List<String> committedSnapshots = new ArrayList<>();
    UUID ownerToken = UUID.randomUUID();
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(sqlManager.updateFencedDataBatch(any())).thenAnswer(invocation -> {
      List<SQLManager.FencedDataUpdate> updates = invocation.getArgument(0);
      committedSnapshots.add(updates.getFirst().data());
      if (batchCalls.getAndIncrement() == 0) {
        queue.queueSave(
            new FencedPlayerSnapshot(playerId, ownerToken, 2L, 2L, "replacement"),
            target,
            PlayerDataPurgeGuard.generation(playerId)
        );
      }
      return fencedResults(updates, SQLManager.FencedWriteStatus.COMMITTED);
    });

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      queue.queueSave(
          new FencedPlayerSnapshot(playerId, ownerToken, 2L, 1L, "captured"),
          target,
          PlayerDataPurgeGuard.generation(playerId)
      );
      executor.runAll();
    }

    assertThat(committedSnapshots).containsExactly("captured", "replacement");
    assertThat(queue.pendingCount()).isZero();
  }

  @Test
  void failedFencedSqlBatchRetainsRecoveryUntilRetryCommit() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    List<Long> retryDelays = new ArrayList<>();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor, (task, delayMillis) -> {
      retryDelays.add(delayMillis);
      executor.execute(task);
    });
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "sql-retry.json");
    File recovery = PlayerDataPersistenceQueue.sqlRecoveryFile(target);
    FencedPlayerSnapshot snapshot = new FencedPlayerSnapshot(
        playerId, UUID.randomUUID(), 5L, 8L, "latest");
    PlayerDataPersistenceQueue.writeSqlRecovery(recovery, snapshot);
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(sqlManager.updateFencedDataBatch(any())).thenAnswer(invocation ->
        fencedResults(invocation.getArgument(0), SQLManager.FencedWriteStatus.FAILED))
        .thenAnswer(invocation ->
            fencedResults(invocation.getArgument(0), SQLManager.FencedWriteStatus.COMMITTED));

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      queue.queueSave(snapshot, target, PlayerDataPurgeGuard.generation(playerId));
      executor.runNext();

      assertThat(queue.getPendingSave(playerId)).isEqualTo("latest");
      assertThat(recovery).exists();

      executor.runNext();
    }

    assertThat(retryDelays).containsExactly(50L);
    assertThat(recovery).doesNotExist();
    assertThat(queue.pendingCount()).isZero();
    verify(sqlManager, times(2)).updateFencedDataBatch(any());
  }

  @Test
  void terminalSqlFailureAtomicallyRetainsLatestSnapshotWithoutPreexistingRecovery() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    List<Long> retryDelays = new ArrayList<>();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor, (task, delayMillis) -> {
      retryDelays.add(delayMillis);
      executor.execute(task);
    });
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "terminal-sql.json");
    File recovery = PlayerDataPersistenceQueue.sqlRecoveryFile(target);
    FencedPlayerSnapshot snapshot = new FencedPlayerSnapshot(
        playerId, UUID.randomUUID(), 3L, 14L, "latest");
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(sqlManager.updateFencedDataBatch(any())).thenAnswer(invocation ->
        fencedResults(invocation.getArgument(0), SQLManager.FencedWriteStatus.FAILED));

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      queue.queueSave(snapshot, target, PlayerDataPurgeGuard.generation(playerId));
      executor.runAll();
    }

    assertThat(retryDelays).containsExactly(50L, 250L, 1_000L);
    assertThat(PlayerDataPersistenceQueue.readSqlRecovery(recovery).snapshot()).isEqualTo(snapshot);
    assertThat(queue.getPendingSave(playerId)).isEqualTo("latest");
    assertThat(executor.pendingTaskCount()).isZero();
    verify(sqlManager, times(4)).updateFencedDataBatch(any());
  }

  @Test
  void committedFencedSqlWritePreservesRecoveryOwnedByDifferentFence() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "cleanup-retry.json");
    File recovery = PlayerDataPersistenceQueue.sqlRecoveryFile(target);
    FencedPlayerSnapshot committed = new FencedPlayerSnapshot(
        playerId, UUID.randomUUID(), 7L, 11L, "committed");
    FencedPlayerSnapshot foreign = new FencedPlayerSnapshot(
        playerId, UUID.randomUUID(), 8L, 1L, "foreign");
    PlayerDataPersistenceQueue.writeSqlRecovery(recovery, foreign);
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(sqlManager.updateFencedDataBatch(any())).thenAnswer(invocation ->
        fencedResults(invocation.getArgument(0), SQLManager.FencedWriteStatus.COMMITTED));

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      queue.queueSave(committed, target, PlayerDataPurgeGuard.generation(playerId));
      executor.runAll();
    }

    assertThat(queue.pendingCount()).isZero();
    assertThat(PlayerDataPersistenceQueue.readSqlRecovery(recovery).snapshot()).isEqualTo(foreign);
    verify(sqlManager).updateFencedDataBatch(any());
  }

  @Test
  void fencedSqlRejectionRetainsSnapshotForRecoveryWithoutRetrying() {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File target = new File(dataFolder, "fenced-away.json");
    FencedPlayerSnapshot snapshot = new FencedPlayerSnapshot(
        playerId, UUID.randomUUID(), 4L, 20L, "fenced");
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(sqlManager.updateFencedDataBatch(any())).thenAnswer(invocation ->
        fencedResults(invocation.getArgument(0), SQLManager.FencedWriteStatus.FENCED));

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      queue.queueSave(snapshot, target, PlayerDataPurgeGuard.generation(playerId));
      executor.runAll();
    }

    assertThat(queue.pendingCount()).isZero();
    assertThat(PlayerDataPersistenceQueue.readSqlRecovery(
        PlayerDataPersistenceQueue.sqlRecoveryFile(target)).snapshot()).isEqualTo(snapshot);
    verify(sqlManager).updateFencedDataBatch(any());
  }

  @Test
  void playerLoadPrefersLatestQueuedSnapshotOverDisk() {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    PlayerData snapshot = new PlayerData();
    snapshot.addStat("queued", 42D);
    queue.queueSave(playerId, snapshot.toJson(false), new File(dataFolder, "player.json"));
    when(plugin.getPlayerDataPersistenceQueue()).thenReturn(queue);

    PlayerData loaded = AdaptPlayer.loadPlayerData(playerId);

    assertThat(loaded.getStat("queued")).isEqualTo(42D);
  }

  @Test
  void sqlReadFailureGuardsLocalFallbackFromBeingUploaded() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File playerDirectory = new File(dataFolder, "sql-read-failure");
    assertThat(playerDirectory.mkdirs()).isTrue();
    File playerFile = new File(playerDirectory, playerId + ".json");
    PlayerData local = new PlayerData();
    local.addStat("local", 42D);
    Files.writeString(playerFile.toPath(), local.toJson(true));
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(sqlManager.fetchData(playerId)).thenReturn(SQLManager.FetchResult.failure());
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(plugin.getPlayerDataPersistenceQueue()).thenReturn(queue);
    when(plugin.getDataFolder(any(String[].class))).thenReturn(playerDirectory);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      PlayerData loaded = AdaptPlayer.loadPlayerData(playerId);

      assertThat(loaded.getStat("local")).isEqualTo(42D);
      assertThat(AdaptPlayer.hasLoadFailure(playerId)).isTrue();
      assertThat(queue.getPendingSave(playerId)).isNull();
    } finally {
      AdaptPlayer.forgetLoadFailure(playerId);
    }
  }

  @Test
  void durableDeleteMarkerSuppressesStaleStorageOnNextLoad() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File playerDirectory = new File(dataFolder, "delete-recovery");
    assertThat(playerDirectory.mkdirs()).isTrue();
    File playerFile = new File(playerDirectory, playerId + ".json");
    PlayerData stale = new PlayerData();
    stale.addStat("stale", 42D);
    Files.writeString(playerFile.toPath(), stale.toJson(true));
    Files.writeString(PlayerDataPersistenceQueue.deleteMarkerFile(playerFile).toPath(), "1");
    when(plugin.getPlayerDataPersistenceQueue()).thenReturn(queue);
    when(plugin.getDataFolder(any(String[].class))).thenReturn(playerDirectory);

    PlayerData loaded = AdaptPlayer.loadPlayerData(playerId);

    assertThat(loaded.getStat("stale")).isZero();
    executor.runAll();
    assertThat(playerFile).doesNotExist();
    assertThat(PlayerDataPersistenceQueue.deleteMarkerFile(playerFile)).doesNotExist();
  }

  @Test
  void malformedDeleteMarkerIsPreservedAndBlocksDestructiveReplay() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File playerDirectory = new File(dataFolder, "invalid-delete-recovery");
    assertThat(playerDirectory.mkdirs()).isTrue();
    File playerFile = new File(playerDirectory, playerId + ".json");
    PlayerData stale = new PlayerData();
    stale.addStat("stale", 42D);
    Files.writeString(playerFile.toPath(), stale.toJson(true));
    File deleteMarker = PlayerDataPersistenceQueue.deleteMarkerFile(playerFile);
    Files.writeString(deleteMarker.toPath(), "not-a-valid-delete-journal");
    when(plugin.getPlayerDataPersistenceQueue()).thenReturn(queue);
    when(plugin.getDataFolder(any(String[].class))).thenReturn(playerDirectory);

    try {
      PlayerData loaded = AdaptPlayer.loadPlayerData(playerId);

      assertThat(loaded.getStat("stale")).isZero();
      assertThat(AdaptPlayer.hasLoadFailure(playerId)).isTrue();
      assertThat(playerFile).exists();
      assertThat(Files.readString(deleteMarker.toPath())).isEqualTo("not-a-valid-delete-journal");
      assertThat(executor.pendingTaskCount()).isZero();
      assertThat(queue.pendingCount()).isZero();

      queue.queueDelete(playerId, playerFile);
      assertThat(queue.queueReset(
          playerId,
          new PlayerData().toJson(true),
          playerFile,
          PlayerDataPurgeGuard.generation(playerId)
      )).isFalse();
      assertThat(Files.readString(deleteMarker.toPath())).isEqualTo("not-a-valid-delete-journal");
      assertThat(executor.pendingTaskCount()).isZero();
      assertThat(queue.pendingCount()).isZero();
    } finally {
      AdaptPlayer.forgetLoadFailure(playerId);
    }
  }

  @Test
  void corruptLocalMigrationIsNotUploadedToSql() throws Exception {
    UUID playerId = UUID.randomUUID();
    File playerDirectory = new File(dataFolder, "players");
    assertThat(playerDirectory.mkdirs()).isTrue();
    File playerFile = new File(playerDirectory, playerId + ".json");
    Files.writeString(playerFile.toPath(), "null");
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    SQLManager sqlManager = mock(SQLManager.class);
    when(sqlSettings.isEnabled()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(sqlManager.fetchData(playerId)).thenReturn(SQLManager.FetchResult.success(null));
    when(plugin.getDataFolder(any(String[].class))).thenReturn(playerDirectory);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);

      PlayerData loaded = AdaptPlayer.loadPlayerData(playerId);

      assertThat(loaded).isNotNull();
      verify(sqlManager).fetchData(playerId);
      verify(sqlManager, never()).updateFencedDataBatch(any());
    }
  }

  @Test
  void corruptLocalProfileCannotBecomeRuntimeClaimData() throws Exception {
    UUID playerId = UUID.randomUUID();
    File playerDirectory = new File(dataFolder, "local-claim-guard");
    assertThat(playerDirectory.mkdirs()).isTrue();
    File playerFile = new File(playerDirectory, playerId + ".json");
    Files.writeString(playerFile.toPath(), "{");
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = stubSqlSettings(config);
    when(sqlSettings.isEnabled()).thenReturn(false);
    when(plugin.getDataFolder(any(String[].class))).thenReturn(playerDirectory);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);

      assertThatThrownBy(() -> AdaptPlayer.claimPlayerData(playerId))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("could not be loaded safely");
      assertThat(AdaptPlayer.hasLoadFailure(playerId)).isTrue();
      assertThat(playerFile).exists();
    } finally {
      AdaptPlayer.forgetLoadFailure(playerId);
    }
  }

  private static AdaptConfig.SqlSettings stubSqlSettings(AdaptConfig config) {
    AdaptConfig.SqlSettings sqlSettings = mock(AdaptConfig.SqlSettings.class);
    when(config.getSql()).thenReturn(sqlSettings);
    return sqlSettings;
  }

  private static List<SQLManager.FencedWriteResult> fencedResults(
      List<SQLManager.FencedDataUpdate> updates,
      SQLManager.FencedWriteStatus status
  ) {
    List<SQLManager.FencedWriteResult> results = new ArrayList<>(updates.size());
    for (SQLManager.FencedDataUpdate update : updates) {
      results.add(new SQLManager.FencedWriteResult(update.uuid(), update.sequence(), status));
    }
    return results;
  }

  private static final class CapturingExecutor extends AbstractExecutorService {
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private boolean shutdown;

    @Override
    public void shutdown() {
      shutdown = true;
    }

    @Override
    public List<Runnable> shutdownNow() {
      shutdown = true;
      List<Runnable> pending = new ArrayList<>(tasks);
      tasks.clear();
      return pending;
    }

    @Override
    public boolean isShutdown() {
      return shutdown;
    }

    @Override
    public boolean isTerminated() {
      return shutdown && tasks.isEmpty();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
      return isTerminated();
    }

    @Override
    public void execute(Runnable command) {
      if (shutdown) {
        throw new RejectedExecutionException("executor is shut down");
      }
      tasks.addLast(command);
    }

    private int pendingTaskCount() {
      return tasks.size();
    }

    private void runNext() {
      Runnable task = tasks.removeFirst();
      task.run();
    }

    private void runAll() {
      while (!tasks.isEmpty()) {
        runNext();
      }
    }
  }
}

package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.io.SQLManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerDataPersistenceQueueTest extends AdaptTestBase {
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
    SQLManager sqlManager = mock(SQLManager.class);
    when(config.isUseSql()).thenReturn(false);
    when(plugin.getSqlManager()).thenReturn(sqlManager);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      queue.queueSave(playerId, "local", target);
      when(config.isUseSql()).thenReturn(true);
      executor.runNext();
    }

    assertThat(Files.readString(target.toPath()).trim()).isEqualTo("local");
    verify(sqlManager, never()).updateData(eq(playerId), anyString());
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
  void sqlFailureIsRetriedThroughCapturedManager() {
    CapturingExecutor executor = new CapturingExecutor();
    List<Long> retryDelays = new ArrayList<>();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor, (task, delayMillis) -> {
      retryDelays.add(delayMillis);
      executor.execute(task);
    });
    UUID playerId = UUID.randomUUID();
    AdaptConfig config = mock(AdaptConfig.class);
    SQLManager sqlManager = mock(SQLManager.class);
    when(config.isUseSql()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(sqlManager.updateData(eq(playerId), anyString())).thenReturn(false, true);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      queue.queueSave(playerId, "sql", new File(dataFolder, "unused.json"));
      executor.runAll();
    }

    assertThat(retryDelays).containsExactly(50L);
    assertThat(queue.getPendingSave(playerId)).isNull();
    verify(sqlManager, times(2)).updateData(playerId, "sql");
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
  void shutdownFallbackJournalsLatestSqlSnapshotWithoutBlockingOnTheDatabase() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    AdaptConfig config = mock(AdaptConfig.class);
    SQLManager sqlManager = mock(SQLManager.class);
    when(config.isUseSql()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    File localFile = new File(dataFolder, "unused.json");

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      queue.queueSave(playerId, "old", localFile);
      queue.queueSave(playerId, "latest", localFile);
      queue.flushAndShutdown(0L);
    }

    assertThat(queue.getPendingSave(playerId)).isNull();
    assertThat(Files.readString(PlayerDataPersistenceQueue.sqlRecoveryFile(localFile).toPath()).trim())
        .isEqualTo("latest");
    verify(sqlManager, never()).updateData(eq(playerId), anyString());
  }

  @Test
  void playerLoadPrefersShutdownSqlRecoveryOverStaleRemoteData() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    UUID playerId = UUID.randomUUID();
    File playerDirectory = new File(dataFolder, "data/players");
    assertThat(playerDirectory.mkdirs()).isTrue();
    File localFile = new File(playerDirectory, playerId + ".json");
    PlayerData recovered = new PlayerData();
    recovered.addStat("recovered", 42D);
    Files.writeString(PlayerDataPersistenceQueue.sqlRecoveryFile(localFile).toPath(), recovered.toJson(true));
    AdaptConfig config = mock(AdaptConfig.class);
    SQLManager sqlManager = mock(SQLManager.class);
    when(config.isUseSql()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(plugin.getPlayerDataPersistenceQueue()).thenReturn(queue);
    when(plugin.getDataFolder(any(String[].class))).thenReturn(playerDirectory);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);

      PlayerData loaded = AdaptPlayer.loadPlayerData(playerId);

      assertThat(loaded.getStat("recovered")).isEqualTo(42D);
      verify(sqlManager, never()).fetchData(playerId);
    }
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
  void corruptLocalMigrationIsNotUploadedToSql() throws Exception {
    UUID playerId = UUID.randomUUID();
    File playerDirectory = new File(dataFolder, "players");
    assertThat(playerDirectory.mkdirs()).isTrue();
    File playerFile = new File(playerDirectory, playerId + ".json");
    Files.writeString(playerFile.toPath(), "null");
    AdaptConfig config = mock(AdaptConfig.class);
    SQLManager sqlManager = mock(SQLManager.class);
    when(config.isUseSql()).thenReturn(true);
    when(plugin.getSqlManager()).thenReturn(sqlManager);
    when(plugin.getDataFolder(any(String[].class))).thenReturn(playerDirectory);

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);

      PlayerData loaded = AdaptPlayer.loadPlayerData(playerId);

      assertThat(loaded).isNotNull();
      verify(sqlManager).fetchData(playerId);
      verify(sqlManager, never()).updateData(eq(playerId), anyString());
    }
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

package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.advancement.AdvancementManager;
import art.arcane.adapt.platform.entity.PlayerRef;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AdaptPlayerCheckpointTest extends AdaptTestBase {
  @Test
  void aLaterSaveOfThePlayerAcknowledgesItsPendingCheckpoint() throws Exception {
    UUID playerId = UUID.randomUUID();
    QueuedExecutor executor = new QueuedExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(new TestPersistenceHost(), executor);
    when(core.getPlayerDataPersistenceQueue()).thenReturn(queue);
    when(core.getAdvancementManager()).thenReturn(mock(AdvancementManager.class));
    AdaptConfig config = localConfig();

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      AdaptPlayer adaptPlayer = new AdaptPlayer(player(playerId), new PlayerData());

      CompletableFuture<Void> checkpoint = adaptPlayer.checkpoint();
      adaptPlayer.getData().setWisdom(7L);
      adaptPlayer.saveNow();

      assertThat(checkpoint).isNotDone();
      executor.runAll();
      assertThat(checkpoint).isCompleted();
      String stored = Files.readString(AdaptPlayer.getPlayerDataFile(playerId).toPath());
      assertThat(PlayerData.fromJson(stored).getWisdom()).isEqualTo(7L);
    }
  }

  @Test
  void aWriteThePlayerDidNotMakeStillFailsItsCheckpoint() {
    UUID playerId = UUID.randomUUID();
    QueuedExecutor executor = new QueuedExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(new TestPersistenceHost(), executor);
    when(core.getPlayerDataPersistenceQueue()).thenReturn(queue);
    when(core.getAdvancementManager()).thenReturn(mock(AdvancementManager.class));
    AdaptConfig config = localConfig();

    try (MockedStatic<AdaptConfig> configured = mockStatic(AdaptConfig.class)) {
      configured.when(AdaptConfig::get).thenReturn(config);
      AdaptPlayer adaptPlayer = new AdaptPlayer(player(playerId), new PlayerData());

      CompletableFuture<Void> checkpoint = adaptPlayer.checkpoint();
      queue.queueSave(playerId, "unrelated state", AdaptPlayer.getPlayerDataFile(playerId));

      assertThat(checkpoint).isCompletedExceptionally();
    }
  }

  private static PlayerRef player(UUID playerId) {
    PlayerRef player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(playerId);
    return player;
  }

  private static AdaptConfig localConfig() {
    AdaptConfig config = mock(AdaptConfig.class);
    AdaptConfig.SqlSettings sqlSettings = mock(AdaptConfig.SqlSettings.class);
    when(config.getSql()).thenReturn(sqlSettings);
    when(sqlSettings.isEnabled()).thenReturn(false);
    return config;
  }

  private static final class QueuedExecutor extends AbstractExecutorService {
    private final Deque<Runnable> tasks = new ArrayDeque<>();
    private boolean shutdown;

    void runAll() {
      while (!tasks.isEmpty()) {
        tasks.removeFirst().run();
      }
    }

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
      tasks.addLast(command);
    }
  }
}

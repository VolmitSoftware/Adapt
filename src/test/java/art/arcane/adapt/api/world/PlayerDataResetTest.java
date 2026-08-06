package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.util.common.io.SQLManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins the reset semantics: a reset replaces a profile with a pristine one, the fresh state is
 * persisted ahead of anything already queued, and a purged profile can never be resurrected by a
 * stale in-memory copy writing itself back out.
 */
class PlayerDataResetTest extends AdaptTestBase {
  private UUID playerId;
  private File playerDirectory;
  private File playerFile;
  private AdaptConfig config;
  private MockedStatic<AdaptConfig> configured;

  @BeforeEach
  void prepareStorage() {
    PlayerDataPurgeGuard.reset();
    playerId = UUID.randomUUID();
    playerDirectory = new File(dataFolder, "data/players");
    assertThat(playerDirectory.mkdirs()).isTrue();
    playerFile = new File(playerDirectory, playerId + ".json");
    config = mock(AdaptConfig.class);
    when(config.isUseSql()).thenReturn(false);
    when(plugin.getSqlManager()).thenReturn(mock(SQLManager.class));
    when(plugin.getDataFolder(any(String[].class))).thenReturn(playerDirectory);
    configured = mockStatic(AdaptConfig.class);
    configured.when(AdaptConfig::get).thenReturn(config);
  }

  @AfterEach
  void releaseStorage() {
    configured.close();
    PlayerDataPurgeGuard.reset();
  }

  @Test
  @DisplayName("a reset profile is a pristine profile, not a partially cleared one")
  void resetProfileIsPristineRatherThanCleared() {
    PlayerData played = populatedProfile();

    played.clearAll();

    assertThat(played.toJson(true))
        .describedAs("clearAll leaves session fields behind, which is why a reset swaps the instance")
        .isNotEqualTo(new PlayerData().toJson(true));
    assertThat(new PlayerData().toJson(true)).isEqualTo(new PlayerData().toJson(true));
  }

  @Test
  @DisplayName("the fresh profile is written after any save already queued for the player")
  void freshProfilePersistsAfterAlreadyQueuedSave() throws Exception {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    when(plugin.getPlayerDataPersistenceQueue()).thenReturn(queue);

    queue.queueSave(playerId, populatedProfile().toJson(true), playerFile);
    queue.queueSave(playerId, new PlayerData().toJson(true), playerFile);
    executor.runAll();

    PlayerData persisted = PlayerData.fromJson(Files.readString(playerFile.toPath()));
    assertThat(persisted.getSkillLines()).isEmpty();
    assertThat(persisted.getStats()).isEmpty();
    assertThat(persisted.getAdvancements()).isEmpty();
    assertThat(persisted.getWisdom()).isZero();
    assertThat(persisted.getMasterXp()).isEqualTo(new PlayerData().getMasterXp());
  }

  @Test
  @DisplayName("purging drops a save that was already queued for the player")
  void purgeDropsAnAlreadyQueuedSave() {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    when(plugin.getPlayerDataPersistenceQueue()).thenReturn(queue);
    queue.queueSave(playerId, populatedProfile().toJson(true), playerFile);

    AdaptPlayer.purgeStoredData(playerId);
    executor.runAll();

    assertThat(queue.getPendingSave(playerId)).isNull();
    assertThat(playerFile).doesNotExist();
    assertThat(PlayerDataPurgeGuard.isPurged(playerId)).isTrue();
  }

  @Test
  @DisplayName("an offline purge is not resurrected by a stale copy saving itself back out")
  void purgedProfileIsNotResurrectedByALaterSave() {
    CapturingExecutor executor = new CapturingExecutor();
    PlayerDataPersistenceQueue queue = new PlayerDataPersistenceQueue(executor);
    when(plugin.getPlayerDataPersistenceQueue()).thenReturn(queue);
    String stale = populatedProfile().toJson(true);
    queue.queueSave(playerId, stale, playerFile);

    AdaptPlayer.purgeStoredData(playerId);
    executor.runAll();
    // A lingering AdaptPlayer flushing itself after the purge writes the old profile back.
    queue.queueSave(playerId, stale, playerFile);
    executor.runAll();

    assertThat(playerFile).exists();
    PlayerData loaded = AdaptPlayer.loadPlayerData(playerId);

    assertThat(loaded.getStat("pickaxe.blocks"))
        .describedAs("the purge guard outranks any resurrected copy on disk or in the save queue")
        .isZero();
    assertThat(loaded.getSkillLines()).isEmpty();
  }

  @Test
  @DisplayName("a purged player loads default data even when a stored profile is still on disk")
  void purgedPlayerLoadsDefaultDataOverStoredProfile() throws Exception {
    Files.writeString(playerFile.toPath(), populatedProfile().toJson(true));
    PlayerDataPurgeGuard.mark(playerId);

    PlayerData loaded = AdaptPlayer.loadPlayerData(playerId);

    assertThat(loaded.getStat("pickaxe.blocks")).isZero();
    assertThat(loaded.getSkillLines()).isEmpty();
    assertThat(loaded.getAdvancements()).isEmpty();
    assertThat(loaded.getWisdom()).isZero();
    assertThat(PlayerDataPurgeGuard.isPurged(playerId))
        .describedAs("the guard is one shot and lifts once a fresh profile has been handed out")
        .isFalse();
  }

  @Test
  @DisplayName("an unpurged player still loads the stored profile")
  void unpurgedPlayerLoadsStoredProfile() throws Exception {
    Files.writeString(playerFile.toPath(), populatedProfile().toJson(true));

    PlayerData loaded = AdaptPlayer.loadPlayerData(playerId);

    assertThat(loaded.getStat("pickaxe.blocks")).isEqualTo(1_200D);
    assertThat(loaded.getWisdom()).isEqualTo(7L);
  }

  private static PlayerData populatedProfile() {
    PlayerData data = new PlayerData();
    PlayerSkillLine pickaxe = new PlayerSkillLine();
    pickaxe.setLine("pickaxe");
    pickaxe.setXp(9_000D);
    pickaxe.setKnowledge(12);
    PlayerAdaptation adaptation = new PlayerAdaptation();
    adaptation.setId("pickaxe-vein-miner");
    adaptation.setLevel(3);
    pickaxe.getAdaptations().put("pickaxe-vein-miner", adaptation);
    data.getSkillLines().put("pickaxe", pickaxe);
    data.addStat("pickaxe.blocks", 1_200D);
    data.ensureGranted("adaptation_pickaxe-vein-miner");
    data.setWisdom(7L);
    data.setMasterXp(50_000D);
    data.setLast("pickaxe");
    data.setLastLogin(1_700_000_000_000L);
    return data;
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

    private void runAll() {
      while (!tasks.isEmpty()) {
        tasks.removeFirst().run();
      }
    }
  }
}

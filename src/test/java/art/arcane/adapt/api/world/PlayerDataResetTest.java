package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.advancement.AdvancementManager;
import art.arcane.adapt.util.common.io.SQLManager;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    AdaptConfig.SqlSettings sqlSettings = mock(AdaptConfig.SqlSettings.class);
    when(config.getSql()).thenReturn(sqlSettings);
    when(sqlSettings.isEnabled()).thenReturn(false);
    when(plugin.getSqlManager()).thenReturn(mock(SQLManager.class));
    when(plugin.getManager()).thenReturn(mock(AdvancementManager.class));
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
  @DisplayName("an online reset journals its fresh profile before asynchronous persistence runs")
  void onlineResetIsDurableBeforeExecutorDrain() throws Exception {
    CapturingExecutor resetExecutor = new CapturingExecutor();
    PlayerDataPersistenceQueue resetQueue = new PlayerDataPersistenceQueue(resetExecutor);
    CapturingExecutor restartExecutor = new CapturingExecutor();
    PlayerDataPersistenceQueue restartQueue = new PlayerDataPersistenceQueue(restartExecutor);
    when(plugin.getPlayerDataPersistenceQueue()).thenReturn(resetQueue);
    Files.writeString(playerFile.toPath(), populatedProfile().toJson(false));
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(playerId);
    AdaptPlayer adaptPlayer = new AdaptPlayer(player, populatedProfile());
    long purgeGeneration = PlayerDataPurgeGuard.mark(playerId);
    adaptPlayer.adoptPurgeGeneration(purgeGeneration);
    PlayerDataPurgeGuard.clear(playerId);
    PlayerData replacement = new PlayerData();

    assertThat(adaptPlayer.persistResetNow(replacement)).isTrue();
    adaptPlayer.replaceData(replacement);

    File journalFile = PlayerDataPersistenceQueue.deleteMarkerFile(playerFile);
    PlayerDataPersistenceQueue.DeleteJournal journal = PlayerDataPersistenceQueue.readDeleteJournal(journalFile);
    assertThat(journalFile).exists();
    assertThat(journal.hasSuccessor()).isTrue();
    assertThat(PlayerData.fromJson(journal.successorJson()).getSkillLines()).isEmpty();
    assertThat(resetExecutor.pendingTaskCount()).isOne();
    assertThat(PlayerData.fromJson(Files.readString(playerFile.toPath())).getStat("pickaxe.blocks")).isEqualTo(1_200D);

    when(plugin.getPlayerDataPersistenceQueue()).thenReturn(restartQueue);
    PlayerData recovered = AdaptPlayer.loadPlayerData(playerId);

    assertThat(recovered.getSkillLines()).isEmpty();
    assertThat(recovered.getStats()).isEmpty();
    assertThat(recovered.getWisdom()).isZero();
    assertThat(restartExecutor.pendingTaskCount()).isOne();
  }

  @Test
  void onlineResetEntryPointDispatchesAndRechecksEntityOwnership() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/api/world/AdaptServer.java"));

    assertThat(source)
        .contains("if (!J.isOwnedByCurrentRegion(player))")
        .contains("() -> completePlayerDataReset(playerId, player, completion)")
        .contains("private PlayerDataResetResult resetPlayerDataOwned(UUID playerId, Player player)")
        .contains("if (player != expectedPlayer || !J.isOwnedByCurrentRegion(player))")
        .contains("synchronized (playerOperationLock(playerId))");
    assertThat(source.indexOf("if (!J.isOwnedByCurrentRegion(player))"))
        .isLessThan(source.indexOf("adaptPlayer.persistResetNow(replacement)"));
    assertThat(source.indexOf("adaptPlayer.persistResetNow(replacement)"))
        .isLessThan(source.indexOf("adaptPlayer.replaceData(replacement)"));
    int completionEntry = source.indexOf("private void completePlayerDataReset");
    int ownershipRecheck = source.indexOf(
        "if (player != expectedPlayer || !J.isOwnedByCurrentRegion(player))", completionEntry);
    int ownedOnlineCheck = source.indexOf("if (!player.isOnline())", completionEntry);
    assertThat(ownershipRecheck).isGreaterThan(completionEntry).isLessThan(ownedOnlineCheck);
    String schedulerSource = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/util/common/scheduling/J.java"));
    assertThat(schedulerSource)
        .contains("FoliaScheduler.runEntity(Adapt.instance, entity, runnable, 0L, retired)");
  }

  @Test
  void durableResetAcceptancePrecedesLoadGuardClearAndJoinSharesResetLock() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/api/world/AdaptServer.java"));
    int reset = source.indexOf("private PlayerDataResetResult resetPlayerDataOwned");
    int durableAcceptance = source.indexOf("adaptPlayer.persistResetNow(replacement)", reset);
    int loadGuardClear = source.indexOf("AdaptPlayer.forgetLoadFailure(playerId)", reset);
    int join = source.indexOf("private boolean join(Player p, boolean refreshSnapshots)");
    int joinLock = source.indexOf("synchronized (playerOperationLock(playerId))", join);
    int resetEntry = source.indexOf("public CompletableFuture<PlayerDataResetResult> resetPlayerData");
    int resetLock = source.indexOf("synchronized (playerOperationLock(playerId))", resetEntry);

    assertThat(durableAcceptance).isGreaterThan(reset);
    assertThat(loadGuardClear).isGreaterThan(durableAcceptance);
    assertThat(joinLock).isGreaterThan(join).isLessThan(resetEntry);
    assertThat(resetLock).isGreaterThan(resetEntry);
  }

  @Test
  void resetCommandRetainsConfirmationWhenCentralDispatchIsRejected() throws Exception {
    String source = Files.readString(Path.of(
        "src/main/java/art/arcane/adapt/command/CommandReset.java"));
    int rejected = source.indexOf("resetResult == AdaptServer.PlayerDataResetResult.DISPATCH_REJECTED");
    int confirmation = source.indexOf(
        "pendingConfirmations.record(feedback.senderUuid(), feedback.targetUuid()", rejected);
    int dispatchFailure = source.indexOf("CommandRuntimeMessages.TARGET_DISPATCH_FAILED", rejected);
    int successLog = source.indexOf("Adapt.info(\"Sender \"", rejected);

    assertThat(rejected).isGreaterThanOrEqualTo(0);
    assertThat(confirmation).isGreaterThan(rejected).isLessThan(successLog);
    assertThat(dispatchFailure).isGreaterThan(rejected).isLessThan(successLog);
    assertThat(source.substring(rejected, successLog)).contains("return;");
    assertThat(source)
        .contains("completion.thenAccept(resetResult -> completeResetPlayer(feedback, resetResult))")
        .contains("CommandTargetExecutor.send(completedTarget")
        .contains("CommandTargetExecutor.send(feedback.sender()")
        .doesNotContain("onlineTarget.isOnline()");
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
    queue.queueSave(playerId, stale, playerFile);
    executor.runAll();

    assertThat(playerFile).doesNotExist();
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
        .describedAs("only a live AdaptPlayer may consume the purge generation")
        .isTrue();
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

    private int pendingTaskCount() {
      return tasks.size();
    }
  }
}

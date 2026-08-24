/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.api.world;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.SnippetsMessages;
import art.arcane.adapt.localization.catalog.RuntimeMessages;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.notification.AdvancementNotification;
import art.arcane.adapt.api.notification.Notifier;
import art.arcane.adapt.api.protection.RegionGrantRuntime;
import art.arcane.adapt.api.protection.RegionPolicyService;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.papi.AdaptPlaceholders;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.io.SQLManager;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.project.redis.RedisSync;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
import lombok.AccessLevel;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

@Getter
public class AdaptPlayer extends TickedObject {
  private static final long UPDATE_INTERVAL_MS = 1_000L;
  private static final long SAVE_INTERVAL_MS = 60_000L;
  private static final long SPATIAL_INTERVAL_MS = 500L;
  private static final long MIN_TICK_INTERVAL_MS = 50L;
  private static final long RETIRED_RETENTION_MS = 60_000L;
  private static final int SAVE_DEBOUNCE_TICKS = 20;
  private static final int MAX_SQL_CLAIM_ATTEMPTS = 4;
  private static final long SQL_CLAIM_TIMEOUT_SECONDS = 8L;
  private static final long REDIS_TRANSFER_WAIT_MILLIS = 250L;
  private static final long UPDATE_SALT = 0x5DEECE66DL;
  private static final long SAVE_SALT = 0x9E3779B97F4A7C15L;
  private static final Set<UUID> LOAD_FAILURE_GUARD = ConcurrentHashMap.newKeySet();

  private final Player player;
  private final boolean persistenceFenceRequired;
  private volatile PlayerData data;
  private final Set<String> dirtyStats = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean dirtyStatEvaluationScheduled = new AtomicBoolean();
  private final AtomicBoolean loginStatReconciliationComplete = new AtomicBoolean();
  private final AtomicBoolean saveScheduled = new AtomicBoolean();
  private final AtomicLong requestedSaveRevision = new AtomicLong();
  private final AtomicLong persistenceSequence = new AtomicLong();
  @Getter(AccessLevel.NONE)
  private Location positionScratch;
  private volatile FxPosition fxPosition;
  private Notifier not;
  private Notifier actionBarNotifier;
  private AdvancementHandler advancementHandler;
  private volatile long lastSeen;
  private long nextUpdateAt;
  private long nextSaveAt;
  private volatile long purgeGeneration;
  private volatile UUID persistenceOwnerToken;
  private volatile long persistenceEpoch = -1L;
  private volatile boolean pendingDataDeletion;
  private volatile boolean runtimeReady;

  public AdaptPlayer(Player p) {
    this(p, (LoadedPlayerData) null);
  }

  public AdaptPlayer(Player p, PlayerData prefetchedData) {
    this(p, prefetchedData == null ? null : LoadedPlayerData.inspected(prefetchedData));
  }

  AdaptPlayer(Player p, LoadedPlayerData prefetchedData) {
    super("players", p.getUniqueId().toString(), MIN_TICK_INTERVAL_MS);
    this.player = p;
    UUID playerId = p.getUniqueId();
    long loadGeneration = PlayerDataPurgeGuard.generation(playerId);
    boolean purged = PlayerDataPurgeGuard.isPurged(playerId);
    boolean sqlEnabled = AdaptConfig.get().getSql().isEnabled();
    persistenceFenceRequired = sqlEnabled;
    if (sqlEnabled && (prefetchedData == null || !prefetchedData.isOwned())) {
      throw new IllegalStateException("SQL-backed player runtime requires claimed persistence ownership for "
          + playerId);
    }
    LoadedPlayerData loadedState = purged && !sqlEnabled
        ? LoadedPlayerData.inspected(new PlayerData())
        : prefetchedData == null || (!sqlEnabled && requiresCanonicalLoad(playerId))
            ? LoadedPlayerData.inspected(loadPlayerData(playerId))
            : prefetchedData;
    long currentGeneration = PlayerDataPurgeGuard.generation(playerId);
    if (!sqlEnabled && (purged || PlayerDataPurgeGuard.isPurged(playerId)
        || currentGeneration != loadGeneration)) {
      loadedState = LoadedPlayerData.inspected(new PlayerData());
      currentGeneration = PlayerDataPurgeGuard.generation(playerId);
      if (PlayerDataPurgeGuard.clear(playerId)) {
        Adapt.verbose(() -> "Loading default player data for " + playerId
            + " (data was purged this session)");
      }
      LOAD_FAILURE_GUARD.remove(playerId);
    }
    if (LOAD_FAILURE_GUARD.contains(playerId)) {
      throw new IllegalStateException(
          "Player data is unavailable because its authoritative profile could not be loaded for "
              + playerId);
    }
    purgeGeneration = currentGeneration;
    data = loadedState.data();
    persistenceOwnerToken = loadedState.ownerToken();
    persistenceEpoch = loadedState.epoch();
    persistenceSequence.set(loadedState.sequence());
    data.bindRuntimeOwner(this);
    not = new Notifier(this);
    actionBarNotifier = new Notifier(this);
    advancementHandler = new AdvancementHandler(this);
    long now = M.ms();
    lastSeen = now;
    nextUpdateAt = now + staggerDelay(p.getUniqueId(), UPDATE_INTERVAL_MS, UPDATE_SALT);
    nextSaveAt = now + staggerDelay(p.getUniqueId(), SAVE_INTERVAL_MS, SAVE_SALT);
    setInterval(Math.max(MIN_TICK_INTERVAL_MS, Math.min(nextUpdateAt, nextSaveAt) - now));
    runtimeReady = true;
  }

  public void startRuntime() {
    activateRuntime();
  }

  public boolean isRuntimeReady() {
    return runtimeReady && (!persistenceFenceRequired || persistenceOwnerToken != null);
  }

  public static PlayerData loadPlayerData(UUID uuid) {
    long purgeGeneration = PlayerDataPurgeGuard.generation(uuid);
    File f = getPlayerDataFile(uuid);
    File deleteMarker = PlayerDataPersistenceQueue.deleteMarkerFile(f);
    PlayerDataPersistenceQueue persistenceQueue = Adapt.instance.getPlayerDataPersistenceQueue();
    boolean sqlEnabled = AdaptConfig.get().getSql().isEnabled();
    if (deleteMarker.exists() && !sqlEnabled) {
      PlayerDataPersistenceQueue.DeleteJournal journal = PlayerDataPersistenceQueue.readDeleteJournal(deleteMarker);
      if (!journal.valid()) {
        LOAD_FAILURE_GUARD.add(uuid);
        Adapt.warn("Player data deletion journal for " + uuid
            + " is invalid and was preserved for operator recovery: " + deleteMarker.getAbsolutePath());
        return new PlayerData();
      }
      if (persistenceQueue != null && persistenceQueue.hasPendingDelete(uuid)) {
        String pendingSuccessor = persistenceQueue.getPendingSave(uuid);
        if (pendingSuccessor != null) {
          try {
            PlayerData recovered = PlayerData.fromJson(pendingSuccessor);
            if (recovered == null) {
              throw new IllegalArgumentException("Pending delete successor JSON resolved to null");
            }
            LOAD_FAILURE_GUARD.remove(uuid);
            return recovered;
          } catch (Throwable error) {
            Adapt.warn("Failed to load pending player data after deletion for " + uuid + ": "
                + error.getClass().getSimpleName()
                + (error.getMessage() == null ? "" : " (" + error.getMessage() + ")"));
            Adapt.error(error);
          }
        }
      }
      if (journal.hasSuccessor()) {
        try {
          PlayerData recovered = PlayerData.fromJson(journal.successorJson());
          if (recovered == null) {
            throw new IllegalArgumentException("Delete-then-save journal JSON resolved to null");
          }
          if (persistenceQueue != null && persistenceQueue.resumeDeleteThenSave(uuid, f, journal)) {
            String activeSuccessor = persistenceQueue.getPendingSave(uuid);
            if (activeSuccessor != null) {
              PlayerData active = PlayerData.fromJson(activeSuccessor);
              if (active == null) {
                throw new IllegalArgumentException("Active delete successor JSON resolved to null");
              }
              LOAD_FAILURE_GUARD.remove(uuid);
              return active;
            }
            if (persistenceQueue.hasPendingDelete(uuid)) {
              LOAD_FAILURE_GUARD.remove(uuid);
              return new PlayerData();
            }
            return loadPlayerData(uuid);
          }
          Adapt.warn("Player data delete-then-save journal for " + uuid
              + " remains pending because the persistence queue is unavailable.");
          LOAD_FAILURE_GUARD.add(uuid);
          return new PlayerData();
        } catch (Throwable error) {
          Adapt.warn("Failed to load player data delete-then-save journal for " + uuid + ": "
              + error.getClass().getSimpleName()
              + (error.getMessage() == null ? "" : " (" + error.getMessage() + ")"));
          Adapt.error(error);
          LOAD_FAILURE_GUARD.add(uuid);
          return new PlayerData();
        }
      }
      if (persistenceQueue != null) {
        persistenceQueue.queueDelete(uuid, f);
      }
      LOAD_FAILURE_GUARD.remove(uuid);
      return new PlayerData();
    }

    if (PlayerDataPurgeGuard.isPurged(uuid)) {
      LOAD_FAILURE_GUARD.remove(uuid);
      return new PlayerData();
    }

    boolean loadFailed = false;
    if (persistenceQueue != null) {
      String pendingSave = persistenceQueue.getPendingSave(uuid);
      if (pendingSave != null) {
        try {
          PlayerData parsed = PlayerData.fromJson(pendingSave);
          if (parsed == null) {
            throw new IllegalArgumentException("Pending player data JSON resolved to null");
          }
          LOAD_FAILURE_GUARD.remove(uuid);
          return parsed;
        } catch (Throwable error) {
          loadFailed = true;
          LOAD_FAILURE_GUARD.add(uuid);
          Adapt.warn("Failed to parse pending player data for " + uuid + ": " + error.getClass().getSimpleName() + (error.getMessage() == null ? "" : " (" + error.getMessage() + ")"));
          Adapt.error(error);
        }
      }
      if (persistenceQueue.hasPendingDelete(uuid)) {
        LOAD_FAILURE_GUARD.remove(uuid);
        return new PlayerData();
      }
    }

    File recoveryFile = PlayerDataPersistenceQueue.sqlRecoveryFile(f);
    if (recoveryFile.exists() && !sqlEnabled) {
      try {
        PlayerDataPersistenceQueue.SqlRecoverySnapshot recovery =
            PlayerDataPersistenceQueue.readSqlRecovery(recoveryFile);
        if (!recovery.valid() || !uuid.equals(recovery.snapshot().playerId())) {
          throw incompatibleSqlRecovery(recoveryFile);
        }
        PlayerData recovered = PlayerData.fromJson(recovery.snapshot().json());
        if (recovered == null) {
          throw new IllegalArgumentException("SQL recovery player data JSON resolved to null");
        }
        LOAD_FAILURE_GUARD.remove(uuid);
        return recovered;
      } catch (Throwable error) {
        loadFailed = true;
        LOAD_FAILURE_GUARD.add(uuid);
        Adapt.warn("Failed to load SQL recovery player data for " + uuid + " from "
            + recoveryFile.getAbsolutePath() + ": " + error.getClass().getSimpleName()
            + (error.getMessage() == null ? "" : " (" + error.getMessage() + ")"));
        Adapt.error(error);
      }
    }

    if (sqlEnabled) {
      if (Adapt.instance.getSqlManager() != null) {
        SQLManager.FetchResult sqlResult = Adapt.instance.getSqlManager().fetchData(uuid);
        if (sqlResult == null || !sqlResult.successful()) {
          loadFailed = true;
          LOAD_FAILURE_GUARD.add(uuid);
          Adapt.warn("Player data load for " + uuid + " is guarded because SQL could not confirm whether the row exists.");
        } else if (sqlResult.found()) {
          try {
            PlayerData parsed = PlayerData.fromJson(sqlResult.data());
            if (parsed == null) {
              throw new IllegalArgumentException("SQL player data JSON resolved to null");
            }
            LOAD_FAILURE_GUARD.remove(uuid);
            return parsed;
          } catch (Throwable e) {
            loadFailed = true;
            LOAD_FAILURE_GUARD.add(uuid);
            Adapt.warn("Failed to parse SQL player data for " + uuid + ": " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"));
            Adapt.error(e);
          }
        }
      } else {
        loadFailed = true;
        LOAD_FAILURE_GUARD.add(uuid);
        Adapt.warn("Player data load for " + uuid + " is guarded because SQL is enabled without an active manager.");
      }
    }

    if (f.exists()) {
      try {
        String text = IO.readAll(f);
        PlayerData parsed = PlayerData.fromJson(text);
        if (parsed == null) {
          throw new IllegalArgumentException("Player data JSON resolved to null");
        }
        if (!loadFailed) {
          LOAD_FAILURE_GUARD.remove(uuid);
        }
        return parsed;
      } catch (Throwable e) {
        loadFailed = true;
        LOAD_FAILURE_GUARD.add(uuid);
        Adapt.warn("Failed to load player data for " + uuid + " from " + f.getAbsolutePath() + ": " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"));
        Adapt.error(e);
      }
    }

    if (!loadFailed) {
      LOAD_FAILURE_GUARD.remove(uuid);
    }
    return new PlayerData();
  }

  static LoadedPlayerData claimPlayerData(UUID uuid) {
    if (!AdaptConfig.get().getSql().isEnabled()) {
      PlayerData loaded = loadPlayerData(uuid);
      if (LOAD_FAILURE_GUARD.contains(uuid)) {
        throw new IllegalStateException(
            "Local player data could not be loaded safely for " + uuid);
      }
      return LoadedPlayerData.inspected(loaded);
    }

    SQLManager sqlManager = Adapt.instance.getSqlManager();
    if (sqlManager == null) {
      throw new IllegalStateException("SQL persistence is enabled without an active manager");
    }

    Throwable lastFailure = null;
    for (int attempt = 0; attempt < MAX_SQL_CLAIM_ATTEMPTS; attempt++) {
      try {
        SQLManager.ClaimResult claim = sqlManager.claimData(uuid)
            .get(SQL_CLAIM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (claim == null || !claim.successful() || claim.newToken() == null) {
          lastFailure = new IllegalStateException("SQL manager rejected the ownership claim");
          continue;
        }
        LoadedPlayerData adopted = adoptClaimedPlayerData(uuid, sqlManager, claim);
        if (adopted != null) {
          PlayerDataPurgeGuard.clear(uuid);
          LOAD_FAILURE_GUARD.remove(uuid);
          return adopted;
        }
        lastFailure = new IllegalStateException("SQL ownership changed before adoption completed");
      } catch (InterruptedException error) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while claiming SQL player data for " + uuid, error);
      } catch (TimeoutException error) {
        lastFailure = error;
      } catch (IllegalStateException error) {
        LOAD_FAILURE_GUARD.add(uuid);
        throw error;
      } catch (Exception error) {
        lastFailure = error;
      }
    }

    LOAD_FAILURE_GUARD.add(uuid);
    throw new IllegalStateException("Failed to claim SQL player data for " + uuid,
        lastFailure);
  }

  private static LoadedPlayerData adoptClaimedPlayerData(UUID uuid, SQLManager sqlManager,
                                                          SQLManager.ClaimResult claim)
      throws Exception {
    File localFile = getPlayerDataFile(uuid);
    rejectIncompatibleSqlDeleteJournal(localFile);
    SQLManager.SqlToken predecessor = claim.effectivePredecessor();
    ArrayList<FencedPlayerSnapshot> candidates = new ArrayList<>();
    PlayerDataPersistenceQueue queue = Adapt.instance.getPlayerDataPersistenceQueue();
    if (queue != null) {
      FencedPlayerSnapshot pending = queue.getPendingFencedSnapshot(uuid);
      if (pending != null) {
        candidates.add(pending);
      }
    }

    File recoveryFile = PlayerDataPersistenceQueue.sqlRecoveryFile(localFile);
    if (recoveryFile.exists()) {
      PlayerDataPersistenceQueue.SqlRecoverySnapshot recovery =
          PlayerDataPersistenceQueue.readSqlRecovery(recoveryFile);
      if (!recovery.valid() || !uuid.equals(recovery.snapshot().playerId())) {
        throw incompatibleSqlRecovery(recoveryFile);
      }
      candidates.add(recovery.snapshot());
    }

    if (predecessor != null && Adapt.instance.getRedisSync() != null) {
      collectRedisTransfer(candidates, uuid, predecessor, REDIS_TRANSFER_WAIT_MILLIS);
    }

    String selectedJson;
    if (predecessor == null) {
      selectedJson = claim.committedData();
      if (selectedJson == null) {
        selectedJson = readInitialSqlSeed(localFile);
      }
    } else {
      FencedSnapshotSelector.Selection selection = FencedSnapshotSelector.select(
          uuid,
          predecessor.ownerToken(),
          predecessor.epoch(),
          claim.previousCommittedSequence(),
          claim.committedData(),
          candidates
      );
      selectedJson = selection.json();
    }

    PlayerData selectedData = selectedJson == null ? new PlayerData() : PlayerData.fromJson(selectedJson);
    if (selectedData == null) {
      throw new IllegalStateException("Claimed SQL player data resolved to null for " + uuid);
    }
    String canonicalJson = selectedData.toJson(true);
    SQLManager.SqlToken newToken = claim.newToken();
    SQLManager.FencedWriteResult adoption = sqlManager.adoptFencedData(
        new SQLManager.FencedDataUpdate(
            uuid,
            newToken.ownerToken().toString(),
            newToken.epoch(),
            1L,
            canonicalJson
        )
    );
    if (adoption == null
        || adoption.status() == SQLManager.FencedWriteStatus.FENCED
        || adoption.status() == SQLManager.FencedWriteStatus.FAILED
        || adoption.status() == SQLManager.FencedWriteStatus.SUPERSEDED) {
      return null;
    }

    if (predecessor != null) {
      if (queue != null) {
        queue.discardPredecessorSaves(uuid, predecessor.ownerToken(), predecessor.epoch());
      }
      PlayerDataPersistenceQueue.deleteAdoptedRecovery(
          recoveryFile, predecessor.ownerToken(), predecessor.epoch());
      acknowledgeAdoptedTransfer(uuid, predecessor.ownerToken(), predecessor.epoch());
    }
    return LoadedPlayerData.owned(
        selectedData, newToken.ownerToken(), newToken.epoch(), 1L);
  }

  static void collectRedisTransfer(List<FencedPlayerSnapshot> candidates, UUID uuid,
                                   SQLManager.SqlToken predecessor, long timeoutMillis)
      throws Exception {
    try {
      CompletableFuture<Optional<FencedPlayerSnapshot>> transfer =
          Adapt.instance.getRedisSync().awaitTransfers(
              uuid, predecessor.ownerToken(), predecessor.epoch(), timeoutMillis);
      Optional<FencedPlayerSnapshot> snapshot = transfer.get(
          Math.max(1_000L, timeoutMillis + 500L), TimeUnit.MILLISECONDS);
      snapshot.ifPresent(candidates::add);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      throw error;
    } catch (ExecutionException | TimeoutException error) {
      throw new IllegalStateException(
          "Redis player-data transfer could not be verified for " + uuid, error);
    }
  }

  static void acknowledgeAdoptedTransfer(UUID playerId, UUID predecessorToken,
                                         long predecessorEpoch) {
    RedisSync redisSync = Adapt.instance.getRedisSync();
    if (redisSync != null) {
      redisSync.acknowledgeTransfer(playerId, predecessorToken, predecessorEpoch);
    }
  }

  private static String readInitialSqlSeed(File localFile) throws IOException {
    rejectIncompatibleSqlDeleteJournal(localFile);
    return localFile.exists() ? IO.readAll(localFile) : null;
  }

  static void rejectIncompatibleSqlDeleteJournal(File localFile) throws IOException {
    File deleteMarker = PlayerDataPersistenceQueue.deleteMarkerFile(localFile);
    if (deleteMarker.exists()) {
      throw new IOException("Incompatible pre-fence deletion journal was preserved at "
          + deleteMarker.getAbsolutePath()
          + ". Stop the server, reconcile the SQL row from backup, delete only this journal,"
          + " then restart so ADAPT can regenerate fenced state.");
    }
  }

  private static IllegalStateException incompatibleSqlRecovery(File recoveryFile) {
    return new IllegalStateException("Incompatible SQL recovery file was preserved at "
        + recoveryFile.getAbsolutePath()
        + ". Stop the server, reconcile the player row from SQL or backup, delete only this file,"
        + " then restart so ADAPT can regenerate a fenced recovery envelope.");
  }

  static File getPlayerDataFile(UUID uuid) {
    return new File(Adapt.instance.getDataFolder("data", "players"), uuid.toString() + ".json");
  }

  static boolean requiresCanonicalLoad(UUID uuid) {
    File playerFile = getPlayerDataFile(uuid);
    if (PlayerDataPersistenceQueue.deleteMarkerFile(playerFile).exists()
        || PlayerDataPersistenceQueue.sqlRecoveryFile(playerFile).exists()) {
      return true;
    }
    PlayerDataPersistenceQueue persistenceQueue = Adapt.instance.getPlayerDataPersistenceQueue();
    return persistenceQueue != null
        && (persistenceQueue.hasPendingDelete(uuid) || persistenceQueue.getPendingSave(uuid) != null);
  }

  static void forgetLoadFailure(UUID uuid) {
    LOAD_FAILURE_GUARD.remove(uuid);
  }

  static boolean hasLoadFailure(UUID uuid) {
    return LOAD_FAILURE_GUARD.contains(uuid);
  }

  /**
   * Swaps the live data instance. The previous instance is unbound first so any lingering reference
   * to it stops driving stat trackers, and the replacement is bound before it becomes visible.
   */
  PlayerData replaceData(PlayerData replacement) {
    PlayerData previous = data;
    if (replacement == null || replacement == previous) {
      return previous;
    }

    previous.unbindRuntimeOwner(this);
    dirtyStats.clear();
    replacement.bindRuntimeOwner(this);
    data = replacement;
    return previous;
  }

  public boolean canConsumeFood(double cost, int minFood) {
    return (player.getFoodLevel() + player.getSaturation()) - minFood > cost;
  }

  public boolean consumeFood(double cost, int minFood) {
    if (!canConsumeFood(cost, minFood)) {
      return false;
    }

    applyFoodCharge(cost);
    return true;
  }

  public void applyFoodCharge(double cost) {
    FoodCharge charge = chargeFood(player.getFoodLevel(), player.getSaturation(), cost);
    player.setFoodLevel(charge.food());
    player.setSaturation((float) charge.saturation());
  }

  static FoodCharge chargeFood(int food, double saturation, double cost) {
    double remainingSaturation = Double.isFinite(saturation) ? Math.max(0D, saturation) : 0D;
    if (!Double.isFinite(cost) || cost <= 0D) {
      return new FoodCharge(food, remainingSaturation);
    }

    double remainingCost = cost;
    double fromSaturation = Math.min(remainingSaturation, remainingCost);
    remainingSaturation -= fromSaturation;
    remainingCost -= fromSaturation;

    int wholeFood = (int) Math.floor(remainingCost);
    if (wholeFood > 0) {
      remainingCost -= wholeFood;
    }

    if (remainingCost > 0D) {
      remainingSaturation -= Math.min(remainingSaturation, remainingCost);
    }

    return new FoodCharge(Math.max(0, food - wholeFood), remainingSaturation);
  }

  public boolean isBusy() {
    return not.isBusy();
  }

  public PlayerSkillLine getSkillLine(String l) {
    return getData().getSkillLine(l);
  }

  private void save() {
    UUID uuid = player.getUniqueId();
    File playerDataFile = getPlayerDataFile(uuid);

    if (pendingDataDeletion || PlayerDataPurgeGuard.isPurged(uuid)) {
      if (!AdaptConfig.get().getSql().isEnabled()) {
        queueDelete(uuid, playerDataFile);
      }
      return;
    }

    if (!PlayerDataPurgeGuard.allowsSave(uuid, purgeGeneration)) {
      return;
    }

    if (LOAD_FAILURE_GUARD.contains(uuid)) {
      Adapt.warn("Skipping save for " + uuid + " because player data failed to load earlier. Existing file is preserved.");
      return;
    }

    PlayerDataPersistenceQueue queue = Adapt.instance.getPlayerDataPersistenceQueue();
    if (AdaptConfig.get().getSql().isEnabled()) {
      FencedPlayerSnapshot snapshot = capturePersistenceSnapshot(false);
      if (snapshot == null) {
        Adapt.warn("Skipping unfenced SQL player data save for " + uuid);
        return;
      }
      if (queue == null) {
        Adapt.warn("Skipping SQL player data save for " + uuid
            + " because the persistence queue is unavailable");
        return;
      }
      queue.queueSave(snapshot, playerDataFile, purgeGeneration);
      return;
    }

    String json = data.toJson(false);
    if (queue != null) {
      queue.queueSave(uuid, json, playerDataFile, purgeGeneration);
      return;
    }
    J.attempt(() -> IO.writeAll(playerDataFile, json));
  }

  public void saveNow() {
    save();
  }

  public synchronized FencedPlayerSnapshot captureFencedSnapshot() {
    return capturePersistenceSnapshot(true);
  }

  private synchronized FencedPlayerSnapshot capturePersistenceSnapshot(boolean requireRuntime) {
    if ((requireRuntime && !runtimeReady)
        || persistenceOwnerToken == null || persistenceEpoch < 1L) {
      return null;
    }
    long sequence = persistenceSequence.incrementAndGet();
    return new FencedPlayerSnapshot(
        player.getUniqueId(),
        persistenceOwnerToken,
        persistenceEpoch,
        sequence,
        data.toJson(true)
    );
  }

  synchronized void installPersistenceFence(UUID ownerToken, long epoch, long sequence) {
    persistenceOwnerToken = ownerToken;
    persistenceEpoch = epoch;
    persistenceSequence.set(sequence);
  }

  synchronized boolean installNewerPersistenceFence(UUID ownerToken, long epoch, long sequence) {
    if (ownerToken == null || epoch < 1L || sequence < 0L || epoch <= persistenceEpoch) {
      return false;
    }
    installPersistenceFence(ownerToken, epoch, sequence);
    return true;
  }

  synchronized boolean ownsPersistenceFence(UUID ownerToken, long epoch) {
    return persistenceOwnerToken != null
        && persistenceOwnerToken.equals(ownerToken)
        && persistenceEpoch == epoch;
  }

  synchronized long persistenceFenceEpoch() {
    return persistenceEpoch;
  }

  synchronized boolean invalidatePersistenceFence(UUID ownerToken, long epoch) {
    if (!ownsPersistenceFence(ownerToken, epoch)) {
      return false;
    }
    persistenceOwnerToken = null;
    persistenceEpoch = -1L;
    return true;
  }

  boolean persistResetNow(PlayerData replacement) {
    UUID uuid = player.getUniqueId();
    if (replacement == null || pendingDataDeletion
        || !PlayerDataPurgeGuard.allowsSave(uuid, purgeGeneration)
        || AdaptConfig.get().getSql().isEnabled()) {
      return false;
    }

    String json = replacement.toJson(false);
    File playerDataFile = getPlayerDataFile(uuid);
    PlayerDataPersistenceQueue queue = Adapt.instance.getPlayerDataPersistenceQueue();
    if (queue != null) {
      return queue.queueReset(uuid, json, playerDataFile, purgeGeneration);
    }

    try {
      PlayerDataPersistenceQueue.writeSnapshot(playerDataFile, json);
      return true;
    } catch (Throwable error) {
      Adapt.warn("Failed to persist reset player data for " + uuid + ": " + error.getMessage());
      Adapt.error(error);
      return false;
    }
  }

  public void requestSave() {
    if (!isRuntimeReady() || pendingDataDeletion) {
      return;
    }
    requestedSaveRevision.incrementAndGet();
    scheduleRequestedSave();
  }

  private void scheduleRequestedSave() {
    if (!saveScheduled.compareAndSet(false, true)) {
      return;
    }
    if (!J.runEntity(player, this::drainRequestedSave, SAVE_DEBOUNCE_TICKS)) {
      saveScheduled.set(false);
    }
  }

  private void drainRequestedSave() {
    long revision = requestedSaveRevision.get();
    if (runtimeReady && !pendingDataDeletion) {
      save();
    }
    saveScheduled.set(false);
    if (runtimeReady && !pendingDataDeletion && requestedSaveRevision.get() != revision) {
      scheduleRequestedSave();
    }
  }

  @Override
  public synchronized void unregister() {
    if (!runtimeReady) {
      super.unregister();
      return;
    }
    retireRuntime();
    save();
  }

  synchronized FencedPlayerSnapshot retireForTransfer(UUID ownerToken, long epoch) {
    if (!runtimeReady || pendingDataDeletion || !ownsPersistenceFence(ownerToken, epoch)) {
      return null;
    }
    retireRuntime();
    FencedPlayerSnapshot snapshot = capturePersistenceSnapshot(false);
    persistenceOwnerToken = null;
    persistenceEpoch = -1L;
    return snapshot;
  }

  synchronized boolean retireForRemoteFenceAdvance(long epoch) {
    if (!runtimeReady || epoch <= persistenceEpoch) {
      return false;
    }
    retireRuntime();
    persistenceOwnerToken = null;
    persistenceEpoch = -1L;
    return true;
  }

  synchronized boolean invalidateForRemoteFenceAdvance(long epoch) {
    if (epoch <= persistenceEpoch) {
      return false;
    }
    persistenceOwnerToken = null;
    persistenceEpoch = -1L;
    return true;
  }

  private void retireRuntime() {
    lastSeen = M.ms();
    runtimeReady = false;
    data.unbindRuntimeOwner(this);
    data.stripRegionGrantedAdaptations();
    data.setRegionPowerBonus(0);
    dirtyStats.clear();
    super.unregister();
    not.unregister();
    actionBarNotifier.unregister();
  }

  /**
   * Purges the persisted copy of this player's data and stops this instance from ever writing again.
   * Used for offline resets; online resets replace the live data instead so the player keeps playing.
   */
  void purge(UUID uuid) {
    pendingDataDeletion = true;
    purgeStoredData(uuid);
  }

  synchronized void retireAfterFencedPurge(UUID uuid, long generation) {
    pendingDataDeletion = true;
    persistenceOwnerToken = null;
    persistenceEpoch = -1L;
    purgeGeneration = generation;
    LOAD_FAILURE_GUARD.remove(uuid);
  }

  void adoptPurgeGeneration(long generation) {
    purgeGeneration = generation;
  }

  static void purgeStoredData(UUID uuid) {
    PlayerDataPurgeGuard.mark(uuid);
    LOAD_FAILURE_GUARD.remove(uuid);
    File local = getPlayerDataFile(uuid);
    Adapt.warn("Purging player data: " + local.getAbsolutePath());
    queueDelete(uuid, local);
  }

  public boolean shouldUnload() {
    return shouldUnload(M.ms(), runtimeReady);
  }

  boolean shouldUnload(long now, boolean onlineMembership) {
    if (onlineMembership) {
      lastSeen = now;
    }
    if (runtimeReady) {
      return false;
    }

    return retiredRetentionExpired(lastSeen, now);
  }

  static boolean retiredRetentionExpired(long retiredAt, long now) {
    long deadline = retiredAt > Long.MAX_VALUE - RETIRED_RETENTION_MS
        ? Long.MAX_VALUE
        : retiredAt + RETIRED_RETENTION_MS;
    return now > deadline;
  }

  @Override
  public void onTick() {
    if (!isRuntimeReady()) {
      return;
    }

    long now = M.ms();
    Location playerLocation = null;
    if (now >= nextUpdateAt) {
      if (data.isEffectsEnabled() || RegionPolicyService.isActive()) {
        playerLocation = capturePosition();
      }
      RegionGrantRuntime.refresh(this, playerLocation);
      getData().update(this);
      AdaptPlaceholders.get().publishPlayer(this);
      nextUpdateAt = now + UPDATE_INTERVAL_MS;
    }

    if (now >= nextSaveAt) {
      save();
      nextSaveAt = now + SAVE_INTERVAL_MS;
    }

    if (getServer().hasSpatialTickets()) {
      if (playerLocation == null) {
        playerLocation = capturePosition();
      }
      getServer().takeSpatial(this, playerLocation);
    }

    setInterval(nextTickDelay(now));
  }

  static long staggerDelay(UUID playerId, long interval, long salt) {
    if (playerId == null || interval <= 1L) {
      return 0L;
    }

    long mixed = playerId.getMostSignificantBits() ^ Long.rotateLeft(playerId.getLeastSignificantBits(), 17) ^ salt;
    mixed ^= mixed >>> 33;
    mixed *= 0xff51afd7ed558ccdL;
    mixed ^= mixed >>> 33;
    return Math.floorMod(mixed, interval);
  }

  public boolean hasAdaptation(String id) {
    if (id == null || id.isBlank()) {
      return false;
    }

    int separator = id.indexOf('-');
    if (separator <= 0) {
      return false;
    }

    String skillLine = id.substring(0, separator);
    if (skillLine.isBlank()) {
      return false;
    }

    PlayerSkillLine line = getData().getSkillLineNullable(skillLine);
    if (line == null) {
      return false;
    }

    PlayerAdaptation adaptation = line.getAdaptation(id);
    return adaptation != null && adaptation.getLevel() > 0;
  }

  public void giveXPToRecents(AdaptPlayer p, double xpGained, int ms) {
    for (PlayerSkillLine i : p.getData().getSkillLines().v()) {
      if (M.ms() - i.getLast() < ms) {
        i.giveXP(not, xpGained);
      }
    }
  }

  public void giveXPToRandom(AdaptPlayer p, double xpGained) {
    p.getData().getSkillLines().v().getRandom().giveXP(p.getNot(), xpGained);
  }

  public void boostXPToRandom(AdaptPlayer p, double boost, long ms) {
    p.getData().getSkillLines().v().getRandom().boost(boost, ms);
  }

  public void boostXPToRecents(double boost, long ms) {
    for (PlayerSkillLine i : this.getData().getSkillLines().v()) {
      if (M.ms() - i.getLast() < ms) {
        i.boost(boost, ms);
      }
    }
  }

  public void loggedIn() {
    lastSeen = M.ms();
    if (data.isEffectsEnabled()) {
      J.runEntity(player, () -> {
        if (runtimeReady) {
          capturePosition();
        }
      });
    }
    if (loginStatReconciliationComplete.compareAndSet(false, true)) {
      reconcileStatTrackers();
    }
    if (AdaptConfig.get().isLoginBonus()) {
      long timeGone = M.ms() - getData().getLastLogin();
      boolean first = getData().getLastLogin() == 0;
      getData().setLastLogin(M.ms());
      long boostTime = (long) Math.min(timeGone / 12D, TimeUnit.HOURS.toMillis(1));
      if (boostTime < TimeUnit.MINUTES.toMillis(5)) {
        return;
      }
      double boostAmount = M.lerp(0.1, 0.25, (double) boostTime / (double) TimeUnit.HOURS.toMillis(1));
      getData().globalXPMultiplier(boostAmount, boostTime);
      if (!AdaptConfig.get().isWelcomeMessage())
        return;
      getNot().queue(AdvancementNotification.builder()
          .title(first ? AdaptLanguage.text(SnippetsMessages.GUI_WELCOME) : AdaptLanguage.text(SnippetsMessages.GUI_WELCOME_BACK))
          .description(AdaptLanguage.text(
              RuntimeMessages.XP_BONUS,
              trusted("percent", C.GREEN + Form.pc(boostAmount, 0) + C.GRAY),
              trusted("duration", C.AQUA + Form.duration(boostTime, 0))
          ))
          .model(CustomModel.get(Material.DIAMOND, "snippets", "gui", first ? "welcome" : "welcomeback"))
          .build());
    }
  }

  public boolean hasSkill(Skill s) {
    if (s == null) {
      return false;
    }

    PlayerSkillLine line = getData().getSkillLine(s.getName());
    return line != null && line.getXp() > 1;
  }

  void onStatChanged(String stat) {
    if (!runtimeReady || !AdaptConfig.get().isAdvancements()) {
      return;
    }

    boolean foliaThreading = J.isFoliaThreading();
    if ((!foliaThreading && J.isPrimaryThread())
        || (foliaThreading && J.isOwnedByCurrentRegion(player))) {
      evaluateStatTrackers(stat);
      return;
    }

    dirtyStats.add(stat);
    if (!dirtyStatEvaluationScheduled.compareAndSet(false, true)) {
      return;
    }

    if (!J.runEntity(player, this::drainDirtyStats)) {
      dirtyStatEvaluationScheduled.set(false);
    }
  }

  public void reconcileStatTrackers() {
    if (!runtimeReady || !AdaptConfig.get().isAdvancements()) {
      return;
    }

    J.runEntity(player, () -> {
      if (runtimeReady) {
        getSkillRegistry().reconcileStatTrackers(this);
      }
    });
  }

  private void drainDirtyStats() {
    if (!runtimeReady) {
      dirtyStats.clear();
      dirtyStatEvaluationScheduled.set(false);
      return;
    }

    while (true) {
      for (String stat : dirtyStats) {
        if (dirtyStats.remove(stat)) {
          evaluateStatTrackers(stat);
        }
      }

      dirtyStatEvaluationScheduled.set(false);
      if (dirtyStats.isEmpty() || !dirtyStatEvaluationScheduled.compareAndSet(false, true)) {
        return;
      }
    }
  }

  private void evaluateStatTrackers(String stat) {
    if (runtimeReady) {
      getSkillRegistry().evaluateStatTrackers(this, stat);
    }
  }

  private SkillRegistry getSkillRegistry() {
    return getServer().getSkillRegistry();
  }

  private static void queueDelete(UUID uuid, File localFile) {
    PlayerDataPersistenceQueue queue = Adapt.instance.getPlayerDataPersistenceQueue();
    if (queue != null) {
      queue.queueDelete(uuid, localFile);
      return;
    }

    if (localFile.exists() && !localFile.delete()) {
      Adapt.verbose("Failed to delete local player data file " + localFile.getAbsolutePath());
    }
  }

  private long nextTickDelay(long now) {
    long nextDeadline = Math.min(nextUpdateAt, nextSaveAt);
    if (getServer().hasSpatialTickets()) {
      nextDeadline = Math.min(nextDeadline, now + SPATIAL_INTERVAL_MS);
    }
    return Math.max(MIN_TICK_INTERVAL_MS, nextDeadline - now);
  }

  private Location capturePosition() {
    Location location = positionScratch == null ? player.getLocation() : player.getLocation(positionScratch);
    positionScratch = location;
    fxPosition = new FxPosition(location.getWorld(), location.getX(), location.getY(), location.getZ());
    return location;
  }

  @Override
  protected Entity getTickOwner() {
    return player;
  }

  @Override
  public final boolean equals(Object obj) {
    return this == obj;
  }

  @Override
  public final int hashCode() {
    return System.identityHashCode(this);
  }

  public record FxPosition(World world, double x, double y, double z) {
  }

  public record FoodCharge(int food, double saturation) {
  }
}

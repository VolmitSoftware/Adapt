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

import art.arcane.adapt.Adapt;
import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.api.fx.ViewerGlowCoordinator;
import art.arcane.adapt.api.minion.MinionBurden;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.notification.AdvancementNotification;
import art.arcane.adapt.api.notification.SoundNotification;
import art.arcane.adapt.api.potion.AdaptPotionRegistry;
import art.arcane.adapt.api.recipe.AdaptRecipeBook;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.SpatialXP;
import art.arcane.adapt.api.xp.XpNovelty;
import art.arcane.adapt.api.xp.XPMultiplier;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.service.MutationSVC;
import art.arcane.adapt.content.item.ExperienceOrb;
import art.arcane.adapt.content.item.KnowledgeOrb;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.papi.AdaptPlaceholders;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.io.SQLManager;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.project.redis.RedisSync;
import art.arcane.adapt.util.project.redis.codec.ResetNotice;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.inventorygui.UIWindow;
import art.arcane.volmlib.util.math.M;
import art.arcane.volmlib.util.scheduling.SchedulerUtils;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class AdaptServer extends TickedObject {
  private static final long POTION_RETENTION_TIMEOUT_MILLIS = 5_000L;
  private static final int PLAYER_OPERATION_LOCK_COUNT = 1_024;
  private static final int RESET_RACE_CLAIM_ATTEMPTS = 2;
  private static final int ONLINE_PROFILE_RECOVERY_ATTEMPTS = 4;
  private static final int ONLINE_PROFILE_RECOVERY_BASE_DELAY_TICKS = 20;
  private static final int ONLINE_PROFILE_RECOVERY_JITTER_TICKS = 20;

  private final ReentrantLock clearLock = new ReentrantLock();
  private final Object[] playerOperationLocks = createPlayerOperationLocks();
  private final ExecutorService playerClaimExecutor = Executors.newVirtualThreadPerTaskExecutor();
  private final Map<UUID, AdaptPlayer> players = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, CompletableFuture<LoadedPlayerData>> playerDataClaims =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, Player> runtimeSessions = new ConcurrentHashMap<>();
  private final Map<UUID, AdaptPlayer> onlineAdaptPlayers = new ConcurrentHashMap<>();
  private final Map<UUID, Set<String>> learnedAdaptationsByPlayer = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Integer>> learnedAdaptationLevelsByPlayer = new ConcurrentHashMap<>();
  private final Map<String, Set<UUID>> playersByLearnedAdaptation = new ConcurrentHashMap<>();
  private final Map<String, List<AdaptPlayer>> learnedAdaptPlayerSnapshots = new ConcurrentHashMap<>();
  private final Set<UUID> recipeBookSyncScheduled = ConcurrentHashMap.newKeySet();
  private final Set<UUID> onlineProfileClaims = ConcurrentHashMap.newKeySet();
  private final Set<UUID> unavailableOnlinePlayers = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean spatialFailureReported = new AtomicBoolean(false);
  private final SpatialXpLedger spatialXpLedger = new SpatialXpLedger();
  private final AtomicBoolean onlineSnapshotRefreshScheduled = new AtomicBoolean(false);
  private final AtomicBoolean acceptingPlayerClaims = new AtomicBoolean(true);
  private final AtomicLong onlineMembershipRevision = new AtomicLong();
  private final AtomicLong learnerIndexRevision = new AtomicLong();
  private final Cache<UUID, LoadedPlayerData> prefetchedPlayerData = Caffeine.newBuilder()
      .expireAfterWrite(30, TimeUnit.SECONDS)
      .maximumSize(2048)
      .build();
  private final Cache<UUID, Long> resetFenceEpochs = Caffeine.newBuilder()
      .expireAfterWrite(1, TimeUnit.MINUTES)
      .maximumSize(2048)
      .build();
  private final Cache<UUID, Boolean> profileFailureReported = Caffeine.newBuilder()
      .expireAfterWrite(10, TimeUnit.MINUTES)
      .maximumSize(8192)
      .build();
  private final boolean sqlPersistenceEnabled;
  @Getter
  private final SkillRegistry skillRegistry = new SkillRegistry();
  @Getter
  private volatile List<Player> onlinePlayerSnapshot = List.of();
  @Getter
  private volatile List<AdaptPlayer> onlineAdaptPlayerSnapshot = List.of();
  @Getter
  private AdaptServerData data = new AdaptServerData();

  public AdaptServer() {
    super("core", UUID.randomUUID().toString(), 1000);
    sqlPersistenceEnabled = AdaptConfig.get().getSql().isEnabled();
    load();
  }

  public int getLearnedAdaptationCount() {
    return playersByLearnedAdaptation.size();
  }

  public long getLearnerIndexRevision() {
    return learnerIndexRevision.get();
  }

  public int getSpatialTicketCount() {
    return spatialXpLedger.size();
  }

  public synchronized void startRuntime() {
    if (isRuntimeRegistered()) {
      return;
    }
    skillRegistry.startRuntime();
    activateRuntime();
    for (Player player : Bukkit.getOnlinePlayers()) {
      runtimeSessions.put(player.getUniqueId(), player);
      unavailableOnlinePlayers.add(player.getUniqueId());
      claimOnlinePlayer(player.getUniqueId(), 0);
    }
    rebuildOnlinePlayerSnapshots();
  }

  private void claimOnlinePlayer(UUID playerId, int recoveryAttempt) {
    if (!acceptingPlayerClaims.get() || !onlineProfileClaims.add(playerId)) {
      return;
    }
    try {
      playerClaimExecutor.execute(() -> {
        try {
          claimAndCacheOnlinePlayerData(playerId);
          onlineProfileClaims.remove(playerId);
          activateClaimedOnlinePlayer(playerId, recoveryAttempt);
        } catch (Throwable error) {
          onlineProfileClaims.remove(playerId);
          if (acceptingPlayerClaims.get()) {
            reportProfileFailure(playerId, "claim", error);
            scheduleOnlineProfileRecovery(playerId, recoveryAttempt + 1);
          }
        }
      });
    } catch (RejectedExecutionException error) {
      onlineProfileClaims.remove(playerId);
      if (acceptingPlayerClaims.get()) {
        reportProfileFailure(playerId, "claim dispatch", error);
      }
    }
  }

  private void claimAndCacheOnlinePlayerData(UUID playerId) throws Exception {
    awaitAndCachePlayerDataClaim(playerId);
  }

  private void activateClaimedOnlinePlayer(UUID playerId, int recoveryAttempt) {
    Player player = runtimeSessions.get(playerId);
    if (player == null || getOnlineAdaptPlayer(playerId) != null) {
      return;
    }
    if (!J.runEntity(player, () -> {
      if (acceptingPlayerClaims.get() && runtimeSessions.get(playerId) == player
          && player.isOnline() && Bukkit.getPlayer(playerId) == player
          && getOnlineAdaptPlayer(playerId) == null
          && !join(player, true)) {
        scheduleOnlineProfileRecovery(playerId, recoveryAttempt + 1);
      }
    })) {
      prefetchedPlayerData.invalidate(playerId);
      scheduleOnlineProfileRecovery(playerId, recoveryAttempt + 1);
    }
  }

  private void scheduleOnlineProfileRecovery(UUID playerId, int recoveryAttempt) {
    if (!acceptingPlayerClaims.get()
        || recoveryAttempt >= ONLINE_PROFILE_RECOVERY_ATTEMPTS) {
      return;
    }
    Player player = runtimeSessions.get(playerId);
    if (player == null) {
      return;
    }
    int delayTicks = onlineProfileRecoveryDelayTicks(playerId, recoveryAttempt);
    if (!J.runEntity(player, () -> {
      if (acceptingPlayerClaims.get() && runtimeSessions.get(playerId) == player
          && player.isOnline() && Bukkit.getPlayer(playerId) == player
          && onlineAdaptPlayers.get(playerId) == null) {
        claimOnlinePlayer(playerId, recoveryAttempt);
      }
    }, delayTicks)) {
      reportProfileFailure(playerId, "recovery dispatch", new IllegalStateException(
          "Player owner scheduler rejected profile recovery"));
    }
  }

  static int onlineProfileRecoveryDelayTicks(UUID playerId, int recoveryAttempt) {
    int boundedAttempt = Math.max(0, Math.min(recoveryAttempt, 3));
    int backoff = ONLINE_PROFILE_RECOVERY_BASE_DELAY_TICKS << boundedAttempt;
    int jitter = Math.floorMod(playerId.hashCode(), ONLINE_PROFILE_RECOVERY_JITTER_TICKS);
    return backoff + jitter;
  }

  static String profileReadyMessage(
      String playerName, UUID playerId, boolean sqlPersistenceEnabled) {
    return "Player profile ready for " + playerName + " (" + playerId + ") using "
        + (sqlPersistenceEnabled ? "SQL" : "local JSON") + " storage.";
  }

  private void reportProfileReady(Player player) {
    Adapt.verbose(() -> profileReadyMessage(
        player.getName(), player.getUniqueId(), sqlPersistenceEnabled));
  }

  private void reportProfileFailure(UUID playerId, String phase, Throwable error) {
    if (profileFailureReported.asMap().putIfAbsent(playerId, Boolean.TRUE) != null) {
      Adapt.verbose(() -> "Adapt profile remains unavailable for " + playerId + " after " + phase
          + " failure: " + error.getClass().getSimpleName()
          + (error.getMessage() == null ? "" : " (" + error.getMessage() + ")"));
      return;
    }
    Adapt.warn("Adapt profile is unavailable for " + playerId + " after " + phase
        + " failure; Minecraft login remains allowed: " + error.getClass().getSimpleName()
        + (error.getMessage() == null ? "" : " (" + error.getMessage() + ")"), error);
  }

  public void offer(SpatialXP xp) {
    spatialXpLedger.offer(xp, M.ms());
  }

  public void takeSpatial(AdaptPlayer p, Location playerLocation) {
    if (spatialXpLedger.size() == 0 || p == null || playerLocation == null) {
      return;
    }

    try {
      SpatialXpLedger.Claim claim = spatialXpLedger.claim(playerLocation, M.ms());
      if (claim != null) {
        XP.xp(p, claim.skill(), claim.xp());
      }
    } catch (Throwable error) {
      if (spatialFailureReported.compareAndSet(false, true)) {
        Adapt.warn("Spatial XP processing failed; further spatial failures will be suppressed until reload.");
        Adapt.error(error);
      }
    }
  }

  boolean hasSpatialTickets() {
    return spatialXpLedger.size() > 0;
  }

  public void join(Player p) {
    if (!join(p, true)) {
      claimOnlinePlayer(p.getUniqueId(), 0);
    }
  }

  private boolean join(Player p, boolean refreshSnapshots) {
    UUID playerId = p.getUniqueId();
    runtimeSessions.put(playerId, p);
    unavailableOnlinePlayers.add(playerId);
    AdaptPlaceholders.get().evictNow(playerId);
    boolean activated = false;
    try {
      synchronized (playerOperationLock(playerId)) {
        activated = joinSerialized(p, refreshSnapshots);
      }
    } catch (Throwable error) {
      prefetchedPlayerData.invalidate(playerId);
      reportProfileFailure(playerId, "runtime activation", error);
    }
    return activated;
  }

  private boolean joinSerialized(Player p, boolean refreshSnapshots) {
    // A retired AdaptPlayer lingers in the map for a minute after quit, and so does a prefetched
    // snapshot. Reusing either would resurrect data that was purged while they sat there, so a
    // purged player always falls through to a fresh load.
    UUID playerId = p.getUniqueId();
    boolean purged = PlayerDataPurgeGuard.isPurged(playerId);
    AdaptPlayer existing = players.get(playerId);
    if (existing != null) {
      if (!purged && existing.getPlayer() == p && existing.isRuntimeReady()) {
        try {
          onlineAdaptPlayers.put(playerId, existing);
          refreshLearnedAdaptations(existing);
          existing.loggedIn();
          unavailableOnlinePlayers.remove(playerId);
          profileFailureReported.invalidate(playerId);
          reconcileMutations(existing);
          onlineMembershipRevision.incrementAndGet();
          if (refreshSnapshots) {
            scheduleOnlinePlayerSnapshotRefresh();
          }
          AdaptPlaceholders.get().publishPlayer(existing);
          reportProfileReady(p);
          return true;
        } catch (Throwable error) {
          rollbackPlayerRuntimeActivation(playerId, p, existing, error);
          throw error;
        }
      }
    }

    LoadedPlayerData claimed = takePrefetchedData(playerId);
    if (claimed == null) {
      return false;
    }
    if (claimed.isOwned() != sqlPersistenceEnabled) {
      throw new IllegalStateException(sqlPersistenceEnabled
          ? "SQL-backed Adapt runtime received unfenced player data for " + playerId
          : "Local Adapt runtime received SQL-fenced player data for " + playerId);
    }
    if (!sqlPersistenceEnabled && AdaptPlayer.hasLoadFailure(playerId)) {
      throw new IllegalStateException(
          "Local player data remains guarded after a failed authoritative load for " + playerId);
    }

    if (existing != null) {
      players.remove(playerId, existing);
      if (existing.isRuntimeReady()) {
        existing.unregister();
      }
    }

    LoadedPlayerData prefetched;
    if (purged) {
      prefetchedPlayerData.invalidate(playerId);
      prefetched = null;
    } else {
      prefetched = claimed;
    }
    AdaptPlayer a = new AdaptPlayer(p, prefetched);
    try {
      a.startRuntime();
      players.put(playerId, a);
      onlineAdaptPlayers.put(playerId, a);
      refreshLearnedAdaptations(a);
      a.loggedIn();
      unavailableOnlinePlayers.remove(playerId);
      profileFailureReported.invalidate(playerId);
      reconcileMutations(a);
      onlineMembershipRevision.incrementAndGet();
      if (refreshSnapshots) {
        scheduleOnlinePlayerSnapshotRefresh();
      }
      AdaptPlaceholders.get().publishPlayer(a);
      reportProfileReady(p);
      return true;
    } catch (Throwable error) {
      rollbackPlayerRuntimeActivation(playerId, p, a, error);
      throw error;
    }
  }

  private void rollbackPlayerRuntimeActivation(
      UUID playerId, Player player, AdaptPlayer adaptPlayer, Throwable activationFailure) {
    unavailableOnlinePlayers.add(playerId);
    onlineAdaptPlayers.remove(playerId, adaptPlayer);
    players.remove(playerId, adaptPlayer);
    recipeBookSyncScheduled.remove(playerId);
    removeLearnedPlayer(playerId);
    try {
      adaptPlayer.unregister();
    } catch (Throwable cleanupFailure) {
      activationFailure.addSuppressed(cleanupFailure);
    }
    try {
      tearDownPlayerRuntime(player);
    } catch (Throwable cleanupFailure) {
      activationFailure.addSuppressed(cleanupFailure);
    }
  }

  public void quit(UUID p) {
    Player session = runtimeSessions.get(p);
    if (session != null) {
      runtimeSessions.remove(p, session);
    }
    unavailableOnlinePlayers.remove(p);
    profileFailureReported.invalidate(p);
    prefetchedPlayerData.invalidate(p);
    retirePlayer(p);
  }

  private CompletableFuture<Boolean> retirePlayer(UUID p) {
    AdaptPlayer a = players.get(p);
    if (a == null) {
      return CompletableFuture.completedFuture(true);
    }
    try {
      a.unregister();
    } catch (Throwable error) {
      Adapt.warn("Failed to retire Adapt player " + p + ": " + error.getMessage());
      Adapt.error(error);
    }
    CompletableFuture<Boolean> potionRetention = retainPotionState(p, a.getPlayer());
    // Keep the entry briefly after quit so late quit listeners/tasks do not
    // re-create a new AdaptPlayer for an offline player.
    try {
      prefetchedPlayerData.invalidate(p);
      AdaptPlaceholders.get().evictAfterGrace(p);
      onlineAdaptPlayers.remove(p, a);
      recipeBookSyncScheduled.remove(p);
      removeLearnedPlayer(p);
      onlineMembershipRevision.incrementAndGet();
      scheduleOnlinePlayerSnapshotRefresh();
    } catch (Throwable error) {
      Adapt.warn("Failed to clear runtime indexes for Adapt player " + p + ": " + error.getMessage());
      Adapt.error(error);
    }
    return potionRetention;
  }

  @Override
  public void unregister() {
    acceptingPlayerClaims.set(false);
    rejectPlayerDataClaims(
        playerDataClaims,
        new IllegalStateException("Adapt is shutting down")
    );
    playerClaimExecutor.shutdownNow();
    List<CompletableFuture<Boolean>> potionRetentions = new ArrayList<>();
    for (UUID playerId : new HashSet<>(players.keySet())) {
      try {
        potionRetentions.add(retirePlayer(playerId));
      } catch (Throwable error) {
        Adapt.warn("Failed to retire Adapt player " + playerId + " during shutdown: " + error.getMessage());
        Adapt.error(error);
      }
    }
    if (!awaitPotionRetention(potionRetentions, POTION_RETENTION_TIMEOUT_MILLIS)) {
      Adapt.warn("Adapt potion-state retention was incomplete at shutdown.");
    }
    players.clear();
    prefetchedPlayerData.invalidateAll();
    resetFenceEpochs.invalidateAll();
    runtimeSessions.clear();
    onlineAdaptPlayers.clear();
    learnedAdaptationsByPlayer.clear();
    learnedAdaptationLevelsByPlayer.clear();
    playersByLearnedAdaptation.clear();
    learnedAdaptPlayerSnapshots.clear();
    learnerIndexRevision.incrementAndGet();
    recipeBookSyncScheduled.clear();
    onlineProfileClaims.clear();
    unavailableOnlinePlayers.clear();
    profileFailureReported.invalidateAll();
    onlinePlayerSnapshot = List.of();
    onlineAdaptPlayerSnapshot = List.of();
    onlineSnapshotRefreshScheduled.set(false);
    skillRegistry.unregister();
    save();
    super.unregister();
  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(ProjectileLaunchEvent e) {
    if (e.getEntity() instanceof Snowball s && e.getEntity().getShooter() instanceof Player p) {
      KnowledgeOrb.Data data = KnowledgeOrb.get(s.getItem());
      if (data != null) {
        AdaptPlayer adaptPlayer = getPlayer(p);
        Skill<?> skill = getSkillRegistry().getSkill(data.getSkill());
        if (adaptPlayer == null || skill == null || !data.apply(p)) {
          e.setCancelled(true);
          return;
        }
        SoundNotification.builder()
            .sound(Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM)
            .volume(0.35f).pitch(1.455f)
            .build().play(adaptPlayer);
        SoundNotification.builder()
            .sound(Sound.ENTITY_SHULKER_OPEN)
            .volume(1f).pitch(1.655f)
            .build().play(adaptPlayer);
        adaptPlayer.getNot().queue(AdvancementNotification.builder()
            .icon(Material.BOOK)
            .model(CustomModel.get(Material.BOOK, "snippets", "gui", "knowledge"))
            .title(C.GRAY + AdaptLanguage.text(
                RuntimeMessages.KNOWLEDGE_GAIN,
                trusted("amount", C.WHITE + String.valueOf(data.getKnowledge())),
                trusted("skill", skill.getDisplayName())
            ))
            .build());
      } else {
        ExperienceOrb.Data datax = ExperienceOrb.get(s.getItem());
        if (datax != null) {
          AdaptPlayer adaptPlayer = getPlayer(p);
          if (adaptPlayer == null || !datax.apply(p)) {
            e.setCancelled(true);
            return;
          }
          SoundNotification.builder()
              .sound(Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM)
              .volume(0.35f).pitch(1.455f)
              .build().play(adaptPlayer);
          SoundNotification.builder()
              .sound(Sound.ENTITY_SHULKER_OPEN)
              .volume(1f).pitch(1.655f)
              .build().play(adaptPlayer);
        }
      }
    }

  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerJoinEvent e) {
    Player p = e.getPlayer();
    join(p);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(AsyncPlayerPreLoginEvent e) {
    if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
      return;
    }

    UUID uuid = e.getUniqueId();
    if (prefetchedPlayerData.getIfPresent(uuid) == null) {
      claimOnlinePlayer(uuid, 0);
    }
  }

  private LoadedPlayerData awaitPlayerDataClaim(UUID playerId) throws Exception {
    CompletableFuture<LoadedPlayerData> claim = beginPlayerDataClaim(
        playerId,
        playerDataClaims,
        playerClaimExecutor,
        acceptingPlayerClaims,
        AdaptPlayer::claimPlayerData
    );
    return claim.get(40L, TimeUnit.SECONDS);
  }

  private void awaitAndCachePlayerDataClaim(UUID playerId) throws Exception {
    for (int attempt = 0; attempt < RESET_RACE_CLAIM_ATTEMPTS; attempt++) {
      LoadedPlayerData claimed = awaitPlayerDataClaim(playerId);
      if (cacheClaimedPlayerData(playerId, claimed)) {
        return;
      }
    }
    throw new IllegalStateException(
        "Player-data ownership kept changing during login claim for " + playerId);
  }

  private boolean cacheClaimedPlayerData(UUID playerId, LoadedPlayerData claimed) {
    synchronized (playerOperationLock(playerId)) {
      LoadedPlayerData selected = selectClaimAfterResetEpoch(
          prefetchedPlayerData.getIfPresent(playerId),
          claimed,
          resetFenceEpochs.getIfPresent(playerId),
          sqlPersistenceEnabled
      );
      if (selected == null) {
        prefetchedPlayerData.invalidate(playerId);
        return false;
      }
      prefetchedPlayerData.put(playerId, selected);
      Long resetEpoch = resetFenceEpochs.getIfPresent(playerId);
      if (resetEpoch != null && selected.isOwned() && selected.epoch() > resetEpoch) {
        resetFenceEpochs.invalidate(playerId);
      }
      return true;
    }
  }

  static CompletableFuture<LoadedPlayerData> beginPlayerDataClaim(
      UUID playerId,
      ConcurrentMap<UUID, CompletableFuture<LoadedPlayerData>> activeClaims,
      Executor executor,
      AtomicBoolean acceptingClaims,
      Function<UUID, LoadedPlayerData> claimLoader
  ) {
    CompletableFuture<LoadedPlayerData> candidate = new CompletableFuture<>();
    if (!acceptingClaims.get()) {
      candidate.completeExceptionally(new IllegalStateException("Adapt is shutting down"));
      return candidate;
    }

    CompletableFuture<LoadedPlayerData> claim = activeClaims.putIfAbsent(playerId, candidate);
    if (claim != null) {
      if (!acceptingClaims.get()) {
        rejectPlayerDataClaim(
            activeClaims,
            playerId,
            claim,
            new IllegalStateException("Adapt is shutting down")
        );
      }
      return claim;
    }

    if (!acceptingClaims.get()) {
      rejectPlayerDataClaim(
          activeClaims,
          playerId,
          candidate,
          new IllegalStateException("Adapt is shutting down")
      );
      return candidate;
    }

    try {
      executor.execute(() -> {
        LoadedPlayerData loaded = null;
        Throwable failure = null;
        try {
          loaded = claimLoader.apply(playerId);
          if (loaded == null) {
            failure = new IllegalStateException("Player-data claim returned no data for " + playerId);
          }
        } catch (Throwable error) {
          failure = error;
        }
        completePlayerDataClaim(
            activeClaims,
            playerId,
            candidate,
            loaded,
            failure,
            acceptingClaims
        );
      });
    } catch (RejectedExecutionException error) {
      completePlayerDataClaim(
          activeClaims,
          playerId,
          candidate,
          null,
          error,
          acceptingClaims
      );
    }
    return candidate;
  }

  private static void completePlayerDataClaim(
      ConcurrentMap<UUID, CompletableFuture<LoadedPlayerData>> activeClaims,
      UUID playerId,
      CompletableFuture<LoadedPlayerData> claim,
      LoadedPlayerData loaded,
      Throwable failure,
      AtomicBoolean acceptingClaims
  ) {
    if (!activeClaims.remove(playerId, claim) || claim.isDone()) {
      return;
    }
    if (failure != null) {
      claim.completeExceptionally(failure);
    } else if (!acceptingClaims.get()) {
      claim.completeExceptionally(new IllegalStateException("Adapt is shutting down"));
    } else {
      claim.complete(loaded);
    }
  }

  static void rejectPlayerDataClaims(
      ConcurrentMap<UUID, CompletableFuture<LoadedPlayerData>> activeClaims,
      Throwable failure
  ) {
    for (Map.Entry<UUID, CompletableFuture<LoadedPlayerData>> entry : activeClaims.entrySet()) {
      rejectPlayerDataClaim(activeClaims, entry.getKey(), entry.getValue(), failure);
    }
  }

  private static void rejectPlayerDataClaim(
      ConcurrentMap<UUID, CompletableFuture<LoadedPlayerData>> activeClaims,
      UUID playerId,
      CompletableFuture<LoadedPlayerData> claim,
      Throwable failure
  ) {
    if (activeClaims.remove(playerId, claim)) {
      claim.completeExceptionally(failure);
    }
  }

  static LoadedPlayerData selectPrefetchedPlayerData(
      LoadedPlayerData current, LoadedPlayerData claimed) {
    if (claimed == null) {
      throw new IllegalArgumentException("Claimed player data cannot be null");
    }
    if (!claimed.isOwned() || current == null || !current.isOwned()) {
      return claimed;
    }
    if (claimed.epoch() > current.epoch()) {
      return claimed;
    }
    if (claimed.epoch() < current.epoch()) {
      return current;
    }
    if (!claimed.ownerToken().equals(current.ownerToken())) {
      throw new IllegalStateException("Conflicting SQL ownership tokens share epoch "
          + claimed.epoch());
    }
    return claimed.sequence() > current.sequence() ? claimed : current;
  }

  static LoadedPlayerData selectClaimAfterResetEpoch(
      LoadedPlayerData current, LoadedPlayerData claimed, Long resetEpoch,
      boolean sqlPersistenceEnabled) {
    if (claimed == null || claimed.isOwned() != sqlPersistenceEnabled) {
      throw new IllegalArgumentException(sqlPersistenceEnabled
          ? "SQL player-data claim must own a persistence fence"
          : "Local player-data claim must not own a persistence fence");
    }
    if (!sqlPersistenceEnabled && resetEpoch != null) {
      throw new IllegalStateException(
          "Local player-data claim cannot be evaluated against an SQL reset fence");
    }
    LoadedPlayerData selected = selectPrefetchedPlayerData(current, claimed);
    return sqlPersistenceEnabled && resetEpoch != null && selected.epoch() <= resetEpoch
        ? null
        : selected;
  }

  static void cacheResetFenceEpoch(
      ConcurrentMap<UUID, LoadedPlayerData> prefetchedData,
      ConcurrentMap<UUID, Long> resetEpochs,
      UUID playerId,
      long epoch
  ) {
    if (epoch < 1L) {
      throw new IllegalArgumentException("Reset fence epoch must be positive");
    }
    long watermark = resetEpochs.merge(playerId, epoch, Math::max);
    prefetchedData.computeIfPresent(playerId, (ignored, current) ->
        current.isOwned() && current.epoch() > watermark ? current : null);
  }

  private void recordResetFenceEpoch(UUID playerId, long epoch) {
    synchronized (playerOperationLock(playerId)) {
      cacheResetFenceEpoch(
          prefetchedPlayerData.asMap(), resetFenceEpochs.asMap(), playerId, epoch);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    Player p = e.getPlayer();
    UUID playerId = p.getUniqueId();
    quit(playerId);
    ViewerDisplayDirector.retireViewer(playerId);
    AdaptHud.clear(p);
  }

  @EventHandler
  public void on(CraftItemEvent e) {
    if (e.getWhoClicked() instanceof Player p) {
      Adaptation<?> required = getSkillRegistry().getRequiredAdaptation(e.getRecipe());
      if (required == null || required.hasAdaptation(p)) {
        return;
      }

      Skill<?> requiredSkill = required.getSkill();
      String skillName = requiredSkill == null
          ? AdaptLanguage.text(RuntimeMessages.UNKNOWN_SKILL)
          : requiredSkill.getDisplayName();
      SoundPlayer sp = SoundPlayer.of(p);
      Adapt.actionbar(p, C.RED + AdaptLanguage.text(
          RuntimeMessages.REQUIRES_ADAPTATION,
          trusted("adaptation", required.getDisplayName()),
          trusted("skill", skillName)
      ));
      sp.play(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.8f);
      e.setCancelled(true);
    }
  }

  @Override
  public void onTick() {
    data.getMultipliers().removeIf(multiplier -> multiplier == null || !multiplier.isActive());

    long now = M.ms();
    spatialXpLedger.purgeExpired(now);

    if (!clearLock.tryLock())
      return;

    try {
      int sizeBefore = players.size();
      Iterator<Map.Entry<UUID, AdaptPlayer>> iterator = players.entrySet().iterator();
      while (iterator.hasNext()) {
        Map.Entry<UUID, AdaptPlayer> entry = iterator.next();
        AdaptPlayer player = entry.getValue();
        if (player == null) {
          iterator.remove();
          onlineAdaptPlayers.remove(entry.getKey());
          prefetchedPlayerData.invalidate(entry.getKey());
          continue;
        }

        boolean onlineMembership = onlineAdaptPlayers.get(entry.getKey()) == player
            && player.isRuntimeReady();
        if (!player.shouldUnload(now, onlineMembership)) {
          continue;
        }

        player.unregister();
        iterator.remove();
        onlineAdaptPlayers.remove(entry.getKey(), player);
        prefetchedPlayerData.invalidate(entry.getKey());
      }

      if (players.size() != sizeBefore) {
        onlineMembershipRevision.incrementAndGet();
        scheduleOnlinePlayerSnapshotRefresh();
      }
    } finally {
      clearLock.unlock();
    }
  }

  public PlayerData peekData(UUID player) {
    if (PlayerDataPurgeGuard.isPurged(player) || unavailableOnlinePlayers.contains(player)) {
      return new PlayerData();
    }

    AdaptPlayer loaded = players.get(player);
    if (loaded != null) {
      return loaded.getData();
    }

    LoadedPlayerData prefetched = prefetchedPlayerData.getIfPresent(player);
    if (prefetched != null && !AdaptPlayer.requiresCanonicalLoad(player)
        && !AdaptPlayer.hasLoadFailure(player)) {
      return prefetched.data();
    }

    PlayerData data = AdaptPlayer.loadPlayerData(player);
    if (!AdaptPlayer.requiresCanonicalLoad(player) && !AdaptPlayer.hasLoadFailure(player)) {
      prefetchedPlayerData.put(player, LoadedPlayerData.inspected(data));
    }
    return data;
  }

  public boolean addStat(UUID playerId, String stat, double amount) {
    if (playerId == null || stat == null || stat.isBlank() || amount == 0D) {
      return false;
    }

    AdaptPlayer online = getOnlineAdaptPlayer(playerId);
    if (online == null) {
      return false;
    }

    Player player = online.getPlayer();
    return player != null && J.runEntity(player, () -> {
      if (onlineAdaptPlayers.get(playerId) == online && online.isRuntimeReady()) {
        online.getData().addStat(stat, amount);
      }
    });
  }

  public int getOnlineAdaptationLevel(UUID playerId, String skillName, String adaptationName) {
    AdaptPlayer online = getOnlineAdaptPlayer(playerId);
    if (online == null) {
      return 0;
    }
    Map<String, Integer> levels = learnedAdaptationLevelsByPlayer.get(playerId);
    return levels == null ? 0 : levels.getOrDefault(adaptationName, 0);
  }

  public boolean hasOnlineLearner(String adaptationName) {
    if (adaptationName == null || adaptationName.isBlank()) {
      return false;
    }
    Set<UUID> playerIds = playersByLearnedAdaptation.get(adaptationName);
    return playerIds != null && !playerIds.isEmpty();
  }

  public boolean hasOnlineLearner(UUID playerId, String adaptationName) {
    if (playerId == null || adaptationName == null || adaptationName.isBlank()) {
      return false;
    }
    Set<UUID> playerIds = playersByLearnedAdaptation.get(adaptationName);
    return playerIds != null && playerIds.contains(playerId);
  }

  public List<AdaptPlayer> getLearnedAdaptPlayerSnapshot(String adaptationName) {
    if (adaptationName == null || adaptationName.isBlank()) {
      return List.of();
    }

    return learnedAdaptPlayerSnapshots.computeIfAbsent(adaptationName, this::buildLearnedAdaptPlayerSnapshot);
  }

  private List<AdaptPlayer> buildLearnedAdaptPlayerSnapshot(String adaptationName) {
    Set<UUID> playerIds = playersByLearnedAdaptation.get(adaptationName);
    if (playerIds == null || playerIds.isEmpty()) {
      return List.of();
    }

    ArrayList<AdaptPlayer> learned = new ArrayList<>(playerIds.size());
    for (UUID playerId : playerIds) {
      AdaptPlayer player = getOnlineAdaptPlayer(playerId);
      if (player != null) {
        learned.add(player);
      }
    }
    return Collections.unmodifiableList(learned);
  }

  public void updateLearnedAdaptation(AdaptPlayer player, String adaptationName, int level) {
    if (player == null || adaptationName == null || adaptationName.isBlank()) {
      return;
    }

    UUID playerId = player.getPlayer().getUniqueId();
    Set<String> learned = learnedAdaptationsByPlayer.computeIfAbsent(playerId, unused -> ConcurrentHashMap.newKeySet());
    Map<String, Integer> levels = learnedAdaptationLevelsByPlayer.computeIfAbsent(playerId, unused -> new ConcurrentHashMap<>());
    if (level > 0) {
      learned.add(adaptationName);
      levels.put(adaptationName, level);
      playersByLearnedAdaptation.computeIfAbsent(adaptationName, unused -> ConcurrentHashMap.newKeySet()).add(playerId);
      learnedAdaptPlayerSnapshots.remove(adaptationName);
      learnerIndexRevision.incrementAndGet();
      reconcileMutations(player);
      synchronizeRecipeBook(playerId, player);
      return;
    }

    learned.remove(adaptationName);
    levels.remove(adaptationName);
    if (levels.isEmpty()) {
      learnedAdaptationLevelsByPlayer.remove(playerId, levels);
    }
    if (learned.isEmpty()) {
      learnedAdaptationsByPlayer.remove(playerId, learned);
    }
    removeLearnedAdaptationPlayer(adaptationName, playerId);
    reconcileMutations(player);
    synchronizeRecipeBook(playerId, player);
  }

  public void refreshLearnedAdaptations(AdaptPlayer player) {
    if (player == null) {
      return;
    }

    UUID playerId = player.getPlayer().getUniqueId();
    Set<String> refreshed = new HashSet<>();
    Map<String, Integer> refreshedLevels = new ConcurrentHashMap<>();
    for (PlayerSkillLine line : player.getData().getSkillLines().values()) {
      if (line == null) {
        continue;
      }
      for (Map.Entry<String, PlayerAdaptation> entry : line.getAdaptations().entrySet()) {
        String adaptationName = entry.getKey();
        PlayerAdaptation adaptation = entry.getValue();
        if (adaptationName != null && !adaptationName.isBlank()
            && adaptation != null && adaptation.getLevel() > 0) {
          refreshed.add(adaptationName);
          refreshedLevels.put(adaptationName, adaptation.getLevel());
        }
      }
    }

    Set<String> indexed = ConcurrentHashMap.newKeySet();
    indexed.addAll(refreshed);
    Set<String> previous = learnedAdaptationsByPlayer.put(playerId, indexed);
    if (refreshedLevels.isEmpty()) {
      learnedAdaptationLevelsByPlayer.remove(playerId);
    } else {
      learnedAdaptationLevelsByPlayer.put(playerId, refreshedLevels);
    }
    if (previous != null) {
      for (String adaptationName : previous) {
        if (!refreshed.contains(adaptationName)) {
          removeLearnedAdaptationPlayer(adaptationName, playerId);
          AdaptAttributeService.onAdaptationUnlearned(player.getPlayer(), adaptationName);
        }
      }
    }
    for (String adaptationName : refreshed) {
      playersByLearnedAdaptation.computeIfAbsent(adaptationName, unused -> ConcurrentHashMap.newKeySet()).add(playerId);
      learnedAdaptPlayerSnapshots.remove(adaptationName);
    }
    if (indexed.isEmpty()) {
      learnedAdaptationsByPlayer.remove(playerId, indexed);
    }
    learnerIndexRevision.incrementAndGet();
    reconcileMutations(player);
    synchronizeRecipeBook(playerId, player);
  }

  public void synchronizeRecipeBooksForOnlinePlayers() {
    for (Map.Entry<UUID, AdaptPlayer> entry : onlineAdaptPlayers.entrySet()) {
      synchronizeRecipeBook(entry.getKey(), entry.getValue());
    }
  }

  private void synchronizeRecipeBook(UUID playerId, AdaptPlayer adaptPlayer) {
    if (playerId == null || adaptPlayer == null) {
      return;
    }

    Player player = adaptPlayer.getPlayer();
    if (player == null) {
      return;
    }

    if (!recipeBookSyncScheduled.add(playerId)) {
      return;
    }

    boolean scheduled = J.runEntity(player, () -> {
      recipeBookSyncScheduled.remove(playerId);
      if (!player.isOnline() || !adaptPlayer.isRuntimeReady()
          || getOnlineAdaptPlayer(playerId) != adaptPlayer) {
        return;
      }
      if (!skillRegistry.isRecipeRegistrationReady()) {
        return;
      }

      AdaptRecipeBook.Plan plan = AdaptRecipeBook.plan(
          skillRegistry.getRegisteredRecipeUnlocks(),
          adaptation -> adaptation.getLevel(adaptPlayer)
      );
      AdaptRecipeBook.synchronize(player, plan);
    }, 1);
    if (!scheduled) {
      recipeBookSyncScheduled.remove(playerId);
    }
  }

  private void reconcileMutations(AdaptPlayer player) {
    MutationSVC mutationService = MutationSVC.get();
    if (mutationService != null && mutationService.getManager() != null) {
      mutationService.getManager().reconcile(player);
    }
  }

  @NonNull
  public Optional<PlayerData> getPlayerData(@NonNull UUID uuid) {
    if (unavailableOnlinePlayers.contains(uuid)) {
      return Optional.empty();
    }
    return Optional.ofNullable(players.get(uuid))
        .map(AdaptPlayer::getData);
  }

  public AdaptPlayer getOnlineAdaptPlayer(UUID playerId) {
    if (playerId == null || unavailableOnlinePlayers.contains(playerId)) {
      return null;
    }
    AdaptPlayer player = onlineAdaptPlayers.get(playerId);
    return player != null && player.isRuntimeReady()
        && runtimeSessions.get(playerId) == player.getPlayer()
        ? player
        : null;
  }

  public boolean isPlayerRuntimeAvailable(UUID playerId) {
    return getOnlineAdaptPlayer(playerId) != null;
  }

  public FencedPlayerSnapshot retirePlayerForTransfer(UUID playerId, UUID ownerToken, long epoch,
                                                       AdaptPlayer expected, Player player) {
    if (playerId == null || ownerToken == null || expected == null || player == null
        || epoch < 1L || !J.isOwnedByCurrentRegion(player)) {
      return null;
    }
    synchronized (playerOperationLock(playerId)) {
      AdaptPlayer current = onlineAdaptPlayers.get(playerId);
      if (current != expected || !expected.isRuntimeReady() || expected.getPlayer() != player) {
        return null;
      }
      FencedPlayerSnapshot snapshot = expected.retireForTransfer(ownerToken, epoch);
      if (snapshot == null) {
        return null;
      }
      onlineAdaptPlayers.remove(playerId, expected);
      unavailableOnlinePlayers.add(playerId);
      prefetchedPlayerData.invalidate(playerId);
      recipeBookSyncScheduled.remove(playerId);
      removeLearnedPlayer(playerId);
      ViewerDisplayDirector.retireViewer(playerId);
      RedisSync redisSync = Adapt.instance.getRedisSync();
      if (redisSync != null) {
        redisSync.retainRetiredTransfer(snapshot);
      }
      onlineMembershipRevision.incrementAndGet();
      scheduleOnlinePlayerSnapshotRefresh();
      tearDownPlayerRuntime(player);
      return snapshot;
    }
  }

  void onPersistenceFenceLost(UUID playerId, UUID ownerToken, long epoch) {
    AdaptPlayer adaptPlayer = onlineAdaptPlayers.get(playerId);
    if (adaptPlayer == null || !adaptPlayer.isRuntimeReady()) {
      synchronized (playerOperationLock(playerId)) {
        LoadedPlayerData prefetched = prefetchedPlayerData.getIfPresent(playerId);
        if (ownerToken != null && prefetched != null && prefetched.isOwned()
            && ownerToken.equals(prefetched.ownerToken()) && epoch == prefetched.epoch()) {
          prefetchedPlayerData.invalidate(playerId);
          if (runtimeSessions.containsKey(playerId)) {
            unavailableOnlinePlayers.add(playerId);
          }
        }
      }
      return;
    }
    Player player = adaptPlayer.getPlayer();
    if (!J.runEntity(player, () -> {
      FencedPlayerSnapshot snapshot = retirePlayerForTransfer(
          playerId, ownerToken, epoch, adaptPlayer, player);
      if (snapshot == null) {
        return;
      }
      Adapt.warn("Retired Adapt for " + playerId
          + " because this server no longer owns the player's SQL persistence fence; "
          + "the Minecraft session remains connected.");
    })) {
      synchronized (playerOperationLock(playerId)) {
        AdaptPlayer current = onlineAdaptPlayers.get(playerId);
        if (current == adaptPlayer && adaptPlayer.invalidatePersistenceFence(ownerToken, epoch)) {
          onlineAdaptPlayers.remove(playerId, adaptPlayer);
          unavailableOnlinePlayers.add(playerId);
          prefetchedPlayerData.invalidate(playerId);
          recipeBookSyncScheduled.remove(playerId);
          removeLearnedPlayer(playerId);
          onlineMembershipRevision.incrementAndGet();
        }
      }
      Adapt.warn("Could not dispatch persistence-fence retirement for " + playerId
          + "; the rejected fence was invalidated immediately.");
    }
  }

  public void applyRemoteReset(UUID playerId, UUID operationId, long epoch, boolean purge) {
    if (playerId == null || operationId == null || epoch < 1L) {
      return;
    }
    recordResetFenceEpoch(playerId, epoch);
    PlayerDataPersistenceQueue queue = Adapt.instance.getPlayerDataPersistenceQueue();
    if (queue != null) {
      queue.discardFencedSavesBefore(playerId, epoch);
    }

    AdaptPlayer adaptPlayer = onlineAdaptPlayers.get(playerId);
    if (adaptPlayer == null || !adaptPlayer.isRuntimeReady()) {
      return;
    }
    Player player = adaptPlayer.getPlayer();
    if (!J.runEntity(player, () -> retireAfterRemoteReset(
        playerId, operationId, epoch, purge, adaptPlayer, player))) {
      invalidateRejectedFenceAdvance(playerId, adaptPlayer, epoch);
      Adapt.warn("Could not dispatch remote profile retirement for " + playerId
          + "; its old persistence fence was invalidated.");
    }
  }

  private void retireAfterRemoteReset(UUID playerId, UUID operationId, long epoch,
                                      boolean purge, AdaptPlayer expected, Player player) {
    synchronized (playerOperationLock(playerId)) {
      AdaptPlayer current = onlineAdaptPlayers.get(playerId);
      if (current != expected || !expected.isRuntimeReady() || expected.getPlayer() != player
          || !expected.retireForRemoteFenceAdvance(epoch)) {
        return;
      }
      onlineAdaptPlayers.remove(playerId, expected);
      unavailableOnlinePlayers.add(playerId);
      prefetchedPlayerData.invalidate(playerId);
      recipeBookSyncScheduled.remove(playerId);
      removeLearnedPlayer(playerId);
      ViewerDisplayDirector.retireViewer(playerId);
      onlineMembershipRevision.incrementAndGet();
      scheduleOnlinePlayerSnapshotRefresh();
      tearDownPlayerRuntime(player);
      Adapt.info("Retired remote Adapt profile after " + (purge ? "purge" : "reset") + " "
          + operationId + " for " + playerId);
    }
  }

  private boolean invalidateRejectedFenceAdvance(
      UUID playerId, AdaptPlayer expected, long epoch) {
    synchronized (playerOperationLock(playerId)) {
      AdaptPlayer current = onlineAdaptPlayers.get(playerId);
      if (current == null || (expected != null && current != expected)
          || !current.invalidateForRemoteFenceAdvance(epoch)) {
        return false;
      }
      onlineAdaptPlayers.remove(playerId, current);
      unavailableOnlinePlayers.add(playerId);
      prefetchedPlayerData.invalidate(playerId);
      recipeBookSyncScheduled.remove(playerId);
      removeLearnedPlayer(playerId);
      onlineMembershipRevision.incrementAndGet();
      return true;
    }
  }

  public AdaptPlayer getPlayer(Player p) {
    if (p == null) {
      return null;
    }
    UUID playerId = p.getUniqueId();
    AdaptPlayer existing = getOnlineAdaptPlayer(playerId);
    return existing != null && runtimeSessions.get(playerId) == p
        && existing.getPlayer() == p
        ? existing
        : null;
  }

  /**
   * Resets a player to a brand new Adapt profile.
   * <p>
   * An online player is reset in place and is never kicked: every side effect their adaptations
   * applied is torn down, their live data is swapped for a default instance, and the fresh state is
   * written immediately so a crash cannot bring the old profile back. An offline player has the
   * stored copy purged and is guarded so no lingering in-memory copy can write it back.
   *
   */
  public CompletableFuture<PlayerDataResetResult> resetPlayerData(UUID playerId) {
    CompletableFuture<PlayerDataResetResult> completion = new CompletableFuture<>();
    if (playerId == null) {
      completion.complete(PlayerDataResetResult.DISPATCH_REJECTED);
      return completion;
    }
    if (AdaptConfig.get().getSql().isEnabled()) {
      resetFencedPlayerData(playerId, completion);
      return completion;
    }

    try {
      Player player;
      synchronized (playerOperationLock(playerId)) {
        player = Bukkit.getPlayer(playerId);
        if (player == null) {
          purgeStoredPlayerData(playerId, players.get(playerId));
          completion.complete(PlayerDataResetResult.PURGED);
          return completion;
        }
      }
      completePlayerDataReset(playerId, player, completion);
    } catch (Throwable error) {
      failPlayerDataReset(playerId, completion, error);
    }
    return completion;
  }

  private void resetFencedPlayerData(
      UUID playerId, CompletableFuture<PlayerDataResetResult> completion) {
    PlayerDataPersistenceQueue queue = Adapt.instance.getPlayerDataPersistenceQueue();
    if (queue == null) {
      completion.complete(PlayerDataResetResult.DISPATCH_REJECTED);
      return;
    }

    Player expectedPlayer;
    AdaptPlayer expectedAdaptPlayer;
    synchronized (playerOperationLock(playerId)) {
      expectedPlayer = Bukkit.getPlayer(playerId);
      expectedAdaptPlayer = players.get(playerId);
    }
    boolean purge = expectedPlayer == null;
    PlayerData replacement = new PlayerData();
    File localFile = AdaptPlayer.getPlayerDataFile(playerId);
    CompletableFuture<SQLManager.TokenMutationResult> mutation = purge
        ? queue.purgeFencedData(playerId, localFile)
        : queue.resetFencedData(
            playerId,
            replacement.toJson(true),
            localFile,
            PlayerDataPurgeGuard.generation(playerId)
        );
    UUID operationId = UUID.randomUUID();
    mutation.whenComplete((result, error) -> completeFencedPlayerDataReset(
        playerId,
        operationId,
        expectedPlayer,
        expectedAdaptPlayer,
        replacement,
        purge,
        result,
        error,
        completion
    ));
  }

  private void completeFencedPlayerDataReset(
      UUID playerId,
      UUID operationId,
      Player expectedPlayer,
      AdaptPlayer expectedAdaptPlayer,
      PlayerData replacement,
      boolean purge,
      SQLManager.TokenMutationResult result,
      Throwable error,
      CompletableFuture<PlayerDataResetResult> completion
  ) {
    if (error != null || result == null || !result.successful() || result.newToken() == null) {
      failPlayerDataReset(
          playerId,
          completion,
          error == null
              ? new IllegalStateException("SQL manager rejected the fenced reset")
              : error
      );
      return;
    }

    SQLManager.SqlToken token = result.newToken();
    recordResetFenceEpoch(playerId, token.epoch());
    publishFencedReset(playerId, operationId, token, purge);
    if (purge) {
      scheduleFencedPurgeCompletion(playerId, token, completion);
      return;
    }

    long generation = PlayerDataPurgeGuard.mark(playerId);
    Runnable retired = () -> {
      invalidateRejectedFenceAdvance(playerId, expectedAdaptPlayer, token.epoch());
      completion.complete(PlayerDataResetResult.PURGED);
    };
    boolean accepted;
    try {
      accepted = J.runEntity(expectedPlayer, () -> applyFencedLiveReset(
          playerId,
          expectedPlayer,
          expectedAdaptPlayer,
          replacement,
          token,
          generation,
          completion
      ), retired);
    } catch (Throwable dispatchError) {
      invalidateRejectedFenceAdvance(playerId, expectedAdaptPlayer, token.epoch());
      failPlayerDataReset(playerId, completion, dispatchError);
      return;
    }
    if (!accepted) {
      invalidateRejectedFenceAdvance(playerId, expectedAdaptPlayer, token.epoch());
      completion.complete(PlayerDataResetResult.DISPATCH_REJECTED);
    }
  }

  private void scheduleFencedPurgeCompletion(
      UUID playerId,
      SQLManager.SqlToken token,
      CompletableFuture<PlayerDataResetResult> completion
  ) {
    boolean globalAccepted;
    try {
      globalAccepted = SchedulerUtils.runGlobal(Adapt.instance, () -> {
        Player currentPlayer;
        synchronized (playerOperationLock(playerId)) {
          currentPlayer = Bukkit.getPlayer(playerId);
          if (currentPlayer == null) {
            long generation = PlayerDataPurgeGuard.mark(playerId);
            purgeStoredPlayerDataAfterFence(playerId, players.get(playerId), generation);
            recordResetFenceEpoch(playerId, token.epoch());
            completion.complete(PlayerDataResetResult.PURGED);
            return;
          }
        }

        Runnable retired = () -> {
          invalidateRejectedFenceAdvance(playerId, null, token.epoch());
          completion.complete(PlayerDataResetResult.PURGED);
        };
        boolean accepted = J.runEntity(currentPlayer, () -> {
          synchronized (playerOperationLock(playerId)) {
            AdaptPlayer current = players.get(playerId);
            if (current == null || !current.isRuntimeReady()
                || current.getPlayer() != currentPlayer) {
              completion.complete(PlayerDataResetResult.PURGED);
              return;
            }
            if (current.installNewerPersistenceFence(
                token.ownerToken(), token.epoch(), 0L)) {
              PlayerDataPersistenceQueue queue = Adapt.instance.getPlayerDataPersistenceQueue();
              if (queue != null) {
                queue.discardFencedSaves(playerId, token.ownerToken(), token.epoch());
              }
              installPristinePlayerData(current, currentPlayer, new PlayerData());
            }
            completion.complete(PlayerDataResetResult.LIVE);
          }
        }, retired);
        if (!accepted) {
          invalidateRejectedFenceAdvance(playerId, null, token.epoch());
          completion.complete(PlayerDataResetResult.DISPATCH_REJECTED);
        }
      });
    } catch (Throwable dispatchError) {
      invalidateRejectedFenceAdvance(playerId, null, token.epoch());
      failPlayerDataReset(playerId, completion, dispatchError);
      return;
    }
    if (!globalAccepted) {
      invalidateRejectedFenceAdvance(playerId, null, token.epoch());
      completion.complete(PlayerDataResetResult.DISPATCH_REJECTED);
    }
  }

  private void applyFencedLiveReset(
      UUID playerId,
      Player expectedPlayer,
      AdaptPlayer expectedAdaptPlayer,
      PlayerData replacement,
      SQLManager.SqlToken token,
      long generation,
      CompletableFuture<PlayerDataResetResult> completion
  ) {
    synchronized (playerOperationLock(playerId)) {
      Player currentPlayer = Bukkit.getPlayer(playerId);
      AdaptPlayer current = players.get(playerId);
      if (currentPlayer != expectedPlayer || !expectedPlayer.isOnline()) {
        completion.complete(PlayerDataResetResult.PURGED);
        return;
      }
      if (current != expectedAdaptPlayer || current == null || !current.isRuntimeReady()) {
        if (current != null && current.persistenceFenceEpoch() >= token.epoch()) {
          PlayerDataPurgeGuard.clear(playerId);
          completion.complete(PlayerDataResetResult.LIVE);
          return;
        }
        prefetchedPlayerData.put(playerId, LoadedPlayerData.owned(
            replacement, token.ownerToken(), token.epoch(), 0L));
        boolean joined = joinSerialized(expectedPlayer, true);
        PlayerDataPurgeGuard.clear(playerId);
        completion.complete(joined
            ? PlayerDataResetResult.LIVE
            : PlayerDataResetResult.DISPATCH_REJECTED);
        return;
      }
      if (!current.installNewerPersistenceFence(token.ownerToken(), token.epoch(), 0L)) {
        PlayerDataPurgeGuard.clear(playerId);
        completion.complete(current.persistenceFenceEpoch() >= token.epoch()
            ? PlayerDataResetResult.LIVE
            : PlayerDataResetResult.DISPATCH_REJECTED);
        return;
      }
      current.adoptPurgeGeneration(generation);
      PlayerDataPurgeGuard.clear(playerId);
      installPristinePlayerData(current, expectedPlayer, replacement);
      Adapt.info("Reset live Adapt data for " + expectedPlayer.getName() + " (" + playerId + ")");
      completion.complete(PlayerDataResetResult.LIVE);
    }
  }

  private void publishFencedReset(
      UUID playerId, UUID operationId, SQLManager.SqlToken token, boolean purge) {
    RedisSync redisSync = Adapt.instance.getRedisSync();
    if (redisSync == null) {
      return;
    }
    try {
      redisSync.publishReset(new ResetNotice(
          playerId, operationId, token.epoch(), purge));
    } catch (Throwable error) {
      Adapt.warn("SQL profile mutation committed for " + playerId
          + " but its Redis reset notice failed: " + error.getMessage());
      Adapt.error(error);
    }
  }

  private void completePlayerDataReset(UUID playerId, Player expectedPlayer,
                                       CompletableFuture<PlayerDataResetResult> completion) {
    if (completion.isDone()) {
      return;
    }

    Player dispatchTarget = null;
    try {
      synchronized (playerOperationLock(playerId)) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
          purgeStoredPlayerData(playerId, players.get(playerId));
          completion.complete(PlayerDataResetResult.PURGED);
          return;
        }
        if (player != expectedPlayer || !J.isOwnedByCurrentRegion(player)) {
          dispatchTarget = player;
        } else if (!player.isOnline()) {
          purgeStoredPlayerData(playerId, players.get(playerId));
          completion.complete(PlayerDataResetResult.PURGED);
          return;
        } else {
          completion.complete(resetPlayerDataOwned(playerId, player));
        }
      }
      if (dispatchTarget != null) {
        dispatchPlayerDataReset(playerId, dispatchTarget, completion);
      }
    } catch (Throwable error) {
      failPlayerDataReset(playerId, completion, error);
    }
  }

  private void dispatchPlayerDataReset(UUID playerId, Player player,
                                       CompletableFuture<PlayerDataResetResult> completion) {
    Runnable retired = () -> completion.complete(PlayerDataResetResult.DISPATCH_REJECTED);
    boolean accepted;
    try {
      accepted = J.runEntity(
          player,
          () -> completePlayerDataReset(playerId, player, completion),
          retired
      );
    } catch (Throwable error) {
      failPlayerDataReset(playerId, completion, error);
      return;
    }
    if (!accepted) {
      completion.complete(PlayerDataResetResult.DISPATCH_REJECTED);
    }
  }

  private void failPlayerDataReset(UUID playerId, CompletableFuture<PlayerDataResetResult> completion,
                                   Throwable error) {
    Adapt.warn("Failed to complete player data reset for " + playerId + ": " + error.getMessage());
    Adapt.error(error);
    completion.complete(PlayerDataResetResult.DISPATCH_REJECTED);
  }

  private PlayerDataResetResult resetPlayerDataOwned(UUID playerId, Player player) {
    if (!J.isOwnedByCurrentRegion(player)) {
      return PlayerDataResetResult.DISPATCH_REJECTED;
    }

    AdaptPlayer adaptPlayer = players.get(playerId);
    if (adaptPlayer == null || !adaptPlayer.isRuntimeReady() || adaptPlayer.getPlayer() != player) {
      join(player);
      adaptPlayer = getPlayer(player);
    }

    if (adaptPlayer == null) {
      purgeStoredPlayerData(playerId, null);
      return PlayerDataResetResult.PURGED;
    }

    long purgeGeneration = PlayerDataPurgeGuard.mark(playerId);
    adaptPlayer.adoptPurgeGeneration(purgeGeneration);
    PlayerDataPurgeGuard.clear(playerId);
    prefetchedPlayerData.invalidate(playerId);

    PlayerData replacement = new PlayerData();
    if (!adaptPlayer.persistResetNow(replacement)) {
      Adapt.warn("Reset player data for " + playerId + " was not durably accepted by persistence.");
      return PlayerDataResetResult.DISPATCH_REJECTED;
    }
    installPristinePlayerData(adaptPlayer, player, replacement);
    Adapt.info("Reset live Adapt data for " + player.getName() + " (" + playerId + ")");
    return PlayerDataResetResult.LIVE;
  }

  private void installPristinePlayerData(AdaptPlayer adaptPlayer, Player player,
                                         PlayerData replacement) {
    UUID playerId = player.getUniqueId();
    AdaptPlayer.forgetLoadFailure(playerId);
    adaptPlayer.replaceData(replacement);
    refreshLearnedAdaptations(adaptPlayer);
    tearDownPlayerRuntime(player);
    adaptPlayer.reconcileStatTrackers();
    AdaptPlaceholders.get().publishPlayer(adaptPlayer);
    onlineMembershipRevision.incrementAndGet();
    scheduleOnlinePlayerSnapshotRefresh();
  }

  private void purgeStoredPlayerData(UUID playerId, AdaptPlayer stale) {
    purgeStoredPlayerData(playerId, stale, false, -1L);
  }

  private void purgeStoredPlayerDataAfterFence(UUID playerId, AdaptPlayer stale, long generation) {
    purgeStoredPlayerData(playerId, stale, true, generation);
  }

  private void purgeStoredPlayerData(UUID playerId, AdaptPlayer stale,
                                     boolean fenced, long generation) {
    prefetchedPlayerData.invalidate(playerId);
    recipeBookSyncScheduled.remove(playerId);
    removeLearnedPlayer(playerId);
    onlineAdaptPlayers.remove(playerId);

    if (stale == null && !fenced) {
      AdaptPlayer.purgeStoredData(playerId);
    } else if (stale != null) {
      players.remove(playerId, stale);
      if (fenced) {
        stale.retireAfterFencedPurge(playerId, generation);
      } else {
        stale.purge(playerId);
      }
      if (stale.isRuntimeReady()) {
        stale.unregister();
      }
    }

    MutationSVC mutationService = MutationSVC.get();
    if (mutationService != null && mutationService.getManager() != null) {
      mutationService.getManager().cleanup(playerId);
    }
    MinionBurden.get().clearOwner(playerId);
    AdaptPotionRegistry.forget(playerId);
    XpNovelty.clear(playerId);
    PlayerStateRegistry.clearPlayer(playerId);
    ViewerDisplayDirector.retireViewer(playerId);
    onlineMembershipRevision.incrementAndGet();
    scheduleOnlinePlayerSnapshotRefresh();
    Adapt.info("Purged stored Adapt data for " + playerId);
  }

  private void tearDownPlayerRuntime(Player player) {
    UUID playerId = player.getUniqueId();
    AdaptPlaceholders.get().evictNow(playerId);
    MutationSVC mutationService = MutationSVC.get();
    if (mutationService != null && mutationService.getManager() != null) {
      mutationService.getManager().cleanup(playerId);
    }
    UIWindow window = Adapt.instance.getGuiLeftovers().remove(playerId.toString());
    if (window != null) {
      window.close();
    }
    AdaptAttributeService.get().clearAllAdapt(player);
    MinionBurden.get().clearOwner(playerId);
    XpNovelty.clear(playerId);
    PlayerStateRegistry.clearPlayer(playerId);
    ViewerDisplayDirector.clearViewer(playerId);
    ViewerGlowCoordinator glow = Adapt.instance.getViewerGlowCoordinator();
    if (glow != null) {
      glow.discardViewer(playerId);
    }

    J.runEntity(player, () -> {
      AdaptPotionRegistry.strip(player);
      AdaptHud.clear(player);
    });
  }

  private void removeLearnedPlayer(UUID playerId) {
    Set<String> learned = learnedAdaptationsByPlayer.remove(playerId);
    learnedAdaptationLevelsByPlayer.remove(playerId);
    if (learned == null) {
      return;
    }
    for (String adaptationName : learned) {
      removeLearnedAdaptationPlayer(adaptationName, playerId);
    }
  }

  private void removeLearnedAdaptationPlayer(String adaptationName, UUID playerId) {
    playersByLearnedAdaptation.computeIfPresent(adaptationName, (name, playerIds) -> {
      playerIds.remove(playerId);
      return playerIds.isEmpty() ? null : playerIds;
    });
    learnedAdaptPlayerSnapshots.remove(adaptationName);
    learnerIndexRevision.incrementAndGet();
  }

  private LoadedPlayerData takePrefetchedData(UUID uuid) {
    LoadedPlayerData prefetched = prefetchedPlayerData.getIfPresent(uuid);
    if (prefetched != null) {
      prefetchedPlayerData.invalidate(uuid);
    }
    return prefetched;
  }

  private static Object[] createPlayerOperationLocks() {
    Object[] locks = new Object[PLAYER_OPERATION_LOCK_COUNT];
    for (int index = 0; index < locks.length; index++) {
      locks[index] = new Object();
    }
    return locks;
  }

  private Object playerOperationLock(UUID playerId) {
    int hash = playerId.hashCode();
    hash ^= hash >>> 16;
    return playerOperationLocks[hash & (playerOperationLocks.length - 1)];
  }

  private void scheduleOnlinePlayerSnapshotRefresh() {
    if (onlineSnapshotRefreshScheduled.compareAndSet(false, true)) {
      J.s(this::rebuildOnlinePlayerSnapshots);
    }
  }

  private void rebuildOnlinePlayerSnapshots() {
    long revision = onlineMembershipRevision.get();
    ArrayList<AdaptPlayer> adaptPlayers = new ArrayList<>(onlineAdaptPlayers.size());
    ArrayList<Player> playerSnapshot = new ArrayList<>(onlineAdaptPlayers.size());

    for (AdaptPlayer adaptPlayer : onlineAdaptPlayers.values()) {
      if (adaptPlayer == null || !adaptPlayer.isRuntimeReady()) {
        continue;
      }
      Player player = adaptPlayer.getPlayer();
      if (player != null) {
        adaptPlayers.add(adaptPlayer);
        playerSnapshot.add(player);
      }
    }

    onlineAdaptPlayerSnapshot = Collections.unmodifiableList(adaptPlayers);
    onlinePlayerSnapshot = Collections.unmodifiableList(playerSnapshot);
    onlineSnapshotRefreshScheduled.set(false);
    if (onlineMembershipRevision.get() != revision) {
      scheduleOnlinePlayerSnapshotRefresh();
    }
  }

  public void openSkillGUI(Skill<?> skill, Player p) {
    skill.openGui(p);
  }

  public void openAdaptGui(Player p) {
    SkillsGui.open(p);
  }

  public void openAdaptationGUI(Adaptation<?> adaptation, Player p) {
    adaptation.openGui(p);
  }

  public void boostXP(double boost, long ms) {
    data.getMultipliers().add(new XPMultiplier(boost, ms));
  }

  public void load() {
    File f = new File(Adapt.instance.getDataFolder("data"), "server-data.json");
    if (f.exists()) {
      try {
        AdaptServerData loaded = Json.fromJson(IO.readAll(f), AdaptServerData.class);
        if (loaded != null) {
          loaded.normalize();
          data = loaded;
        }
      } catch (Throwable error) {
        Adapt.warn("Failed to load global boosts data from " + f.getAbsolutePath());
        Adapt.error(error);
      }
    }
  }

  @SneakyThrows
  public void save() {
    File file = new File(Adapt.instance.getDataFolder("data"), "server-data.json");
    PlayerDataPersistenceQueue.writeSnapshot(file, Json.toJson(data, true));
  }

  static CompletableFuture<Boolean> retainPotionState(UUID playerId, Player player) {
    if (playerId == null || AdaptPotionRegistry.applied(playerId).isEmpty()) {
      return CompletableFuture.completedFuture(true);
    }
    return runOwnedPotionRetention(
        playerId, player, () -> AdaptPotionRegistry.retainActive(player));
  }

  static CompletableFuture<Boolean> runOwnedPotionRetention(UUID playerId, Player player,
                                                              Runnable retentionAction) {
    if (player == null) {
      return CompletableFuture.completedFuture(false);
    }

    CompletableFuture<Boolean> completion = new CompletableFuture<>();
    Runnable retention = () -> {
      try {
        retentionAction.run();
        completion.complete(true);
      } catch (Throwable error) {
        reportPotionRetentionFailure(playerId, error);
        completion.complete(false);
      }
    };

    try {
      if (J.isOwnedByCurrentRegion(player)) {
        retention.run();
      } else if (!J.runEntity(player, retention, () -> completion.complete(false))) {
        completion.complete(false);
      }
    } catch (Throwable error) {
      reportPotionRetentionFailure(playerId, error);
      completion.complete(false);
    }
    return completion;
  }

  static boolean awaitPotionRetention(List<CompletableFuture<Boolean>> completions, long timeoutMillis) {
    if (completions.isEmpty()) {
      return true;
    }

    CompletableFuture<Void> all = CompletableFuture.allOf(completions.toArray(CompletableFuture[]::new));
    try {
      all.get(Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS);
    } catch (InterruptedException error) {
      Thread.currentThread().interrupt();
      Adapt.warn("Interrupted while waiting for Adapt potion-state retention.");
      Adapt.error(error);
      return false;
    } catch (ExecutionException | TimeoutException error) {
      Adapt.warn("Adapt potion-state retention did not complete before shutdown.");
      Adapt.error(error);
      return false;
    }

    for (CompletableFuture<Boolean> completion : completions) {
      if (!completion.join()) {
        return false;
      }
    }
    return true;
  }

  private static void reportPotionRetentionFailure(UUID playerId, Throwable error) {
    Adapt.warn("Failed to retain Adapt potion state for " + playerId + ": " + error.getMessage());
    Adapt.error(error);
  }

  public enum PlayerDataResetResult {
    LIVE,
    PURGED,
    DISPATCH_REJECTED
  }
}

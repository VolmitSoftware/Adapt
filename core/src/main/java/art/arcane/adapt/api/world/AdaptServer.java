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

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.PlayerStateRegistry;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.minion.MinionBurden;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.notification.AdvancementNotification;
import art.arcane.adapt.api.notification.SoundNotification;
import art.arcane.adapt.api.potion.AdaptPotionRegistry;
import art.arcane.adapt.api.preference.PlayerPreferences;
import art.arcane.adapt.api.recipe.AdaptRecipeBook;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.xp.SpatialXP;
import art.arcane.adapt.content.item.ExperienceOrb;
import art.arcane.adapt.content.item.KnowledgeOrb;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XPMultiplier;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.RuntimeMessages;
import art.arcane.adapt.platform.AdaptScheduler;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.inventory.CraftItemEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerPreLoginEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.adapt.util.common.io.SQLManager;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.BlockingTaskExecutor;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.project.redis.codec.ResetNotice;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;

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
  private final ExecutorService playerClaimExecutor = BlockingTaskExecutor.create("Adapt player claims");
  private final Map<UUID, Integer> orbUseTicks = new ConcurrentHashMap<>();
  private final Map<UUID, AdaptPlayer> players = new ConcurrentHashMap<>();
  private final ConcurrentMap<UUID, CompletableFuture<PlayerDataClaimFence.Claim>> playerDataClaims =
      new ConcurrentHashMap<>();
  private final PlayerDataClaimFence playerDataClaimFence = new PlayerDataClaimFence();
  private final ConcurrentMap<UUID, PlayerRef> runtimeSessions = new ConcurrentHashMap<>();
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
  private final ExpiringCache<UUID, LoadedPlayerData> prefetchedPlayerData =
      new ExpiringCache<>(30, TimeUnit.SECONDS, 2048);
  private final ExpiringCache<UUID, Long> resetFenceEpochs = new ExpiringCache<>(1, TimeUnit.MINUTES, 2048);
  private final ExpiringCache<UUID, Boolean> profileFailureReported =
      new ExpiringCache<>(10, TimeUnit.MINUTES, 8192);
  private final boolean sqlPersistenceEnabled;
  @Getter
  private final SkillRegistry skillRegistry = new SkillRegistry();
  @Getter
  private volatile List<PlayerRef> onlinePlayerSnapshot = List.of();
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
    for (PlayerRef player : AdaptCore.platform().host().onlinePlayers()) {
      runtimeSessions.put(player.uuid(), player);
      unavailableOnlinePlayers.add(player.uuid());
      claimOnlinePlayer(player.uuid(), 0);
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
    PlayerRef player = runtimeSessions.get(playerId);
    if (player == null || getOnlineAdaptPlayer(playerId) != null) {
      return;
    }
    if (!Tasks.runEntity(player, () -> {
      if (acceptingPlayerClaims.get() && runtimeSessions.get(playerId) == player
          && player.isOnline() && onlinePlayer(playerId) == player
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
    PlayerRef player = runtimeSessions.get(playerId);
    if (player == null) {
      return;
    }
    int delayTicks = onlineProfileRecoveryDelayTicks(playerId, recoveryAttempt);
    if (!Tasks.runEntity(player, () -> {
      if (acceptingPlayerClaims.get() && runtimeSessions.get(playerId) == player
          && player.isOnline() && onlinePlayer(playerId) == player
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

  private void reportProfileReady(PlayerRef player) {
    AdaptLog.verbose(() -> profileReadyMessage(
        player.name(), player.uuid(), sqlPersistenceEnabled));
  }

  private void reportProfileFailure(UUID playerId, String phase, Throwable error) {
    if (profileFailureReported.asMap().putIfAbsent(playerId, Boolean.TRUE) != null) {
      AdaptLog.verbose(() -> "Adapt profile remains unavailable for " + playerId + " after " + phase
          + " failure: " + error.getClass().getSimpleName()
          + (error.getMessage() == null ? "" : " (" + error.getMessage() + ")"));
      return;
    }
    AdaptLog.warn("Adapt profile is unavailable for " + playerId + " after " + phase
        + " failure; Minecraft login remains allowed: " + error.getClass().getSimpleName()
        + (error.getMessage() == null ? "" : " (" + error.getMessage() + ")"), error);
  }

  public void offer(SpatialXP xp) {
    spatialXpLedger.offer(xp, M.ms());
  }

  public void takeSpatial(AdaptPlayer p, Loc playerLocation) {
    if (spatialXpLedger.size() == 0 || p == null || playerLocation == null) {
      return;
    }

    try {
      SpatialXpLedger.Claim claim = spatialXpLedger.claim(playerLocation, M.ms());
      if (claim != null) {
        XP.xp(p, claim.skill(), claim.xp(), "skill:" + claim.skill().getName() + ":spatial");
      }
    } catch (Throwable error) {
      if (spatialFailureReported.compareAndSet(false, true)) {
        AdaptLog.warn("Spatial XP processing failed; further spatial failures will be suppressed until reload.");
        AdaptLog.error(error);
      }
    }
  }

  boolean hasSpatialTickets() {
    return spatialXpLedger.size() > 0;
  }

  public void join(PlayerRef p) {
    if (!join(p, true)) {
      claimOnlinePlayer(p.uuid(), 0);
    }
  }

  private boolean join(PlayerRef p, boolean refreshSnapshots) {
    UUID playerId = p.uuid();
    runtimeSessions.put(playerId, p);
    unavailableOnlinePlayers.add(playerId);
    placeholders().evictNow(playerId);
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

  private boolean joinSerialized(PlayerRef p, boolean refreshSnapshots) {
    // A retired AdaptPlayer lingers in the map for a minute after quit, and so does a prefetched
    // snapshot. Reusing either would resurrect data that was purged while they sat there, so a
    // purged player always falls through to a fresh load.
    UUID playerId = p.uuid();
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
          onlineMembershipRevision.incrementAndGet();
          if (refreshSnapshots) {
            scheduleOnlinePlayerSnapshotRefresh();
          }
          placeholders().publishPlayer(existing);
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

    boolean replacedLiveProfile = false;
    if (existing != null) {
      players.remove(playerId, existing);
      if (existing.isRuntimeReady()) {
        existing.unregister();
        playerDataClaimFence.revoke(playerId);
        replacedLiveProfile = true;
      }
    }

    LoadedPlayerData prefetched;
    if (purged || (replacedLiveProfile && !sqlPersistenceEnabled)) {
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
      onlineMembershipRevision.incrementAndGet();
      if (refreshSnapshots) {
        scheduleOnlinePlayerSnapshotRefresh();
      }
      placeholders().publishPlayer(a);
      reportProfileReady(p);
      return true;
    } catch (Throwable error) {
      rollbackPlayerRuntimeActivation(playerId, p, a, error);
      throw error;
    }
  }

  private void rollbackPlayerRuntimeActivation(
      UUID playerId, PlayerRef player, AdaptPlayer adaptPlayer, Throwable activationFailure) {
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
    PlayerRef session = runtimeSessions.get(p);
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
      AdaptLog.warn("Failed to retire Adapt player " + p + ": " + error.getMessage());
      AdaptLog.error(error);
    }
    revokePlayerDataClaims(p);
    CompletableFuture<Boolean> potionRetention = retainPotionState(p, a.getPlayer());
    // Keep the entry briefly after quit so late quit listeners/tasks do not
    // re-create a new AdaptPlayer for an offline player.
    try {
      placeholders().evictAfterGrace(p);
      onlineAdaptPlayers.remove(p, a);
      recipeBookSyncScheduled.remove(p);
      removeLearnedPlayer(p);
      onlineMembershipRevision.incrementAndGet();
      scheduleOnlinePlayerSnapshotRefresh();
    } catch (Throwable error) {
      AdaptLog.warn("Failed to clear runtime indexes for Adapt player " + p + ": " + error.getMessage());
      AdaptLog.error(error);
    }
    return potionRetention;
  }

  private void revokePlayerDataClaims(UUID playerId) {
    synchronized (playerOperationLock(playerId)) {
      playerDataClaimFence.revoke(playerId);
      prefetchedPlayerData.invalidate(playerId);
    }
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
        AdaptLog.warn("Failed to retire Adapt player " + playerId + " during shutdown: " + error.getMessage());
        AdaptLog.error(error);
      }
    }
    if (!awaitPotionRetention(potionRetentions, POTION_RETENTION_TIMEOUT_MILLIS)) {
      AdaptLog.warn("Adapt potion-state retention was incomplete at shutdown.");
    }
    players.clear();
    orbUseTicks.clear();
    prefetchedPlayerData.invalidateAll();
    playerDataClaimFence.clear();
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

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEvent event) {
    if (!event.action().isRightClick()) {
      return;
    }
    PlayerRef player = event.player();
    Hand hand = event.hand();
    if (hand == null) {
      return;
    }
    ItemRef item = player.inventory().hand(hand);
    if (!isOrb(item) && !usedOrbThisTick(player)) {
      return;
    }
    boolean denied = event.useItem() == UseResult.DENY;
    event.setUseBlock(UseResult.DENY);
    event.setUseItem(UseResult.DENY);
    if (!denied) {
      useOrb(player, hand);
    }
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerInteractEntityEvent event) {
    PlayerRef player = event.player();
    if (!isOrb(player.inventory().hand(event.hand())) && !usedOrbThisTick(player)) {
      return;
    }
    boolean denied = event.isCancelled();
    event.setCancelled(true);
    if (!denied) {
      useOrb(player, event.hand());
    }
  }

  private static PlaceholderPort placeholders() {
    return AdaptPlayer.placeholderPort();
  }

  private static PlayerSessionPort sessions() {
    return AdaptPorts.get(PlayerSessionPort.class, PlayerSessionPort.NONE);
  }

  private static PlayerRef onlinePlayer(UUID playerId) {
    return AdaptCore.platform().host().player(playerId);
  }

  private boolean isOrb(ItemRef item) {
    return item != null && !item.isEmpty() && (KnowledgeOrb.io.hasData(item) || ExperienceOrb.io.hasData(item));
  }

  private boolean usedOrbThisTick(PlayerRef player) {
    Integer tick = orbUseTicks.get(player.uuid());
    return tick != null && tick == player.ticksLived();
  }

  private void useOrb(PlayerRef player, Hand hand) {
    if (usedOrbThisTick(player)) {
      return;
    }
    ItemRef item = player.inventory().hand(hand);
    if (item == null || item.amount() < 1 || !isOrb(item)) {
      return;
    }
    orbUseTicks.put(player.uuid(), player.ticksLived());
    KnowledgeOrb.Data knowledge;
    ExperienceOrb.Data experience;
    try {
      knowledge = KnowledgeOrb.get(item);
      experience = knowledge == null ? ExperienceOrb.get(item) : null;
    } catch (RuntimeException error) {
      AdaptLog.warn("Unable to read orb for " + player.uuid());
      AdaptLog.error(error);
      return;
    }
    if (knowledge == null && experience == null) {
      return;
    }
    ItemRef remaining = item.copyWithAmount(item.amount() - 1);
    player.inventory().setHand(hand, remaining.amount() == 0 ? null : remaining);
    try {
      boolean applied = knowledge != null ? knowledge.apply(player) : experience.apply(player);
      if (!applied) {
        player.inventory().setHand(hand, item);
        return;
      }
      notifyOrbUse(player, knowledge == null ? null : knowledge.getKnowledgeMap());
    } catch (RuntimeException error) {
      AdaptLog.warn("Unable to finish orb reward for " + player.uuid());
      AdaptLog.error(error);
    }
  }

  private void notifyOrbUse(PlayerRef player, Map<String, Integer> knowledge) {
    AdaptPlayer adaptPlayer = getOnlineAdaptPlayer(player.uuid());
    if (adaptPlayer == null) {
      return;
    }
    SoundNotification.builder()
        .sound(Sounds.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM)
        .volume(0.35f).pitch(1.455f)
        .build().play(adaptPlayer);
    SoundNotification.builder()
        .sound(Sounds.ENTITY_SHULKER_OPEN)
        .volume(1f).pitch(1.655f)
        .build().play(adaptPlayer);
    if (knowledge == null) {
      return;
    }
    for (Map.Entry<String, Integer> entry : knowledge.entrySet()) {
      Skill<?> skill = getSkillRegistry().getSkill(entry.getKey());
      if (skill == null) {
        continue;
      }
      adaptPlayer.getNot().queue(AdvancementNotification.builder()
          .icon(Id.minecraft("book"))
          .model(CustomModel.get(Id.minecraft("book"), "snippets", "gui", "knowledge"))
          .title(AdaptLanguage.textStyled(C.GRAY.toString(), RuntimeMessages.KNOWLEDGE_GAIN,
              trusted("amount", C.WHITE + String.valueOf(entry.getValue())),
              trusted("skill", skill.getDisplayName())))
          .build());
    }
  }

  @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void on(PlayerJoinEvent e) {
    PlayerRef p = e.player();
    join(p);
  }

  @Handle(priority = EventPriority.HIGHEST, ignoreCancelled = false)
  public void on(PlayerPreLoginEvent e) {
    if (!e.allowed()) {
      return;
    }

    UUID uuid = e.uuid();
    if (prefetchedPlayerData.getIfPresent(uuid) == null) {
      claimOnlinePlayer(uuid, 0);
    }
  }

  private PlayerDataClaimFence.Claim awaitPlayerDataClaim(UUID playerId) throws Exception {
    CompletableFuture<PlayerDataClaimFence.Claim> claim = beginPlayerDataClaim(
        playerId,
        playerDataClaims,
        playerClaimExecutor,
        acceptingPlayerClaims,
        (UUID id) -> fencedClaim(playerDataClaimFence, id, AdaptPlayer::claimPlayerData)
    );
    try {
      return claim.get(40L, TimeUnit.SECONDS);
    } catch (TimeoutException timeout) {
      claim.thenAccept((PlayerDataClaimFence.Claim late) ->
          playerDataClaimFence.close(playerId, late.ticket()));
      throw timeout;
    }
  }

  static PlayerDataClaimFence.Claim fencedClaim(
      PlayerDataClaimFence fence, UUID playerId, Function<UUID, LoadedPlayerData> loader) {
    PlayerDataClaimFence.Ticket ticket = fence.open(playerId);
    try {
      return new PlayerDataClaimFence.Claim(loader.apply(playerId), ticket);
    } catch (RuntimeException | Error error) {
      fence.close(playerId, ticket);
      throw error;
    }
  }

  private void awaitAndCachePlayerDataClaim(UUID playerId) throws Exception {
    for (int attempt = 0; attempt < RESET_RACE_CLAIM_ATTEMPTS; attempt++) {
      PlayerDataClaimFence.Claim claimed = awaitPlayerDataClaim(playerId);
      if (cacheClaimedPlayerData(playerId, claimed)) {
        return;
      }
    }
    throw new IllegalStateException(
        "Player-data ownership kept changing during login claim for " + playerId);
  }

  private boolean cacheClaimedPlayerData(UUID playerId, PlayerDataClaimFence.Claim claimed) {
    synchronized (playerOperationLock(playerId)) {
      if (!playerDataClaimFence.close(playerId, claimed.ticket())) {
        return false;
      }
      LoadedPlayerData selected = selectClaimAfterResetEpoch(
          prefetchedPlayerData.getIfPresent(playerId),
          claimed.data(),
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

  static <T> CompletableFuture<T> beginPlayerDataClaim(
      UUID playerId,
      ConcurrentMap<UUID, CompletableFuture<T>> activeClaims,
      Executor executor,
      AtomicBoolean acceptingClaims,
      Function<UUID, T> claimLoader
  ) {
    CompletableFuture<T> candidate = new CompletableFuture<>();
    if (!acceptingClaims.get()) {
      candidate.completeExceptionally(new IllegalStateException("Adapt is shutting down"));
      return candidate;
    }

    CompletableFuture<T> claim = activeClaims.putIfAbsent(playerId, candidate);
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
        T loaded = null;
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

  private static <T> void completePlayerDataClaim(
      ConcurrentMap<UUID, CompletableFuture<T>> activeClaims,
      UUID playerId,
      CompletableFuture<T> claim,
      T loaded,
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

  static <T> void rejectPlayerDataClaims(
      ConcurrentMap<UUID, CompletableFuture<T>> activeClaims,
      Throwable failure
  ) {
    for (Map.Entry<UUID, CompletableFuture<T>> entry : activeClaims.entrySet()) {
      rejectPlayerDataClaim(activeClaims, entry.getKey(), entry.getValue(), failure);
    }
  }

  private static <T> void rejectPlayerDataClaim(
      ConcurrentMap<UUID, CompletableFuture<T>> activeClaims,
      UUID playerId,
      CompletableFuture<T> claim,
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

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void on(PlayerQuitEvent e) {
    PlayerRef p = e.player();
    UUID playerId = p.uuid();
    orbUseTicks.remove(playerId);
    quit(playerId);
    sessions().retireViewer(playerId);
    AdaptHud.clear(p);
  }

  @Handle(ignoreCancelled = false)
  public void on(CraftItemEvent e) {
    PlayerRef p = e.player();
    Id recipe = e.recipeKey();
    if (p != null && recipe != null) {
      Adaptation<?> required = getSkillRegistry().getRequiredAdaptation(recipe);
      if (required == null || required.getActiveLevel(p) >= getSkillRegistry().getRequiredRecipeLevel(recipe)) {
        return;
      }

      Skill<?> requiredSkill = required.getSkill();
      String skillName = requiredSkill == null
          ? AdaptLanguage.text(RuntimeMessages.UNKNOWN_SKILL)
          : requiredSkill.getDisplayName();
      SoundPlayer sp = SoundPlayer.of(p);
      AdaptHud.actionBar(p, C.RED + AdaptLanguage.text(
          RuntimeMessages.REQUIRES_ADAPTATION,
          trusted("adaptation", required.getDisplayName()),
          trusted("skill", skillName)
      ));
      sp.play(p.location(), Sounds.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.8f);
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

    PlayerRef player = online.getPlayer();
    return player != null && Tasks.runEntity(player, () -> {
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
    int level = levels == null ? 0 : levels.getOrDefault(adaptationName, 0);
    if (level <= 0) {
      return 0;
    }
    Skill<?> skill = skillRegistry.getSkill(skillName);
    if (skill == null || !skill.isEnabled()) {
      return 0;
    }
    for (Adaptation<?> adaptation : skill.getAdaptations()) {
      if (adaptation.getName().equals(adaptationName)) {
        return adaptation.isEnabled() && PlayerPreferences.isEnabled(adaptation, online.getData(), level) ? level : 0;
      }
    }
    return 0;
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

    UUID playerId = player.getPlayer().uuid();
    Set<String> learned = learnedAdaptationsByPlayer.computeIfAbsent(playerId, unused -> ConcurrentHashMap.newKeySet());
    Map<String, Integer> levels = learnedAdaptationLevelsByPlayer.computeIfAbsent(playerId, unused -> new ConcurrentHashMap<>());
    if (level > 0) {
      learned.add(adaptationName);
      levels.put(adaptationName, level);
      playersByLearnedAdaptation.computeIfAbsent(adaptationName, unused -> ConcurrentHashMap.newKeySet()).add(playerId);
      learnedAdaptPlayerSnapshots.remove(adaptationName);
      learnerIndexRevision.incrementAndGet();
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
    synchronizeRecipeBook(playerId, player);
  }

  public void refreshLearnedAdaptations(AdaptPlayer player) {
    if (player == null) {
      return;
    }

    UUID playerId = player.getPlayer().uuid();
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
    synchronizeRecipeBook(playerId, player);
  }

  public void synchronizeRecipeBooksForOnlinePlayers() {
    for (Map.Entry<UUID, AdaptPlayer> entry : onlineAdaptPlayers.entrySet()) {
      synchronizeRecipeBook(entry.getKey(), entry.getValue());
    }
  }

  public void synchronizeRecipeBook(UUID playerId, AdaptPlayer adaptPlayer) {
    if (playerId == null || adaptPlayer == null) {
      return;
    }

    PlayerRef player = adaptPlayer.getPlayer();
    if (player == null) {
      return;
    }

    if (!recipeBookSyncScheduled.add(playerId)) {
      return;
    }

    boolean scheduled = Tasks.runEntity(player, () -> {
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
          adaptation -> PlayerPreferences.isEnabled(adaptation, adaptPlayer.getData(), adaptation.getLevel(adaptPlayer))
              ? adaptation.getLevel(adaptPlayer) : 0
      );
      AdaptRecipeBook.synchronize(player, plan);
    }, 1);
    if (!scheduled) {
      recipeBookSyncScheduled.remove(playerId);
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
                                                       AdaptPlayer expected, PlayerRef player) {
    if (playerId == null || ownerToken == null || expected == null || player == null
        || epoch < 1L || !Tasks.isOwnedByCurrentRegion(player)) {
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
      sessions().retireViewer(playerId);
      PlayerTransferPort transfers = AdaptPlayer.transferPort();
      if (transfers.isActive()) {
        transfers.retainRetiredTransfer(snapshot);
      }
      onlineMembershipRevision.incrementAndGet();
      scheduleOnlinePlayerSnapshotRefresh();
      tearDownPlayerRuntime(player);
      return snapshot;
    }
  }

  public void onPersistenceFenceLost(UUID playerId, UUID ownerToken, long epoch) {
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
    PlayerRef player = adaptPlayer.getPlayer();
    if (!Tasks.runEntity(player, () -> {
      FencedPlayerSnapshot snapshot = retirePlayerForTransfer(
          playerId, ownerToken, epoch, adaptPlayer, player);
      if (snapshot == null) {
        return;
      }
      AdaptLog.warn("Retired Adapt for " + playerId
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
      AdaptLog.warn("Could not dispatch persistence-fence retirement for " + playerId
          + "; the rejected fence was invalidated immediately.");
    }
  }

  public void applyRemoteReset(UUID playerId, UUID operationId, long epoch, boolean purge) {
    if (playerId == null || operationId == null || epoch < 1L) {
      return;
    }
    recordResetFenceEpoch(playerId, epoch);
    PlayerDataPersistenceQueue queue = AdaptCore.get().getPlayerDataPersistenceQueue();
    if (queue != null) {
      queue.discardFencedSavesBefore(playerId, epoch);
    }

    AdaptPlayer adaptPlayer = onlineAdaptPlayers.get(playerId);
    if (adaptPlayer == null || !adaptPlayer.isRuntimeReady()) {
      return;
    }
    PlayerRef player = adaptPlayer.getPlayer();
    if (!Tasks.runEntity(player, () -> retireAfterRemoteReset(
        playerId, operationId, epoch, purge, adaptPlayer, player))) {
      invalidateRejectedFenceAdvance(playerId, adaptPlayer, epoch);
      AdaptLog.warn("Could not dispatch remote profile retirement for " + playerId
          + "; its old persistence fence was invalidated.");
    }
  }

  private void retireAfterRemoteReset(UUID playerId, UUID operationId, long epoch,
                                      boolean purge, AdaptPlayer expected, PlayerRef player) {
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
      sessions().retireViewer(playerId);
      onlineMembershipRevision.incrementAndGet();
      scheduleOnlinePlayerSnapshotRefresh();
      tearDownPlayerRuntime(player);
      AdaptLog.info("Retired remote Adapt profile after " + (purge ? "purge" : "reset") + " "
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

  public AdaptPlayer getPlayer(PlayerRef p) {
    if (p == null) {
      return null;
    }
    UUID playerId = p.uuid();
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
      PlayerRef player;
      synchronized (playerOperationLock(playerId)) {
        player = onlinePlayer(playerId);
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
    PlayerDataPersistenceQueue queue = AdaptCore.get().getPlayerDataPersistenceQueue();
    if (queue == null) {
      completion.complete(PlayerDataResetResult.DISPATCH_REJECTED);
      return;
    }

    PlayerRef expectedPlayer;
    AdaptPlayer expectedAdaptPlayer;
    synchronized (playerOperationLock(playerId)) {
      expectedPlayer = onlinePlayer(playerId);
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
      PlayerRef expectedPlayer,
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
      accepted = Tasks.runEntity(expectedPlayer, () -> applyFencedLiveReset(
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
      globalAccepted = runGlobal(() -> {
        PlayerRef currentPlayer;
        synchronized (playerOperationLock(playerId)) {
          currentPlayer = onlinePlayer(playerId);
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
        boolean accepted = Tasks.runEntity(currentPlayer, () -> {
          synchronized (playerOperationLock(playerId)) {
            AdaptPlayer current = players.get(playerId);
            if (current == null || !current.isRuntimeReady()
                || current.getPlayer() != currentPlayer) {
              completion.complete(PlayerDataResetResult.PURGED);
              return;
            }
            if (current.installNewerPersistenceFence(
                token.ownerToken(), token.epoch(), 0L)) {
              PlayerDataPersistenceQueue queue = AdaptCore.get().getPlayerDataPersistenceQueue();
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
      PlayerRef expectedPlayer,
      AdaptPlayer expectedAdaptPlayer,
      PlayerData replacement,
      SQLManager.SqlToken token,
      long generation,
      CompletableFuture<PlayerDataResetResult> completion
  ) {
    synchronized (playerOperationLock(playerId)) {
      PlayerRef currentPlayer = onlinePlayer(playerId);
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
      AdaptLog.info("Reset live Adapt data for " + expectedPlayer.name() + " (" + playerId + ")");
      completion.complete(PlayerDataResetResult.LIVE);
    }
  }

  private void publishFencedReset(
      UUID playerId, UUID operationId, SQLManager.SqlToken token, boolean purge) {
    PlayerTransferPort transfers = AdaptPlayer.transferPort();
    if (!transfers.isActive()) {
      return;
    }
    try {
      transfers.publishReset(new ResetNotice(
          playerId, operationId, token.epoch(), purge));
    } catch (Throwable error) {
      AdaptLog.warn("SQL profile mutation committed for " + playerId
          + " but its Redis reset notice failed: " + error.getMessage());
      AdaptLog.error(error);
    }
  }

  private void completePlayerDataReset(UUID playerId, PlayerRef expectedPlayer,
                                       CompletableFuture<PlayerDataResetResult> completion) {
    if (completion.isDone()) {
      return;
    }

    PlayerRef dispatchTarget = null;
    try {
      synchronized (playerOperationLock(playerId)) {
        PlayerRef player = onlinePlayer(playerId);
        if (player == null) {
          purgeStoredPlayerData(playerId, players.get(playerId));
          completion.complete(PlayerDataResetResult.PURGED);
          return;
        }
        if (player != expectedPlayer || !Tasks.isOwnedByCurrentRegion(player)) {
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

  private void dispatchPlayerDataReset(UUID playerId, PlayerRef player,
                                       CompletableFuture<PlayerDataResetResult> completion) {
    Runnable retired = () -> completion.complete(PlayerDataResetResult.DISPATCH_REJECTED);
    boolean accepted;
    try {
      accepted = Tasks.runEntity(
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
    AdaptLog.warn("Failed to complete player data reset for " + playerId + ": " + error.getMessage());
    AdaptLog.error(error);
    completion.complete(PlayerDataResetResult.DISPATCH_REJECTED);
  }

  private PlayerDataResetResult resetPlayerDataOwned(UUID playerId, PlayerRef player) {
    if (!Tasks.isOwnedByCurrentRegion(player)) {
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
      AdaptLog.warn("Reset player data for " + playerId + " was not durably accepted by persistence.");
      return PlayerDataResetResult.DISPATCH_REJECTED;
    }
    installPristinePlayerData(adaptPlayer, player, replacement);
    AdaptLog.info("Reset live Adapt data for " + player.name() + " (" + playerId + ")");
    return PlayerDataResetResult.LIVE;
  }

  private void installPristinePlayerData(AdaptPlayer adaptPlayer, PlayerRef player,
                                         PlayerData replacement) {
    UUID playerId = player.uuid();
    AdaptPlayer.forgetLoadFailure(playerId);
    adaptPlayer.replaceData(replacement);
    refreshLearnedAdaptations(adaptPlayer);
    tearDownPlayerRuntime(player);
    adaptPlayer.reconcileStatTrackers();
    placeholders().publishPlayer(adaptPlayer);
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

    MinionBurden.get().clearOwner(playerId);
    AdaptPotionRegistry.forget(playerId);
    PlayerStateRegistry.clearPlayer(playerId);
    sessions().retireViewer(playerId);
    onlineMembershipRevision.incrementAndGet();
    scheduleOnlinePlayerSnapshotRefresh();
    AdaptLog.info("Purged stored Adapt data for " + playerId);
  }

  private void tearDownPlayerRuntime(PlayerRef player) {
    UUID playerId = player.uuid();
    placeholders().evictNow(playerId);
    PlayerSessionPort sessions = sessions();
    AdaptAttributeService.get().clearAllAdapt(player);
    MinionBurden.get().clearOwner(playerId);
    PlayerStateRegistry.clearPlayer(playerId);
    sessions.clearViewer(playerId);
    sessions.discardGlowViewer(playerId);

    Tasks.runEntity(player, () -> {
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

  private static boolean runGlobal(Runnable task) {
    AdaptScheduler scheduler = Tasks.scheduler();
    return scheduler != null && scheduler.runGlobal(task);
  }

  private void scheduleOnlinePlayerSnapshotRefresh() {
    if (onlineSnapshotRefreshScheduled.compareAndSet(false, true)) {
      Tasks.s(this::rebuildOnlinePlayerSnapshots);
    }
  }

  private void rebuildOnlinePlayerSnapshots() {
    long revision = onlineMembershipRevision.get();
    ArrayList<AdaptPlayer> adaptPlayers = new ArrayList<>(onlineAdaptPlayers.size());
    ArrayList<PlayerRef> playerSnapshot = new ArrayList<>(onlineAdaptPlayers.size());

    for (AdaptPlayer adaptPlayer : onlineAdaptPlayers.values()) {
      if (adaptPlayer == null || !adaptPlayer.isRuntimeReady()) {
        continue;
      }
      PlayerRef player = adaptPlayer.getPlayer();
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

  public void openSkillGUI(Skill<?> skill, PlayerRef p) {
    skill.openGui(p);
  }

  public void openAdaptGui(PlayerRef p) {
    SkillsGui.open(p);
  }

  public void openAdaptationGUI(Adaptation<?> adaptation, PlayerRef p) {
    adaptation.openGui(p);
  }

  public void boostXP(double boost, long ms) {
    data.getMultipliers().add(new XPMultiplier(boost, ms));
  }

  public void load() {
    File f = AdaptEnvironment.dataFile("data", "server-data.json");
    if (f.exists()) {
      try {
        AdaptServerData loaded = Json.fromJson(IO.readAll(f), AdaptServerData.class);
        if (loaded != null) {
          loaded.normalize();
          data = loaded;
        }
      } catch (Throwable error) {
        AdaptLog.warn("Failed to load global boosts data from " + f.getAbsolutePath());
        AdaptLog.error(error);
      }
    }
  }

  @SneakyThrows
  public void save() {
    File file = AdaptEnvironment.dataFile("data", "server-data.json");
    PlayerDataPersistenceQueue.writeSnapshot(file, Json.toJson(data, true));
  }

  static CompletableFuture<Boolean> retainPotionState(UUID playerId, PlayerRef player) {
    if (playerId == null || AdaptPotionRegistry.applied(playerId).isEmpty()) {
      return CompletableFuture.completedFuture(true);
    }
    return runOwnedPotionRetention(
        playerId, player, () -> AdaptPotionRegistry.retainActive(player));
  }

  static CompletableFuture<Boolean> runOwnedPotionRetention(UUID playerId, PlayerRef player,
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
      if (Tasks.isOwnedByCurrentRegion(player)) {
        retention.run();
      } else if (!Tasks.runEntity(player, retention, () -> completion.complete(false))) {
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
      AdaptLog.warn("Interrupted while waiting for Adapt potion-state retention.");
      AdaptLog.error(error);
      return false;
    } catch (ExecutionException | TimeoutException error) {
      AdaptLog.warn("Adapt potion-state retention did not complete before shutdown.");
      AdaptLog.error(error);
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
    AdaptLog.warn("Failed to retain Adapt potion state for " + playerId + ": " + error.getMessage());
    AdaptLog.error(error);
  }

  public enum PlayerDataResetResult {
    LIVE,
    PURGED,
    DISPATCH_REJECTED
  }
}

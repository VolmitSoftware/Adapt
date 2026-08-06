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
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.papi.AdaptPlaceholders;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.scheduling.J;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

@Getter
public class AdaptPlayer extends TickedObject {
  private static final long UPDATE_INTERVAL_MS = 1_000L;
  private static final long SAVE_INTERVAL_MS = 60_000L;
  private static final long SPATIAL_INTERVAL_MS = 500L;
  private static final long MIN_TICK_INTERVAL_MS = 50L;
  private static final long UPDATE_SALT = 0x5DEECE66DL;
  private static final long SAVE_SALT = 0x9E3779B97F4A7C15L;
  private static final Set<UUID> LOAD_FAILURE_GUARD = ConcurrentHashMap.newKeySet();

  private final Player player;
  private volatile PlayerData data;
  private final Set<String> dirtyStats = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean dirtyStatEvaluationScheduled = new AtomicBoolean();
  private final AtomicBoolean loginStatReconciliationComplete = new AtomicBoolean();
  @Getter(AccessLevel.NONE)
  private Location positionScratch;
  private volatile FxPosition fxPosition;
  private Notifier not;
  private Notifier actionBarNotifier;
  private AdvancementHandler advancementHandler;
  private long lastSeen;
  private long nextUpdateAt;
  private long nextSaveAt;
  private volatile boolean pendingDataDeletion;
  private volatile boolean runtimeReady;

  public AdaptPlayer(Player p) {
    this(p, null);
  }

  public AdaptPlayer(Player p, PlayerData prefetchedData) {
    super("players", p.getUniqueId().toString(), MIN_TICK_INTERVAL_MS);
    this.player = p;
    data = prefetchedData == null ? loadPlayerData(p.getUniqueId()) : prefetchedData;
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
    not.activateRuntime();
    actionBarNotifier.activateRuntime();
  }

  public static PlayerData loadPlayerData(UUID uuid) {
    if (PlayerDataPurgeGuard.clear(uuid)) {
      Adapt.info("Loading default player data for " + uuid + " (data was purged this session)");
      LOAD_FAILURE_GUARD.remove(uuid);
      return new PlayerData();
    }

    boolean loadFailed = false;
    PlayerDataPersistenceQueue persistenceQueue = Adapt.instance.getPlayerDataPersistenceQueue();
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
          error.printStackTrace();
        }
      }
    }

    File f = getPlayerDataFile(uuid);
    File recoveryFile = PlayerDataPersistenceQueue.sqlRecoveryFile(f);
    if (recoveryFile.exists()) {
      try {
        String recoveredJson = IO.readAll(recoveryFile);
        PlayerData recovered = PlayerData.fromJson(recoveredJson);
        if (recovered == null) {
          throw new IllegalArgumentException("SQL recovery player data JSON resolved to null");
        }
        if (persistenceQueue != null) {
          persistenceQueue.queueSave(uuid, recoveredJson, f);
        }
        LOAD_FAILURE_GUARD.remove(uuid);
        return recovered;
      } catch (Throwable error) {
        loadFailed = true;
        LOAD_FAILURE_GUARD.add(uuid);
        Adapt.warn("Failed to load SQL recovery player data for " + uuid + " from "
            + recoveryFile.getAbsolutePath() + ": " + error.getClass().getSimpleName()
            + (error.getMessage() == null ? "" : " (" + error.getMessage() + ")"));
        error.printStackTrace();
      }
    }

    boolean upload = false;
    if (AdaptConfig.get().isUseSql()) {
      if (Adapt.instance.getRedisSync() != null) {
        java.util.Optional<art.arcane.adapt.api.world.PlayerData> opt = Adapt.instance.getRedisSync().cachedData(uuid);
        if (opt.isPresent()) {
          Adapt.verbose("Using cached data for player: " + uuid);
          LOAD_FAILURE_GUARD.remove(uuid);
          return opt.get();
        }
      }

      if (Adapt.instance.getSqlManager() != null) {
        String sqlData = Adapt.instance.getSqlManager().fetchData(uuid);
        if (sqlData != null) {
          try {
            PlayerData parsed = PlayerData.fromJson(sqlData);
            if (parsed == null) {
              throw new IllegalArgumentException("SQL player data JSON resolved to null");
            }
            LOAD_FAILURE_GUARD.remove(uuid);
            return parsed;
          } catch (Throwable e) {
            loadFailed = true;
            LOAD_FAILURE_GUARD.add(uuid);
            Adapt.warn("Failed to parse SQL player data for " + uuid + ": " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"));
            e.printStackTrace();
          }
        }
        upload = true;
      }
    }

    if (f.exists()) {
      try {
        String text = IO.readAll(f);
        PlayerData parsed = PlayerData.fromJson(text);
        if (parsed == null) {
          throw new IllegalArgumentException("Player data JSON resolved to null");
        }
        if (upload) {
          PlayerDataPersistenceQueue queue = Adapt.instance.getPlayerDataPersistenceQueue();
          if (queue != null) {
            queue.queueSave(uuid, text, f);
          } else if (Adapt.instance.getSqlManager() != null) {
            Adapt.instance.getSqlManager().updateData(uuid, text);
          }
        }
        LOAD_FAILURE_GUARD.remove(uuid);
        return parsed;
      } catch (Throwable e) {
        loadFailed = true;
        LOAD_FAILURE_GUARD.add(uuid);
        Adapt.warn("Failed to load player data for " + uuid + " from " + f.getAbsolutePath() + ": " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")"));
        e.printStackTrace();
      }
    }

    if (!loadFailed) {
      LOAD_FAILURE_GUARD.remove(uuid);
    }
    return new PlayerData();
  }

  static File getPlayerDataFile(UUID uuid) {
    return new File(Adapt.instance.getDataFolder("data", "players"), uuid.toString() + ".json");
  }

  static void forgetLoadFailure(UUID uuid) {
    LOAD_FAILURE_GUARD.remove(uuid);
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
    if (canConsumeFood(cost, minFood)) {
      int food = player.getFoodLevel();
      double sat = player.getSaturation();

      if (sat >= cost) {
        sat = (player.getSaturation() - cost);
        cost = 0;
      } else if (player.getSaturation() > 0) {
        cost -= sat;
        sat = 0;
      }

      if (cost >= 1) {
        food -= (int) Math.floor(cost);
        cost = Math.floor(cost);
      }

      if (cost > 0) {
        if (sat >= cost) {
          sat -= cost;
          cost = 0;
        } else {
          sat++;
          food--;
        }
      }

      if (sat >= cost && cost > 0) {
        sat -= cost;
        cost = 0;
      }

      player.setFoodLevel(food);
      player.setSaturation((float) sat);

      return true;
    }

    return false;
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
      queueDelete(uuid, playerDataFile);
      return;
    }

    if (LOAD_FAILURE_GUARD.contains(uuid)) {
      Adapt.warn("Skipping save for " + uuid + " because player data failed to load earlier. Existing file is preserved.");
      return;
    }

    String json = this.data.toJson(AdaptConfig.get().isUseSql());
    PlayerDataPersistenceQueue queue = Adapt.instance.getPlayerDataPersistenceQueue();
    if (queue != null) {
      queue.queueSave(uuid, json, playerDataFile);
      return;
    }

    if (AdaptConfig.get().isUseSql()) {
      if (Adapt.instance.getRedisSync() != null) {
        Adapt.instance.getRedisSync().publish(uuid, json);
      }
      if (Adapt.instance.getSqlManager() != null) {
        Adapt.instance.getSqlManager().updateData(uuid, json);
      }
    } else {
      J.attempt(() -> IO.writeAll(playerDataFile, json));
    }
  }

  public void saveNow() {
    save();
  }

  @Override
  public void unregister() {
    if (!runtimeReady) {
      super.unregister();
      return;
    }
    runtimeReady = false;
    data.unbindRuntimeOwner(this);
    dirtyStats.clear();
    super.unregister();
    not.unregister();
    actionBarNotifier.unregister();
    save();
  }

  /**
   * Purges the persisted copy of this player's data and stops this instance from ever writing again.
   * Used for offline resets; online resets replace the live data instead so the player keeps playing.
   */
  void purge(UUID uuid) {
    pendingDataDeletion = true;
    purgeStoredData(uuid);
  }

  static void purgeStoredData(UUID uuid) {
    PlayerDataPurgeGuard.mark(uuid);
    LOAD_FAILURE_GUARD.remove(uuid);
    File local = getPlayerDataFile(uuid);
    Adapt.warn("Purging player data: " + local.getAbsolutePath());
    queueDelete(uuid, local);
  }

  public boolean shouldUnload() {
    if (player.isOnline()) {
      lastSeen = M.ms();
      return false;
    }

    return lastSeen + 60_000 < System.currentTimeMillis();
  }

  @Override
  public void onTick() {
    if (!runtimeReady) {
      return;
    }

    long now = M.ms();
    Location playerLocation = null;
    if (now >= nextUpdateAt) {
      if (data.isEffectsEnabled()) {
        playerLocation = capturePosition();
      }
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

  public void boostXPToRandom(AdaptPlayer p, double boost, int ms) {
    p.getData().getSkillLines().v().getRandom().boost(boost, ms);
  }

  public void boostXPToRecents(double boost, int ms) {
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
      getData().globalXPMultiplier(boostAmount, (int) boostTime);
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
    if (AdaptConfig.get().isUseSql() && Adapt.instance.getSqlManager() != null) {
      Adapt.instance.getSqlManager().delete(uuid);
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
}

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
import art.arcane.adapt.api.notification.AdvancementNotification;
import art.arcane.adapt.api.notification.SoundNotification;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.skill.SkillRegistry;
import art.arcane.adapt.api.tick.TickedObject;
import art.arcane.adapt.api.xp.SpatialXP;
import art.arcane.adapt.api.xp.XP;
import art.arcane.adapt.api.xp.XPMultiplier;
import art.arcane.adapt.content.gui.SkillsGui;
import art.arcane.adapt.content.item.ExperienceOrb;
import art.arcane.adapt.content.item.KnowledgeOrb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.io.Json;
import art.arcane.adapt.util.common.io.SQLManager;
import art.arcane.adapt.util.common.misc.CustomModel;
import art.arcane.adapt.util.common.misc.SoundPlayer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.io.IO;
import art.arcane.volmlib.util.math.M;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class AdaptServer extends TickedObject {
  private final ReentrantLock clearLock = new ReentrantLock();
  private final Map<UUID, AdaptPlayer> players = new ConcurrentHashMap<>();
  private final Map<UUID, AdaptPlayer> onlineAdaptPlayers = new ConcurrentHashMap<>();
  private final Map<UUID, Set<String>> learnedAdaptationsByPlayer = new ConcurrentHashMap<>();
  private final Map<UUID, Map<String, Integer>> learnedAdaptationLevelsByPlayer = new ConcurrentHashMap<>();
  private final Map<String, Set<UUID>> playersByLearnedAdaptation = new ConcurrentHashMap<>();
  private final Map<String, List<AdaptPlayer>> learnedAdaptPlayerSnapshots = new ConcurrentHashMap<>();
  private final AtomicBoolean spatialFailureReported = new AtomicBoolean(false);
  private final AtomicBoolean onlineSnapshotRefreshScheduled = new AtomicBoolean(false);
  private final AtomicLong onlineMembershipRevision = new AtomicLong();
  private final Cache<UUID, PlayerData> prefetchedPlayerData = Caffeine.newBuilder()
      .expireAfterWrite(2, TimeUnit.MINUTES)
      .maximumSize(2048)
      .build();
  @Getter
  private final List<SpatialXP> spatialTickets = new ArrayList<>();
  private volatile int spatialTicketCount;
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
    load();
  }

  public synchronized void startRuntime() {
    if (isRuntimeRegistered()) {
      return;
    }
    skillRegistry.startRuntime();
    activateRuntime();
    for (Player player : Bukkit.getOnlinePlayers()) {
      join(player, false);
    }
    rebuildOnlinePlayerSnapshots();
  }

  public void offer(SpatialXP xp) {
    if (xp == null || xp.getSkill() == null || xp.getLocation() == null) {
      return;
    }
    if (xp.getRadius() <= 0 || xp.getXp() <= 0 || xp.getMs() <= M.ms()) {
      return;
    }
    synchronized (spatialTickets) {
      spatialTickets.add(xp);
      spatialTicketCount = spatialTickets.size();
    }
  }

  public void takeSpatial(AdaptPlayer p, Location playerLocation) {
    if (spatialTicketCount == 0) {
      return;
    }

    try {
      SpatialXP x;
      synchronized (spatialTickets) {
        int size = spatialTickets.size();
        if (size == 0) {
          return;
        }
        x = spatialTickets.get(size - 1);
      }

      if (M.ms() > x.getMs()) {
        synchronized (spatialTickets) {
          spatialTickets.remove(x);
          spatialTicketCount = spatialTickets.size();
        }
        return;
      }

      if (!p.getPlayer().getClass().getSimpleName().equals("CraftPlayer")) {
        synchronized (spatialTickets) {
          spatialTickets.remove(x);
          spatialTicketCount = spatialTickets.size();
        }
        return;
      }

      if (playerLocation.getWorld().equals(x.getLocation().getWorld())) {
        double c = playerLocation.distanceSquared(x.getLocation());
        if (c < x.getRadius() * x.getRadius()) {
          double distl = M.lerpInverse(0, x.getRadius() * x.getRadius(), c);
          double xp = x.getXp() / (1.5D * ((distl * 9) + 1));
          synchronized (spatialTickets) {
            x.setXp(x.getXp() - xp);

            if (x.getXp() < 10) {
              xp += x.getXp();
              spatialTickets.remove(x);
              spatialTicketCount = spatialTickets.size();
            }
          }

          XP.xp(p, x.getSkill(), xp);
        }
      }
    } catch (Throwable error) {
      if (spatialFailureReported.compareAndSet(false, true)) {
        Adapt.warn("Spatial XP processing failed; further spatial failures will be suppressed until reload.");
        error.printStackTrace();
      }
    }
  }

  boolean hasSpatialTickets() {
    return spatialTicketCount > 0;
  }

  public void join(Player p) {
    join(p, true);
  }

  private void join(Player p, boolean refreshSnapshots) {
    AdaptPlayer existing = players.get(p.getUniqueId());
    if (existing != null) {
      if (existing.getPlayer() == p && existing.isRuntimeReady()) {
        onlineAdaptPlayers.put(p.getUniqueId(), existing);
        refreshLearnedAdaptations(existing);
        onlineMembershipRevision.incrementAndGet();
        existing.loggedIn();
        if (refreshSnapshots) {
          scheduleOnlinePlayerSnapshotRefresh();
        }
        return;
      }

      players.remove(p.getUniqueId(), existing);
      if (existing.isRuntimeReady()) {
        existing.unregister();
      }
    }

    PlayerData prefetched = existing == null ? takePrefetchedData(p.getUniqueId()) : existing.getData();
    AdaptPlayer a = new AdaptPlayer(p, prefetched);
    a.startRuntime();
    players.put(p.getUniqueId(), a);
    onlineAdaptPlayers.put(p.getUniqueId(), a);
    refreshLearnedAdaptations(a);
    onlineMembershipRevision.incrementAndGet();
    if (refreshSnapshots) {
      scheduleOnlinePlayerSnapshotRefresh();
    }
    a.loggedIn();
  }

  public void quit(UUID p) {
    AdaptPlayer a = players.get(p);
    if (a == null) return;
    a.unregister();
    // Keep the entry briefly after quit so late quit listeners/tasks do not
    // re-create a new AdaptPlayer for an offline player.
    prefetchedPlayerData.invalidate(p);
    onlineAdaptPlayers.remove(p, a);
    removeLearnedPlayer(p);
    onlineMembershipRevision.incrementAndGet();
    scheduleOnlinePlayerSnapshotRefresh();
  }

  @Override
  public void unregister() {
    new HashSet<>(players.keySet()).forEach(this::quit);
    players.clear();
    prefetchedPlayerData.invalidateAll();
    onlineAdaptPlayers.clear();
    learnedAdaptationsByPlayer.clear();
    learnedAdaptationLevelsByPlayer.clear();
    playersByLearnedAdaptation.clear();
    learnedAdaptPlayerSnapshots.clear();
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
        Skill<?> skill = getSkillRegistry().getSkill(data.getSkill());
        data.apply(p);
        SoundNotification.builder()
            .sound(Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM)
            .volume(0.35f).pitch(1.455f)
            .build().play(getPlayer(p));
        SoundNotification.builder()
            .sound(Sound.ENTITY_SHULKER_OPEN)
            .volume(1f).pitch(1.655f)
            .build().play(getPlayer(p));
        getPlayer(p).getNot().queue(AdvancementNotification.builder()
            .icon(Material.BOOK)
            .model(CustomModel.get(Material.BOOK, "snippets", "gui", "knowledge"))
            .title(C.GRAY + "+ " + C.WHITE + data.getKnowledge() + " " + skill.getDisplayName() + " Knowledge")
            .build());
      } else {
        ExperienceOrb.Data datax = ExperienceOrb.get(s.getItem());
        if (datax != null) {
          datax.apply(p);
          SoundNotification.builder()
              .sound(Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM)
              .volume(0.35f).pitch(1.455f)
              .build().play(getPlayer(p));
          SoundNotification.builder()
              .sound(Sound.ENTITY_SHULKER_OPEN)
              .volume(1f).pitch(1.655f)
              .build().play(getPlayer(p));
        }
      }
    }

  }

  @EventHandler(priority = EventPriority.LOWEST)
  public void on(PlayerJoinEvent e) {
    Player p = e.getPlayer();
    join(p);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(AsyncPlayerPreLoginEvent e) {
    if (e.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
      return;
    }

    UUID uuid = e.getUniqueId();
    if (players.containsKey(uuid) || prefetchedPlayerData.getIfPresent(uuid) != null) {
      return;
    }

    try {
      prefetchedPlayerData.put(uuid, AdaptPlayer.loadPlayerData(uuid));
    } catch (Throwable ignored) {
      Adapt.verbose("Failed to prefetch player data for " + uuid);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    Player p = e.getPlayer();
    quit(p.getUniqueId());
  }

  @EventHandler
  public void on(CraftItemEvent e) {
    if (e.getWhoClicked() instanceof Player p) {
      Adaptation<?> required = getSkillRegistry().getRequiredAdaptation(e.getRecipe());
      if (required == null || required.hasAdaptation(p)) {
        return;
      }

      Skill<?> requiredSkill = required.getSkill();
      String skillName = requiredSkill == null ? "Unknown Skill" : requiredSkill.getDisplayName();
      SoundPlayer sp = SoundPlayer.of(p);
      Adapt.actionbar(p, C.RED + "Requires " + required.getDisplayName() + C.RED + " from " + skillName);
      sp.play(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.5f, 1.8f);
      e.setCancelled(true);
    }
  }

  @Override
  public void onTick() {
    data.getMultipliers().removeIf(multiplier -> multiplier == null || multiplier.isExpired());

    synchronized (spatialTickets) {
      spatialTickets.removeIf(ticket -> M.ms() > ticket.getMs());
      spatialTicketCount = spatialTickets.size();
    }

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

        if (!player.shouldUnload()) {
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
    AdaptPlayer loaded = players.get(player);
    if (loaded != null) {
      return loaded.getData();
    }

    PlayerData prefetched = prefetchedPlayerData.getIfPresent(player);
    if (prefetched != null) {
      return prefetched;
    }

    if (AdaptConfig.get().isUseSql()) {
      SQLManager sqlManager = Adapt.instance.getSqlManager();
      String sqlData = sqlManager == null ? null : sqlManager.fetchData(player);
      if (sqlData != null) {
        PlayerData data = Json.fromJson(sqlData, PlayerData.class);
        if (data != null) {
          prefetchedPlayerData.put(player, data);
          return data;
        }
      }
    }

    File file = new File(Adapt.instance.getDataFolder("data", "players"), player + ".json");
    if (file.exists()) {
      try {
        PlayerData data = Json.fromJson(IO.readAll(file), PlayerData.class);
        if (data != null) {
          prefetchedPlayerData.put(player, data);
          return data;
        }
      } catch (Throwable error) {
        Adapt.verbose("Failed to load player data for " + player);
      }
    }

    return new PlayerData();
  }

  public boolean addStat(UUID playerId, String stat, double amount) {
    if (playerId == null || stat == null || stat.isBlank() || amount == 0D) {
      return false;
    }

    AdaptPlayer online = onlineAdaptPlayers.get(playerId);
    if (online == null || !online.isRuntimeReady()) {
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
    AdaptPlayer online = onlineAdaptPlayers.get(playerId);
    if (online == null || !online.isRuntimeReady()) {
      return 0;
    }
    Map<String, Integer> levels = learnedAdaptationLevelsByPlayer.get(playerId);
    return levels == null ? 0 : levels.getOrDefault(adaptationName, 0);
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
      AdaptPlayer player = onlineAdaptPlayers.get(playerId);
      if (player != null && player.isRuntimeReady()) {
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
  }

  @NonNull
  public Optional<PlayerData> getPlayerData(@NonNull UUID uuid) {
    return Optional.ofNullable(players.get(uuid))
        .map(AdaptPlayer::getData);
  }

  public AdaptPlayer getPlayer(Player p) {
    AdaptPlayer existing = players.get(p.getUniqueId());
    if (existing != null) {
      return existing;
    }

    AdaptPlayer created = players.computeIfAbsent(p.getUniqueId(), player -> {
      Adapt.warn("Failed to find AdaptPlayer for " + p.getName() + " (" + p.getUniqueId() + ")");
      Adapt.warn("Loading new AdaptPlayer...");
      AdaptPlayer loaded = new AdaptPlayer(p, takePrefetchedData(player));
      loaded.startRuntime();
      return loaded;
    });
    onlineAdaptPlayers.put(p.getUniqueId(), created);
    refreshLearnedAdaptations(created);
    onlineMembershipRevision.incrementAndGet();
    scheduleOnlinePlayerSnapshotRefresh();
    return created;
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
  }

  private PlayerData takePrefetchedData(UUID uuid) {
    PlayerData prefetched = prefetchedPlayerData.getIfPresent(uuid);
    if (prefetched != null) {
      prefetchedPlayerData.invalidate(uuid);
    }
    return prefetched;
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

  public void boostXP(double boost, int ms) {
    data.getMultipliers().add(new XPMultiplier(boost, ms));
  }

  public void load() {
    File f = new File(Adapt.instance.getDataFolder("data"), "server-data.json");
    if (f.exists()) {
      try {
        data = Json.fromJson(IO.readAll(f), AdaptServerData.class);
      } catch (Throwable ignored) {
        Adapt.verbose("Failed to load global boosts data");
      }
    }
  }

  @SneakyThrows
  public void save() {
    IO.writeAll(new File(Adapt.instance.getDataFolder("data"), "server-data.json"), Json.toJson(data, true));
  }
}

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

package art.arcane.adapt.content.adaptation.stealth;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.ViewerDisplayDirector;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.common.world.WorldBlockScanScheduler;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Color;
import org.bukkit.GameEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

public class StealthTrapSense extends SimpleAdaptation<StealthTrapSense.Config> {
  private static final int MAX_SESSION_VISITS_PER_TICK = 32;
  private static final int MAX_BLOCKS_PER_SCAN = 4096;
  private static final int MAX_MARKERS_PER_SCAN = 96;
  private static final long SCULK_STATE_LIFETIME_MILLIS = 1_500L;

  private final Map<UUID, TrapSession> sessions;
  private final Map<UUID, SculkState> sculkStates = new ConcurrentHashMap<>();

  public StealthTrapSense() {
    super("stealth-trap-sense");
    registerConfiguration(Config.class);
    setIcon(Material.TRIPWIRE_HOOK);
    setInterval(400);
    sessions = playerState();
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SCULK_SENSOR)
        .key("challenge_stealth_trap_500")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.SCULK_SHRIEKER)
            .key("challenge_stealth_trap_5k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_stealth_trap_500", "stealth.trap-sense.traps-revealed", 500, 400);
    registerMilestone("challenge_stealth_trap_5k", "stealth.trap-sense.traps-revealed", 5000, 1500);
  }

  static boolean isTrapBlock(Material type) {
    return switch (type) {
      case TRAPPED_CHEST, TRIPWIRE, TRIPWIRE_HOOK, SCULK_SENSOR, CALIBRATED_SCULK_SENSOR, SCULK_SHRIEKER -> true;
      default -> type.name().endsWith("_PRESSURE_PLATE");
    };
  }

  static boolean isSculkTrap(Material type) {
    return type == Material.SCULK_SENSOR || type == Material.CALIBRATED_SCULK_SENSOR || type == Material.SCULK_SHRIEKER;
  }

  static double computeRange(double base, double factor, double percent) {
    return Math.max(3D, Math.min(8D, base + (percent * factor)));
  }

  static double computeMercy(double maxChance, int level, int maxLevel) {
    if (maxLevel > 0 && level >= maxLevel) {
      return 1D;
    }
    double percent = maxLevel <= 0 ? 0D : Math.max(0D, Math.min(1D, level / (double) maxLevel));
    return Math.max(0D, Math.min(1D, maxChance * percent));
  }

  static boolean isMovementGameEvent(GameEvent event) {
    return event == GameEvent.STEP
        || event == GameEvent.SWIM
        || event == GameEvent.FLAP
        || event == GameEvent.HIT_GROUND
        || event == GameEvent.ELYTRA_GLIDE
        || event == GameEvent.SPLASH
        || (event != null && event == BounceEvent.EVENT)
        || event == GameEvent.TELEPORT
        || event == GameEvent.ENTITY_MOUNT
        || event == GameEvent.ENTITY_DISMOUNT;
  }

  private static final class BounceEvent {
    // GameEvent.BOUNCE is 26.2+; resolved by registry key so the 26.1.2 compile baseline builds.
    private static final GameEvent EVENT = resolve();

    private static GameEvent resolve() {
      try {
        return Registry.GAME_EVENT.get(NamespacedKey.minecraft("bounce"));
      } catch (Throwable absent) {
        return null;
      }
    }
  }

  static boolean shouldSuppressMovement(int level, int maxLevel, boolean sneaking, double mercy, double roll) {
    if (level <= 0 || maxLevel <= 0) {
      return false;
    }
    if (level >= maxLevel) {
      return true;
    }
    return sneaking && Double.isFinite(roll) && roll >= 0D && roll < mercy;
  }

  static long blockKey(int x, int y, int z) {
    return (((long) x & 0x3FFFFFFL) << 38) | (((long) z & 0x3FFFFFFL) << 12) | ((long) y & 0xFFFL);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRange(level), 1), 1);
    statLore(v, C.YELLOW, "* ", Form.pc(getMercy(level), 0), 2);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    cacheSculkState(p, e.isSneaking());
    if (!e.isSneaking()) {
      sessions.remove(id);
      WorldBlockScanScheduler.cancel(this, id);
      ViewerDisplayDirector.clearViewer(getName(), id);
      return;
    }

    if (getActiveLevel(p) <= 0) {
      return;
    }

    sessions.putIfAbsent(id, new TrapSession(p, id));
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    sessions.remove(e.getPlayer().getUniqueId());
    sculkStates.remove(e.getPlayer().getUniqueId());
    WorldBlockScanScheduler.cancel(this, e.getPlayer().getUniqueId());
    ViewerDisplayDirector.clearViewer(getName(), e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(BlockReceiveGameEvent e) {
    Material type = e.getBlock().getType();
    if (!isSculkTrap(type)) {
      return;
    }

    if (!(e.getEntity() instanceof Player p) || !isMovementGameEvent(e.getEvent())) {
      return;
    }

    UUID playerId = p.getUniqueId();
    SculkState state = sculkStates.get(playerId);
    long now = System.currentTimeMillis();
    if (state == null) {
      return;
    }
    if (now > state.expiresAt()) {
      sculkStates.remove(playerId, state);
      return;
    }

    if (shouldSuppressMovement(
        state.level(),
        state.maxLevel(),
        state.sneaking(),
        state.mercy(),
        ThreadLocalRandom.current().nextDouble()
    )) {
      e.setCancelled(true);
      showTrap(p, e.getBlock().getLocation(), getMarkerDurationTicks());
      J.runEntity(p, () -> fx(p, FxPriority.AMBIENT)
          .sound(Sound.BLOCK_SCULK_SENSOR_CLICKING_STOP, 0.25F, 1.4F));
    }
  }

  @Override
  public void onTick() {
    refreshSculkStates();
    if (sessions.isEmpty()) {
      return;
    }

    long now = System.currentTimeMillis();
    int visited = 0;
    for (TrapSession session : sessions.values()) {
      if (visited++ >= MAX_SESSION_VISITS_PER_TICK) {
        break;
      }
      if (now < session.nextScanAt || !session.processing.compareAndSet(false, true)) {
        continue;
      }
      if (!J.runEntity(session.player, () -> scan(session))) {
        session.processing.set(false);
        sessions.remove(session.id, session);
      }
    }
  }

  private void scan(TrapSession session) {
    Player p = session.player;
    UUID id = session.id;
    session.nextScanAt = System.currentTimeMillis() + Math.max(200L, getConfig().scanIntervalMillis);
    if (sessions.get(id) != session || !p.isOnline() || !p.isSneaking() || getActiveLevel(p) <= 0) {
      sessions.remove(id, session);
      session.processing.set(false);
      return;
    }

    int level = getActiveLevel(p);
    int r = (int) Math.ceil(getRange(level));
    Location base = p.getLocation().clone();
    WorldBlockScanScheduler.ScanRequest request = WorldBlockScanScheduler.ScanRequest.builder(base)
        .radius(r)
        .denseRadius(r)
        .maxSamples(MAX_BLOCKS_PER_SCAN)
        .maxResults(MAX_MARKERS_PER_SCAN)
        .blockMatcher(block -> isTrapBlock(block.getType()))
        .completion(result -> completeScan(session, result))
        .build();
    session.scanId = WorldBlockScanScheduler.submit(this, id, request);
  }

  private void completeScan(TrapSession session, WorldBlockScanScheduler.ScanResult result) {
    if (!result.scanId().equals(session.scanId)) {
      session.processing.set(false);
      return;
    }
    if (!J.runEntity(session.player, () -> completeScanOwned(session, result))) {
      session.processing.set(false);
      sessions.remove(session.id, session);
    }
  }

  private void completeScanOwned(TrapSession session, WorldBlockScanScheduler.ScanResult result) {
    try {
      Player player = session.player;
      if (sessions.get(session.id) != session || !player.isOnline() || !player.isSneaking()
          || getActiveLevel(player) <= 0) {
        sessions.remove(session.id, session);
        return;
      }

      int newlyRevealed = 0;
      Set<Long> inRange = new HashSet<>();
      List<WorldBlockScanScheduler.Match> matches = result.matches();
      for (WorldBlockScanScheduler.Match match : matches) {
        long key = blockKey(match.x(), match.y(), match.z());
        inRange.add(key);
        if (session.countedTraps.add(key)) {
          newlyRevealed++;
        }
        showTrap(
            player,
            new Location(result.world(), match.x(), match.y(), match.z()),
            getMarkerDurationTicks()
        );
      }
      session.countedTraps.retainAll(inRange);
      finishScan(player, newlyRevealed);
    } finally {
      session.processing.set(false);
    }
  }

  private void finishScan(Player p, int revealed) {
    if (revealed > 0) {
      addStat(p, "stealth.trap-sense.traps-revealed", revealed);
    }
  }

  private void showTrap(Player player, Location location, int durationTicks) {
    Location anchor = location.clone();
    J.runAt(anchor, () -> {
      Material currentType = anchor.getBlock().getType();
      if (!isTrapBlock(currentType)) {
        return;
      }
      ViewerDisplayDirector.showBlock(
          getName(),
          "trap-" + anchor.getBlockX() + ":" + anchor.getBlockY() + ":" + anchor.getBlockZ(),
          player,
          anchor,
          anchor.getBlock().getBlockData(),
          trapColor(currentType),
          durationTicks
      );
    });
  }

  private Color trapColor(Material type) {
    if (isSculkTrap(type)) {
      return Color.fromRGB(40, 220, 210);
    }
    if (type == Material.TRIPWIRE || type == Material.TRIPWIRE_HOOK) {
      return Color.fromRGB(255, 220, 45);
    }
    return Color.fromRGB(255, 70, 70);
  }

  @Override
  public void unregister() {
    sessions.clear();
    sculkStates.clear();
    WorldBlockScanScheduler.cancelOwner(this);
    ViewerDisplayDirector.clearChannel(getName());
    super.unregister();
  }

  private int getMarkerDurationTicks() {
    long scanMillis = Math.max(200L, getConfig().scanIntervalMillis);
    return Math.max(20, (int) Math.min(200L, (scanMillis / 50L) + 10L));
  }

  private double getRange(int level) {
    return computeRange(getConfig().rangeBase, getConfig().rangeFactor, getLevelPercent(level));
  }

  private double getMercy(int level) {
    return computeMercy(getConfig().mercyMaxChance, level, getMaxLevel());
  }

  private void refreshSculkStates() {
    long now = System.currentTimeMillis();
    for (Map.Entry<UUID, SculkState> entry : sculkStates.entrySet()) {
      if (now > entry.getValue().expiresAt()) {
        sculkStates.remove(entry.getKey(), entry.getValue());
      }
    }
    for (AdaptPlayer adaptPlayer : learnedCandidates(now)) {
      Player player = adaptPlayer.getPlayer();
      if (player != null && player.isOnline()) {
        J.runEntity(player, () -> cacheSculkState(player, player.isSneaking()));
      }
    }
  }

  private void cacheSculkState(Player player, boolean sneaking) {
    UUID playerId = player.getUniqueId();
    int level = getActiveLevel(player);
    if (level <= 0) {
      sculkStates.remove(playerId);
      return;
    }
    sculkStates.put(playerId, new SculkState(
        level,
        getMaxLevel(),
        sneaking,
        getMercy(level),
        System.currentTimeMillis() + SCULK_STATE_LIFETIME_MILLIS
    ));
  }

  private static final class TrapSession {
    private final Player player;
    private final UUID id;
    private final AtomicBoolean processing = new AtomicBoolean();
    private final Set<Long> countedTraps = new HashSet<>();
    private volatile long nextScanAt;
    private volatile UUID scanId;

    private TrapSession(Player player, UUID id) {
      this.player = player;
      this.id = id;
    }
  }

  private record SculkState(int level, int maxLevel, boolean sneaking, double mercy, long expiresAt) {
  }

  @ConfigDescription("While sneaking, nearby trapped chests, tripwires, hooks, pressure plates, and sculk blocks glow; maximum level suppresses all movement vibrations.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Base detection range for revealing traps, in blocks.", impact = "Higher values reveal traps from farther away.")
    double rangeBase = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Extra detection range gained across levels, in blocks.", impact = "Higher values extend detection range more per level; capped at 8 blocks.")
    double rangeFactor = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Suppression chance used below maximum level.", impact = "Higher values let lower levels avoid movement vibrations more often; maximum level is always fully suppressed.")
    double mercyMaxChance = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Milliseconds between trap scans while sneaking.", impact = "Lower values refresh glimmers faster; higher values reduce block scan cost.")
    long scanIntervalMillis = 500;

    public Config() {
      baseCost = 3;
      costFactor = 0.35;
      maxLevel = 4;
      initialCost = 3;
    }
  }
}

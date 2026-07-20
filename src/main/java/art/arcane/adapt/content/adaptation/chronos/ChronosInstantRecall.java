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

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.AdaptConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPresets;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.input.DoubleJumpGesture;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.entity.StackExclusion;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ChronosInstantRecall extends SimpleAdaptation<ChronosInstantRecallConfig> {
  private static final EnumSet<Action> RECALL_ACTIONS = EnumSet.of(
      Action.RIGHT_CLICK_AIR,
      Action.RIGHT_CLICK_BLOCK,
      Action.LEFT_CLICK_AIR,
      Action.LEFT_CLICK_BLOCK
  );
  private static final Map<UUID, Long> TELEPORT_XP_SUPPRESS_UNTIL = new ConcurrentHashMap<>();

  private final Map<UUID, Deque<Snapshot>> snapshots = playerState();
  private final Map<UUID, Long> lastSnapshot = playerState();
  private final Map<UUID, Long> cooldowns = playerState();
  private final Map<UUID, Boolean> cooldownReadyNotify = playerState();
  private final Map<UUID, Long> rewindProtection = playerState();
  private final Map<UUID, Boolean> rewinding = playerState();
  private final Map<UUID, RecallXPFarmStamp> recallXpStamps = playerState();
  private final Map<UUID, Runnable> rewindCleanups = new ConcurrentHashMap<>();
  private final DoubleJumpGesture doubleJump = new DoubleJumpGesture();

  public ChronosInstantRecall() {
    super("chronos-instant-recall");
    registerConfiguration(ChronosInstantRecallConfig.class);
    setIcon(Material.RECOVERY_COMPASS);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CLOCK)
        .key("challenge_chronos_recall_50")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.RECOVERY_COMPASS)
            .key("challenge_chronos_recall_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.RECOVERY_COMPASS)
        .key("challenge_chronos_recall_cheat_death")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .build());
    registerMilestone("challenge_chronos_recall_50", "chronos.instant-recall.recalls", 50, 300);
    registerMilestone("challenge_chronos_recall_1k", "chronos.instant-recall.recalls", 1000, 1500);
  }

  private static void markRecallTeleportSuppressed(UUID id, long suppressUntilMillis) {
    long current = TELEPORT_XP_SUPPRESS_UNTIL.getOrDefault(id, 0L);
    if (suppressUntilMillis > current) {
      TELEPORT_XP_SUPPRESS_UNTIL.put(id, suppressUntilMillis);
    }
  }

  public static boolean isRecallTeleportSuppressed(Player p) {
    if (p == null) {
      return false;
    }

    UUID id = p.getUniqueId();
    long until = TELEPORT_XP_SUPPRESS_UNTIL.getOrDefault(id, 0L);
    if (until <= M.ms()) {
      TELEPORT_XP_SUPPRESS_UNTIL.remove(id);
      return false;
    }

    return true;
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + "+ " + Form.duration(getRewindDurationMillis(level), 1) + " " + Localizer.dLocalize("chronos.instant_recall.lore1"));
    v.addLore(C.RED + "* " + Form.duration(getCooldownMillis(level), 1) + " " + Localizer.dLocalize("chronos.instant_recall.lore2"));
    v.addLore(C.GRAY + "* " + Localizer.dLocalize("chronos.instant_recall.lore3"));
    if (getConfig().consumeClock) {
      v.addLore(C.RED + "* " + Localizer.dLocalize("chronos.instant_recall.lore_cost_clock"));
    }
    if (getConfig().healthCostFraction > 0) {
      v.addLore(C.RED + "* " + Form.pc(getConfig().healthCostFraction, 0) + " " + Localizer.dLocalize("chronos.instant_recall.lore_cost_health"));
    }
    List<String> combos = getTriggerCombos();
    if (combos.isEmpty()) {
      v.addLore(C.AQUA + "* " + C.GRAY + "Trigger: " + C.WHITE + "none");
      return;
    }

    for (String combo : combos) {
      v.addLore(C.AQUA + "* " + C.GRAY + "Trigger: " + C.WHITE + combo);
    }
  }

  private long getRewindDurationMillis(int level) {
    return (long) (getRewindDurationSeconds(level) * 1000D);
  }

  private double getRewindDurationSeconds(int level) {
    double raw = getConfig().baseRewindSeconds + (level * getConfig().rewindSecondsPerLevel);
    return Math.max(0.25D, Math.min(getConfig().maxRewindSeconds, raw));
  }

  private long getCooldownMillis(int level) {
    return getRewindDurationMillis(level) + (getConfig().cooldownPaddingSeconds * 1000L);
  }

  private long getMaximumHistoryMillis() {
    return (long) ((getRewindDurationSeconds(getConfig().maxLevel) + getConfig().historyPaddingSeconds) * 1000D);
  }

  private int getRewindAnimationTicks() {
    int durationMillis = Math.max(100, getConfig().rewindAnimationDurationMillis);
    int ticks = (int) Math.ceil(durationMillis / 50D);
    if (ticks <= 1) {
      ticks = Math.max(2, getConfig().rewindAnimationTicks);
    }

    return Math.max(2, ticks);
  }

  private boolean isSingleWorldPath(List<Snapshot> path) {
    if (path.isEmpty()) {
      return false;
    }

    String worldKey = path.get(0).worldKey();
    for (Snapshot snapshot : path) {
      if (!Objects.equals(worldKey, snapshot.worldKey())) {
        return false;
      }
    }

    return true;
  }

  private ArmorStand spawnRecallCameraAnchor(Player p) {
    Location location = p.getLocation().clone();
    if (location.getWorld() == null) {
      return null;
    }

    ArmorStand spawned = location.getWorld().spawn(location, ArmorStand.class, stand -> {
      StackExclusion.exclude(stand);
      stand.setInvisible(true);
      stand.setMarker(false);
      stand.setGravity(false);
      stand.setSilent(true);
      stand.setInvulnerable(true);
      stand.setCollidable(false);
      stand.setBasePlate(false);
      stand.setSmall(true);
      stand.setPersistent(false);
    });
    return spawned;
  }

  private List<String> getTriggerCombos() {
    List<String> triggers = new ArrayList<>();
    String clickSurface = getClickSurfaceLabel();
    if (getConfig().enableClockClickTrigger) {
      appendClickCombos(triggers, "Clock", getConfig().clockClickLeftClick, getConfig().clockClickRightClick, clickSurface);
    }

    if (getConfig().enableSprintClickTrigger) {
      appendClickCombos(triggers, "Sprint + Clock", getConfig().sprintClickLeftClick, getConfig().sprintClickRightClick, clickSurface);
    }

    if (getConfig().enableSingleSneakTrigger) {
      String combo = getConfig().singleSneakRequiresSprint ? "Sprint + Sneak" : "Sneak";
      if (getConfig().singleSneakRequiresClockInHand) {
        combo += " + Clock";
      }
      triggers.add(combo);
    }

    if (getConfig().enableDoubleJumpTrigger) {
      String combo = "Double Jump";
      if (getConfig().doubleJumpRequiresSprint) {
        combo += " + Sprint";
      }
      if (getConfig().doubleJumpRequiresClockInHand) {
        combo += " + Clock";
      }
      triggers.add(combo);
    }

    return triggers;
  }

  private void appendClickCombos(List<String> triggers, String prefix, boolean allowLeft, boolean allowRight, String clickSurface) {
    if (clickSurface.isBlank()) {
      return;
    }

    if (allowLeft) {
      triggers.add(prefix + " + Left Click" + clickSurface);
    }

    if (allowRight) {
      triggers.add(prefix + " + Right Click" + clickSurface);
    }
  }

  private String getClickSurfaceLabel() {
    if (getConfig().allowAirClicks && getConfig().allowBlockClicks) {
      return " (air/block)";
    }

    if (getConfig().allowAirClicks) {
      return " (air)";
    }

    if (getConfig().allowBlockClicks) {
      return " (block)";
    }

    return "";
  }

  private RecallXPContext buildRecallXPContext(Snapshot from, Snapshot to) {
    double distance;
    if (Objects.equals(from.worldKey(), to.worldKey())) {
      double dx = from.x() - to.x();
      double dy = from.y() - to.y();
      double dz = from.z() - to.z();
      distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    } else {
      distance = getConfig().xpCrossWorldDistanceCredit;
    }

    double healthRecovered = Math.max(0D, to.health() - from.health());
    double hungerRecovered = Math.max(0D, to.foodLevel() - from.foodLevel());
    double saturationRecovered = Math.max(0D, to.saturation() - from.saturation());

    return new RecallXPContext(
        from.worldKey(),
        from.x(),
        from.y(),
        from.z(),
        to.worldKey(),
        to.x(),
        to.y(),
        to.z(),
        distance,
        healthRecovered,
        hungerRecovered,
        saturationRecovered);
  }

  private boolean pointsAreSimilar(String worldA, double ax, double ay, double az, String worldB, double bx, double by, double bz, double radius) {
    if (!Objects.equals(worldA, worldB)) {
      return false;
    }

    double dx = ax - bx;
    double dy = ay - by;
    double dz = az - bz;
    return (dx * dx) + (dy * dy) + (dz * dz) <= (radius * radius);
  }

  private boolean isRepeatRecall(RecallXPFarmStamp stamp, RecallXPContext context) {
    return pointsAreSimilar(stamp.fromWorldKey(), stamp.fromX(), stamp.fromY(), stamp.fromZ(),
        context.fromWorldKey(), context.fromX(), context.fromY(), context.fromZ(),
        getConfig().xpRepeatSourceRadius)
        && pointsAreSimilar(stamp.toWorldKey(), stamp.toX(), stamp.toY(), stamp.toZ(),
        context.toWorldKey(), context.toX(), context.toY(), context.toZ(),
        getConfig().xpRepeatTargetRadius);
  }

  private double computeRecallXPGain(UUID playerId, int level, RecallXPContext context, long now) {
    double raw = (context.distance() * getConfig().xpPerDistanceBlock)
        + (context.healthRecovered() * getConfig().xpPerHealthPoint)
        + (context.hungerRecovered() * getConfig().xpPerHungerPoint)
        + (context.saturationRecovered() * getConfig().xpPerSaturationPoint);

    if (raw < getConfig().xpMinRawReward) {
      return 0D;
    }

    double leveled = raw * (1D + ((Math.max(1, level) - 1) * getConfig().xpLevelMultiplierPerLevel));
    double multiplier = 1D;

    RecallXPFarmStamp previous = recallXpStamps.get(playerId);
    if (previous != null) {
      long elapsed = now - previous.awardedAt();
      if (elapsed < getConfig().xpDiminishWindowMillis) {
        double t = Math.max(0D, Math.min(1D, elapsed / (double) Math.max(1L, getConfig().xpDiminishWindowMillis)));
        multiplier *= getConfig().xpDiminishMinMultiplier + ((1D - getConfig().xpDiminishMinMultiplier) * t);
      }

      if (elapsed < getConfig().xpRepeatWindowMillis && isRepeatRecall(previous, context)) {
        multiplier *= getConfig().xpRepeatPenaltyMultiplier;
      }
    }

    double reward = Math.min(getConfig().xpMaxAward, leveled * multiplier);
    if (reward < getConfig().xpMinAward) {
      return 0D;
    }

    return reward;
  }

  private Snapshot snapshotFromPlayer(Player p, long now) {
    return new Snapshot(now,
        WorldIdentity.serialize(p.getWorld()),
        p.getLocation().getX(),
        p.getLocation().getY(),
        p.getLocation().getZ(),
        p.getLocation().getYaw(),
        p.getLocation().getPitch(),
        p.getHealth(),
        p.getFoodLevel(),
        p.getSaturation(),
        p.getExhaustion(),
        p.getFireTicks());
  }

  private Snapshot snapshotFromLocation(Player p, Location location, long now) {
    World world = location.getWorld();
    if (world == null) {
      world = p.getWorld();
    }

    return new Snapshot(now,
        WorldIdentity.serialize(world),
        location.getX(),
        location.getY(),
        location.getZ(),
        location.getYaw(),
        location.getPitch(),
        p.getHealth(),
        p.getFoodLevel(),
        p.getSaturation(),
        p.getExhaustion(),
        p.getFireTicks());
  }

  private void resetSnapshotHistory(Player p, Location location) {
    if (location == null) {
      return;
    }

    long now = M.ms();
    UUID id = p.getUniqueId();
    Deque<Snapshot> queue = snapshots.computeIfAbsent(id, unused -> new ArrayDeque<>());
    queue.clear();
    queue.addLast(snapshotFromLocation(p, location, now));
    lastSnapshot.put(id, now);
  }

  private void captureSnapshot(Player p) {
    long now = M.ms();
    UUID id = p.getUniqueId();
    long last = lastSnapshot.getOrDefault(id, 0L);
    if (now - last < getConfig().snapshotIntervalMillis) {
      return;
    }

    lastSnapshot.put(id, now);
    Deque<Snapshot> queue = snapshots.computeIfAbsent(id, k -> new ArrayDeque<>());
    queue.addLast(snapshotFromPlayer(p, now));

    long maxAge = getMaximumHistoryMillis();
    while (!queue.isEmpty() && now - queue.getFirst().timestamp() > maxAge) {
      queue.removeFirst();
    }
  }

  private Snapshot findSnapshot(Player p, long rewindMillis) {
    Deque<Snapshot> queue = snapshots.get(p.getUniqueId());
    if (queue == null || queue.isEmpty()) {
      return null;
    }

    long target = M.ms() - rewindMillis;
    Snapshot fallback = queue.getFirst();

    for (Snapshot s : queue) {
      if (s.timestamp() <= target) {
        fallback = s;
      } else {
        break;
      }
    }

    return fallback;
  }

  private List<Snapshot> buildRewindPath(Player p, long rewindMillis, Snapshot anchor) {
    List<Snapshot> path = new ArrayList<>();
    long now = M.ms();
    path.add(snapshotFromPlayer(p, now));

    Deque<Snapshot> queue = snapshots.get(p.getUniqueId());
    if (queue == null || queue.isEmpty()) {
      path.add(anchor);
      return path;
    }

    Iterator<Snapshot> reverse = queue.descendingIterator();
    while (reverse.hasNext()) {
      Snapshot snap = reverse.next();
      if (snap.timestamp() < anchor.timestamp()) {
        break;
      }
      if (snap.timestamp() <= now) {
        path.add(snap);
      }
    }

    Snapshot last = path.get(path.size() - 1);
    if (last.timestamp() != anchor.timestamp()) {
      path.add(anchor);
    }

    if (path.size() < 2) {
      path.add(anchor);
    }

    return path;
  }

  private List<Snapshot> buildAnimationPath(List<Snapshot> rawPath, int animationTicks) {
    List<Snapshot> animationPath = new ArrayList<>();
    if (rawPath.isEmpty()) {
      return animationPath;
    }

    if (animationTicks <= 1 || rawPath.size() == 1) {
      animationPath.add(rawPath.get(rawPath.size() - 1));
      return animationPath;
    }

    for (int step = 0; step < animationTicks; step++) {
      double progress = step / (double) (animationTicks - 1);
      double scaled = progress * (rawPath.size() - 1);
      int lower = (int) Math.floor(scaled);
      int upper = Math.min(rawPath.size() - 1, lower + 1);
      double alpha = scaled - lower;
      Snapshot a = rawPath.get(lower);
      Snapshot b = rawPath.get(upper);
      animationPath.add(interpolateSnapshot(a, b, alpha));
    }

    Snapshot anchor = rawPath.get(rawPath.size() - 1);
    animationPath.set(animationPath.size() - 1, anchor);
    return animationPath;
  }

  private Snapshot interpolateSnapshot(Snapshot a, Snapshot b, double alpha) {
    if (alpha <= 0D) {
      return a;
    }
    if (alpha >= 1D) {
      return b;
    }

    long timestamp = (long) Math.round(lerp(a.timestamp(), b.timestamp(), alpha));
    String worldKey = alpha < 0.5D ? a.worldKey() : b.worldKey();
    double x = lerp(a.x(), b.x(), alpha);
    double y = lerp(a.y(), b.y(), alpha);
    double z = lerp(a.z(), b.z(), alpha);
    float yaw = lerpAngle(a.yaw(), b.yaw(), alpha);
    float pitch = (float) lerp(a.pitch(), b.pitch(), alpha);
    double health = lerp(a.health(), b.health(), alpha);
    int foodLevel = (int) Math.round(lerp(a.foodLevel(), b.foodLevel(), alpha));
    float saturation = (float) lerp(a.saturation(), b.saturation(), alpha);
    float exhaustion = (float) lerp(a.exhaustion(), b.exhaustion(), alpha);
    int fireTicks = (int) Math.round(lerp(a.fireTicks(), b.fireTicks(), alpha));

    return new Snapshot(timestamp, worldKey, x, y, z, yaw, pitch, health, foodLevel, saturation, exhaustion, fireTicks);
  }

  private double lerp(double a, double b, double alpha) {
    return a + ((b - a) * alpha);
  }

  private float lerpAngle(float from, float to, double alpha) {
    float delta = to - from;
    while (delta > 180F) {
      delta -= 360F;
    }
    while (delta < -180F) {
      delta += 360F;
    }

    return from + (float) (delta * alpha);
  }

  private Location toLocation(Snapshot snapshot, World fallback) {
    World world = WorldIdentity.resolve(snapshot.worldKey()).orElse(fallback);
    return new Location(world, snapshot.x(), snapshot.y(), snapshot.z(), snapshot.yaw(), snapshot.pitch());
  }

  private void applySnapshotState(Player p, Snapshot snapshot) {
    double maxHealth = p.getAttribute(Attribute.MAX_HEALTH) == null ? 20D : p.getAttribute(Attribute.MAX_HEALTH).getValue();
    p.setHealth(Math.max(1, Math.min(maxHealth, snapshot.health())));
    p.setFoodLevel(Math.max(0, Math.min(20, snapshot.foodLevel())));
    p.setSaturation(Math.max(0, snapshot.saturation()));
    p.setExhaustion(Math.max(0, snapshot.exhaustion()));
    p.setFireTicks(Math.max(0, snapshot.fireTicks()));
    p.setFallDistance(0);
    p.setVelocity(new Vector());
  }

  @EventHandler
  public void on(PlayerQuitEvent e) {
    UUID id = e.getPlayer().getUniqueId();
    finishRewindVisualState(id);
    clearPlayerState(id);
  }

  private void finishRewindVisualState(UUID id) {
    Runnable cleanup = rewindCleanups.remove(id);
    if (cleanup != null) {
      cleanup.run();
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerTeleportEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    if (!isRecallEligible(p) || rewinding.containsKey(id)) {
      return;
    }

    Location destination = e.getTo();
    if (destination == null) {
      return;
    }

    resetSnapshotHistory(p, destination);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerChangedWorldEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    if (!isRecallEligible(p) || rewinding.containsKey(id)) {
      return;
    }

    resetSnapshotHistory(p, p.getLocation());
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    Player p = e.getPlayer();
    if (!isRecallEligible(p)) {
      return;
    }

    if (shouldTriggerSprintClockClick(e)) {
      e.setCancelled(true);
      attemptRecall(p);
      return;
    }

    if (shouldTriggerClockClick(e)) {
      e.setCancelled(true);
      attemptRecall(p);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(PlayerToggleSneakEvent e) {
    Player p = e.getPlayer();
    if (!e.isSneaking() || !isRecallEligible(p) || !getConfig().enableSingleSneakTrigger) {
      return;
    }

    if (getConfig().singleSneakRequiresSprint && !p.isSprinting()) {
      return;
    }

    if (getConfig().singleSneakRequiresClockInHand && !hasRecallClockInEitherHand(p)) {
      return;
    }

    attemptRecall(p);
  }

  private EquipmentSlot resolveRecallHand(Player p, EquipmentSlot eventHand) {
    ItemStack main = p.getInventory().getItemInMainHand();
    ItemStack off = p.getInventory().getItemInOffHand();

    if (eventHand == null) {
      if (isRecallClock(main)) {
        return EquipmentSlot.HAND;
      }

      if (isRecallClock(off)) {
        return EquipmentSlot.OFF_HAND;
      }

      return null;
    }

    if (eventHand == EquipmentSlot.HAND) {
      return isRecallClock(main) ? EquipmentSlot.HAND : null;
    }

    if (eventHand == EquipmentSlot.OFF_HAND) {
      if (isRecallClock(main)) {
        return null;
      }

      return isRecallClock(off) ? EquipmentSlot.OFF_HAND : null;
    }

    return null;
  }

  private boolean isRecallEligible(Player p) {
    return hasActiveAdaptation(p) && p.getGameMode() == GameMode.SURVIVAL;
  }

  private boolean isLeftClick(Action action) {
    return action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
  }

  private boolean isRightClick(Action action) {
    return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
  }

  private boolean isBlockClick(Action action) {
    return action == Action.LEFT_CLICK_BLOCK || action == Action.RIGHT_CLICK_BLOCK;
  }

  private boolean isActionAllowed(Action action) {
    if (!RECALL_ACTIONS.contains(action)) {
      return false;
    }

    if (!getConfig().allowAirClicks && !isBlockClick(action)) {
      return false;
    }

    if (!getConfig().allowBlockClicks && isBlockClick(action)) {
      return false;
    }

    return true;
  }

  private boolean shouldTriggerClockClick(PlayerInteractEvent e) {
    if (!getConfig().enableClockClickTrigger) {
      return false;
    }

    Action action = e.getAction();
    if (!isActionAllowed(action)) {
      return false;
    }

    if (isLeftClick(action) && !getConfig().clockClickLeftClick) {
      return false;
    }

    if (isRightClick(action) && !getConfig().clockClickRightClick) {
      return false;
    }

    return resolveRecallHand(e.getPlayer(), e.getHand()) != null;
  }

  private boolean shouldTriggerSprintClockClick(PlayerInteractEvent e) {
    if (!getConfig().enableSprintClickTrigger || !e.getPlayer().isSprinting()) {
      return false;
    }

    Action action = e.getAction();
    if (!isActionAllowed(action)) {
      return false;
    }

    if (isLeftClick(action) && !getConfig().sprintClickLeftClick) {
      return false;
    }

    if (isRightClick(action) && !getConfig().sprintClickRightClick) {
      return false;
    }

    return resolveRecallHand(e.getPlayer(), e.getHand()) != null;
  }

  private boolean hasRecallClockInEitherHand(Player p) {
    return isRecallClock(p.getInventory().getItemInMainHand())
        || isRecallClock(p.getInventory().getItemInOffHand());
  }

  private boolean canTriggerDoubleJump(Player p) {
    if (rewinding.containsKey(p.getUniqueId())) {
      return false;
    }

    if (getConfig().doubleJumpRequiresSprint && !p.isSprinting()) {
      return false;
    }

    return !getConfig().doubleJumpRequiresClockInHand || hasRecallClockInEitherHand(p);
  }

  private void clearPlayerState(UUID id) {
    snapshots.remove(id);
    lastSnapshot.remove(id);
    cooldowns.remove(id);
    cooldownReadyNotify.remove(id);
    rewindProtection.remove(id);
    rewinding.remove(id);
    recallXpStamps.remove(id);
    rewindCleanups.remove(id);
    doubleJump.reset(id);
    TELEPORT_XP_SUPPRESS_UNTIL.remove(id);
  }

  private boolean isRecallClock(ItemStack stack) {
    return stack != null
        && stack.getType() == Material.CLOCK
        && !ChronoTimeBombItem.isBindableItem(stack);
  }

  private void consumeRecallClock(Player p) {
    if (!getConfig().consumeClock) {
      return;
    }

    ItemStack main = p.getInventory().getItemInMainHand();
    if (isRecallClock(main)) {
      main.setAmount(main.getAmount() - 1);
      return;
    }

    ItemStack off = p.getInventory().getItemInOffHand();
    if (isRecallClock(off)) {
      off.setAmount(off.getAmount() - 1);
    }
  }

  private void applyRecallHealthCost(Player p) {
    double fraction = Math.max(0D, Math.min(1D, getConfig().healthCostFraction));
    if (fraction <= 0D || p.isDead()) {
      return;
    }

    double healthAfter = Math.max(1.0D, p.getHealth() * (1D - fraction));
    applyPlayerDamage(p, p.getHealth() - healthAfter);
  }

  private void attemptRecall(Player p) {
    UUID id = p.getUniqueId();
    if (!isRecallEligible(p)) {
      return;
    }

    if (rewinding.containsKey(id)) {
      return;
    }

    long now = M.ms();
    long cooldown = cooldowns.getOrDefault(id, 0L);
    if (cooldown > now) {
      if (getConfig().playClockSounds) {
        ChronosSoundFX.playClockReject(p);
      }
      return;
    }

    int level = getActiveLevel(p);
    long rewindMillis = getRewindDurationMillis(level);
    Snapshot anchor = findSnapshot(p, rewindMillis);
    if (anchor == null) {
      if (getConfig().playClockSounds) {
        ChronosSoundFX.playClockReject(p);
      }
      return;
    }

    int animationTicks = getRewindAnimationTicks();
    List<Snapshot> path = buildRewindPath(p, rewindMillis, anchor);
    List<Snapshot> animationPath = buildAnimationPath(path, animationTicks);
    if (animationPath.isEmpty()) {
      if (getConfig().playClockSounds) {
        ChronosSoundFX.playClockReject(p);
      }
      return;
    }

    consumeRecallClock(p);
    Snapshot finalSnapshot = animationPath.get(animationPath.size() - 1);
    RecallXPContext xpContext = buildRecallXPContext(animationPath.get(0), finalSnapshot);
    double healthBeforeRecall = p.getHealth();
    double healthAfterRecall = finalSnapshot.health();
    long castAt = M.ms();
    GameMode originalGameMode = p.getGameMode();
    boolean temporarySpectator = getConfig().rewindUseTemporarySpectator && originalGameMode == GameMode.SURVIVAL;
    boolean allowClientCamera = temporarySpectator
        && getConfig().rewindUseClientCamera
        && isSingleWorldPath(animationPath);
    ArmorStand cameraAnchor = null;
    if (temporarySpectator) {
      p.setGameMode(GameMode.SPECTATOR);
      p.setFlying(true);
      if (allowClientCamera) {
        cameraAnchor = spawnRecallCameraAnchor(p);
        if (cameraAnchor != null && cameraAnchor.isValid()) {
          p.setSpectatorTarget(cameraAnchor);
        } else {
          allowClientCamera = false;
        }
      }
    }

    cooldowns.put(id, castAt + getCooldownMillis(level));
    cooldownReadyNotify.put(id, true);
    rewinding.put(id, true);
    long protectionUntil = castAt + ((long) (animationTicks + getConfig().rewindProtectionTicks) * 50L);
    rewindProtection.put(id, protectionUntil);
    markRecallTeleportSuppressed(id, protectionUntil + ((long) Math.max(0, getConfig().rewindTeleportXpSuppressExtraTicks) * 50L));
    p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, animationTicks + getConfig().rewindProtectionTicks, 0, true, false, false), true);

    FxPresets.chargeRing(this, p.getLocation(), 3);
    if (getConfig().playClockSounds) {
      ChronosSoundFX.playRewindStart(p);
      ChronosSoundFX.playRewindFinish(p);
    }

    final boolean initialClientCamera = allowClientCamera;
    final ArmorStand initialCameraAnchor = cameraAnchor;
    int[] step = {0};
    Location[] lastLoc = {p.getLocation().clone()};
    boolean[] clientCameraActive = {initialClientCamera};
    ArmorStand[] cameraRef = {initialCameraAnchor};

    Consumer<Boolean> cleanupVisualState = restoreGameMode -> {
      if (cameraRef[0] != null) {
        Entity anchorEntity = cameraRef[0];
        cameraRef[0] = null;
        if (anchorEntity.isValid()) {
          anchorEntity.remove();
        }
      }

      if (temporarySpectator) {
        p.setSpectatorTarget(null);
        if (restoreGameMode && p.getGameMode() == GameMode.SPECTATOR) {
          p.setGameMode(originalGameMode);
          p.setFlying(false);
        }
      }
    };
    rewindCleanups.put(id, () -> cleanupVisualState.accept(true));

    Runnable[] rewindTask = new Runnable[1];
    rewindTask[0] = () -> {
      if (!p.isOnline() || p.isDead()) {
        rewinding.remove(id);
        finishRewindVisualState(id);
        return;
      }

      float progress = animationTicks <= 1 ? 1f : (float) step[0] / (float) (animationTicks - 1);
      int index = Math.min(animationPath.size() - 1, step[0]);
      Snapshot snapshot = animationPath.get(index);
      Location destination = toLocation(snapshot, p.getWorld());

      if (getConfig().showRewindTraceParticles && lastLoc[0].getWorld() != null && lastLoc[0].getWorld().equals(destination.getWorld())) {
        Location traceFrom = lastLoc[0].clone().add(0, 1, 0);
        Location traceTo = destination.clone().add(0, 1, 0);
        fx(traceFrom, FxPriority.TRAIL)
            .line(Particle.REVERSE_PORTAL, traceTo.getX(), traceTo.getY(), traceTo.getZ(), Math.max(4, getConfig().rewindTracePoints))
            .particle(Particles.END_ROD, 1, traceTo.getX() - traceFrom.getX(), traceTo.getY() - traceFrom.getY(), traceTo.getZ() - traceFrom.getZ(), 0, 0);
      }

      boolean movedClient = false;
      if (clientCameraActive[0] && cameraRef[0] != null && cameraRef[0].isValid()) {
        Entity target = p.getSpectatorTarget();
        if (target == null || !target.getUniqueId().equals(cameraRef[0].getUniqueId())) {
          p.setSpectatorTarget(cameraRef[0]);
          target = p.getSpectatorTarget();
        }

        if (target != null
            && target.getUniqueId().equals(cameraRef[0].getUniqueId())
            && destination.getWorld() != null
            && destination.getWorld().equals(cameraRef[0].getWorld())) {
          J.teleport(cameraRef[0], destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
          movedClient = true;
        } else {
          clientCameraActive[0] = false;
        }
      }

      if (!movedClient) {
        J.teleport(p, destination, PlayerTeleportEvent.TeleportCause.PLUGIN);
      }

      if (!temporarySpectator || step[0] >= animationPath.size() - 1) {
        applySnapshotState(p, snapshot);
      }

      if (getConfig().playClockSounds) {
        ChronosSoundFX.playRewindStep(p, progress);
      }

      lastLoc[0] = destination;
      step[0]++;

      if (step[0] >= animationTicks) {
        finishRewindVisualState(id);
        Location finalDestination = toLocation(finalSnapshot, p.getWorld());
        if (finalDestination.getWorld() != null) {
          J.teleport(p, finalDestination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
        applySnapshotState(p, finalSnapshot);
        applyRecallHealthCost(p);

        Location bloomAt = p.getLocation().add(0, 1, 0);
        fx(bloomAt, FxPriority.TRANSITION)
            .particle(Particle.FLASH, 1, 0, 0, 0, 0, 0)
            .particle(Particles.TOTEM, 26, 0, 0, 0, 0.3D, 0.02D);
        timeline(p)
            .duration(3)
            .priority(FxPriority.TRANSITION)
            .cullRadius(32)
            .frame((f, tick, prog) -> {
              if (tick >= 1) {
                f.particle(Particles.ITEM_CRACK, 9, 0, 1.0D, 0, 0.3D, 0.02D, new ItemStack(Material.CLOCK));
              }
            })
            .start();
        if (getConfig().playClockSounds) {
          ChronosSoundFX.playRewindFinish(p);
        }
        rewinding.remove(id);
        addStat(p, "chronos.instant-recall.recalls", 1);
        if (healthBeforeRecall <= 4 && healthAfterRecall >= 16
            && AdaptConfig.get().isAdvancements()
            && !getPlayer(p).getData().isGranted("challenge_chronos_recall_cheat_death")) {
          getPlayer(p).getAdvancementHandler().grant("challenge_chronos_recall_cheat_death");
        }
        long awardAt = M.ms();
        double xpGain = computeRecallXPGain(id, level, xpContext, awardAt);
        if (xpGain > 0D) {
          xp(p, p.getLocation(), xpGain);
          recallXpStamps.put(id, new RecallXPFarmStamp(
              awardAt,
              xpContext.fromWorldKey(),
              xpContext.fromX(),
              xpContext.fromY(),
              xpContext.fromZ(),
              xpContext.toWorldKey(),
              xpContext.toX(),
              xpContext.toY(),
              xpContext.toZ()));
        }
        return;
      }

      if (J.isFoliaThreading()) {
        J.runEntity(p, rewindTask[0], 1);
      } else {
        J.s(rewindTask[0], 1);
      }
    };
    if (J.isFoliaThreading()) {
      J.runEntity(p, rewindTask[0]);
    } else {
      J.s(rewindTask[0]);
    }
  }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void on(EntityDamageEvent e) {
    if (!(e.getEntity() instanceof Player p)) {
      return;
    }

    UUID id = p.getUniqueId();
    long protectedUntil = rewindProtection.getOrDefault(id, 0L);
    if (rewinding.containsKey(id) || protectedUntil > M.ms()) {
      e.setCancelled(true);
      p.setNoDamageTicks(Math.max(p.getNoDamageTicks(), getConfig().rewindProtectionTicks));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    if (!isRecallEligible(p)) {
      return;
    }

    captureSnapshot(p);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void onDoubleJumpMove(PlayerMoveEvent e) {
    Player p = e.getPlayer();
    if (!isRecallEligible(p) || !getConfig().enableDoubleJumpTrigger) {
      doubleJump.reset(p);
      return;
    }

    if (!doubleJump.update(p)) {
      return;
    }

    UUID id = p.getUniqueId();
    if (!canTriggerDoubleJump(p) || cooldowns.getOrDefault(id, 0L) > M.ms()) {
      return;
    }

    attemptRecall(p);
  }

  @Override
  public void onTick() {
    long now = M.ms();
    for (UUID id : cooldownReadyNotify.keySet()) {
      Player p = Bukkit.getPlayer(id);
      if (p == null) {
        cooldownReadyNotify.remove(id);
        continue;
      }

      long cooldown = cooldowns.getOrDefault(id, 0L);
      if (cooldown <= now && cooldownReadyNotify.remove(id) != null) {
        J.runEntity(p, () -> {
          if (p.isOnline() && getConfig().playClockSounds) {
            ChronosSoundFX.playCooldownReady(p);
          }
        });
      }
    }

    cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    rewindProtection.entrySet().removeIf(entry -> entry.getValue() <= now);
    TELEPORT_XP_SUPPRESS_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
  }

}

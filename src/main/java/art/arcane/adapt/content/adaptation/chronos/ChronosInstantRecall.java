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
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.format.Localizer;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class ChronosInstantRecall extends SimpleAdaptation<ChronosInstantRecallConfig> {
  private static final EnumSet<Action> RECALL_ACTIONS = EnumSet.of(
      Action.RIGHT_CLICK_AIR,
      Action.RIGHT_CLICK_BLOCK,
      Action.LEFT_CLICK_AIR,
      Action.LEFT_CLICK_BLOCK
  );
  private static final Map<UUID, Long> TELEPORT_XP_SUPPRESS_UNTIL = new ConcurrentHashMap<>();

  private final Map<UUID, Deque<Snapshot>> snapshots = new ConcurrentHashMap<>();
  private final Map<UUID, Long> lastSnapshot = new ConcurrentHashMap<>();
  private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
  private final Set<UUID> cooldownReadyNotify = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Long> rewindProtection = new ConcurrentHashMap<>();
  private final Set<UUID> rewinding = ConcurrentHashMap.newKeySet();
  private final Map<UUID, RecallXPFarmStamp> recallXpStamps = new ConcurrentHashMap<>();
  private final Map<UUID, Long> jumpArmUntil = new ConcurrentHashMap<>();
  private final Map<UUID, Boolean> lastOnGround = new ConcurrentHashMap<>();

  public ChronosInstantRecall() {
    super("chronos-instant-recall");
    registerConfiguration(ChronosInstantRecallConfig.class);
    setDescription(Localizer.dLocalize("chronos.instant_recall.description"));
    setDisplayName(Localizer.dLocalize("chronos.instant_recall.name"));
    setIcon(Material.RECOVERY_COMPASS);
    setBaseCost(getConfig().baseCost);
    setMaxLevel(getConfig().maxLevel);
    setInitialCost(getConfig().initialCost);
    setCostFactor(getConfig().costFactor);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.CLOCK)
        .key("challenge_chronos_recall_50")
        .title(Localizer.dLocalize("advancement.challenge_chronos_recall_50.title"))
        .description(Localizer.dLocalize("advancement.challenge_chronos_recall_50.description"))
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.RECOVERY_COMPASS)
            .key("challenge_chronos_recall_1k")
            .title(Localizer.dLocalize("advancement.challenge_chronos_recall_1k.title"))
            .description(Localizer.dLocalize("advancement.challenge_chronos_recall_1k.description"))
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.RECOVERY_COMPASS)
        .key("challenge_chronos_recall_cheat_death")
        .title(Localizer.dLocalize("advancement.challenge_chronos_recall_cheat_death.title"))
        .description(Localizer.dLocalize("advancement.challenge_chronos_recall_cheat_death.description"))
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
    List<String> combos = getTriggerCombos();
    if (combos.isEmpty()) {
      v.addLore(C.AQUA + "* " + C.GRAY + "Trigger: " + C.WHITE + "none");
      return;
    }

    for (String combo : combos) {
      v.addLore(C.AQUA + "* " + C.GRAY + "Trigger: " + C.WHITE + combo);
    }
  }

  @Override
  public String getDescription() {
    return "Rewind to a recent snapshot with health and hunger restored. " + summarizeTriggerDescription();
  }

  private String summarizeTriggerDescription() {
    List<String> combos = getTriggerCombos();
    if (combos.isEmpty()) {
      return "No active triggers are currently enabled.";
    }

    if (combos.size() == 1) {
      return "Trigger: " + combos.get(0) + ".";
    }

    if (combos.size() == 2) {
      return "Triggers: " + combos.get(0) + " or " + combos.get(1) + ".";
    }

    return "Triggers: " + combos.get(0) + ", " + combos.get(1) + ", +" + (combos.size() - 2) + " more.";
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

    String world = path.get(0).worldName();
    for (Snapshot snapshot : path) {
      if (!Objects.equals(world, snapshot.worldName())) {
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

    return location.getWorld().spawn(location, ArmorStand.class, stand -> {
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
    if (Objects.equals(from.worldName(), to.worldName())) {
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
        from.worldName(),
        from.x(),
        from.y(),
        from.z(),
        to.worldName(),
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
    return pointsAreSimilar(stamp.fromWorld(), stamp.fromX(), stamp.fromY(), stamp.fromZ(),
        context.fromWorld(), context.fromX(), context.fromY(), context.fromZ(),
        getConfig().xpRepeatSourceRadius)
        && pointsAreSimilar(stamp.toWorld(), stamp.toX(), stamp.toY(), stamp.toZ(),
        context.toWorld(), context.toX(), context.toY(), context.toZ(),
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
        p.getWorld().getName(),
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
        world.getName(),
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
    String worldName = alpha < 0.5D ? a.worldName() : b.worldName();
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

    return new Snapshot(timestamp, worldName, x, y, z, yaw, pitch, health, foodLevel, saturation, exhaustion, fireTicks);
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
    World world = Bukkit.getWorld(snapshot.worldName());
    if (world == null) {
      world = fallback;
    }
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
    clearPlayerState(e.getPlayer().getUniqueId());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(PlayerTeleportEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    if (!isRecallEligible(p) || rewinding.contains(id)) {
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
    if (!isRecallEligible(p) || rewinding.contains(id)) {
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

  @EventHandler(priority = EventPriority.HIGHEST)
  public void on(PlayerToggleFlightEvent e) {
    Player p = e.getPlayer();
    UUID id = p.getUniqueId();
    if (!isRecallEligible(p) || !getConfig().enableDoubleJumpTrigger) {
      return;
    }

    Long armUntil = jumpArmUntil.get(id);
    if (armUntil == null) {
      return;
    }

    e.setCancelled(true);
    p.setFlying(false);
    clearDoubleJumpArm(p, id);
    if (armUntil > M.ms()) {
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

  private boolean canArmDoubleJump(Player p) {
    if (rewinding.contains(p.getUniqueId())) {
      return false;
    }

    if (getConfig().doubleJumpRequiresSprint && !p.isSprinting()) {
      return false;
    }

    return !getConfig().doubleJumpRequiresClockInHand || hasRecallClockInEitherHand(p);
  }

  private void clearDoubleJumpArm(Player p, UUID id) {
    if (jumpArmUntil.remove(id) == null) {
      return;
    }

    if (p.getGameMode() == GameMode.SURVIVAL) {
      p.setAllowFlight(false);
      p.setFlying(false);
    }
  }

  private void armDoubleJump(Player p, UUID id) {
    int triggerWindowMillis = Math.max(150, getConfig().doubleJumpWindowMillis);
    jumpArmUntil.put(id, M.ms() + triggerWindowMillis);
    p.setAllowFlight(true);
    J.runEntity(p, () -> {
      if (!p.isOnline()) {
        return;
      }

      Long armUntil = jumpArmUntil.get(id);
      if (armUntil != null && armUntil <= M.ms()) {
        clearDoubleJumpArm(p, id);
      }
    }, Math.max(1, (int) Math.ceil(triggerWindowMillis / 50D)));
  }

  private boolean isDoubleJumpStart(boolean wasOnGround, boolean onGround, Player p) {
    return wasOnGround
        && !onGround
        && p.getVelocity().getY() >= getConfig().doubleJumpMinVerticalVelocity;
  }

  private void clearPlayerState(UUID id) {
    snapshots.remove(id);
    lastSnapshot.remove(id);
    cooldowns.remove(id);
    cooldownReadyNotify.remove(id);
    rewindProtection.remove(id);
    rewinding.remove(id);
    recallXpStamps.remove(id);
    jumpArmUntil.remove(id);
    lastOnGround.remove(id);
    TELEPORT_XP_SUPPRESS_UNTIL.remove(id);
  }

  private boolean isRecallClock(ItemStack stack) {
    return stack != null
        && stack.getType() == Material.CLOCK
        && !ChronoTimeBombItem.isBindableItem(stack);
  }

  private void attemptRecall(Player p) {
    UUID id = p.getUniqueId();
    if (!isRecallEligible(p)) {
      return;
    }

    clearDoubleJumpArm(p, id);
    if (rewinding.contains(id)) {
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
    cooldownReadyNotify.add(id);
    rewinding.add(id);
    long protectionUntil = castAt + ((long) (animationTicks + getConfig().rewindProtectionTicks) * 50L);
    rewindProtection.put(id, protectionUntil);
    markRecallTeleportSuppressed(id, protectionUntil + ((long) Math.max(0, getConfig().rewindTeleportXpSuppressExtraTicks) * 50L));
    p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, animationTicks + getConfig().rewindProtectionTicks, 0, true, false, false), true);

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

    Runnable[] rewindTask = new Runnable[1];
    rewindTask[0] = () -> {
      if (!p.isOnline() || p.isDead()) {
        rewinding.remove(id);
        cleanupVisualState.accept(true);
        return;
      }

      float progress = animationTicks <= 1 ? 1f : (float) step[0] / (float) (animationTicks - 1);
      int index = Math.min(animationPath.size() - 1, step[0]);
      Snapshot snapshot = animationPath.get(index);
      Location destination = toLocation(snapshot, p.getWorld());

      if (getConfig().showRewindTraceParticles && lastLoc[0].getWorld() != null && lastLoc[0].getWorld().equals(destination.getWorld())) {
        Predicate<Location> traceFilter = J.isFoliaThreading() ? null : l -> l.getBlock().isPassable();
        vfxParticleLine(lastLoc[0].clone().add(0, 1, 0), destination.clone().add(0, 1, 0), Particle.REVERSE_PORTAL,
            Math.max(4, getConfig().rewindTracePoints), 1, 0.08D, 0.08D, 0.08D, 0D, null, true,
            traceFilter);
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
        cleanupVisualState.accept(true);
        Location finalDestination = toLocation(finalSnapshot, p.getWorld());
        if (finalDestination.getWorld() != null) {
          J.teleport(p, finalDestination, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
        applySnapshotState(p, finalSnapshot);

        if (areParticlesEnabled()) {
          p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 26, 0.25, 0.35, 0.25, 0.01);
        }
        if (areParticlesEnabled()) {
          p.getWorld().spawnParticle(Particle.ITEM, p.getLocation().add(0, 1, 0), 18, 0.30, 0.30, 0.30, 0.01, new ItemStack(Material.CLOCK));
        }
        if (getConfig().playClockSounds) {
          ChronosSoundFX.playRewindFinish(p);
        }
        rewinding.remove(id);
        getPlayer(p).getData().addStat("chronos.instant-recall.recalls", 1);
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
              xpContext.fromWorld(),
              xpContext.fromX(),
              xpContext.fromY(),
              xpContext.fromZ(),
              xpContext.toWorld(),
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
    if (rewinding.contains(id) || protectedUntil > M.ms()) {
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
    UUID id = p.getUniqueId();
    boolean wasOnGround = lastOnGround.getOrDefault(id, true);
    boolean onGround = p.isOnGround();
    lastOnGround.put(id, onGround);

    if (!isRecallEligible(p) || !getConfig().enableDoubleJumpTrigger) {
      clearDoubleJumpArm(p, id);
      return;
    }

    if (!wasOnGround && onGround) {
      clearDoubleJumpArm(p, id);
      return;
    }

    if (!canArmDoubleJump(p)) {
      clearDoubleJumpArm(p, id);
      return;
    }

    if (cooldowns.getOrDefault(id, 0L) > M.ms()) {
      clearDoubleJumpArm(p, id);
      return;
    }

    if (isDoubleJumpStart(wasOnGround, onGround, p)) {
      armDoubleJump(p, id);
      return;
    }

    Long armUntil = jumpArmUntil.get(id);
    if (armUntil != null && armUntil <= M.ms()) {
      clearDoubleJumpArm(p, id);
    }
  }

  @Override
  public void onTick() {
    long now = M.ms();
    Set<UUID> cooldownReadySet = cooldownReadyNotify;
    for (Iterator<UUID> iterator = cooldownReadySet.iterator(); iterator.hasNext(); ) {
      UUID id = iterator.next();
      Player p = Bukkit.getPlayer(id);
      if (p == null) {
        iterator.remove();
        continue;
      }

      long cooldown = cooldowns.getOrDefault(id, 0L);
      if (cooldown <= now) {
        if (getConfig().playClockSounds) {
          ChronosSoundFX.playCooldownReady(p);
        }
        iterator.remove();
      }
    }

    cooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
    rewindProtection.entrySet().removeIf(entry -> entry.getValue() <= now);
    TELEPORT_XP_SUPPRESS_UNTIL.entrySet().removeIf(entry -> entry.getValue() <= now);
  }

  @Override
  public boolean isEnabled() {
    return getConfig().enabled;
  }

  @Override
  public boolean isPermanent() {
    return getConfig().permanent;
  }
}

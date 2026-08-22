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

package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.Cooldowns;
import art.arcane.adapt.api.adaptation.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.taming.TamingStableHand;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.DiscoveryMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class DiscoveryInsight extends SimpleAdaptation<DiscoveryInsight.Config> {
  private static final String BAR_SEGMENT = "❚";
  private static final int HARD_MAX_HUD_CLEANUPS_PER_TICK = 4;
  private static final long HUD_UPDATE_INTERVAL_MILLIS = 250L;
  private static final long HUD_CLEANUP_INTERVAL_MILLIS = 1000L;
  private final Map<UUID, InsightHud> huds = new ConcurrentHashMap<>();
  private final Cooldowns xpCooldowns = cooldowns();
  private final Cooldowns hudUpdateThrottle = cooldowns();
  private final InsightRequestGate requestGate = new InsightRequestGate();
  private final InsightWorkBudget workBudget = new InsightWorkBudget(50L, System::currentTimeMillis);
  private final ConcurrentLinkedQueue<MovedViewer> movedViewers = new ConcurrentLinkedQueue<>();
  private final Set<UUID> queuedMovedViewers = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingViewerUpdates = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean();
  private Iterator<UUID> hudCleanupCursor = Collections.emptyIterator();
  private int playerCursor;
  private long nextHudCleanupAt;

  public DiscoveryInsight() {
    super("discovery-insight");
    registerConfiguration(Config.class);
    setIcon(Material.SPYGLASS);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPYGLASS)
        .key("challenge_discovery_insight_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.PARENT_GRANTED)
        .child(AdaptAdvancement.builder()
            .icon(Material.WRITABLE_BOOK)
            .key("challenge_discovery_insight_1000")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.PARENT_GRANTED)
            .build())
        .build());
    registerMilestone("challenge_discovery_insight_100", "discovery.insight.entities-inspected", 100, 300);
    registerMilestone("challenge_discovery_insight_1000", "discovery.insight.entities-inspected", 1000, 1200);
  }

  @Override
  public void addStats(int level, Element v) {
    statLore(v, Form.f(getRange(level), 0), 1);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!getConfig().showDamageNumbers || !(e.getEntity() instanceof LivingEntity victim) || victim instanceof ArmorStand) {
      return;
    }

    Player attacker;
    boolean projectileCritical;
    boolean melee;
    if (e.getDamager() instanceof Player pl) {
      attacker = pl;
      projectileCritical = false;
      melee = true;
    } else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player pl) {
      attacker = pl;
      projectileCritical = pr instanceof AbstractArrow arrow && arrow.isCritical();
      melee = false;
    } else {
      return;
    }

    double damage = e.getFinalDamage();
    int damageLimit = InsightWorkLimits.damageNumbers(getConfig().maxDamageNumbersPerTick);
    if (attacker == victim || damage <= 0D
        || !workBudget.tryAcquireDamageNumber(damageLimit)) {
      return;
    }

    ThreadLocalRandom random = ThreadLocalRandom.current();
    Location location = victim.getLocation().add(
        random.nextDouble(-0.35D, 0.35D),
        (victim.getHeight() * 0.8D) + 0.3D,
        random.nextDouble(-0.35D, 0.35D));
    DamageTargetSnapshot target = new DamageTargetSnapshot(location, damage);
    J.runEntity(attacker, () -> prepareDamageNumberOwned(attacker, target, melee, projectileCritical));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  @RunsWithoutLearnedAdaptation
  public void on(PlayerMoveEvent e) {
    Location to = e.getTo();
    if (to == null || !viewChanged(e.getFrom(), to)) {
      return;
    }

    Player player = e.getPlayer();
    UUID playerId = player.getUniqueId();
    if (!huds.containsKey(playerId) && !getServer().hasOnlineLearner(playerId, getName())) {
      return;
    }
    if (queuedMovedViewers.add(playerId)) {
      movedViewers.offer(new MovedViewer(playerId, player));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    UUID playerId = e.getPlayer().getUniqueId();
    requestGate.advance(playerId);
    queuedMovedViewers.remove(playerId);
    pendingViewerUpdates.remove(playerId);
    clearHud(playerId);
  }

  @Override
  public void unregister() {
    if (lifecycleCleanupStarted.compareAndSet(false, true)) {
      requestGate.clear();
      movedViewers.clear();
      queuedMovedViewers.clear();
      pendingViewerUpdates.clear();
      cleanupHudsOnUnregister(huds.keySet().iterator());
    }
    super.unregister();
  }

  @Override
  public boolean hasTickDemand() {
    return !lifecycleCleanupStarted.get()
        && (!huds.isEmpty() || !movedViewers.isEmpty() || !pendingViewerUpdates.isEmpty());
  }

  @Override
  public void onTick() {
    if (lifecycleCleanupStarted.get()) {
      return;
    }
    long now = System.currentTimeMillis();
    updatePlayerBatch(now);
    cleanupHudBatch(now);
  }

  private void updatePlayerBatch(long now) {
    List<AdaptPlayer> candidates = learnedCandidates(now);
    int limit = InsightWorkLimits.viewerUpdates(getConfig().maxPlayersPerPass);
    int priorityAttempts = drainMovedViewers(InsightWorkLimits.priorityViewerUpdates(limit));
    int size = candidates.size();
    if (size == 0) {
      playerCursor = 0;
      return;
    }

    int regularLimit = Math.min(size, Math.max(0, limit - priorityAttempts));
    int start = Math.floorMod(playerCursor, size);
    for (int i = 0; i < regularLimit; i++) {
      AdaptPlayer adaptPlayer = candidates.get((start + i) % size);
      Player player = adaptPlayer.getPlayer();
      if (player != null && huds.containsKey(player.getUniqueId())) {
        queueHudUpdate(player);
      }
    }
    playerCursor = (start + regularLimit) % size;
  }

  private int drainMovedViewers(int limit) {
    int processed = 0;
    while (processed < limit) {
      MovedViewer moved = movedViewers.poll();
      if (moved == null) {
        break;
      }
      queuedMovedViewers.remove(moved.playerId());
      queueHudUpdate(moved.player());
      processed++;
    }
    return processed;
  }

  private void queueHudUpdate(Player player) {
    UUID playerId = player.getUniqueId();
    if (!hudUpdateThrottle.isReady(playerId, HUD_UPDATE_INTERVAL_MILLIS)
        || !pendingViewerUpdates.add(playerId)) {
      return;
    }

    hudUpdateThrottle.mark(playerId);
    if (!J.runEntity(player, () -> {
      try {
        if (player.isOnline()) {
          updateHudOwned(player);
        } else {
          requestGate.advance(playerId);
          clearHud(playerId);
        }
      } finally {
        pendingViewerUpdates.remove(playerId);
      }
    })) {
      pendingViewerUpdates.remove(playerId);
    }
  }

  private void cleanupHudBatch(long now) {
    if (!hudCleanupCursor.hasNext()) {
      if (now < nextHudCleanupAt) {
        return;
      }
      hudCleanupCursor = huds.keySet().iterator();
      nextHudCleanupAt = now + HUD_CLEANUP_INTERVAL_MILLIS;
    }

    int limit = HARD_MAX_HUD_CLEANUPS_PER_TICK;
    int processed = 0;
    while (processed < limit && hudCleanupCursor.hasNext()) {
      UUID id = hudCleanupCursor.next();
      processed++;
      InsightHud hud = huds.get(id);
      if (hud == null) {
        continue;
      }
      Player owner = hud.owner;
      if (!J.runEntity(owner, () -> {
        if (!owner.isOnline() || getActiveLevel(owner) <= 0) {
          requestGate.advance(id);
          clearHud(id);
        }
      })) {
        requestGate.advance(id);
        clearHud(id);
      }
    }
  }

  private void cleanupHudsOnUnregister(Iterator<UUID> cursor) {
    int limit = InsightWorkLimits.viewerUpdates(getConfig().maxPlayersPerPass);
    int processed = 0;
    while (processed < limit && cursor.hasNext()) {
      clearHud(cursor.next());
      processed++;
    }

    if (cursor.hasNext()) {
      J.s(() -> cleanupHudsOnUnregister(cursor), 1);
    }
  }

  private void updateHudOwned(Player p) {
    UUID id = p.getUniqueId();
    long token = requestGate.advance(id);
    int level = getActiveLevel(p);
    if (level <= 0) {
      clearHudIfCurrent(id, token);
      return;
    }

    Location eye = p.getEyeLocation();
    ViewerPoint viewer = new ViewerPoint(eye.getWorld().getUID(), eye.getX(), eye.getY(), eye.getZ());
    LivingEntity target = findLookTargetCandidate(p, eye, getRange(level));
    if (target == null) {
      clearHudIfCurrent(id, token);
      return;
    }

    if (target instanceof Player other && !p.canSee(other)) {
      clearHudIfCurrent(id, token);
      return;
    }

    if (!J.runEntity(target, () -> inspectTargetOwned(id, p, target, viewer, token))) {
      clearHudIfCurrent(id, token);
    }
  }

  private LivingEntity findLookTargetCandidate(Player p, Location eye, double range) {
    if (!Double.isFinite(range) || range <= 0D) {
      return null;
    }

    Vector direction = eye.getDirection();
    RayTraceResult entityHit = p.getWorld().rayTraceEntities(eye, direction, range, 0.3D,
        entity -> entity instanceof LivingEntity && entity != p && !(entity instanceof ArmorStand));
    if (entityHit == null || !(entityHit.getHitEntity() instanceof LivingEntity target)) {
      return null;
    }

    double entityDistance = eye.toVector().distance(entityHit.getHitPosition());
    RayTraceResult blockHit = p.getWorld().rayTraceBlocks(
        eye,
        direction,
        entityDistance,
        FluidCollisionMode.NEVER,
        true
    );
    if (blockHit != null && isOccluded(eye.toVector(), entityHit.getHitPosition(), blockHit.getHitPosition())) {
      return null;
    }
    return target;
  }

  static boolean isOccluded(Vector origin, Vector entityHit, Vector blockHit) {
    if (origin == null || entityHit == null || blockHit == null) {
      return false;
    }
    return origin.distanceSquared(blockHit) + 1.0E-8D < origin.distanceSquared(entityHit);
  }

  private void inspectTargetOwned(UUID ownerId, Player owner, LivingEntity target, ViewerPoint viewer, long token) {
    if (lifecycleCleanupStarted.get() || !requestGate.isCurrent(ownerId, token)) {
      return;
    }
    if (!target.isValid() || target.isDead() || target.isInvisible()) {
      clearHudIfCurrent(ownerId, token);
      return;
    }

    Location location = hudLocation(target);
    HudVitals vitals = readVitals(target);
    float scale = displayScale(viewer, location);
    UUID targetId = target.getUniqueId();
    InsightHud current = huds.get(ownerId);
    if (current != null && current.targetId.equals(targetId) && current.display != null) {
      refreshDisplay(current, new HudRenderState(location, scale, vitals), token);
      return;
    }

    spawnHudOwned(ownerId, owner, target, targetId, location, scale, vitals, token);
  }

  private void spawnHudOwned(UUID ownerId, Player owner, LivingEntity target, UUID targetId, Location location,
                             float scale, HudVitals vitals, long token) {
    if (!requestGate.isCurrent(ownerId, token)) {
      return;
    }

    InsightHud hud = new InsightHud(ownerId, owner, targetId);
    InsightHud replaced = huds.put(ownerId, hud);
    if (replaced != null) {
      removeDisplayEntity(replaced.display);
    }

    TextDisplay display = target.getWorld().spawn(location, TextDisplay.class, candidate -> {
      applyDisplayDefaults(candidate);
      candidate.setTeleportDuration(3);
      candidate.setLineWidth(220);
      candidate.setText(buildHudText(vitals));
      candidate.setTransformation(scaleTransformation(scale, 0f));
    });
    hud.display = display;
    hud.applyRenderState(new HudRenderState(location, scale, vitals));
    if (!requestGate.isCurrent(ownerId, token) || huds.get(ownerId) != hud) {
      huds.remove(ownerId, hud);
      removeDisplayEntity(display);
      return;
    }

    if (!J.runEntity(owner, () -> acceptSpawnedHudOwned(owner, hud, display, token))) {
      huds.remove(ownerId, hud);
      removeDisplayEntity(display);
    }
  }

  private void acceptSpawnedHudOwned(Player owner, InsightHud hud, TextDisplay display, long token) {
    UUID ownerId = owner.getUniqueId();
    if (!owner.isOnline() || getActiveLevel(owner) <= 0 || !requestGate.isCurrent(ownerId, token)
        || huds.get(ownerId) != hud) {
      huds.remove(ownerId, hud);
      removeDisplayEntity(display);
      return;
    }

    owner.showEntity(Adapt.instance, display);
    if (xpCooldowns.isReady(ownerId, getConfig().xpCooldownMs)) {
      xpCooldowns.mark(ownerId);
      xp(owner, getConfig().xpPerInspection);
      addStat(owner, "discovery.insight.entities-inspected", 1);
    }
  }

  private void refreshDisplay(InsightHud hud, HudRenderState state, long token) {
    TextDisplay display = hud.display;
    if (display == null || hud.matches(state)) {
      return;
    }

    J.runEntity(display, () -> refreshDisplayOwned(hud, display, state, token));
  }

  private void refreshDisplayOwned(InsightHud hud, TextDisplay display, HudRenderState state, long token) {
    UUID ownerId = hud.ownerId;
    if (!display.isValid()) {
      huds.remove(ownerId, hud);
      return;
    }
    if (huds.get(ownerId) != hud || !requestGate.isCurrent(ownerId, token)) {
      return;
    }

    boolean positionChanged = !hud.hasPosition(state.location());
    boolean scaleChanged = Float.compare(hud.scale, state.scale()) != 0;
    boolean vitalsChanged = !hud.hasVitals(state.vitals());
    if (vitalsChanged) {
      display.setText(buildHudText(state.vitals()));
    }
    if (scaleChanged) {
      display.setTransformation(scaleTransformation(state.scale(), 0f));
    }
    if (positionChanged) {
      J.teleport(display, state.location());
    }
    hud.applyRenderState(state);
  }

  private void prepareDamageNumberOwned(Player attacker, DamageTargetSnapshot target,
                                        boolean melee, boolean projectileCritical) {
    if (!attacker.isOnline() || !hasActiveAdaptation(attacker)) {
      return;
    }

    boolean critical = projectileCritical || (melee && attacker.getFallDistance() > 0F && !attacker.isOnGround());
    Location eye = attacker.getEyeLocation();
    ViewerPoint viewer = new ViewerPoint(eye.getWorld().getUID(), eye.getX(), eye.getY(), eye.getZ());
    J.runAt(target.location(), () -> spawnDamageNumberOwned(attacker, viewer, target, critical));
  }

  private void spawnDamageNumberOwned(Player attacker, ViewerPoint viewer,
                                      DamageTargetSnapshot target, boolean critical) {
    Location location = target.location();
    float scale = displayScale(viewer, location) * (critical ? 1.25F : 1F);
    String text = (critical ? C.GOLD : C.WHITE) + formatDamage(target.damage());
    TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, d -> {
      applyDisplayDefaults(d);
      d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
      d.setText(text);
      d.setTransformation(scaleTransformation(scale, 0f));
    });
    showToOwner(attacker, display);

    int life = Math.max(6, getConfig().damageNumberLifeTicks);
    float rise = (float) getConfig().damageNumberRise;
    J.runEntity(display, () -> {
      if (!display.isValid()) {
        return;
      }

      display.setInterpolationDelay(0);
      display.setInterpolationDuration(life);
      display.setTransformation(scaleTransformation(scale, rise));
    }, 1);
    J.runEntity(display, () -> {
      if (display.isValid()) {
        display.remove();
      }
    }, life + 2);
  }

  private boolean viewChanged(Location from, Location to) {
    return Float.compare(from.getYaw(), to.getYaw()) != 0
        || Float.compare(from.getPitch(), to.getPitch()) != 0
        || from.getWorld() != to.getWorld()
        || from.distanceSquared(to) > 0.01D;
  }

  private void applyDisplayDefaults(TextDisplay d) {
    d.setPersistent(false);
    d.setInvulnerable(true);
    d.setGravity(false);
    d.setSilent(true);
    d.setVisibleByDefault(false);
    d.setBillboard(Display.Billboard.CENTER);
    d.setShadowed(true);
    d.setSeeThrough(false);
    d.setShadowRadius(0f);
    d.setShadowStrength(0f);
  }

  private HudVitals readVitals(LivingEntity target) {
    double max = maxHealth(target);
    double health = Math.max(0D, Math.min(target.getHealth(), max));
    String name = target.getCustomName() == null ? target.getName() : target.getCustomName();
    AnimalStats animalStats = target instanceof Tameable ? readAnimalStats(target) : null;
    return new HudVitals(name, health, max, animalStats);
  }

  private String buildHudText(HudVitals vitals) {
    return buildHudText(vitals, Math.max(4, getConfig().healthBarSegments));
  }

  static String buildHudText(HudVitals vitals, int healthBarSegments) {
    double max = vitals.maxHealth();
    double hp = vitals.health();
    double fraction = max <= 0 ? 0 : hp / max;
    int segments = Math.max(4, healthBarSegments);
    int filled = (int) Math.ceil(fraction * segments);
    if (hp > 0) {
      filled = Math.max(1, filled);
    }
    filled = Math.min(segments, filled);

    C barColor = fraction > 0.5 ? C.GREEN : (fraction > 0.25 ? C.YELLOW : C.RED);
    String text = C.WHITE + vitals.name() + "\n"
        + barColor + BAR_SEGMENT.repeat(filled)
        + C.DARK_GRAY + BAR_SEGMENT.repeat(segments - filled)
        + C.GRAY + " " + Form.f(hp, 1) + C.DARK_GRAY + "/" + C.GRAY + Form.f(max, 0);
    return text + buildAnimalStatsText(vitals.animalStats());
  }

  private AnimalStats readAnimalStats(LivingEntity target) {
    return new AnimalStats(
        attributeValue(target, Attributes.MOVEMENT_SPEED),
        attributeValue(target, Attributes.JUMP_STRENGTH),
        attributeValue(target, Attributes.ATTACK_DAMAGE),
        TamingStableHand.hasAppliedBias(target));
  }

  private double attributeValue(LivingEntity target, Attribute attribute) {
    if (attribute == null) {
      return Double.NaN;
    }

    AttributeInstance instance = target.getAttribute(attribute);
    if (instance == null || !Double.isFinite(instance.getValue())) {
      return Double.NaN;
    }
    return instance.getValue();
  }

  private static String buildAnimalStatsText(AnimalStats stats) {
    if (stats == null) {
      return "";
    }

    StringBuilder text = new StringBuilder();
    appendAnimalStat(text, DiscoveryMessages.INSIGHT_SPEED, stats.movementSpeed(), 2);
    appendAnimalStat(text, DiscoveryMessages.INSIGHT_JUMP, stats.jumpStrength(), 2);
    appendAnimalStat(text, DiscoveryMessages.INSIGHT_ATTACK, stats.attackDamage(), 1);
    if (stats.stableHandEnhanced()) {
      text.append('\n').append(C.AQUA).append(AdaptLanguage.text(DiscoveryMessages.INSIGHT_STABLE_HAND));
    }
    return text.toString();
  }

  private static void appendAnimalStat(StringBuilder text, TextKey label, double value, int decimals) {
    if (!Double.isFinite(value)) {
      return;
    }
    if (text.isEmpty()) {
      text.append('\n');
    } else {
      text.append(C.DARK_GRAY).append(" • ");
    }
    text.append(C.GRAY).append(AdaptLanguage.text(
        DiscoveryMessages.INSIGHT_STAT,
        trusted("label", AdaptLanguage.text(label)),
        trusted("value", C.WHITE + Form.f(value, decimals))
    ));
  }

  private double maxHealth(LivingEntity target) {
    AttributeInstance attribute = target.getAttribute(Attribute.MAX_HEALTH);
    return attribute == null ? Math.max(1, target.getHealth()) : attribute.getValue();
  }

  private String formatDamage(double damage) {
    return damage >= 10 ? String.valueOf(Math.round(damage)) : Form.f(damage, 1);
  }

  private Location hudLocation(LivingEntity target) {
    return target.getLocation().add(0, target.getHeight() + 0.5, 0);
  }

  private float displayScale(ViewerPoint viewer, Location loc) {
    double distance;
    if (!viewer.worldId().equals(loc.getWorld().getUID())) {
      distance = getConfig().hudMaxScale / getConfig().hudScalePerBlock;
    } else {
      double dx = viewer.x() - loc.getX();
      double dy = viewer.y() - loc.getY();
      double dz = viewer.z() - loc.getZ();
      distance = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz));
    }
    return (float) Math.min(getConfig().hudMaxScale, Math.max(getConfig().hudMinScale, distance * getConfig().hudScalePerBlock));
  }

  private Transformation scaleTransformation(float scale, float riseY) {
    return new Transformation(new Vector3f(0f, riseY, 0f), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf());
  }

  private double getRange(int level) {
    return getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor);
  }

  private void clearHud(UUID id) {
    InsightHud hud = huds.remove(id);
    if (hud == null) {
      return;
    }

    removeDisplayEntity(hud.display);
  }

  private void clearHudIfCurrent(UUID id, long token) {
    if (requestGate.isCurrent(id, token)) {
      clearHud(id);
    }
  }

  private void removeDisplayEntity(Entity entity) {
    if (entity == null) {
      return;
    }

    J.runEntity(entity, () -> {
      if (entity.isValid()) {
        entity.remove();
      }
    });
  }

  private void showToOwner(Player owner, Entity entity) {
    J.runEntity(owner, () -> {
      if (owner.isOnline()) {
        owner.showEntity(Adapt.instance, entity);
      }
    });
  }

  private static final class InsightHud {
    private final UUID ownerId;
    private final Player owner;
    private final UUID targetId;
    private volatile TextDisplay display;
    private volatile UUID worldId;
    private volatile double x = Double.NaN;
    private volatile double y = Double.NaN;
    private volatile double z = Double.NaN;
    private volatile float scale = Float.NaN;
    private volatile String name;
    private volatile double health = Double.NaN;
    private volatile double maxHealth = Double.NaN;
    private volatile AnimalStats animalStats;

    private InsightHud(UUID ownerId, Player owner, UUID targetId) {
      this.ownerId = ownerId;
      this.owner = owner;
      this.targetId = targetId;
    }

    private boolean hasPosition(Location location) {
      return Objects.equals(worldId, location.getWorld().getUID())
          && Double.compare(x, location.getX()) == 0
          && Double.compare(y, location.getY()) == 0
          && Double.compare(z, location.getZ()) == 0;
    }

    private boolean hasVitals(HudVitals vitals) {
      return Objects.equals(name, vitals.name())
          && Double.compare(health, vitals.health()) == 0
          && Double.compare(maxHealth, vitals.maxHealth()) == 0
          && Objects.equals(animalStats, vitals.animalStats());
    }

    private boolean matches(HudRenderState state) {
      return hasPosition(state.location())
          && Float.compare(scale, state.scale()) == 0
          && hasVitals(state.vitals());
    }

    private void applyRenderState(HudRenderState state) {
      Location location = state.location();
      HudVitals vitals = state.vitals();
      worldId = location.getWorld().getUID();
      x = location.getX();
      y = location.getY();
      z = location.getZ();
      this.scale = state.scale();
      name = vitals.name();
      health = vitals.health();
      maxHealth = vitals.maxHealth();
      animalStats = vitals.animalStats();
    }
  }

  record HudVitals(String name, double health, double maxHealth, AnimalStats animalStats) {
  }

  record AnimalStats(double movementSpeed, double jumpStrength, double attackDamage,
                     boolean stableHandEnhanced) {
  }

  private record HudRenderState(Location location, float scale, HudVitals vitals) {
  }

  private record ViewerPoint(UUID worldId, double x, double y, double z) {
  }

  private record DamageTargetSnapshot(Location location, double damage) {
  }

  private record MovedViewer(UUID playerId, Player player) {
  }

  @ConfigDescription("Study creatures at a glance with a floating name, health bar, and personal damage numbers.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Base for the Discovery Insight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeBase = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Range Factor for the Discovery Insight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double rangeFactor = 18;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Display scale gained per block of distance so the HUD keeps a constant on-screen size.", impact = "Higher values make the HUD larger at range.")
    double hudScalePerBlock = 0.22;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Minimum HUD display scale.", impact = "Higher values keep the HUD larger up close.")
    double hudMinScale = 0.5;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum HUD display scale.", impact = "Higher values let the HUD grow larger at long range.")
    double hudMaxScale = 4.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Health Bar Segments for the Discovery Insight adaptation.", impact = "Higher values render a finer-grained health bar.")
    int healthBarSegments = 12;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Shows floating damage numbers when you hit creatures.", impact = "True enables this behavior and false disables it.")
    boolean showDamageNumbers = true;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Vertical drift of damage numbers over their lifetime.", impact = "Higher values make numbers float up further.")
    double damageNumberRise = 0.7;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Lifetime of damage numbers in ticks.", impact = "Higher values keep numbers on screen longer.")
    int damageNumberLifeTicks = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum damage numbers spawned per scheduler tick, capped internally at 16.", impact = "Lower values reduce cosmetic display work during dense combat without changing damage.")
    int maxDamageNumbersPerTick = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp Per Inspection for the Discovery Insight adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpPerInspection = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Cooldown between inspection XP grants in milliseconds.", impact = "Higher values slow inspection XP gain.")
    long xpCooldownMs = 10000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum viewers examined per scheduler tick, capped internally at 32.", impact = "Higher values refresh Insight faster on large servers but increase player and target owned work.")
    int maxPlayersPerPass = 32;

    public Config() {
      baseCost = 2;
      costFactor = 0.2;
      maxLevel = 5;
      initialCost = 2;
    }
  }
}

final class InsightRequestGate {
  private final AtomicLong sequence = new AtomicLong();
  private final Map<UUID, Long> current = new ConcurrentHashMap<>();

  long advance(UUID playerId) {
    long token = sequence.incrementAndGet();
    current.put(playerId, token);
    return token;
  }

  boolean isCurrent(UUID playerId, long token) {
    Long currentToken = current.get(playerId);
    return currentToken != null && currentToken == token;
  }

  void clear() {
    sequence.incrementAndGet();
    current.clear();
  }
}

final class InsightWorkBudget {
  private final long windowMillis;
  private final LongSupplier clock;
  private long window = Long.MIN_VALUE;
  private int damageNumbers;

  InsightWorkBudget(long windowMillis, LongSupplier clock) {
    this.windowMillis = Math.max(1L, windowMillis);
    this.clock = Objects.requireNonNull(clock);
  }

  synchronized boolean tryAcquireDamageNumber(int limit) {
    rotateWindow();
    if (damageNumbers >= Math.max(0, limit)) {
      return false;
    }
    damageNumbers++;
    return true;
  }

  synchronized int damageNumbers() {
    return damageNumbers;
  }

  private void rotateWindow() {
    long currentWindow = Math.floorDiv(clock.getAsLong(), windowMillis);
    if (currentWindow != window) {
      window = currentWindow;
      damageNumbers = 0;
    }
  }
}

final class InsightWorkLimits {
  private static final int MAX_VIEWER_UPDATES_PER_TICK = 32;
  private static final int MAX_PRIORITY_VIEWER_UPDATES_PER_TICK = 8;
  private static final int MAX_DAMAGE_NUMBERS_PER_TICK = 16;

  private InsightWorkLimits() {
  }

  static int viewerUpdates(int configured) {
    return Math.min(MAX_VIEWER_UPDATES_PER_TICK, Math.max(1, configured));
  }

  static int priorityViewerUpdates(int totalLimit) {
    return Math.min(MAX_PRIORITY_VIEWER_UPDATES_PER_TICK, Math.max(0, totalLimit));
  }

  static int damageNumbers(int configured) {
    return Math.min(MAX_DAMAGE_NUMBERS_PER_TICK, Math.max(1, configured));
  }
}

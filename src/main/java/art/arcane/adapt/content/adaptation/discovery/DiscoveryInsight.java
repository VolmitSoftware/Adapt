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
import art.arcane.adapt.localization.catalog.KineticsMessages;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.reflect.registries.Attributes;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.localization.TextKey;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

public class DiscoveryInsight extends SimpleAdaptation<DiscoveryInsight.Config> {
  private static final int HARD_MAX_HUD_CLEANUPS_PER_TICK = 4;
  private static final long HUD_UPDATE_INTERVAL_MILLIS = 250L;
  private static final long HUD_CLEANUP_INTERVAL_MILLIS = 1000L;
  private static final long INSIGHT_DURATION_MILLIS = 2000L;

  private final Map<UUID, InsightHud> huds = new ConcurrentHashMap<>();
  private final Cooldowns xpCooldowns = cooldowns();
  private final Cooldowns hudUpdateThrottle = cooldowns();
  private final InsightRequestGate requestGate = new InsightRequestGate();
  private final GlossInsightIntegration gloss = new GlossInsightIntegration(Adapt.instance);
  private final ConcurrentLinkedQueue<MovedViewer> movedViewers = new ConcurrentLinkedQueue<>();
  private final Set<UUID> queuedMovedViewers = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingViewerUpdates = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean();
  private Iterator<UUID> hudCleanupCursor = Collections.emptyIterator();
  private int playerCursor;
  private long nextHudCleanupAt;
  private volatile boolean runtimeActive;

  public DiscoveryInsight() {
    super("discovery-insight");
    registerConfiguration(Config.class);
    setIcon(Material.SPYGLASS);
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.SPYGLASS)
        .key("challenge_discovery_insight_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.WRITABLE_BOOK)
            .key("challenge_discovery_insight_1000")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
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
  @RunsWithoutLearnedAdaptation
  public void on(PlayerMoveEvent event) {
    Location to = event.getTo();
    if (to == null || !viewChanged(event.getFrom(), to)) {
      return;
    }

    Player player = event.getPlayer();
    UUID playerId = player.getUniqueId();
    if (!huds.containsKey(playerId) && !getServer().hasOnlineLearner(playerId, getName())) {
      return;
    }
    if (queuedMovedViewers.add(playerId)) {
      movedViewers.offer(new MovedViewer(playerId, player));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  @RunsWithoutLearnedAdaptation
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.getPlayer().getUniqueId();
    requestGate.remove(playerId);
    queuedMovedViewers.remove(playerId);
    pendingViewerUpdates.remove(playerId);
    clearHud(playerId);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  @RunsWithoutLearnedAdaptation
  public void on(PluginEnableEvent event) {
    if (event.getPlugin().getName().equals("Gloss")) {
      gloss.refresh();
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  @RunsWithoutLearnedAdaptation
  public void on(ServiceRegisterEvent event) {
    if (event.getProvider().getService().getName().equals(GlossInsightIntegration.API_CLASS)) {
      gloss.refresh();
    }
  }

  @Override
  public void unregister() {
    if (lifecycleCleanupStarted.compareAndSet(false, true)) {
      runtimeActive = false;
      requestGate.clear();
      movedViewers.clear();
      queuedMovedViewers.clear();
      pendingViewerUpdates.clear();
      for (UUID playerId : huds.keySet()) {
        clearHud(playerId);
      }
      gloss.setRestricted(false);
    }
    super.unregister();
  }

  @Override
  public boolean hasTickDemand() {
    return !lifecycleCleanupStarted.get()
        && (!huds.isEmpty() || !movedViewers.isEmpty() || !pendingViewerUpdates.isEmpty()
        || !learnedCandidates(System.currentTimeMillis()).isEmpty());
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

  @Override
  protected void onRuntimeActivated() {
    super.onRuntimeActivated();
    runtimeActive = true;
    Config config = getConfig();
    gloss.setRestricted(config.enabled && config.restrictGlossToInsight);
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    if (runtimeActive) {
      gloss.setRestricted(newConfig.enabled && newConfig.restrictGlossToInsight);
      if (!newConfig.enabled) {
        requestGate.clear();
        for (UUID playerId : huds.keySet()) {
          clearHud(playerId);
        }
      }
    }
  }

  @Override
  protected void normalizeLoadedConfig(Config config) {
    config.rangeBase = finiteNonnegative(config.rangeBase, 6D);
    config.rangeFactor = finiteNonnegative(config.rangeFactor, 18D);
    config.maxPlayersPerPass = InsightWorkLimits.viewerUpdates(config.maxPlayersPerPass);
    config.xpPerInspection = finiteNonnegative(config.xpPerInspection, 3D);
    config.xpCooldownMs = Math.max(0L, config.xpCooldownMs);
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
    for (int index = 0; index < regularLimit; index++) {
      AdaptPlayer adaptPlayer = candidates.get((start + index) % size);
      Player player = adaptPlayer.getPlayer();
      if (player != null) {
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
          requestGate.remove(playerId);
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

    int processed = 0;
    while (processed < HARD_MAX_HUD_CLEANUPS_PER_TICK && hudCleanupCursor.hasNext()) {
      UUID playerId = hudCleanupCursor.next();
      processed++;
      InsightHud hud = huds.get(playerId);
      if (hud == null) {
        continue;
      }
      Player owner = hud.owner();
      if (!J.runEntity(owner, () -> {
        if (!owner.isOnline() || getActiveLevel(owner) <= 0) {
          requestGate.remove(playerId);
          clearHud(playerId);
        }
      })) {
        requestGate.remove(playerId);
        clearHud(playerId);
      }
    }
  }

  private void updateHudOwned(Player player) {
    UUID playerId = player.getUniqueId();
    long token = requestGate.advance(playerId);
    int level = getActiveLevel(player);
    if (level <= 0 || !gloss.available()) {
      clearHudIfCurrent(playerId, token);
      return;
    }

    Location eye = player.getEyeLocation();
    LivingEntity target = findLookTargetCandidate(player, eye, getRange(level));
    if (target == null || target instanceof Player other && !player.canSee(other)) {
      clearHudIfCurrent(playerId, token);
      return;
    }

    if (!J.runEntity(target, () -> inspectTargetOwned(playerId, player, target, token))) {
      clearHudIfCurrent(playerId, token);
    }
  }

  private LivingEntity findLookTargetCandidate(Player player, Location eye, double range) {
    if (!Double.isFinite(range) || range <= 0D) {
      return null;
    }

    Vector direction = eye.getDirection();
    RayTraceResult entityHit = player.getWorld().rayTraceEntities(eye, direction, range, 0.3D,
        entity -> entity instanceof LivingEntity && entity != player && !(entity instanceof ArmorStand));
    if (entityHit == null || !(entityHit.getHitEntity() instanceof LivingEntity target)) {
      return null;
    }

    double entityDistance = eye.toVector().distance(entityHit.getHitPosition());
    RayTraceResult blockHit = player.getWorld().rayTraceBlocks(
        eye, direction, entityDistance, FluidCollisionMode.NEVER, true);
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

  private void inspectTargetOwned(UUID playerId, Player owner, LivingEntity target, long token) {
    if (lifecycleCleanupStarted.get() || !requestGate.isCurrent(playerId, token)) {
      return;
    }
    if (!target.isValid() || target.isDead() || target.isInvisible()) {
      J.runEntity(owner, () -> clearHudIfCurrent(playerId, token));
      return;
    }

    UUID targetId = target.getUniqueId();
    List<String> details = buildInsightDetails(readStats(target));
    if (!J.runEntity(owner, () -> acceptInspectionOwned(owner, target, targetId, details, token))) {
      clearHudIfCurrent(playerId, token);
    }
  }

  private void acceptInspectionOwned(Player owner, LivingEntity target, UUID targetId,
                                     List<String> details, long token) {
    UUID playerId = owner.getUniqueId();
    if (lifecycleCleanupStarted.get() || !requestGate.isCurrent(playerId, token)) {
      return;
    }
    if (!owner.isOnline() || getActiveLevel(owner) <= 0
        || !gloss.update(owner, target, details, INSIGHT_DURATION_MILLIS)) {
      clearHudIfCurrent(playerId, token);
      return;
    }

    InsightHud previous = huds.put(playerId, new InsightHud(owner, targetId));
    if ((previous == null || !previous.targetId().equals(targetId))
        && xpCooldowns.isReady(playerId, getConfig().xpCooldownMs)) {
      xpCooldowns.mark(playerId);
      xp(owner, getConfig().xpPerInspection);
      addStat(owner, "discovery.insight.entities-inspected", 1);
    }
  }

  private boolean viewChanged(Location from, Location to) {
    return Float.compare(from.getYaw(), to.getYaw()) != 0
        || Float.compare(from.getPitch(), to.getPitch()) != 0
        || !from.getWorld().equals(to.getWorld())
        || Double.compare(from.getX(), to.getX()) != 0
        || Double.compare(from.getY(), to.getY()) != 0
        || Double.compare(from.getZ(), to.getZ()) != 0;
  }

  private InsightStats readStats(LivingEntity target) {
    String species = Form.capitalizeWords(target.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
    return new InsightStats(species,
        attributeValue(target, Attributes.MOVEMENT_SPEED),
        attributeValue(target, Attributes.JUMP_STRENGTH),
        attributeValue(target, Attributes.ARMOR_TOUGHNESS),
        attributeValue(target, Attributes.KNOCKBACK_RESISTANCE),
        attributeValue(target, Attributes.FOLLOW_RANGE),
        target instanceof Tameable && TamingStableHand.hasAppliedBias(target));
  }

  private double attributeValue(LivingEntity target, Attribute attribute) {
    if (attribute == null) {
      return Double.NaN;
    }
    AttributeInstance instance = target.getAttribute(attribute);
    return instance == null ? Double.NaN : instance.getValue();
  }

  static List<String> buildInsightDetails(InsightStats stats) {
    List<String> details = new ArrayList<>(4);
    details.add(C.AQUA + stats.species());
    StringBuilder movement = new StringBuilder();
    appendStat(movement, DiscoveryMessages.INSIGHT_SPEED, stats.movementSpeed(), 2);
    appendStat(movement, DiscoveryMessages.INSIGHT_JUMP, stats.jumpStrength(), 2);
    if (!movement.isEmpty()) {
      details.add(movement.toString());
    }
    StringBuilder defenses = new StringBuilder();
    appendStat(defenses, KineticsMessages.QUAKE_GUARD_LORE2, stats.armorToughness(), 1);
    appendStat(defenses, KineticsMessages.QUAKE_GUARD_LORE1, stats.knockbackResistance(), 2);
    appendStat(defenses, DiscoveryMessages.INSIGHT_LORE1, stats.followRange(), 1);
    if (!defenses.isEmpty()) {
      details.add(defenses.toString());
    }
    if (stats.stableHandEnhanced()) {
      details.add(C.AQUA + AdaptLanguage.text(DiscoveryMessages.INSIGHT_STABLE_HAND));
    }
    return List.copyOf(details);
  }

  private static void appendStat(StringBuilder text, TextKey label, double value, int decimals) {
    if (!Double.isFinite(value)) {
      return;
    }
    if (!text.isEmpty()) {
      text.append(C.DARK_GRAY).append(" | ");
    }
    text.append(C.GRAY).append(AdaptLanguage.text(
        DiscoveryMessages.INSIGHT_STAT,
        trusted("label", AdaptLanguage.text(label)),
        trusted("value", C.WHITE + Form.f(value, decimals))
    ));
  }

  private double getRange(int level) {
    return getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor);
  }

  private static double finiteNonnegative(double value, double fallback) {
    return Double.isFinite(value) ? Math.max(0D, value) : fallback;
  }

  private void clearHud(UUID playerId) {
    huds.remove(playerId);
    gloss.clear(playerId);
  }

  private void clearHudIfCurrent(UUID playerId, long token) {
    if (requestGate.isCurrent(playerId, token)) {
      clearHud(playerId);
    }
  }

  record InsightStats(String species, double movementSpeed, double jumpStrength, double armorToughness,
                      double knockbackResistance, double followRange, boolean stableHandEnhanced) {
  }

  private record InsightHud(Player owner, UUID targetId) {
  }

  private record MovedViewer(UUID playerId, Player player) {
  }

  @ConfigDescription("Inspect creatures through Gloss entity overlays and reveal their live attributes.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Base inspection range in blocks.", impact = "Higher values allow inspection from farther away.")
    double rangeBase = 6;
    @ConfigDoc(value = "Additional inspection range across the adaptation's level range.", impact = "Higher values increase the range earned from levels.")
    double rangeFactor = 18;
    @ConfigDoc(value = "Restricts Gloss entity overlays to targets inspected by players with active Discovery Insight.", impact = "False keeps Gloss nearby overlays for everyone and adds Insight details to the inspected target. True hides nearby overlays unless Insight selects that target for its viewer.")
    boolean restrictGlossToInsight = false;
    @ConfigDoc(value = "Discovery XP granted for a new target inspection.", impact = "Higher values increase inspection XP rewards.")
    double xpPerInspection = 3;
    @ConfigDoc(value = "Cooldown between inspection XP grants in milliseconds.", impact = "Higher values slow inspection XP gain.")
    long xpCooldownMs = 10000;
    @ConfigDoc(value = "Maximum viewers examined per scheduler tick, capped internally at 32.", impact = "Higher values refresh Insight faster on large servers but increase player and target owned work.")
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

  void remove(UUID playerId) {
    current.remove(playerId);
  }

  void clear() {
    sequence.incrementAndGet();
    current.clear();
  }
}

final class InsightWorkLimits {
  private static final int MAX_VIEWER_UPDATES_PER_TICK = 32;
  private static final int MAX_PRIORITY_VIEWER_UPDATES_PER_TICK = 8;

  private InsightWorkLimits() {
  }

  static int viewerUpdates(int configured) {
    return Math.min(MAX_VIEWER_UPDATES_PER_TICK, Math.max(1, configured));
  }

  static int priorityViewerUpdates(int totalLimit) {
    return Math.min(MAX_PRIORITY_VIEWER_UPDATES_PER_TICK, Math.max(0, totalLimit));
  }
}

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

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.AdaptationConfig;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.api.content.RunsWithoutLearnedAdaptation;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.insight.InsightFeed;
import art.arcane.adapt.api.integration.GlossOverlay;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.api.insight.InsightMark;
import art.arcane.adapt.api.insight.InsightView;
import art.arcane.adapt.platform.advancement.AdvancementFrame;
import art.arcane.adapt.platform.advancement.AdvancementVisibility;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.event.server.PluginStateEvent;
import art.arcane.adapt.platform.event.server.ServiceChangeEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;


public class DiscoveryInsight extends SimpleAdaptation<DiscoveryInsight.Config> {
  public static final Id ID = Id.adapt("discovery/insight");
  public static final PlayerPreference<CommonPreferences.Toggle> FULL = CommonPreferences.toggle("full-details", Text.key("adapt.discovery.preferences.discoveryinsight.full-details"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> PLAYERS = CommonPreferences.toggle("players", Text.key("adapt.discovery.preferences.discoveryinsight.players"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> HOSTILE = CommonPreferences.toggle("hostile", Text.key("adapt.discovery.preferences.discoveryinsight.hostile"), CommonPreferences.Toggle.ON);
  public static final PlayerPreference<CommonPreferences.Toggle> PASSIVE = CommonPreferences.toggle("passive", Text.key("adapt.discovery.preferences.discoveryinsight.passive"), CommonPreferences.Toggle.ON);

  private static final int HARD_MAX_HUD_CLEANUPS_PER_TICK = 4;
  private static final long HUD_UPDATE_INTERVAL_MILLIS = 250L;
  private static final long HUD_CLEANUP_INTERVAL_MILLIS = 1000L;
  private static final long INSIGHT_DURATION_MILLIS = 2000L;
  private static final long ACTION_BAR_REFRESH_MILLIS = 1000L;
  private static final String ACTION_BAR_PURPOSE = "insight";
  private static final int MAX_MARKS = 6;
  private static final double MAX_MARK_RANGE = 16D;

  private final Map<UUID, InsightHud> huds = new ConcurrentHashMap<>();
  private final Cooldowns xpCooldowns = cooldowns();
  private final Cooldowns hudUpdateThrottle = cooldowns();
  private final InsightRequestGate requestGate = new InsightRequestGate();
  private final EntityInsight.Display display = new Readout();
  private final InsightFeed frames = InsightFeed.create();
  private final ConcurrentLinkedQueue<MovedViewer> movedViewers = new ConcurrentLinkedQueue<>();
  private final Set<UUID> queuedMovedViewers = ConcurrentHashMap.newKeySet();
  private final Set<UUID> pendingViewerUpdates = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean lifecycleCleanupStarted = new AtomicBoolean();
  private Iterator<UUID> hudCleanupCursor = Collections.emptyIterator();
  private int playerCursor;
  private long nextHudCleanupAt;
  private volatile boolean runtimeActive;

  public DiscoveryInsight() {
    super(ID);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("spyglass"));
    setInterval(50);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("spyglass"))
        .key("challenge_discovery_insight_100")
        .frame(AdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("writable_book"))
            .key("challenge_discovery_insight_1000")
            .frame(AdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_discovery_insight_100", "discovery.insight.entities-inspected", 100, 300);
    registerMilestone("challenge_discovery_insight_1000", "discovery.insight.entities-inspected", 1000, 1200);
  }

  @Override
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    clearHud(player.player().uuid());
  }


  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, FULL, PLAYERS, HOSTILE, PASSIVE);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getRange(level), 0), 1);
  }

  @Handle(priority = EventPriority.MONITOR, ignoreCancelled = true)
  @RunsWithoutLearnedAdaptation
  public void on(PlayerMoveEvent event) {
    Loc to = event.to();
    if (to == null || !viewChanged(event.from(), to)) {
      return;
    }

    PlayerRef player = event.player();
    UUID playerId = player.uuid();
    if (!huds.containsKey(playerId) && ownerLevel(playerId) <= 0) {
      return;
    }
    if (queuedMovedViewers.add(playerId)) {
      movedViewers.offer(new MovedViewer(playerId, player));
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  @RunsWithoutLearnedAdaptation
  public void on(PlayerQuitEvent event) {
    UUID playerId = event.player().uuid();
    requestGate.remove(playerId);
    queuedMovedViewers.remove(playerId);
    pendingViewerUpdates.remove(playerId);
    clearHud(playerId);
    frames.forget(playerId);
  }

  @Handle(priority = EventPriority.MONITOR)
  @RunsWithoutLearnedAdaptation
  public void on(PluginStateEvent event) {
    if (event.enabled() && event.plugin().equals("Gloss")) {
      GlossOverlay.refresh();
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  @RunsWithoutLearnedAdaptation
  public void on(ServiceChangeEvent event) {
    if (event.registered() && event.service().getName().equals(GlossOverlay.SERVICE_CLASS)) {
      GlossOverlay.refresh();
    }
  }

  @Override
  public void unregister() {
    if (lifecycleCleanupStarted.compareAndSet(false, true)) {
      runtimeActive = false;
      EntityInsight.detach(display);
      requestGate.clear();
      movedViewers.clear();
      queuedMovedViewers.clear();
      pendingViewerUpdates.clear();
      for (UUID playerId : huds.keySet()) {
        clearHud(playerId);
      }
      frames.forgetAll();
      GlossOverlay.restrict(false);
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
    GlossOverlay.restrict(config.enabled && config.restrictGlossToInsight);
    EntityInsight.attach(display);
  }

  @Override
  protected void onConfigReload(Config previousConfig, Config newConfig) {
    super.onConfigReload(previousConfig, newConfig);
    if (runtimeActive) {
      GlossOverlay.restrict(newConfig.enabled && newConfig.restrictGlossToInsight);
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
    List<PlayerProfile> candidates = learnedCandidates(now);
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
      PlayerProfile adaptPlayer = candidates.get((start + index) % size);
      PlayerRef player = adaptPlayer.player();
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

  private void queueHudUpdate(PlayerRef player) {
    UUID playerId = player.uuid();
    if (!hudUpdateThrottle.isReady(playerId, HUD_UPDATE_INTERVAL_MILLIS)
        || !pendingViewerUpdates.add(playerId)) {
      return;
    }

    hudUpdateThrottle.mark(playerId);
    if (!AdaptApi.scheduler().runEntity(player, () -> {
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
      PlayerRef owner = hud.owner();
      if (!AdaptApi.scheduler().runEntity(owner, () -> {
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

  private void updateHudOwned(PlayerRef player) {
    UUID playerId = player.uuid();
    long token = requestGate.advance(playerId);
    int level = getActiveLevel(player);
    if (level <= 0) {
      clearHudIfCurrent(playerId, token);
      return;
    }

    Loc eye = player.eyeLocation();
    LivingRef target = InsightLens.lookTarget(player, eye, getRange(level));
    if (target == null || target instanceof PlayerRef other && !AdaptApi.platform().viewers().canSee(player, other)
        || target != null && !preferenceEnabled(player, target instanceof PlayerRef ? PLAYERS
            : target.has(EntityTrait.ENEMY) ? HOSTILE : PASSIVE)) {
      clearHudIfCurrent(playerId, token);
      return;
    }

    InsightTier tier = InsightTier.of(getLevelPercent(level), preferenceEnabled(player, FULL));
    List<InsightMark> marks = tier.marks() && frames.capable(playerId)
        ? nearbyMarks(player, target, Math.min(getRange(level), MAX_MARK_RANGE)) : List.of();
    if (!AdaptApi.scheduler().runEntity(target, () -> inspectTargetOwned(playerId, player, target, tier, marks, token))) {
      clearHudIfCurrent(playerId, token);
    }
  }

  private List<InsightMark> nearbyMarks(PlayerRef player, LivingRef focus, double range) {
    UUID playerId = player.uuid();
    UUID focusId = focus.uuid();
    Vec3 eye = player.eyeLocation().position();
    List<LivingRef> nearby = new ArrayList<>();
    for (EntityRef entity : player.nearbyEntities(range, range, range)) {
      if (entity instanceof LivingRef living && !living.uuid().equals(playerId) && !living.uuid().equals(focusId)
          && !living.has(EntityTrait.ARMOR_STAND) && living.isValid() && !living.isDead() && !living.isInvisible()
          && eye.distanceSquared(living.location().position()) <= range * range && player.hasLineOfSight(living)
          && preferenceEnabled(player, living instanceof PlayerRef ? PLAYERS : living.has(EntityTrait.ENEMY) ? HOSTILE : PASSIVE)) {
        nearby.add(living);
      }
    }
    nearby.sort(Comparator.comparingDouble((LivingRef living) -> eye.distanceSquared(living.location().position())));
    List<InsightMark> marks = new ArrayList<>(Math.min(MAX_MARKS, nearby.size()));
    for (LivingRef living : nearby.subList(0, Math.min(MAX_MARKS, nearby.size()))) {
      marks.add(InsightReadout.mark(living));
    }
    return marks;
  }

  static boolean isOccluded(Vec3 origin, Vec3 entityHit, Vec3 blockHit) {
    if (origin == null || entityHit == null || blockHit == null) {
      return false;
    }
    return origin.distanceSquared(blockHit) + 1.0E-8D < origin.distanceSquared(entityHit);
  }

  private void inspectTargetOwned(UUID playerId, PlayerRef owner, LivingRef target, InsightTier tier,
                                  List<InsightMark> marks, long token) {
    if (lifecycleCleanupStarted.get() || !requestGate.isCurrent(playerId, token)) {
      return;
    }
    if (!target.isValid() || target.isDead() || target.isInvisible()) {
      AdaptApi.scheduler().runEntity(owner, () -> clearHudIfCurrent(playerId, token));
      return;
    }

    UUID targetId = target.uuid();
    List<String> provided = EntityInsight.details(owner, target);
    InsightStats stats = InsightLens.stats(target);
    List<String> details = buildInsightDetails(stats);
    InsightView view = InsightLens.view(target, playerId, tier, stats);
    Inspection inspection = new Inspection(target, targetId, provided, details, view, marks, stats.species());
    if (!AdaptApi.scheduler().runEntity(owner, () -> acceptInspectionOwned(owner, inspection, token))) {
      clearHudIfCurrent(playerId, token);
    }
  }

  private void acceptInspectionOwned(PlayerRef owner, Inspection inspection, long token) {
    UUID playerId = owner.uuid();
    if (lifecycleCleanupStarted.get() || !requestGate.isCurrent(playerId, token)) {
      return;
    }
    if (!owner.isOnline() || getActiveLevel(owner) <= 0) {
      clearHudIfCurrent(playerId, token);
      return;
    }
    UUID targetId = inspection.targetId();
    InsightHud current = huds.get(playerId);
    InsightRoute route = frames.capable(playerId) ? InsightRoute.CLIENT
        : GlossOverlay.available() ? InsightRoute.GLOSS : InsightRoute.ACTION_BAR;
    if (current != null && current.route() != route) {
      clearHud(playerId);
      current = null;
    }
    long now = System.currentTimeMillis();
    String barText = "";
    long barSentAt = 0L;
    boolean shown = switch (route) {
      case CLIENT -> frames.show(playerId, inspection.view(), inspection.marks());
      case GLOSS -> GlossOverlay.show(owner, inspection.target(),
          readout(inspection.provided(), inspection.details(), preferenceEnabled(owner, FULL)), INSIGHT_DURATION_MILLIS);
      case ACTION_BAR -> {
        barText = InsightActionBar.text(inspection.view(), inspection.species());
        barSentAt = current != null && barText.equals(current.barText())
            && now - current.barSentAt() < ACTION_BAR_REFRESH_MILLIS ? current.barSentAt() : now;
        if (barSentAt == now) {
          HudStatus.ambient(owner, ACTION_BAR_PURPOSE, barText);
        }
        yield true;
      }
    };
    if (!shown) {
      clearHudIfCurrent(playerId, token);
      return;
    }

    InsightHud previous = huds.put(playerId, new InsightHud(owner, targetId, route, barText, barSentAt));
    if ((previous == null || !previous.targetId().equals(targetId))
        && xpCooldowns.isReady(playerId, getConfig().xpCooldownMs)) {
      xpCooldowns.mark(playerId);
      xp(owner, getConfig().xpPerInspection);
      addStat(owner, "discovery.insight.entities-inspected", 1);
    }
  }

  private boolean viewChanged(Loc from, Loc to) {
    return Float.compare(from.yaw(), to.yaw()) != 0
        || Float.compare(from.pitch(), to.pitch()) != 0
        || !from.world().equals(to.world())
        || Double.compare(from.x(), to.x()) != 0
        || Double.compare(from.y(), to.y()) != 0
        || Double.compare(from.z(), to.z()) != 0;
  }

  static List<String> buildInsightDetails(InsightStats stats) {
    List<String> details = new ArrayList<>(4);
    details.add(TextFormat.AQUA + stats.species());
    StringBuilder movement = new StringBuilder();
    appendStat(movement, Text.key("adapt.discovery.insight.speed"), stats.movementSpeed(), 2);
    appendStat(movement, Text.key("adapt.discovery.insight.jump"), stats.jumpStrength(), 2);
    if (!movement.isEmpty()) {
      details.add(movement.toString());
    }
    StringBuilder defenses = new StringBuilder();
    appendStat(defenses, Text.key("adapt.kinetics.quake_guard.lore2"), stats.armorToughness(), 1);
    appendStat(defenses, Text.key("adapt.kinetics.quake_guard.lore1"), stats.knockbackResistance(), 2);
    appendStat(defenses, Text.key("adapt.discovery.insight.lore1"), stats.followRange(), 1);
    if (!defenses.isEmpty()) {
      details.add(defenses.toString());
    }
    if (stats.stableHandEnhanced()) {
      details.add(TextFormat.AQUA + Text.key("adapt.discovery.insight.stable_hand").resolve());
    }
    return List.copyOf(details);
  }

  static List<String> readout(List<String> provided, List<String> details, boolean full) {
    List<String> shown = full ? details : details.subList(0, Math.min(1, details.size()));
    if (provided.isEmpty()) {
      return shown;
    }
    List<String> lines = new ArrayList<>(provided.size() + shown.size());
    lines.addAll(provided);
    lines.addAll(shown);
    return lines;
  }

  private static void appendStat(StringBuilder text, Text label, double value, int decimals) {
    if (!Double.isFinite(value)) {
      return;
    }
    if (!text.isEmpty()) {
      text.append(TextFormat.DARK_GRAY).append(" | ");
    }
    text.append(TextFormat.GRAY).append(Text.key("adapt.discovery.insight.stat").withMarkup("label", label.resolve()).withMarkup("value", TextFormat.WHITE + Format.f(value, decimals)).resolve());
  }

  private double getRange(int level) {
    return getConfig().rangeBase + (getLevelPercent(level) * getConfig().rangeFactor);
  }

  private static double finiteNonnegative(double value, double fallback) {
    return Double.isFinite(value) ? Math.max(0D, value) : fallback;
  }

  private void clearHud(UUID playerId) {
    InsightHud hud = huds.remove(playerId);
    if (hud == null) {
      return;
    }
    switch (hud.route()) {
      case CLIENT -> frames.clear(playerId);
      case GLOSS -> GlossOverlay.clear(playerId);
      case ACTION_BAR -> AdaptApi.scheduler().runEntity(hud.owner(), () -> HudStatus.clearAmbient(hud.owner(), ACTION_BAR_PURPOSE));
    }
  }

  private void clearHudIfCurrent(UUID playerId, long token) {
    if (requestGate.isCurrent(playerId, token)) {
      clearHud(playerId);
    }
  }

  record InsightStats(String species, double movementSpeed, double jumpStrength, double armorToughness,
                      double knockbackResistance, double followRange, boolean stableHandEnhanced) {
  }

  private record InsightHud(PlayerRef owner, UUID targetId, InsightRoute route, String barText, long barSentAt) {
  }

  private enum InsightRoute {
    CLIENT,
    GLOSS,
    ACTION_BAR
  }

  private record Inspection(LivingRef target, UUID targetId, List<String> provided, List<String> details,
                            InsightView view, List<InsightMark> marks, String species) {
  }

  private final class Readout implements EntityInsight.Display {
    @Override
    public boolean showing(UUID viewerId) {
      return huds.containsKey(viewerId);
    }
  }

  private record MovedViewer(UUID playerId, PlayerRef player) {
  }

  @ConfigDescription("Inspect creatures and reveal their live attributes, through the Adapt client, Gloss entity overlays or the action bar.")
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
      baseCost = 1;
      costFactor = 0.5;
      maxLevel = 5;
      initialCost = 1;
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

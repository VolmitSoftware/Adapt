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

package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.localization.catalog.TamingMessages;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.WorldRef;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TamingBeastRecall extends SimpleAdaptation<TamingBeastRecall.Config> {
  private static final int HARD_MAX_CANDIDATES_PER_ACTIVATION = 32;
  private static final int HARD_MAX_AFFECTED_PER_ACTIVATION = 1;
  private static final Id LEAD = Id.minecraft("lead");
  private final Map<UUID, Long> pendingRecalls = playerState();

  public TamingBeastRecall() {
    super("tame-beast-recall");
    registerConfiguration(Config.class);
    setLocalizationKey("taming.beast_recall");
    setIcon(Id.minecraft("lead"));
    setInterval(2200);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("lead"))
        .key("challenge_taming_recall_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("ender_pearl"))
            .key("challenge_taming_recall_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_taming_recall_100", "taming.beast-recall.recalls", 100, 300);
    registerMilestone("challenge_taming_recall_1k", "taming.beast-recall.recalls", 1000, 1000);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.PETS, TamingPreferences.SITTING, TamingPreferences.HUNGER);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getSearchRadius(level)), 1);
    statLore(v, C.YELLOW, "* ", Form.duration(getCooldownTicks(level) * 50D, 1), 2);
    if (getConfig().hungerCost > 0) {
      statLore(v, C.RED, "* ", getConfig().hungerCost, TamingMessages.BEAST_RECALL_LORE_COST_HUNGER);
    }
  }

  @ReceiveCancelledEvents
  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (e.hand() != Hand.MAIN_HAND) {
      return;
    }

    InteractAction action = e.action();
    if (action != InteractAction.RIGHT_CLICK_AIR && action != InteractAction.RIGHT_CLICK_BLOCK) {
      return;
    }

    if (e.clickedBlock() != null
        ? e.useBlock() == UseResult.DENY
        : e.useItem() == UseResult.DENY) {
      return;
    }

    PlayerRef p = e.player();
    int level = getActiveLevel(p, PlayerRef::isSneaking);
    if (level <= 0 || !LEAD.equals(p.inventory().mainHand().type()) || p.hasCooldown(LEAD)) {
      return;
    }

    int hungerCost = Math.max(0, getConfig().hungerCost);
    if (!preference(p, TamingPreferences.HUNGER).permits(p.foodLevel(), hungerCost)) {
      return;
    }

    if (getAffectedLimit() <= 0) {
      return;
    }

    double radius = getSearchRadius(level);
    Loc origin = p.location();
    List<LivingRef> candidates = collectCandidates(origin, radius);
    if (candidates.isEmpty()) {
      return;
    }

    UUID playerId = p.uuid();
    long token = System.nanoTime();
    if (pendingRecalls.putIfAbsent(playerId, token) != null) {
      return;
    }
    e.setCancelled(true);

    RecallScan scan = new RecallScan(p, origin, radius * radius, getConfig().minRecallDistanceSquared,
        level, hungerCost, getConfig().xpOnRecall, token, candidates.size());
    for (LivingRef tameable : candidates) {
      if (!Tasks.runEntity(tameable, () -> inspectCandidateOwned(scan, tameable))) {
        scan.complete();
      }
    }
    Tasks.runEntity(p, () -> clearPending(scan), 20);
  }

  private List<LivingRef> collectCandidates(Loc origin, double radius) {
    int limit = getCandidateLimit();
    List<LivingRef> candidates = new ArrayList<>(limit);
    if (Tasks.isFoliaThreading() && !Tasks.isOwnedByCurrentRegion(origin, radius, radius)) {
      return candidates;
    }
    for (EntityRef entity : origin.world().entitiesIn(Box.around(origin.position(), radius, radius, radius),
        candidate -> candidate instanceof LivingRef && candidate.has(EntityTrait.TAMEABLE))) {
      candidates.add((LivingRef) entity);
      if (candidates.size() >= limit) {
        break;
      }
    }
    return candidates;
  }

  private void inspectCandidateOwned(RecallScan scan, LivingRef tameable) {
    if (!tameable.isValid() || tameable.isDead() || !AdaptCore.platform().host().animals().isTamed(tameable)
        || !scan.petTypes.accepts(tameable.type())
        || (!scan.includeSitting && AdaptCore.platform().host().animals().isSitting(tameable))) {
      scan.complete();
      return;
    }

    UUID owner = tameable.tameOwner();
    if (owner == null || !scan.playerId.equals(owner)) {
      scan.complete();
      return;
    }

    Loc location = tameable.location();
    if (!location.sameWorld(scan.origin)) {
      scan.complete();
      return;
    }

    double distanceSquared = location.distanceSquared(scan.origin);
    if (distanceSquared > scan.minDistanceSquared && distanceSquared <= scan.radiusSquared) {
      scan.consider(tameable, distanceSquared);
    }
    scan.complete();
  }

  private void finishScanOwned(RecallScan scan) {
    if (!isPending(scan)
        || !scan.player.isOnline()
        || getActiveLevel(scan.player) <= 0
        || scan.player.hasCooldown(LEAD)) {
      clearPending(scan);
      return;
    }

    RecallCandidate nearest = scan.nearest.get();
    Loc safe = findSafeRecallLocation(scan.player);
    if (nearest == null || safe == null || !canPVE(scan.player, safe)) {
      clearPending(scan);
      return;
    }

    if (!Tasks.runEntity(nearest.tameable(), () -> recallOwned(scan, nearest.tameable(), safe))) {
      clearPending(scan);
    }
  }

  private void recallOwned(RecallScan scan, LivingRef tameable, Loc safe) {
    if (!isPending(scan) || !tameable.isValid() || tameable.isDead()
        || !AdaptCore.platform().host().animals().isTamed(tameable)) {
      clearPending(scan);
      return;
    }

    UUID owner = tameable.tameOwner();
    if (owner == null || !scan.playerId.equals(owner)) {
      clearPending(scan);
      return;
    }

    Loc from = tameable.location();
    if (from.sameWorld(safe) && from.distanceSquared(safe) <= scan.minDistanceSquared) {
      clearPending(scan);
      return;
    }

    beginRecallTeleport(scan, tameable, from, safe);
  }

  private void beginRecallTeleport(RecallScan scan, LivingRef tameable, Loc from, Loc safe) {
    CompletableFuture<Boolean> teleport;
    try {
      teleport = tameable.teleport(safe, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      AdaptLog.error("Beast Recall could not start teleport for " + tameable.uuid() + ".");
      AdaptLog.error(error);
      clearPending(scan);
      return;
    }
    if (teleport == null) {
      clearPending(scan);
      return;
    }
    teleport.whenComplete((success, failure) ->
        completeRecallTeleport(scan, tameable, from, safe, success, failure));
  }

  private void completeRecallTeleport(RecallScan scan, LivingRef tameable, Loc from, Loc safe,
                                      Boolean success, Throwable failure) {
    if (failure != null) {
      AdaptLog.error("Beast Recall teleport failed for " + tameable.uuid() + ".");
      AdaptLog.error(failure);
    }
    if (!successfulRecallTeleport(success, failure)) {
      clearPending(scan);
      return;
    }
    if (!Tasks.runEntity(tameable, () -> finishRecallTeleportOwned(scan, tameable, from, safe))) {
      clearPending(scan);
      AdaptLog.warn("Beast Recall completed teleport but could not schedule completion for "
          + tameable.uuid() + ".");
    }
  }

  private void finishRecallTeleportOwned(RecallScan scan, LivingRef tameable, Loc from, Loc safe) {
    if (!isPending(scan) || !tameable.isValid() || tameable.isDead()) {
      clearPending(scan);
      return;
    }

    tameable.setFallDistance(0);
    if (!Tasks.runEntity(scan.player, () -> completeRecallOwned(scan, from, safe))) {
      clearPending(scan);
    }
  }

  static boolean successfulRecallTeleport(Boolean success, Throwable failure) {
    return failure == null && Boolean.TRUE.equals(success);
  }

  private void completeRecallOwned(RecallScan scan, Loc from, Loc safe) {
    if (!isPending(scan)) {
      return;
    }
    clearPending(scan);
    if (!scan.player.isOnline()) {
      return;
    }

    int cooldownTicks = getCooldownTicks(scan.level);
    scan.player.setCooldown(LEAD, cooldownTicks);
    AbilityGlyphs.cooldown(scan.player, this, cooldownTicks * 50L);
    if (scan.hungerCost > 0) {
      scan.player.setFoodLevel(Math.max(0, scan.player.foodLevel() - scan.hungerCost));
    }

    fx(from, FxPriority.TRANSITION)
        .burst(Particles.REVERSE_PORTAL, 12, 0.3D)
        .particle(Particles.POOF, 1, 0, 0.2D, 0, 0.1D, 0.02D)
        .chord(Sounds.ENTITY_ENDERMAN_TELEPORT, 0.6F, 1.45F, Sounds.ITEM_TRIDENT_RETURN, 0.4F, 1.6F);
    fx(safe, FxPriority.TRANSITION)
        .dome(Particles.PORTAL, 0.6D, 8)
        .particle(Particles.VILLAGER_HAPPY, 4, 0, 0.5D, 0, 0.4D, 0)
        .chord(Sounds.ITEM_LEAD_BREAK, 0.6F, 1.2F, Sounds.ENTITY_WOLF_PANT, 0.6F, 1.3F);
    xp(scan.player, scan.xpOnRecall);
    addStat(scan.player, "taming.beast-recall.recalls", 1);
  }

  private void clearPending(RecallScan scan) {
    pendingRecalls.remove(scan.playerId, scan.token);
  }

  private boolean isPending(RecallScan scan) {
    Long current = pendingRecalls.get(scan.playerId);
    return current != null && current == scan.token;
  }

  private Loc findSafeRecallLocation(PlayerRef p) {
    Loc base = p.location();
    int[][] offsets = {
        {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
        {2, 0}, {-2, 0}, {0, 2}, {0, -2}
    };

    for (int y = 0; y <= 2; y++) {
      for (int[] offset : offsets) {
        Loc candidate = base.add(offset[0], y, offset[1]);
        if (isSafeSpot(candidate)) {
          return candidate;
        }
      }
    }

    return null;
  }

  private boolean isSafeSpot(Loc location) {
    WorldRef world = location.world();
    BlockRef feet = world.blockAt(location);
    BlockRef head = world.blockAt(location.add(0, 1, 0));
    BlockRef below = world.blockAt(location.add(0, -1, 0));
    return feet.isPassable() && head.isPassable() && below.isSolid();
  }

  private double getSearchRadius(int level) {
    return getConfig().radiusBase + (getLevelPercent(level) * getConfig().radiusFactor);
  }

  private int getCooldownTicks(int level) {
    return Math.max(40, (int) Math.round(getConfig().cooldownTicksBase - (getLevelPercent(level) * getConfig().cooldownTicksFactor)));
  }

  private int getCandidateLimit() {
    return Math.max(1, Math.min(getConfig().maxCandidatesPerActivation, HARD_MAX_CANDIDATES_PER_ACTIVATION));
  }

  private int getAffectedLimit() {
    return Math.max(0, Math.min(getConfig().maxAffectedPerActivation, HARD_MAX_AFFECTED_PER_ACTIVATION));
  }


  @ConfigDescription("Sneak-right-click with a lead to recall your nearest tamed companion.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Base for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusBase = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Radius Factor for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double radiusFactor = 38;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Min Recall Distance Squared for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double minRecallDistanceSquared = 9.0;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Base for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksBase = 420;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Cooldown Ticks Factor for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double cooldownTicksFactor = 280;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Controls Xp On Recall for the Taming Beast Recall adaptation.", impact = "Higher values usually increase intensity, limits, or frequency; lower values reduce it.")
    double xpOnRecall = 26;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Food points consumed per beast recall.", impact = "Higher values make each recall cost more hunger; 0 disables the cost.")
    int hungerCost = 2;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum nearby tameable candidates inspected by one recall.", impact = "Lower values reduce dense-pet scheduling; values are clamped to an absolute maximum of 32.")
    int maxCandidatesPerActivation = 16;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum pets recalled by one activation.", impact = "This is hard-clamped to 1; set to 0 to disable recall effects without disabling the adaptation.")
    int maxAffectedPerActivation = 1;

    public Config() {
      baseCost = 2;
      costFactor = 0.5;
      initialCost = 1;
    }
  }

  private final class RecallScan {
    private final TamingPreferences.Pets petTypes;
    private final boolean includeSitting;
    private final PlayerRef player;
    private final UUID playerId;
    private final Loc origin;
    private final double radiusSquared;
    private final double minDistanceSquared;
    private final int level;
    private final int hungerCost;
    private final double xpOnRecall;
    private final long token;
    private final AtomicInteger remaining;
    private final AtomicReference<RecallCandidate> nearest = new AtomicReference<>();
    private final AtomicBoolean finalized = new AtomicBoolean();

    private RecallScan(PlayerRef player, Loc origin, double radiusSquared, double minDistanceSquared,
                       int level, int hungerCost, double xpOnRecall, long token, int candidates) {
      this.player = player;
      petTypes = preference(player, TamingPreferences.PETS);
      includeSitting = preferenceEnabled(player, TamingPreferences.SITTING);
      playerId = player.uuid();
      this.origin = origin;
      this.radiusSquared = radiusSquared;
      this.minDistanceSquared = minDistanceSquared;
      this.level = level;
      this.hungerCost = hungerCost;
      this.xpOnRecall = xpOnRecall;
      this.token = token;
      remaining = new AtomicInteger(candidates);
    }

    private void consider(LivingRef tameable, double distanceSquared) {
      RecallCandidate observed = nearest.get();
      while (observed == null || distanceSquared < observed.distanceSquared()) {
        RecallCandidate replacement = new RecallCandidate(tameable, distanceSquared);
        if (nearest.compareAndSet(observed, replacement)) {
          return;
        }
        observed = nearest.get();
      }
    }

    private void complete() {
      if (remaining.decrementAndGet() != 0 || !finalized.compareAndSet(false, true)) {
        return;
      }
      if (!Tasks.runEntity(player, () -> finishScanOwned(this))) {
        clearPending(this);
      }
    }
  }

  private record RecallCandidate(LivingRef tameable, double distanceSquared) {
  }
}

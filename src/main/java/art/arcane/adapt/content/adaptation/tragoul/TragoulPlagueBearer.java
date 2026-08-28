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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;

import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.J;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.reflect.registries.Particles;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class TragoulPlagueBearer extends SimpleAdaptation<TragoulPlagueBearer.Config> {
  private static final NamespacedKey PLAGUE_OWNER_KEY = NamespacedKey.fromString("adapt:tragoul_plague_owner");
  private static final NamespacedKey PLAGUE_GENERATION_KEY = NamespacedKey.fromString("adapt:tragoul_plague_generation");
  private static final NamespacedKey PLAGUE_STAMP_KEY = NamespacedKey.fromString("adapt:tragoul_plague_stamp");
  private static final Color PLAGUE_POISON = Color.fromRGB(90, 130, 30);
  private static final Color PLAGUE_WITHER = Color.fromRGB(30, 30, 30);
  private static final double HARD_MAX_SPREAD_RADIUS = 24D;
  private static final int HARD_MAX_GENERATIONS = 4;
  private static final int HARD_MAX_SPREAD_TARGETS = 8;
  private static final int MAX_CANDIDATES_PER_SPREAD = 24;
  private static final int MAX_CANDIDATE_HANDOFFS = 12;
  private static final int MAX_MARKS_PER_WINDOW = 64;
  private static final int MAX_SPREADS_PER_WINDOW = 32;
  private static final int MAX_PENDING_MARKS = 1024;
  private static final int MAX_PENDING_SPREADS = 256;
  private static final int HARD_MAX_DURATION_TICKS = 600;
  private static final long HARD_MAX_FRESHNESS_MILLIS = 60_000L;
  private static final long WORK_WINDOW_MILLIS = 50L;
  private final PlagueWorkBudget workBudget = new PlagueWorkBudget(
      MAX_MARKS_PER_WINDOW,
      MAX_SPREADS_PER_WINDOW,
      WORK_WINDOW_MILLIS
  );
  private final PendingEntityGate pendingMarks = new PendingEntityGate(MAX_PENDING_MARKS);
  private final AtomicInteger pendingSpreads = new AtomicInteger();
  private volatile boolean acceptingWork = true;

  public TragoulPlagueBearer() {
    super("tragoul-plague-bearer");
    registerConfiguration(Config.class);
    setIcon(Material.POISONOUS_POTATO);
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Material.POISONOUS_POTATO)
        .key("challenge_tragoul_plague_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Material.SPIDER_EYE)
            .key("challenge_tragoul_plague_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_plague_100", "tragoul.plague-bearer.mobs-infected", 100, 400);
    registerMilestone("challenge_tragoul_plague_1k", "tragoul.plague-bearer.mobs-infected", 1000, 1500);
  }

  @Override
  public void addStats(int level, Element v) {
    v.addLore(C.GREEN + AdaptLanguage.text(TragoulMessages.PLAGUE_BEARER_LORE1));
    statLore(v, Form.f(getSpreadRadius(level), 1), 2);
    statLore(v, Form.duration(getSpreadDurationTicks(level) * 50D, 1), 3);
  }

  @Override
  public void unregister() {
    acceptingWork = false;
    pendingMarks.clear();
    super.unregister();
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.getEntity() instanceof Mob mob)) {
      return;
    }

    Player p = null;
    if (e.getDamager() instanceof Player player) {
      p = player;
    } else if (e.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
      p = shooter;
    }

    if (p == null) {
      return;
    }

    UUID mobId = mob.getUniqueId();
    if (!acceptingWork || !pendingMarks.admit(mobId)) {
      return;
    }
    if (!workBudget.tryMark(System.currentTimeMillis())) {
      pendingMarks.complete(mobId);
      return;
    }

    UUID ownerId = p.getUniqueId();
    if (!J.runEntity(mob, () -> prepareMarkTargetOwned(ownerId, mob, mobId), 2)) {
      pendingMarks.complete(mobId);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void on(EntityDeathEvent e) {
    LivingEntity victim = e.getEntity();
    PersistentDataContainer pdc = victim.getPersistentDataContainer();
    String ownerRaw = pdc.get(PLAGUE_OWNER_KEY, PersistentDataType.STRING);
    if (ownerRaw == null) {
      return;
    }

    long now = System.currentTimeMillis();
    Long stamp = pdc.get(PLAGUE_STAMP_KEY, PersistentDataType.LONG);
    if (stamp == null || now - stamp > getAfflictionFreshnessMillis()) {
      return;
    }

    PotionEffect affliction = victim.getPotionEffect(PotionEffectType.WITHER);
    if (affliction == null) {
      affliction = victim.getPotionEffect(PotionEffectType.POISON);
    }
    if (affliction == null) {
      return;
    }

    int generation = Math.max(0, pdc.getOrDefault(PLAGUE_GENERATION_KEY, PersistentDataType.INTEGER, 0));
    if (generation >= getMaxGenerations()) {
      fx(victim.getLocation().add(0, 0.8, 0), FxPriority.AMBIENT).burst(Particles.SMOKE, 2, 0.2);
      return;
    }

    UUID ownerId = parseOwnerId(ownerRaw);
    if (ownerId == null) {
      return;
    }

    Player owner = Bukkit.getPlayer(ownerId);
    if (owner == null) {
      return;
    }

    SpreadSeed seed = new SpreadSeed(
        victim.getLocation().clone(),
        ownerRaw,
        ownerId,
        generation + 1,
        affliction.getType(),
        affliction.getAmplifier(),
        now
    );
    J.runEntity(owner, () -> prepareSpreadOwnerOwned(owner, seed));
  }

  private void prepareMarkTargetOwned(UUID ownerId, Mob mob, UUID mobId) {
    if (!acceptingWork || !mob.isValid() || mob.isDead()
        || isProtectedFriendlyOwned(ownerId, mob)
        || (!mob.hasPotionEffect(PotionEffectType.POISON)
        && !mob.hasPotionEffect(PotionEffectType.WITHER))) {
      pendingMarks.complete(mobId);
      return;
    }

    Location location = mob.getLocation().clone();
    Player owner = Bukkit.getPlayer(ownerId);
    if (owner == null) {
      pendingMarks.complete(mobId);
      return;
    }
    if (!J.runEntity(owner, () -> validateMarkOwnerOwned(owner, mob, mobId, location))) {
      pendingMarks.complete(mobId);
    }
  }

  private void validateMarkOwnerOwned(Player owner, Mob mob, UUID mobId, Location location) {
    if (!acceptingWork || !owner.isOnline() || getActiveLevel(owner) <= 0 || !canPVE(owner, location.clone())) {
      pendingMarks.complete(mobId);
      return;
    }

    UUID ownerId = owner.getUniqueId();
    if (!J.runEntity(mob, () -> applyMarkTargetOwned(mob, mobId, ownerId, location))) {
      pendingMarks.complete(mobId);
    }
  }

  private void applyMarkTargetOwned(Mob mob, UUID mobId, UUID ownerId, Location approvedLocation) {
    try {
      if (!mob.isValid() || mob.isDead() || !sameBlock(mob.getLocation(), approvedLocation)) {
        return;
      }
      markIfAfflicted(mob, ownerId);
    } finally {
      pendingMarks.complete(mobId);
    }
  }

  private void prepareSpreadOwnerOwned(Player owner, SpreadSeed seed) {
    if (!acceptingWork || !owner.isOnline()) {
      return;
    }

    int level = getActiveLevel(owner);
    int maxTargets = Math.max(0, Math.min(HARD_MAX_SPREAD_TARGETS, getConfig().maxSpreadTargets));
    if (level <= 0 || maxTargets <= 0 || !workBudget.trySpread(System.currentTimeMillis()) || !acquireSpread()) {
      return;
    }

    SpreadLease lease = new SpreadLease();
    SpreadPlan plan = new SpreadPlan(
        owner,
        seed.ownerId(),
        seed.corpse(),
        getSpreadRadius(level),
        getSpreadDurationTicks(level),
        seed.effect(),
        amplifiedEffect(seed.amplifier(), getConfig().amplifierBonus),
        seed.ownerRaw(),
        seed.generation(),
        seed.stamp(),
        maxTargets,
        Math.max(0D, getConfig().xpPerInfection),
        seed.effect().equals(PotionEffectType.WITHER),
        lease
    );
    if (!J.runAt(plan.corpse(), () -> collectSpreadCandidatesRegionOwned(plan))) {
      lease.release();
    }
  }

  private void collectSpreadCandidatesRegionOwned(SpreadPlan plan) {
    Collection<Entity> nearby = plan.corpse().getWorld().getNearbyEntities(
        plan.corpse(), plan.radius(), plan.radius(), plan.radius());
    List<Mob> candidates = new ArrayList<>(MAX_CANDIDATE_HANDOFFS);
    int examined = 0;
    for (Entity entity : nearby) {
      if (examined++ >= MAX_CANDIDATES_PER_SPREAD) {
        break;
      }
      if (entity instanceof Mob mob) {
        candidates.add(mob);
        if (candidates.size() >= MAX_CANDIDATE_HANDOFFS) {
          break;
        }
      }
    }
    if (candidates.isEmpty()) {
      plan.lease().release();
      return;
    }

    SpreadCandidateBatch batch = new SpreadCandidateBatch(plan, candidates.size());
    for (Mob candidate : candidates) {
      if (!J.runEntity(candidate, () -> batch.complete(captureSpreadTargetOwned(plan, candidate)))) {
        batch.complete(null);
      }
    }
    batch.armTimeout();
  }

  private SpreadTarget captureSpreadTargetOwned(SpreadPlan plan, Mob target) {
    if (!target.isValid() || target.isDead()
        || isProtectedFriendlyOwned(plan.ownerId(), target)) {
      return null;
    }
    Location location = target.getLocation().clone();
    if (location.getWorld() != plan.corpse().getWorld()
        || location.distanceSquared(plan.corpse()) > plan.radius() * plan.radius()) {
      return null;
    }
    return new SpreadTarget(target, target.getUniqueId(), location);
  }

  private void selectSpreadTargetsOwnerOwned(SpreadCandidateBatch batch) {
    SpreadPlan plan = batch.plan();
    if (!acceptingWork || !plan.owner().isOnline() || getActiveLevel(plan.owner()) <= 0) {
      plan.lease().release();
      return;
    }

    List<SpreadTarget> selected = new ArrayList<>(plan.maxTargets());
    for (SpreadTarget target : batch.targets()) {
      if (canPVE(plan.owner(), target.location().clone())) {
        selected.add(target);
        if (selected.size() >= plan.maxTargets()) {
          break;
        }
      }
    }
    if (selected.isEmpty()) {
      plan.lease().release();
      return;
    }

    SpreadApplyBatch applyBatch = new SpreadApplyBatch(plan, selected.size());
    for (SpreadTarget target : selected) {
      if (!J.runEntity(target.entity(), () -> applySpreadTargetOwned(plan, target, applyBatch))) {
        applyBatch.complete(null);
      }
    }
    applyBatch.armTimeout();
  }

  private void applySpreadTargetOwned(SpreadPlan plan, SpreadTarget snapshot, SpreadApplyBatch batch) {
    if (batch.isDispatched()) {
      return;
    }
    Mob target = snapshot.entity();
    if (!target.isValid() || target.isDead()
        || isProtectedFriendlyOwned(plan.ownerId(), target)) {
      batch.complete(null);
      return;
    }
    Location current = target.getLocation().clone();
    if (!sameBlock(current, snapshot.location())) {
      batch.complete(null);
      return;
    }

    PersistentDataContainer pdc = target.getPersistentDataContainer();
    String existingOwner = pdc.get(PLAGUE_OWNER_KEY, PersistentDataType.STRING);
    Long existingStamp = pdc.get(PLAGUE_STAMP_KEY, PersistentDataType.LONG);
    if (plan.ownerRaw().equals(existingOwner) && existingStamp != null
        && plan.stamp() - existingStamp <= getAfflictionFreshnessMillis()) {
      batch.complete(null);
      return;
    }

    pdc.set(PLAGUE_OWNER_KEY, PersistentDataType.STRING, plan.ownerRaw());
    pdc.set(PLAGUE_GENERATION_KEY, PersistentDataType.INTEGER, plan.generation());
    pdc.set(PLAGUE_STAMP_KEY, PersistentDataType.LONG, plan.stamp());
    target.addPotionEffect(new PotionEffect(plan.effect(), plan.durationTicks(), plan.amplifier(), true, true, true), true);
    batch.complete(current);
  }

  private void rewardSpreadOwnerOwned(SpreadApplyBatch batch) {
    SpreadPlan plan = batch.plan();
    try {
      int spread = batch.hosts().size();
      if (spread <= 0 || !plan.owner().isOnline()) {
        return;
      }
      playSpread(plan.corpse(), new ArrayList<>(batch.hosts()), plan.radius(), plan.withered());
      addStat(plan.owner(), "tragoul.plague-bearer.mobs-infected", spread);
      xp(plan.owner(), spread * plan.xpPerInfection());
    } finally {
      plan.lease().release();
    }
  }

  private void playSpread(Location corpse, List<Location> hosts, double radius, boolean withered) {
    FxEmitter emitter = fx(corpse.clone().add(0, 0.8, 0), FxPriority.GAMEPLAY);
    emitter.dustBurst(withered ? PLAGUE_WITHER : PLAGUE_POISON, 8, radius * 0.35, 1.2F);
    emitter.particle(Particle.SPORE_BLOSSOM_AIR, 6, 0, 0.6, 0, radius * 0.3, 0.02);
    for (Location host : hosts) {
      emitter.line(Particle.SPORE_BLOSSOM_AIR, host.getX(), host.getY() + 1.0, host.getZ(), 4);
    }
    emitter.chord(Sound.ENTITY_ZOMBIE_INFECT, 0.6F, 1.4F, Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.4F, 1.0F);
  }

  private boolean markIfAfflicted(Mob mob, UUID ownerId) {
    if (!mob.hasPotionEffect(PotionEffectType.POISON) && !mob.hasPotionEffect(PotionEffectType.WITHER)) {
      return false;
    }

    PersistentDataContainer pdc = mob.getPersistentDataContainer();
    boolean firstMark = !pdc.has(PLAGUE_OWNER_KEY, PersistentDataType.STRING);
    pdc.set(PLAGUE_OWNER_KEY, PersistentDataType.STRING, ownerId.toString());
    pdc.set(PLAGUE_STAMP_KEY, PersistentDataType.LONG, System.currentTimeMillis());
    if (!pdc.has(PLAGUE_GENERATION_KEY, PersistentDataType.INTEGER)) {
      pdc.set(PLAGUE_GENERATION_KEY, PersistentDataType.INTEGER, 0);
    }
    if (firstMark) {
      fx(mob.getLocation().add(0, 1.0, 0), FxPriority.AMBIENT)
          .particle(Particle.SPORE_BLOSSOM_AIR, 2, 0, 0, 0, 0.2, 0.01);
    }
    return true;
  }

  private double getSpreadRadius(int level) {
    double configured = scaledRadius(getConfig().spreadRadiusStart, getConfig().spreadRadiusEnd,
        level, getMaxLevel());
    if (!Double.isFinite(configured)) {
      return 1D;
    }
    return Math.max(1D, Math.min(HARD_MAX_SPREAD_RADIUS, configured));
  }

  static double scaledRadius(double start, double end, int level, int maxLevel) {
    if (!Double.isFinite(start) || !Double.isFinite(end)) {
      return 1D;
    }
    if (maxLevel <= 1) {
      return end;
    }
    double progress = Math.max(0D, Math.min(1D, (level - 1D) / (maxLevel - 1D)));
    return start + ((end - start) * progress);
  }

  static int amplifiedEffect(int sourceAmplifier, int configuredBonus) {
    return Math.min(255, Math.max(0, sourceAmplifier) + Math.max(0, configuredBonus));
  }

  private int getSpreadDurationTicks(int level) {
    double configured = getConfig().spreadDurationTicksBase
        + (getLevelPercent(level) * getConfig().spreadDurationTicksFactor);
    if (!Double.isFinite(configured)) {
      return 40;
    }
    return Math.max(40, Math.min(HARD_MAX_DURATION_TICKS, (int) Math.round(configured)));
  }

  private int getMaxGenerations() {
    return Math.max(0, Math.min(HARD_MAX_GENERATIONS, getConfig().maxGenerations));
  }

  private long getAfflictionFreshnessMillis() {
    return Math.max(1000L, Math.min(HARD_MAX_FRESHNESS_MILLIS, getConfig().afflictionFreshnessMillis));
  }

  private UUID parseOwnerId(String raw) {
    if (raw == null || raw.length() != 36) {
      return null;
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException ignored) {
      return null;
    }
  }

  private boolean sameBlock(Location first, Location second) {
    return first.getWorld() == second.getWorld()
        && first.getBlockX() == second.getBlockX()
        && first.getBlockY() == second.getBlockY()
        && first.getBlockZ() == second.getBlockZ();
  }

  private boolean acquireSpread() {
    while (true) {
      int current = pendingSpreads.get();
      if (current >= MAX_PENDING_SPREADS) {
        return false;
      }
      if (pendingSpreads.compareAndSet(current, current + 1)) {
        return true;
      }
    }
  }


  @ConfigDescription("Mobs that die poisoned or withered by you spread an amplified affliction to nearby damageable mobs.")
  protected static class Config extends AdaptationConfig {
    @art.arcane.adapt.util.config.ConfigDoc(value = "Spread radius at adaptation level one.", impact = "Higher values infect eligible mobs further from the corpse at low levels.")
    double spreadRadiusStart = 8;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Spread radius at maximum adaptation level.", impact = "Higher values increase the maximum plague reach up to the runtime ceiling.")
    double spreadRadiusEnd = 20;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Spread effect duration in ticks before level scaling.", impact = "Higher values keep infected mobs afflicted longer.")
    double spreadDurationTicksBase = 80;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Additional spread effect duration ticks granted at max level.", impact = "Higher values increase level-scaled duration growth.")
    double spreadDurationTicksFactor = 120;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum plague generations a single affliction can chain through.", impact = "Caps chain length to prevent infinite plague cascades.")
    int maxGenerations = 3;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Maximum eligible mobs infected per death.", impact = "Caps per-death work to protect server performance.")
    int maxSpreadTargets = 6;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Potion amplifier levels added when the affliction spreads.", impact = "Higher values make propagated poison and wither effects stronger.")
    int amplifierBonus = 1;
    @art.arcane.adapt.util.config.ConfigDoc(value = "Window in milliseconds during which an affliction mark is considered fresh at death.", impact = "Higher values let older poisons still spread on death.")
    long afflictionFreshnessMillis = 15000;
    @art.arcane.adapt.util.config.ConfigDoc(value = "XP granted per mob infected by the spread.", impact = "Higher values accelerate skill progression from this adaptation.")
    double xpPerInfection = 6;

    public Config() {
      costFactor = 0.72;
      initialCost = 4;
    }
  }

  private record SpreadSeed(Location corpse, String ownerRaw, UUID ownerId,
                            int generation, PotionEffectType effect, int amplifier, long stamp) {
  }

  private record SpreadPlan(Player owner, UUID ownerId, Location corpse, double radius,
                            int durationTicks, PotionEffectType effect, int amplifier, String ownerRaw,
                            int generation, long stamp, int maxTargets, double xpPerInfection,
                            boolean withered, SpreadLease lease) {
  }

  private record SpreadTarget(Mob entity, UUID entityId, Location location) {
  }

  private final class SpreadLease {
    private final AtomicBoolean released = new AtomicBoolean();

    private void release() {
      if (released.compareAndSet(false, true)) {
        pendingSpreads.decrementAndGet();
      }
    }
  }

  private final class SpreadCandidateBatch {
    private final SpreadPlan plan;
    private final ConcurrentLinkedQueue<SpreadTarget> targets = new ConcurrentLinkedQueue<>();
    private final AtomicInteger remaining;
    private final AtomicBoolean dispatched = new AtomicBoolean();

    private SpreadCandidateBatch(SpreadPlan plan, int remaining) {
      this.plan = plan;
      this.remaining = new AtomicInteger(remaining);
    }

    private SpreadPlan plan() {
      return plan;
    }

    private ConcurrentLinkedQueue<SpreadTarget> targets() {
      return targets;
    }

    private void complete(SpreadTarget target) {
      if (target != null) {
        targets.add(target);
      }
      if (remaining.decrementAndGet() == 0) {
        dispatch();
      }
    }

    private void armTimeout() {
      if (!J.runEntity(plan.owner(), this::dispatch, 10)) {
        plan.lease().release();
      }
    }

    private void dispatch() {
      if (!dispatched.compareAndSet(false, true)) {
        return;
      }
      if (!J.runEntity(plan.owner(), () -> selectSpreadTargetsOwnerOwned(this))) {
        plan.lease().release();
      }
    }
  }

  private final class SpreadApplyBatch {
    private final SpreadPlan plan;
    private final ConcurrentLinkedQueue<Location> hosts = new ConcurrentLinkedQueue<>();
    private final AtomicInteger remaining;
    private final AtomicBoolean dispatched = new AtomicBoolean();

    private SpreadApplyBatch(SpreadPlan plan, int remaining) {
      this.plan = plan;
      this.remaining = new AtomicInteger(remaining);
    }

    private SpreadPlan plan() {
      return plan;
    }

    private ConcurrentLinkedQueue<Location> hosts() {
      return hosts;
    }

    private void complete(Location host) {
      if (host != null) {
        hosts.add(host);
      }
      if (remaining.decrementAndGet() == 0) {
        dispatch();
      }
    }

    private void armTimeout() {
      if (!J.runEntity(plan.owner(), this::dispatch, 10)) {
        plan.lease().release();
      }
    }

    private boolean isDispatched() {
      return dispatched.get();
    }

    private void dispatch() {
      if (!dispatched.compareAndSet(false, true)) {
        return;
      }
      if (!J.runEntity(plan.owner(), () -> rewardSpreadOwnerOwned(this))) {
        plan.lease().release();
      }
    }
  }

  static final class PlagueWorkBudget {
    private final int markLimit;
    private final int spreadLimit;
    private final long windowMillis;
    private long windowStartedAt = Long.MIN_VALUE;
    private int marks;
    private int spreads;

    PlagueWorkBudget(int markLimit, int spreadLimit, long windowMillis) {
      this.markLimit = Math.max(1, markLimit);
      this.spreadLimit = Math.max(1, spreadLimit);
      this.windowMillis = Math.max(1L, windowMillis);
    }

    synchronized boolean tryMark(long now) {
      refresh(now);
      if (marks >= markLimit) {
        return false;
      }
      marks++;
      return true;
    }

    synchronized boolean trySpread(long now) {
      refresh(now);
      if (spreads >= spreadLimit) {
        return false;
      }
      spreads++;
      return true;
    }

    private void refresh(long now) {
      if (windowStartedAt == Long.MIN_VALUE || now < windowStartedAt || now - windowStartedAt >= windowMillis) {
        windowStartedAt = now;
        marks = 0;
        spreads = 0;
      }
    }
  }

  static final class PendingEntityGate {
    private final int capacity;
    private final Set<UUID> pending = new HashSet<>();

    PendingEntityGate(int capacity) {
      this.capacity = Math.max(1, capacity);
    }

    synchronized boolean admit(UUID entityId) {
      if (pending.contains(entityId) || pending.size() >= capacity) {
        return false;
      }
      pending.add(entityId);
      return true;
    }

    synchronized void complete(UUID entityId) {
      pending.remove(entityId);
    }

    synchronized int size() {
      return pending.size();
    }

    synchronized void clear() {
      pending.clear();
    }
  }
}

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

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.advancement.AdaptAdvancement;
import art.arcane.adapt.api.advancement.AdaptAdvancementFrame;
import art.arcane.adapt.api.advancement.AdvancementVisibility;
import art.arcane.adapt.api.fx.FxEmitter;
import art.arcane.adapt.api.fx.FxPriority;
import art.arcane.adapt.api.fx.Particles;
import art.arcane.adapt.api.fx.Sounds;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TragoulMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

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
  private static final DataKey<String> PLAGUE_OWNER_KEY = DataKey.adapt("tragoul_plague_owner", DataCodecs.STRING);
  private static final DataKey<Integer> PLAGUE_GENERATION_KEY = DataKey.adapt("tragoul_plague_generation", DataCodecs.INT);
  private static final DataKey<Long> PLAGUE_STAMP_KEY = DataKey.adapt("tragoul_plague_stamp", DataCodecs.LONG);
  private static final Id POISON = Id.minecraft("poison");
  private static final Id WITHER = Id.minecraft("wither");
  private static final Rgb PLAGUE_POISON = new Rgb(90, 130, 30);
  private static final Rgb PLAGUE_WITHER = new Rgb(30, 30, 30);
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
    setIcon(Id.minecraft("poisonous_potato"));
    setInterval(25000);
    registerAdvancement(AdaptAdvancement.builder()
        .icon(Id.minecraft("poisonous_potato"))
        .key("challenge_tragoul_plague_100")
        .frame(AdaptAdvancementFrame.CHALLENGE)
        .visibility(AdvancementVisibility.VANILLA)
        .child(AdaptAdvancement.builder()
            .icon(Id.minecraft("spider_eye"))
            .key("challenge_tragoul_plague_1k")
            .frame(AdaptAdvancementFrame.CHALLENGE)
            .visibility(AdvancementVisibility.VANILLA)
            .build())
        .build());
    registerMilestone("challenge_tragoul_plague_100", "tragoul.plague-bearer.mobs-infected", 100, 400);
    registerMilestone("challenge_tragoul_plague_1k", "tragoul.plague-bearer.mobs-infected", 1000, 1500);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return List.of(CommonPreferences.ENABLED, TragoulPreferences.PASSIVE, TragoulPreferences.TARGETS);
  }

  @Override
  public void addStats(int level, StatLines v) {
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

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof MobRef mob)) {
      return;
    }

    PlayerRef p = null;
    if (e.damager() instanceof PlayerRef player) {
      p = player;
    } else if (e.damager() instanceof ProjectileRef projectile && projectile.shooter() instanceof PlayerRef shooter) {
      p = shooter;
    }

    if (p == null) {
      return;
    }

    UUID mobId = mob.uuid();
    if (!acceptingWork || !pendingMarks.admit(mobId)) {
      return;
    }
    if (!workBudget.tryMark(System.currentTimeMillis())) {
      pendingMarks.complete(mobId);
      return;
    }

    UUID ownerId = p.uuid();
    if (!Tasks.runEntity(mob, () -> prepareMarkTargetOwned(ownerId, mob, mobId), 2)) {
      pendingMarks.complete(mobId);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(EntityDeathEvent e) {
    LivingRef victim = e.entity();
    DataHolder pdc = victim.data();
    String ownerRaw = pdc.get(PLAGUE_OWNER_KEY);
    if (ownerRaw == null) {
      return;
    }

    long now = System.currentTimeMillis();
    Long stamp = pdc.get(PLAGUE_STAMP_KEY);
    if (stamp == null || now - stamp > getAfflictionFreshnessMillis()) {
      return;
    }

    EffectInstance affliction = victim.effect(WITHER);
    if (affliction == null) {
      affliction = victim.effect(POISON);
    }
    if (affliction == null) {
      return;
    }

    Integer storedGeneration = pdc.get(PLAGUE_GENERATION_KEY);
    int generation = Math.max(0, storedGeneration == null ? 0 : storedGeneration);
    if (generation >= getMaxGenerations()) {
      fx(victim.location().add(0, 0.8, 0), FxPriority.AMBIENT).burst(Particles.SMOKE, 2, 0.2);
      return;
    }

    UUID ownerId = parseOwnerId(ownerRaw);
    if (ownerId == null) {
      return;
    }

    PlayerRef owner = AdaptCore.platform().host().player(ownerId);
    if (owner == null) {
      return;
    }

    SpreadSeed seed = new SpreadSeed(
        victim.location(),
        ownerRaw,
        ownerId,
        generation + 1,
        affliction.type(),
        affliction.amplifier(),
        now
    );
    Tasks.runEntity(owner, () -> prepareSpreadOwnerOwned(owner, seed));
  }

  private void prepareMarkTargetOwned(UUID ownerId, MobRef mob, UUID mobId) {
    if (!acceptingWork || !mob.isValid() || mob.isDead()
        || isProtectedFriendlyOwned(ownerId, mob)
        || (!mob.hasEffect(POISON)
        && !mob.hasEffect(WITHER))) {
      pendingMarks.complete(mobId);
      return;
    }

    Loc location = mob.location();
    PlayerRef owner = AdaptCore.platform().host().player(ownerId);
    if (owner == null) {
      pendingMarks.complete(mobId);
      return;
    }
    if (!Tasks.runEntity(owner, () -> validateMarkOwnerOwned(owner, mob, mobId, location))) {
      pendingMarks.complete(mobId);
    }
  }

  private void validateMarkOwnerOwned(PlayerRef owner, MobRef mob, UUID mobId, Loc location) {
    if (!acceptingWork || !owner.isOnline() || getActiveLevel(owner) <= 0 || !canPVE(owner, location)) {
      pendingMarks.complete(mobId);
      return;
    }

    UUID ownerId = owner.uuid();
    if (!Tasks.runEntity(mob, () -> applyMarkTargetOwned(mob, mobId, ownerId, location))) {
      pendingMarks.complete(mobId);
    }
  }

  private void applyMarkTargetOwned(MobRef mob, UUID mobId, UUID ownerId, Loc approvedLocation) {
    try {
      if (!mob.isValid() || mob.isDead() || !sameBlock(mob.location(), approvedLocation)) {
        return;
      }
      markIfAfflicted(mob, ownerId);
    } finally {
      pendingMarks.complete(mobId);
    }
  }

  private void prepareSpreadOwnerOwned(PlayerRef owner, SpreadSeed seed) {
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
        seed.effect().equals(WITHER),
        lease
    );
    if (!Tasks.runAt(plan.corpse(), () -> collectSpreadCandidatesRegionOwned(plan))) {
      lease.release();
    }
  }

  private void collectSpreadCandidatesRegionOwned(SpreadPlan plan) {
    Collection<EntityRef> nearby = plan.corpse().world().nearbyEntities(
        plan.corpse().position(), plan.radius(), plan.radius(), plan.radius());
    List<MobRef> candidates = new ArrayList<>(MAX_CANDIDATE_HANDOFFS);
    int examined = 0;
    for (EntityRef entity : nearby) {
      if (examined++ >= MAX_CANDIDATES_PER_SPREAD) {
        break;
      }
      if (entity instanceof MobRef mob && AdaptationDamageTargets.allows(mob, getConfig().ignorePassiveMobs)) {
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
    for (MobRef candidate : candidates) {
      if (!Tasks.runEntity(candidate, () -> batch.complete(captureSpreadTargetOwned(plan, candidate)))) {
        batch.complete(null);
      }
    }
    batch.armTimeout();
  }

  private SpreadTarget captureSpreadTargetOwned(SpreadPlan plan, MobRef target) {
    if (!target.isValid() || target.isDead()
        || !AdaptationDamageTargets.allows(target, getConfig().ignorePassiveMobs)
        || isProtectedFriendlyOwned(plan.ownerId(), target)) {
      return null;
    }
    Loc location = target.location();
    if (!location.sameWorld(plan.corpse())
        || location.distanceSquared(plan.corpse()) > plan.radius() * plan.radius()) {
      return null;
    }
    return new SpreadTarget(target, target.uuid(), location, target.type(), !AdaptationDamageTargets.allows(target, true));
  }

  private void selectSpreadTargetsOwnerOwned(SpreadCandidateBatch batch) {
    SpreadPlan plan = batch.plan();
    if (!acceptingWork || !plan.owner().isOnline() || getActiveLevel(plan.owner()) <= 0) {
      plan.lease().release();
      return;
    }

    List<SpreadTarget> selected = new ArrayList<>(plan.maxTargets());
    for (SpreadTarget target : batch.targets()) {
      if (preference(plan.owner(), TragoulPreferences.TARGETS).accepts(target.type())
          && (!preferenceEnabled(plan.owner(), TragoulPreferences.PASSIVE) || !target.passive())
          && canPVE(plan.owner(), target.location())) {
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
      if (!Tasks.runEntity(target.entity(), () -> applySpreadTargetOwned(plan, target, applyBatch))) {
        applyBatch.complete(null);
      }
    }
    applyBatch.armTimeout();
  }

  private void applySpreadTargetOwned(SpreadPlan plan, SpreadTarget snapshot, SpreadApplyBatch batch) {
    if (batch.isDispatched()) {
      return;
    }
    MobRef target = snapshot.entity();
    if (!target.isValid() || target.isDead()
        || !AdaptationDamageTargets.allows(target, getConfig().ignorePassiveMobs)
        || isProtectedFriendlyOwned(plan.ownerId(), target)) {
      batch.complete(null);
      return;
    }
    Loc current = target.location();
    if (!sameBlock(current, snapshot.location())) {
      batch.complete(null);
      return;
    }

    DataHolder pdc = target.data();
    String existingOwner = pdc.get(PLAGUE_OWNER_KEY);
    Long existingStamp = pdc.get(PLAGUE_STAMP_KEY);
    if (plan.ownerRaw().equals(existingOwner) && existingStamp != null
        && plan.stamp() - existingStamp <= getAfflictionFreshnessMillis()) {
      batch.complete(null);
      return;
    }

    pdc.set(PLAGUE_OWNER_KEY, plan.ownerRaw());
    pdc.set(PLAGUE_GENERATION_KEY, plan.generation());
    pdc.set(PLAGUE_STAMP_KEY, plan.stamp());
    target.addEffect(new EffectInstance(plan.effect(), plan.durationTicks(), plan.amplifier(), true, true, true));
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

  private void playSpread(Loc corpse, List<Loc> hosts, double radius, boolean withered) {
    FxEmitter emitter = fx(corpse.add(0, 0.8, 0), FxPriority.GAMEPLAY);
    emitter.dustBurst(withered ? PLAGUE_WITHER : PLAGUE_POISON, 8, radius * 0.35, 1.2F);
    emitter.particle(Particles.SPORE_BLOSSOM_AIR, 6, 0, 0.6, 0, radius * 0.3, 0.02);
    for (Loc host : hosts) {
      emitter.line(Particles.SPORE_BLOSSOM_AIR, host.x(), host.y() + 1.0, host.z(), 4);
    }
    emitter.chord(Sounds.ENTITY_ZOMBIE_INFECT, 0.6F, 1.4F, Sounds.BLOCK_SCULK_CATALYST_BLOOM, 0.4F, 1.0F);
  }

  private boolean markIfAfflicted(MobRef mob, UUID ownerId) {
    if (!mob.hasEffect(POISON) && !mob.hasEffect(WITHER)) {
      return false;
    }

    DataHolder pdc = mob.data();
    boolean firstMark = !pdc.has(PLAGUE_OWNER_KEY);
    pdc.set(PLAGUE_OWNER_KEY, ownerId.toString());
    pdc.set(PLAGUE_STAMP_KEY, System.currentTimeMillis());
    if (!pdc.has(PLAGUE_GENERATION_KEY)) {
      pdc.set(PLAGUE_GENERATION_KEY, 0);
    }
    if (firstMark) {
      fx(mob.location().add(0, 1.0, 0), FxPriority.AMBIENT)
          .particle(Particles.SPORE_BLOSSOM_AIR, 2, 0, 0, 0, 0.2, 0.01);
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

  private boolean sameBlock(Loc first, Loc second) {
    return first.sameWorld(second)
        && first.blockX() == second.blockX()
        && first.blockY() == second.blockY()
        && first.blockZ() == second.blockZ();
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
    @ConfigDoc(value = "Exclude passive and neutral mobs from spreading poison and wither.", impact = "When enabled, protected mobs cannot receive secondary infections. Direct attacks and directly applied effects are unchanged.")
    boolean ignorePassiveMobs = false;
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
      baseCost = 3;
      costFactor = 0.6;
      initialCost = 2;
    }
  }

  private record SpreadSeed(Loc corpse, String ownerRaw, UUID ownerId,
                            int generation, Id effect, int amplifier, long stamp) {
  }

  private record SpreadPlan(PlayerRef owner, UUID ownerId, Loc corpse, double radius,
                            int durationTicks, Id effect, int amplifier, String ownerRaw,
                            int generation, long stamp, int maxTargets, double xpPerInfection,
                            boolean withered, SpreadLease lease) {
  }

  private record SpreadTarget(MobRef entity, UUID entityId, Loc location, Id type, boolean passive) {
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
      if (!Tasks.runEntity(plan.owner(), this::dispatch, 10)) {
        plan.lease().release();
      }
    }

    private void dispatch() {
      if (!dispatched.compareAndSet(false, true)) {
        return;
      }
      if (!Tasks.runEntity(plan.owner(), () -> selectSpreadTargetsOwnerOwned(this))) {
        plan.lease().release();
      }
    }
  }

  private final class SpreadApplyBatch {
    private final SpreadPlan plan;
    private final ConcurrentLinkedQueue<Loc> hosts = new ConcurrentLinkedQueue<>();
    private final AtomicInteger remaining;
    private final AtomicBoolean dispatched = new AtomicBoolean();

    private SpreadApplyBatch(SpreadPlan plan, int remaining) {
      this.plan = plan;
      this.remaining = new AtomicInteger(remaining);
    }

    private SpreadPlan plan() {
      return plan;
    }

    private ConcurrentLinkedQueue<Loc> hosts() {
      return hosts;
    }

    private void complete(Loc host) {
      if (host != null) {
        hosts.add(host);
      }
      if (remaining.decrementAndGet() == 0) {
        dispatch();
      }
    }

    private void armTimeout() {
      if (!Tasks.runEntity(plan.owner(), this::dispatch, 10)) {
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
      if (!Tasks.runEntity(plan.owner(), () -> rewardSpreadOwnerOwned(this))) {
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

package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataHolder;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ObjIntConsumer;

/**
 * A poison or wither plague that jumps from corpse to corpse. Mobs a player strikes while afflicted carry that
 * player's mark; when a marked mob dies with its mark still fresh, its affliction spreads, amplified, to nearby mobs,
 * for a capped number of generations. Marks and spreads are budgeted per tick window.
 */
public final class TragoulPlague {
  private static final DataKey<String> PLAGUE_OWNER_KEY = DataKey.adapt("tragoul_plague_owner", DataCodecs.STRING);
  private static final DataKey<Integer> PLAGUE_GENERATION_KEY = DataKey.adapt("tragoul_plague_generation", DataCodecs.INT);
  private static final DataKey<Long> PLAGUE_STAMP_KEY = DataKey.adapt("tragoul_plague_stamp", DataCodecs.LONG);
  private static final Id POISON = Id.minecraft("poison");
  private static final Id WITHER = Id.minecraft("wither");
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

  private final Adaptation<?> adaptation;
  private final Settings settings;
  private final ObjIntConsumer<PlayerRef> onInfected;
  private final PlagueWorkBudget workBudget = new PlagueWorkBudget(
      MAX_MARKS_PER_WINDOW,
      MAX_SPREADS_PER_WINDOW,
      WORK_WINDOW_MILLIS
  );
  private final PendingEntityGate pendingMarks = new PendingEntityGate(MAX_PENDING_MARKS);
  private final AtomicInteger pendingSpreads = new AtomicInteger();
  private volatile boolean acceptingWork = true;

  /** The owning adaptation's tuning; radius and duration are clamped by {@link #spreadRadius} and {@link #durationTicks}. */
  public interface Settings {
    boolean ignorePassiveMobs();

    int maxSpreadTargets();

    int amplifierBonus();

    int maxGenerations();

    long afflictionFreshnessMillis();

    double spreadRadius(int level);

    int spreadDurationTicks(int level);
  }

  /** {@code onInfected} runs on the owner's thread with the number of mobs one spread infected. */
  public TragoulPlague(Adaptation<?> adaptation, Settings settings, ObjIntConsumer<PlayerRef> onInfected) {
    this.adaptation = adaptation;
    this.settings = settings;
    this.onInfected = onInfected;
  }

  public void stop() {
    acceptingWork = false;
    pendingMarks.clear();
  }

  /** A configured spread radius held between 1 block and the runtime ceiling. */
  public static double spreadRadius(double configured) {
    if (!Double.isFinite(configured)) {
      return 1D;
    }
    return Math.max(1D, Math.min(HARD_MAX_SPREAD_RADIUS, configured));
  }

  /** A configured spread duration rounded to ticks and held between 2 seconds and the runtime ceiling. */
  public static int durationTicks(double configured) {
    if (!Double.isFinite(configured)) {
      return 40;
    }
    return Math.max(40, Math.min(HARD_MAX_DURATION_TICKS, (int) Math.round(configured)));
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

  /** Marks a poisoned or withered mob a player strikes as carrying that player's plague. */
  public void mark(EntityDamageByEntityEvent e) {
    if (!(e.entity() instanceof MobRef mob)) {
      return;
    }

    PlayerRef p = null;
    if (e.damager() instanceof PlayerRef player) {
      p = player;
    } else if (e.damager() instanceof ProjectileRef projectile && projectile.shooter() instanceof PlayerRef shooter) {
      p = shooter;
    }

    if (p == null || !adaptation.getServer().hasOnlineLearner(p.uuid(), adaptation.getName())) {
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

  /** Spreads the affliction of a marked mob that died while its mark was fresh. */
  public void spread(EntityDeathEvent e) {
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
      TragoulPlagueBearer.exhaustedFx(adaptation, victim);
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
        || adaptation.isProtectedFriendlyOwned(ownerId, mob)
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
    if (!acceptingWork || !owner.isOnline() || adaptation.getActiveLevel(owner) <= 0 || !adaptation.canPVE(owner, location)) {
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

    int level = adaptation.getActiveLevel(owner);
    int maxTargets = Math.max(0, Math.min(HARD_MAX_SPREAD_TARGETS, settings.maxSpreadTargets()));
    if (level <= 0 || maxTargets <= 0 || !workBudget.trySpread(System.currentTimeMillis()) || !acquireSpread()) {
      return;
    }

    SpreadLease lease = new SpreadLease();
    SpreadPlan plan = new SpreadPlan(
        owner,
        seed.ownerId(),
        seed.corpse(),
        settings.spreadRadius(level),
        settings.spreadDurationTicks(level),
        seed.effect(),
        amplifiedEffect(seed.amplifier(), settings.amplifierBonus()),
        seed.ownerRaw(),
        seed.generation(),
        seed.stamp(),
        maxTargets,
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
      if (entity instanceof MobRef mob && AdaptationDamageTargets.allows(mob, settings.ignorePassiveMobs())) {
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
        || !AdaptationDamageTargets.allows(target, settings.ignorePassiveMobs())
        || adaptation.isProtectedFriendlyOwned(plan.ownerId(), target)) {
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
    if (!acceptingWork || !plan.owner().isOnline() || adaptation.getActiveLevel(plan.owner()) <= 0) {
      plan.lease().release();
      return;
    }

    List<SpreadTarget> selected = new ArrayList<>(plan.maxTargets());
    for (SpreadTarget target : batch.targets()) {
      if (adaptation.preference(plan.owner(), TragoulPreferences.TARGETS).accepts(target.type())
          && (!adaptation.preferenceEnabled(plan.owner(), TragoulPreferences.PASSIVE) || !target.passive())
          && adaptation.canPVE(plan.owner(), target.location())) {
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
        || !AdaptationDamageTargets.allows(target, settings.ignorePassiveMobs())
        || adaptation.isProtectedFriendlyOwned(plan.ownerId(), target)) {
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
      TragoulPlagueBearer.spreadFx(adaptation, plan.corpse(), new ArrayList<>(batch.hosts()), plan.radius(), plan.withered());
      onInfected.accept(plan.owner(), spread);
    } finally {
      plan.lease().release();
    }
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
      TragoulPlagueBearer.markFx(adaptation, mob);
    }
    return true;
  }

  static int amplifiedEffect(int sourceAmplifier, int configuredBonus) {
    return Math.min(255, Math.max(0, sourceAmplifier) + Math.max(0, configuredBonus));
  }

  private int getMaxGenerations() {
    return Math.max(0, Math.min(HARD_MAX_GENERATIONS, settings.maxGenerations()));
  }

  private long getAfflictionFreshnessMillis() {
    return Math.max(1000L, Math.min(HARD_MAX_FRESHNESS_MILLIS, settings.afflictionFreshnessMillis()));
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


  private record SpreadSeed(Loc corpse, String ownerRaw, UUID ownerId,
                            int generation, Id effect, int amplifier, long stamp) {
  }

  private record SpreadPlan(PlayerRef owner, UUID ownerId, Loc corpse, double radius,
                            int durationTicks, Id effect, int amplifier, String ownerRaw,
                            int generation, long stamp, int maxTargets,
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

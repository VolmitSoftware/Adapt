package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.AdaptationDamageTargets;
import art.arcane.adapt.api.content.Cooldowns;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.EquipmentSlot;
import art.arcane.adapt.platform.entity.LastDamage;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.event.entity.EntityDeathEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.ObjDoubleConsumer;

/**
 * Seeking corpse lances. A kill launches a lance at the nearest eligible enemy around the corpse; a lance that kills
 * launches the next one at half damage, up to one hit per level. Each player runs one chain at a time, searches are
 * budgeted per tick window, and every hit is re-validated on the target's and the player's threads.
 */
public final class TragoulLances {
  private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
  private static final double HARD_MAX_RANGE = 32D;
  private static final int HARD_MAX_CHAIN_HITS = 6;
  private static final int MAX_CANDIDATES_PER_SEARCH = 24;
  private static final int MAX_CANDIDATE_HANDOFFS = 8;
  private static final int MAX_SEARCHES_PER_WINDOW = 32;
  private static final int MAX_PENDING_CHAINS = 256;
  private static final double IMPACT_MOVE_TOLERANCE_SQUARED = 6.25D;
  private static final long WORK_WINDOW_MILLIS = 50L;
  private static final long CHAIN_TIMEOUT_MILLIS = 30_000L;
  private static final long COOLDOWN_MILLIS = 5000L;

  private final Adaptation<?> adaptation;
  private final Settings settings;
  private final Cooldowns cooldowns;
  private final Landed onLanded;
  private final ObjDoubleConsumer<PlayerRef> selfDamage;
  private final LanceAdmission admission = new LanceAdmission(MAX_PENDING_CHAINS);
  private final LanceWorkBudget workBudget = new LanceWorkBudget(MAX_SEARCHES_PER_WINDOW, WORK_WINDOW_MILLIS);
  private volatile boolean acceptingChains = true;

  /** The owning adaptation's tuning, read when a chain starts. */
  public interface Settings {
    boolean ignorePassiveMobs();

    int seekerDelay();

    double seekerDamageMultiplier();

    double unarmoredDamageMultiplier();

    double selfDamage(int level);
  }

  /** Runs on the player's thread after each lance that hurt its target. */
  @FunctionalInterface
  public interface Landed {
    void accept(PlayerRef player, boolean killed);
  }

  /**
   * {@code cooldowns} spaces chains per player; {@code selfDamage} charges the player the configured cost after each
   * landed lance.
   */
  public TragoulLances(Adaptation<?> adaptation, Settings settings, Cooldowns cooldowns, Landed onLanded,
                       ObjDoubleConsumer<PlayerRef> selfDamage) {
    this.adaptation = adaptation;
    this.settings = settings;
    this.cooldowns = cooldowns;
    this.onLanded = onLanded;
    this.selfDamage = selfDamage;
  }

  public void cancel(UUID playerId) {
    admission.cancel(playerId);
  }

  public void stop() {
    acceptingChains = false;
    admission.clear();
  }

  /** Launches a lance chain from a victim a player killed; call from a LOWEST death handler. */
  public void launch(EntityDeathEvent event) {
    LivingRef victim = event.entity();
    LastDamage damage = victim.lastDamage();
    if (damage == null || !damage.byEntity()) {
      return;
    }
    PlayerRef owner = resolvePlayerDamager(damage.damager());
    if (owner == null || adaptation.ownerLevel(owner.uuid()) <= 0) {
      return;
    }

    Loc source = victim.location();
    UUID tameOwnerId = null;
    if (victim.has(EntityTrait.TAMEABLE)) {
      tameOwnerId = victim.tameOwner();
    }
    SourceSnapshot snapshot = new SourceSnapshot(
        source,
        victim instanceof PlayerRef,
        adaptation.isProtectedFriendly(null, victim),
        tameOwnerId,
        Math.max(0D, damage.finalDamage())
    );
    AdaptApi.scheduler().runEntity(owner, () -> prepareChainOwnerOwned(owner, snapshot));
  }

  private void prepareChainOwnerOwned(PlayerRef owner, SourceSnapshot source) {
    if (!acceptingChains || !isOwnerEligible(owner)) {
      return;
    }

    int level = adaptation.getActiveLevel(owner);
    UUID ownerId = owner.uuid();
    if (level <= 0 || !cooldowns.isReady(ownerId, COOLDOWN_MILLIS)
        || !canDamageSnapshotOwned(owner, source.player(), source.protectedFriendly(), source.tameOwnerId(), source.location())) {
      return;
    }

    long token = admission.admit(ownerId, System.currentTimeMillis(), CHAIN_TIMEOUT_MILLIS);
    if (token < 0L) {
      return;
    }

    int remainingHits = Math.max(1, Math.min(HARD_MAX_CHAIN_HITS, level));
    double range = Math.max(1D, Math.min(HARD_MAX_RANGE, 5D + (4D * level)));
    boolean unarmored = isUnarmored(armorContents(owner));
    double damage = lanceDamage(source.damage(), seekerDamageMultiplier(),
        unarmoredDamageMultiplier(), unarmored);
    if (damage <= 0D) {
      admission.complete(ownerId, token);
      return;
    }

    LanceChain chain = new LanceChain(
        owner,
        ownerId,
        token,
        range,
        settings.selfDamage(level),
        seekerDelay()
    );
    requestSearchOwnerOwned(chain, source.location(), damage, remainingHits);
  }

  private static ItemRef[] armorContents(PlayerRef owner) {
    ItemRef[] contents = new ItemRef[ARMOR_SLOTS.length];
    for (int index = 0; index < ARMOR_SLOTS.length; index++) {
      contents[index] = owner.inventory().armor(ARMOR_SLOTS[index]);
    }
    return contents;
  }

  private void requestSearchOwnerOwned(LanceChain chain, Loc source, double damage, int remainingHits) {
    if (!isCurrent(chain) || !isOwnerEligible(chain.owner()) || remainingHits <= 0
        || !workBudget.trySearch(System.currentTimeMillis())) {
      admission.complete(chain.ownerId(), chain.token());
      return;
    }

    SearchPass pass = new SearchPass(chain, source, damage, remainingHits);
    if (!AdaptApi.scheduler().runAt(source, () -> collectCandidatesRegionOwned(pass))) {
      admission.complete(chain.ownerId(), chain.token());
    }
  }

  private void collectCandidatesRegionOwned(SearchPass pass) {
    if (!isCurrent(pass.chain())) {
      return;
    }

    Loc source = pass.source();
    Collection<EntityRef> nearby = source.world().nearbyEntities(
        source.position(),
        pass.chain().range(),
        pass.chain().range(),
        pass.chain().range()
    );
    List<LivingRef> candidates = new ArrayList<>(MAX_CANDIDATE_HANDOFFS);
    int examined = 0;
    for (EntityRef entity : nearby) {
      if (examined++ >= MAX_CANDIDATES_PER_SEARCH) {
        break;
      }
      if (entity instanceof LivingRef candidate
          && AdaptationDamageTargets.allows(candidate, settings.ignorePassiveMobs())
          && !isCaster(pass.chain().ownerId(), candidate.uuid())) {
        candidates.add(candidate);
        if (candidates.size() >= MAX_CANDIDATE_HANDOFFS) {
          break;
        }
      }
    }

    if (candidates.isEmpty()) {
      finishChain(pass.chain());
      return;
    }

    CandidateBatch batch = new CandidateBatch(pass, candidates.size());
    for (LivingRef candidate : candidates) {
      if (!AdaptApi.scheduler().runEntity(candidate, () -> batch.complete(captureTargetOwned(pass.chain().ownerId(), candidate)))) {
        batch.complete(null);
      }
    }
    batch.armTimeout();
  }

  private TargetSnapshot captureTargetOwned(UUID ownerId, LivingRef target) {
    if (!target.isValid() || target.isDead()
        || !AdaptationDamageTargets.allows(target, settings.ignorePassiveMobs())) {
      return null;
    }

    UUID targetId = target.uuid();
    if (isCaster(ownerId, targetId)) {
      return null;
    }
    boolean protectedFriendly = adaptation.isProtectedFriendly(null, target);
    if (!protectedFriendly && target.has(EntityTrait.TAMEABLE)) {
      protectedFriendly = ownerId.equals(target.tameOwner());
    }
    return new TargetSnapshot(
        target,
        targetId,
        target.location(),
        target instanceof PlayerRef,
        protectedFriendly, target.type(), !AdaptationDamageTargets.allows(target, true)
    );
  }

  private void selectTargetOwnerOwned(CandidateBatch batch) {
    SearchPass pass = batch.pass();
    LanceChain chain = pass.chain();
    if (!isCurrent(chain) || !isOwnerEligible(chain.owner()) || adaptation.getActiveLevel(chain.owner()) <= 0) {
      finishChain(chain);
      return;
    }

    TargetSnapshot selected = null;
    double bestDistanceSquared = chain.range() * chain.range();
    for (TargetSnapshot candidate : batch.targets()) {
      if (!adaptation.preference(chain.owner(), TragoulPreferences.TARGETS).accepts(candidate.type())
          || (adaptation.preferenceEnabled(chain.owner(), TragoulPreferences.PASSIVE) && candidate.passive())
          || !candidate.location().world().equals(pass.source().world())
          || isCaster(chain.ownerId(), candidate.entityId())
          || chain.hitIds().contains(candidate.entityId()) || !canDamageSnapshotOwned(
          chain.owner(), candidate.player(), candidate.protectedFriendly(), null, candidate.location())) {
        continue;
      }
      double distanceSquared = pass.source().distanceSquared(candidate.location());
      if (distanceSquared <= bestDistanceSquared) {
        selected = candidate;
        bestDistanceSquared = distanceSquared;
      }
    }

    if (selected == null) {
      finishChain(chain);
      return;
    }

    chain.hitIds().add(selected.entityId());
    TragoulLance.launchFx(adaptation, pass.source());
    TragoulLance.travelFx(adaptation, pass.source(), selected.location(), chain.delayTicks());
    TargetSnapshot target = selected;
    if (!AdaptApi.scheduler().runEntity(target.entity(), () -> prepareImpactTargetOwned(pass, target), chain.delayTicks())) {
      finishChain(chain);
    }
  }

  private void prepareImpactTargetOwned(SearchPass pass, TargetSnapshot snapshot) {
    LanceChain chain = pass.chain();
    if (!isCurrent(chain)) {
      return;
    }

    TargetSnapshot current = captureTargetOwned(chain.ownerId(), snapshot.entity());
    if (current == null || isCaster(chain.ownerId(), current.entityId()) || current.protectedFriendly()
        || !current.entityId().equals(snapshot.entityId())
        || !current.location().world().equals(pass.source().world())
        || current.location().distanceSquared(pass.source()) > chain.range() * chain.range()) {
      finishChain(chain);
      return;
    }

    if (!AdaptApi.scheduler().runEntity(chain.owner(), () -> validateImpactPolicyOwnerOwned(pass, current))) {
      finishChain(chain);
    }
  }

  private void validateImpactPolicyOwnerOwned(SearchPass pass, TargetSnapshot approved) {
    LanceChain chain = pass.chain();
    if (!isCurrent(chain) || !isOwnerEligible(chain.owner())
        || isCaster(chain.ownerId(), approved.entityId()) || !canDamageSnapshotOwned(
        chain.owner(), approved.player(), approved.protectedFriendly(), null, approved.location())) {
      finishChain(chain);
      return;
    }

    if (!AdaptApi.scheduler().runEntity(approved.entity(), () -> applyTargetOwned(pass, approved))) {
      finishChain(chain);
    }
  }

  private void applyTargetOwned(SearchPass pass, TargetSnapshot approved) {
    LanceChain chain = pass.chain();
    if (!isCurrent(chain)) {
      return;
    }

    TargetSnapshot current = captureTargetOwned(chain.ownerId(), approved.entity());
    if (current == null || isCaster(chain.ownerId(), current.entityId()) || current.protectedFriendly()
        || !current.entityId().equals(approved.entityId())
        || !current.location().world().equals(approved.location().world())
        || current.location().distanceSquared(approved.location()) > IMPACT_MOVE_TOLERANCE_SQUARED) {
      finishChain(chain);
      return;
    }

    LivingRef target = current.entity();
    double healthBefore = target.health() + target.absorption();
    target.damage(pass.damage(), chain.owner());
    double healthAfter = target.isDead() ? 0D : target.health() + target.absorption();
    boolean successful = healthAfter < healthBefore;
    boolean killed = target.isDead();
    Loc impact = target.location();
    if (successful) {
      int generation = chain.hitIds().size() - 1;
      float pitch = (float) Math.min(2.0, 1.0 + (generation * 0.1));
      TragoulLance.hitFx(adaptation, target, pitch);
    }

    if (!AdaptApi.scheduler().runEntity(chain.owner(), () -> completeHitOwnerOwned(pass, impact, successful, killed))) {
      admission.complete(chain.ownerId(), chain.token());
    }
  }

  private void completeHitOwnerOwned(SearchPass pass, Loc impact, boolean successful, boolean killed) {
    LanceChain chain = pass.chain();
    PlayerRef owner = chain.owner();
    if (!isCurrent(chain) || !isOwnerEligible(owner) || !successful) {
      finishChain(chain);
      return;
    }

    if (chain.activated().compareAndSet(false, true)) {
      cooldowns.mark(chain.ownerId(), COOLDOWN_MILLIS);
    }
    onLanded.accept(owner, killed);

    double selfDamage = chain.selfDamage();
    if (selfDamage > 0D && owner.isValid() && !owner.isDead()) {
      this.selfDamage.accept(owner, selfDamage);
    }

    if (canContinueChain(owner, killed, pass.remainingHits())) {
      requestSearchOwnerOwned(chain, impact, pass.damage() * 0.5D, pass.remainingHits() - 1);
    } else {
      finishChain(chain);
    }
  }

  static PlayerRef resolvePlayerDamager(EntityRef damager) {
    if (damager instanceof PlayerRef player) {
      return player;
    }
    if (damager instanceof ProjectileRef projectile && projectile.shooter() instanceof PlayerRef player) {
      return player;
    }
    return null;
  }

  static boolean isCaster(UUID ownerId, UUID targetId) {
    return ownerId.equals(targetId);
  }

  static boolean isOwnerEligible(PlayerRef owner) {
    return owner != null && owner.isOnline() && owner.isValid() && !owner.isDead();
  }

  static boolean canContinueChain(PlayerRef owner, boolean killed, int remainingHits) {
    return killed && remainingHits > 1 && isOwnerEligible(owner);
  }

  private boolean canDamageSnapshotOwned(PlayerRef owner, boolean player, boolean protectedFriendly,
                                         UUID tameOwnerId, Loc target) {
    if (protectedFriendly || owner.uuid().equals(tameOwnerId)) {
      return false;
    }
    return player ? adaptation.canPVP(owner, target) : adaptation.canPVE(owner, target);
  }

  private boolean isCurrent(LanceChain chain) {
    return acceptingChains && admission.isCurrent(chain.ownerId(), chain.token(), System.currentTimeMillis());
  }

  private void finishChain(LanceChain chain) {
    admission.complete(chain.ownerId(), chain.token());
  }

  private int seekerDelay() {
    return Math.max(1, Math.min(40, settings.seekerDelay()));
  }

  private double seekerDamageMultiplier() {
    double configured = settings.seekerDamageMultiplier();
    return Double.isFinite(configured) ? Math.max(0D, Math.min(4D, configured)) : 0D;
  }

  private double unarmoredDamageMultiplier() {
    double configured = settings.unarmoredDamageMultiplier();
    return Double.isFinite(configured) ? Math.max(1D, Math.min(10D, configured)) : 1D;
  }

  static boolean isUnarmored(ItemRef[] armorContents) {
    if (armorContents == null) {
      return true;
    }
    for (ItemRef armor : armorContents) {
      if (armor != null && !armor.isEmpty()) {
        return false;
      }
    }
    return true;
  }

  static double lanceDamage(double killingDamage, double baseMultiplier,
                            double unarmoredMultiplier, boolean unarmored) {
    if (!Double.isFinite(killingDamage) || !Double.isFinite(baseMultiplier)
        || !Double.isFinite(unarmoredMultiplier)) {
      return 0D;
    }
    double armorMultiplier = unarmored ? Math.max(1D, unarmoredMultiplier) : 1D;
    return Math.max(0D, killingDamage) * Math.max(0D, baseMultiplier) * armorMultiplier;
  }

  static double selfDamageForLevel(int level, int maxLevel, double firstLevelDamage,
                                   double maxLevelDamage) {
    if (level <= 0 || maxLevel <= 0 || !Double.isFinite(firstLevelDamage)
        || !Double.isFinite(maxLevelDamage)) {
      return 0D;
    }

    double first = Math.max(0D, firstLevelDamage);
    double last = Math.max(0D, Math.min(first, maxLevelDamage));
    if (maxLevel == 1) {
      return first;
    }

    int clampedLevel = Math.max(1, Math.min(maxLevel, level));
    double progress = (clampedLevel - 1D) / (maxLevel - 1D);
    return first + ((last - first) * progress);
  }

  private record SourceSnapshot(Loc location, boolean player, boolean protectedFriendly,
                                UUID tameOwnerId, double damage) {
  }

  private record TargetSnapshot(LivingRef entity, UUID entityId, Loc location,
                                boolean player, boolean protectedFriendly, Id type, boolean passive) {
  }

  private record SearchPass(LanceChain chain, Loc source, double damage, int remainingHits) {
  }

  private record LanceChain(PlayerRef owner, UUID ownerId, long token, double range,
                            double selfDamage, int delayTicks,
                            Set<UUID> hitIds, AtomicBoolean activated) {
    private LanceChain(PlayerRef owner, UUID ownerId, long token, double range,
                       double selfDamage, int delayTicks) {
      this(owner, ownerId, token, range, selfDamage, delayTicks,
          ConcurrentHashMap.newKeySet(), new AtomicBoolean());
    }
  }

  private final class CandidateBatch {
    private final SearchPass pass;
    private final ConcurrentLinkedQueue<TargetSnapshot> targets = new ConcurrentLinkedQueue<>();
    private final AtomicInteger remaining;
    private final AtomicBoolean dispatched = new AtomicBoolean();

    private CandidateBatch(SearchPass pass, int remaining) {
      this.pass = pass;
      this.remaining = new AtomicInteger(remaining);
    }

    private SearchPass pass() {
      return pass;
    }

    private ConcurrentLinkedQueue<TargetSnapshot> targets() {
      return targets;
    }

    private void complete(TargetSnapshot target) {
      if (target != null) {
        targets.add(target);
      }
      if (remaining.decrementAndGet() == 0) {
        dispatch();
      }
    }

    private void armTimeout() {
      if (!AdaptApi.scheduler().runEntity(pass.chain().owner(), this::dispatch, 10)) {
        finishChain(pass.chain());
      }
    }

    private void dispatch() {
      if (!dispatched.compareAndSet(false, true)) {
        return;
      }
      if (!AdaptApi.scheduler().runEntity(pass.chain().owner(), () -> selectTargetOwnerOwned(this))) {
        finishChain(pass.chain());
      }
    }
  }

  static final class LanceWorkBudget {
    private final int searchLimit;
    private final long windowMillis;
    private long windowStartedAt = Long.MIN_VALUE;
    private int searches;

    LanceWorkBudget(int searchLimit, long windowMillis) {
      this.searchLimit = Math.max(1, searchLimit);
      this.windowMillis = Math.max(1L, windowMillis);
    }

    synchronized boolean trySearch(long now) {
      if (windowStartedAt == Long.MIN_VALUE || now < windowStartedAt || now - windowStartedAt >= windowMillis) {
        windowStartedAt = now;
        searches = 0;
      }
      if (searches >= searchLimit) {
        return false;
      }
      searches++;
      return true;
    }
  }

  static final class LanceAdmission {
    private final int capacity;
    private final Map<UUID, PendingChain> pending = new HashMap<>();
    private long nextToken;

    LanceAdmission(int capacity) {
      this.capacity = Math.max(1, capacity);
    }

    synchronized long admit(UUID ownerId, long now, long timeoutMillis) {
      PendingChain existing = pending.get(ownerId);
      if (existing != null && existing.expiresAt() > now) {
        return -1L;
      }
      if (existing != null) {
        pending.remove(ownerId);
      }
      if (pending.size() >= capacity) {
        pending.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
        if (pending.size() >= capacity) {
          return -1L;
        }
      }

      long token = ++nextToken;
      pending.put(ownerId, new PendingChain(token, deadline(now, timeoutMillis)));
      return token;
    }

    synchronized boolean isCurrent(UUID ownerId, long token, long now) {
      PendingChain chain = pending.get(ownerId);
      if (chain == null || chain.token() != token) {
        return false;
      }
      if (chain.expiresAt() <= now) {
        pending.remove(ownerId);
        return false;
      }
      return true;
    }

    synchronized boolean complete(UUID ownerId, long token) {
      PendingChain chain = pending.get(ownerId);
      if (chain == null || chain.token() != token) {
        return false;
      }
      pending.remove(ownerId);
      return true;
    }

    synchronized void cancel(UUID ownerId) {
      pending.remove(ownerId);
    }

    synchronized int size() {
      return pending.size();
    }

    synchronized void clear() {
      pending.clear();
    }

    private static long deadline(long now, long timeoutMillis) {
      long timeout = Math.max(1L, timeoutMillis);
      return now > Long.MAX_VALUE - timeout ? Long.MAX_VALUE : now + timeout;
    }

    private record PendingChain(long token, long expiresAt) {
    }
  }
}

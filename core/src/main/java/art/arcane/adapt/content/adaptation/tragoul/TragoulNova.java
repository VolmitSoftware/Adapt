package art.arcane.adapt.content.adaptation.tragoul;

import art.arcane.adapt.AdaptPorts;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationDamageTargets;
import art.arcane.adapt.api.adaptation.EntityGuardPort;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.minion.MinionIdentity;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * The blood nova a corpse releases: hostile mobs around it take flat damage plus a share of the corpse's maximum
 * health. Novas are queued and drained a few per tick, targets are snapshotted on their own threads and authorized on
 * the owner's, and a mob hit by a nova cannot detonate another one for a while.
 */
public final class TragoulNova {
  public static final double MAX_RADIUS = 16D;
  private static final int HARD_MAX_NOVA_TARGETS = 16;
  private static final int HARD_MAX_NOVA_CANDIDATES = 32;
  private static final int MAX_NOVAS_PER_TICK = 8;
  private static final int MAX_PENDING_NOVAS = 256;
  private static final DataKey<Long> NOVA_KEY = DataKey.adapt("tragoul_nova_stamp", DataCodecs.LONG);

  private final Adaptation<?> adaptation;
  private final Settings settings;
  private final BiConsumer<PlayerRef, List<LivingRef>> onDetonated;
  private final ConcurrentLinkedQueue<NovaPlan> pendingNovas = new ConcurrentLinkedQueue<>();
  private final AtomicInteger pendingNovaCount = new AtomicInteger();
  private final AtomicBoolean novaDrainScheduled = new AtomicBoolean();

  /** The owning adaptation's tuning, read when a nova is planned. */
  public interface Settings {
    boolean ignorePassiveMobs();

    long chainSuppressionMillis();

    int maxTargets();

    double radius(int level);

    double damage(int level, double victimMaxHealth);
  }

  /** {@code onDetonated} runs on the owner's thread with the mobs a nova damaged. */
  public TragoulNova(Adaptation<?> adaptation, Settings settings, BiConsumer<PlayerRef, List<LivingRef>> onDetonated) {
    this.adaptation = adaptation;
    this.settings = settings;
    this.onDetonated = onDetonated;
  }

  /** Detonates {@code victim}'s corpse for {@code owner}; {@code servant} tints the nova for a servant's kill. */
  public void request(PlayerRef owner, LivingRef victim, boolean servant) {
    if (owner == null || victim == null || !adaptation.getServer().hasOnlineLearner(owner.uuid(), adaptation.getName())) {
      return;
    }
    NovaSourceSnapshot source = captureSourceOwned(victim);
    if (source != null) {
      Tasks.runEntity(owner, () -> prepareNovaOwnerOwned(owner, source, servant));
    }
  }

  private NovaSourceSnapshot captureSourceOwned(LivingRef victim) {
    long now = System.currentTimeMillis();
    Long novaStamp = victim.data().get(NOVA_KEY);
    if (isSuppressed(novaStamp, now, settings.chainSuppressionMillis())) {
      return null;
    }

    double attributeMaxHealth = victim.attributeValue(Attributes.MAX_HEALTH);
    double maxHealth = Double.isNaN(attributeMaxHealth) ? 20D : attributeMaxHealth;
    ProtectionSnapshot protection = captureProtectionOwned(victim);
    return new NovaSourceSnapshot(victim.location(), maxHealth,
        victim instanceof PlayerRef, protection.protectedFriendly(), protection.tameOwnerId());
  }

  static boolean isSuppressed(Long novaStamp, long now, long suppressionMillis) {
    return novaStamp != null && now - novaStamp < Math.max(0L, suppressionMillis);
  }

  static boolean isNovaTarget(EntityRef entity) {
    return entity instanceof LivingRef && entity.has(EntityTrait.ENEMY);
  }

  private void prepareNovaOwnerOwned(PlayerRef owner, NovaSourceSnapshot source, boolean servant) {
    if (!owner.isOnline()) {
      return;
    }

    int level = adaptation.getActiveLevel(owner);
    if (level <= 0 || !canDamageSnapshotOwned(owner, source.player(), source.protectedFriendly(),
        source.tameOwnerId(), source.location())) {
      return;
    }
    double radius = settings.radius(level);
    double damage = settings.damage(level, source.maxHealth());
    int maxTargets = Math.min(HARD_MAX_NOVA_TARGETS, Math.max(1, settings.maxTargets()));
    int candidateLimit = Math.min(HARD_MAX_NOVA_CANDIDATES, maxTargets * 2);
    NovaPlan plan = new NovaPlan(owner, source.location(), radius, damage, maxTargets, candidateLimit, servant);
    enqueueNova(plan);
  }

  private void enqueueNova(NovaPlan plan) {
    if (pendingNovaCount.incrementAndGet() > MAX_PENDING_NOVAS) {
      pendingNovaCount.decrementAndGet();
      return;
    }
    pendingNovas.add(plan);
    scheduleNovaDrain();
  }

  private void scheduleNovaDrain() {
    if (novaDrainScheduled.compareAndSet(false, true)) {
      Tasks.s(this::drainNovas, 1);
    }
  }

  private void drainNovas() {
    int drained = 0;
    while (drained < MAX_NOVAS_PER_TICK) {
      NovaPlan plan = pendingNovas.poll();
      if (plan == null) {
        break;
      }
      pendingNovaCount.decrementAndGet();
      Tasks.runAt(plan.source(), () -> collectCandidatesOwned(plan));
      drained++;
    }
    novaDrainScheduled.set(false);
    if (!pendingNovas.isEmpty()) {
      scheduleNovaDrain();
    }
  }

  private void collectCandidatesOwned(NovaPlan plan) {
    TragoulCorpseExplosion.novaFx(adaptation, plan.source(), plan.radius(), plan.servant());
    List<LivingRef> candidates = new ArrayList<>(plan.candidateLimit());
    Loc source = plan.source();
    double radius = plan.radius();
    for (EntityRef entity : source.world().nearbyEntities(source.position(), radius, radius, radius)) {
      if (!isNovaTarget(entity) || !AdaptationDamageTargets.allows(entity, settings.ignorePassiveMobs())) {
        continue;
      }
      candidates.add((LivingRef) entity);
      if (candidates.size() >= plan.candidateLimit()) {
        break;
      }
    }

    if (candidates.isEmpty()) {
      return;
    }

    NovaBatch batch = new NovaBatch(plan, candidates.size());
    for (LivingRef candidate : candidates) {
      if (!Tasks.runEntity(candidate, () -> batch.complete(captureTargetOwned(candidate)))) {
        batch.complete(null);
      }
    }
    batch.armTimeout();
  }

  private NovaTargetSnapshot captureTargetOwned(LivingRef target) {
    if (!target.isValid() || target.isDead()
        || !AdaptationDamageTargets.allows(target, settings.ignorePassiveMobs())) {
      return null;
    }
    Loc location = target.location();
    ProtectionSnapshot protection = captureProtectionOwned(target);
    return new NovaTargetSnapshot(target, location, protection.protectedFriendly(), protection.tameOwnerId(), target.type(), !AdaptationDamageTargets.allows(target, true));
  }

  private ProtectionSnapshot captureProtectionOwned(LivingRef target) {
    if (target.isInvulnerable() || AdaptPorts.get(EntityGuardPort.class, EntityGuardPort.NONE).isShielded(target)
        || MinionIdentity.isMinion(target)) {
      return new ProtectionSnapshot(true, null);
    }
    if (target.has(EntityTrait.TAMEABLE)) {
      UUID tamer = target.tameOwner();
      if (tamer != null) {
        return new ProtectionSnapshot(false, tamer);
      }
    }
    return new ProtectionSnapshot(false, null);
  }

  private void completeBatchOwnerOwned(NovaBatch batch) {
    NovaPlan plan = batch.plan();
    PlayerRef owner = plan.owner();
    if (!owner.isOnline() || adaptation.getActiveLevel(owner) <= 0) {
      return;
    }

    List<NovaTargetSnapshot> selected = new ArrayList<>(plan.maxTargets());
    for (NovaTargetSnapshot target : batch.targets()) {
      if (adaptation.preference(owner, TragoulPreferences.TARGETS).accepts(target.type())
          && (!adaptation.preferenceEnabled(owner, TragoulPreferences.PASSIVE) || !target.passive())
          && canDamageSnapshotOwned(owner, false, target.protectedFriendly(), target.tameOwnerId(), target.location())) {
        selected.add(target);
        if (selected.size() >= plan.maxTargets()) {
          break;
        }
      }
    }
    if (selected.isEmpty()) {
      return;
    }

    long now = System.currentTimeMillis();
    NovaApplicationBatch application = new NovaApplicationBatch(plan, selected.size());
    for (NovaTargetSnapshot target : selected) {
      if (!Tasks.runEntity(target.entity(), () -> applyNovaTargetOwned(application, target, now))) {
        application.complete();
      }
    }
    application.armTimeout();
  }

  private void applyNovaTargetOwned(NovaApplicationBatch application, NovaTargetSnapshot target, long now) {
    LivingRef enemy = target.entity();
    if (!enemy.isValid() || enemy.isDead()
        || !AdaptationDamageTargets.allows(enemy, settings.ignorePassiveMobs())) {
      application.complete();
      return;
    }

    Long previousStamp = enemy.data().get(NOVA_KEY);
    int previousNoDamageTicks = enemy.noDamageTicks();
    double healthBefore = enemy.health() + enemy.absorption();
    enemy.data().set(NOVA_KEY, now);
    enemy.setNoDamageTicks(0);
    enemy.damage(application.plan().damage(), application.plan().owner());
    double healthAfter = enemy.isDead() ? 0D : enemy.health() + enemy.absorption();
    boolean successful = healthAfter < healthBefore;
    if (!successful) {
      enemy.setNoDamageTicks(previousNoDamageTicks);
      if (previousStamp == null) {
        enemy.data().remove(NOVA_KEY);
      } else {
        enemy.data().set(NOVA_KEY, previousStamp);
      }
      application.complete();
      return;
    }

    TragoulCorpseExplosion.novaHitFx(adaptation, enemy);
    application.hit(enemy);
  }

  private void finishNovaApplicationOwnerOwned(NovaApplicationBatch application) {
    NovaPlan plan = application.plan();
    PlayerRef owner = plan.owner();
    List<LivingRef> hits = application.hits();
    if (!owner.isOnline() || adaptation.getActiveLevel(owner) <= 0 || hits.isEmpty()) {
      return;
    }
    onDetonated.accept(owner, hits);
  }

  private boolean canDamageSnapshotOwned(PlayerRef owner, boolean player, boolean protectedFriendly,
                                         UUID tameOwnerId, Loc target) {
    if (protectedFriendly || owner.uuid().equals(tameOwnerId)) {
      return false;
    }
    return player ? adaptation.canPVP(owner, target) : adaptation.canPVE(owner, target);
  }

  private record ProtectionSnapshot(boolean protectedFriendly, UUID tameOwnerId) {
  }

  private record NovaSourceSnapshot(Loc location, double maxHealth, boolean player,
                                    boolean protectedFriendly, UUID tameOwnerId) {
  }

  private record NovaTargetSnapshot(LivingRef entity, Loc location, boolean protectedFriendly,
                                    UUID tameOwnerId, Id type, boolean passive) {
  }

  private record NovaPlan(PlayerRef owner, Loc source, double radius, double damage,
                          int maxTargets, int candidateLimit, boolean servant) {
  }

  private final class NovaBatch {
    private final NovaPlan plan;
    private final ConcurrentLinkedQueue<NovaTargetSnapshot> targets = new ConcurrentLinkedQueue<>();
    private final AtomicInteger remaining;
    private final AtomicBoolean dispatched = new AtomicBoolean();

    private NovaBatch(NovaPlan plan, int remaining) {
      this.plan = plan;
      this.remaining = new AtomicInteger(remaining);
    }

    private NovaPlan plan() {
      return plan;
    }

    private ConcurrentLinkedQueue<NovaTargetSnapshot> targets() {
      return targets;
    }

    private void complete(NovaTargetSnapshot target) {
      if (target != null) {
        targets.add(target);
      }
      if (remaining.decrementAndGet() == 0) {
        dispatch();
      }
    }

    private void armTimeout() {
      Tasks.runEntity(plan.owner(), this::dispatch, 10);
    }

    private void dispatch() {
      if (dispatched.compareAndSet(false, true)) {
        Tasks.runEntity(plan.owner(), () -> completeBatchOwnerOwned(this));
      }
    }
  }

  private final class NovaApplicationBatch {
    private final NovaPlan plan;
    private final AtomicInteger remaining;
    private final ConcurrentLinkedQueue<LivingRef> hits = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean dispatched = new AtomicBoolean();

    private NovaApplicationBatch(NovaPlan plan, int remaining) {
      this.plan = plan;
      this.remaining = new AtomicInteger(remaining);
    }

    private NovaPlan plan() {
      return plan;
    }

    private List<LivingRef> hits() {
      return List.copyOf(hits);
    }

    private void hit(LivingRef enemy) {
      hits.add(enemy);
      complete();
    }

    private void complete() {
      if (remaining.decrementAndGet() == 0) {
        dispatch();
      }
    }

    private void armTimeout() {
      Tasks.runEntity(plan.owner(), this::dispatch, 10);
    }

    private void dispatch() {
      if (dispatched.compareAndSet(false, true)) {
        Tasks.runEntity(plan.owner(), () -> finishNovaApplicationOwnerOwned(this));
      }
    }
  }
}

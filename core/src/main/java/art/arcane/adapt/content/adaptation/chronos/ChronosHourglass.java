package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Hourglass Guard's save: the killing blow is cancelled, the player is left alive and briefly invulnerable, and nearby
 * enemies are slowed.
 */
public final class ChronosHourglass {
  private static final double HARD_MAX_ENEMY_SLOW_RADIUS = 12D;
  private static final int HARD_MAX_CANDIDATES_PER_SAVE = 32;
  private static final int HARD_MAX_AFFECTED_PER_SAVE = 16;
  private static final int HARD_MAX_TARGET_CHAINS_PER_WINDOW = 128;

  private final Adaptation<?> owner;
  private final Map<UUID, Long> invulnerableUntil;
  private final ChronosHourglassGuard.HourglassWorkBudget slowBudget = new ChronosHourglassGuard.HourglassWorkBudget(
      HARD_MAX_TARGET_CHAINS_PER_WINDOW,
      50L,
      System::currentTimeMillis
  );

  /** What one save does: the health left, how long nothing hurts, the cooldown shown, and the slow on enemies. */
  public record Save(double survivalHealth, long invulnerabilityMillis, long cooldownMillis, double slowRadius,
                     int slowTicks, int slowAmplifier) {
  }

  /** {@code invulnerableUntil} comes from the owner's player state. */
  public ChronosHourglass(Adaptation<?> owner, Map<UUID, Long> invulnerableUntil) {
    this.owner = owner;
    this.invulnerableUntil = invulnerableUntil;
  }

  public boolean isInvulnerable(UUID id, long now) {
    return invulnerableUntil.getOrDefault(id, 0L) > now;
  }

  public void forget(UUID id) {
    invulnerableUntil.remove(id);
  }

  public void prune(long now) {
    invulnerableUntil.entrySet().removeIf(entry -> entry.getValue() <= now);
  }

  /** Saves the player from {@code e}, which is cancelled. */
  public void save(PlayerRef p, EntityDamageEvent e, Save save, long now) {
    UUID id = p.uuid();
    e.setCancelled(true);
    p.setHealth(Math.max(0.5D, save.survivalHealth()));
    invulnerableUntil.put(id, now + save.invulnerabilityMillis());
    AbilityGlyphs.cooldown(id, owner, now, save.cooldownMillis());
    AbilityGlyphs.duration(id, owner, now, save.invulnerabilityMillis());
    p.setNoDamageTicks(Math.max(p.noDamageTicks(), (int) (save.invulnerabilityMillis() / 50L)));

    slowNearbyEnemies(p, save);

    ChronosHourglassGuard.saveFx(owner, p);
  }

  private void slowNearbyEnemies(PlayerRef p, Save save) {
    int targetBudget = slowBudget.reserve(HARD_MAX_CANDIDATES_PER_SAVE);
    if (targetBudget <= 0) {
      return;
    }
    Loc center = p.location();
    double radius = Math.max(1D, Math.min(HARD_MAX_ENEMY_SLOW_RADIUS, save.slowRadius()));
    List<LivingRef> candidates = new ArrayList<>(targetBudget);
    for (EntityRef entity : center.world().nearbyEntities(center.position(), radius, radius, radius)) {
      if (!(entity instanceof LivingRef living) || entity.uuid().equals(p.uuid())) {
        continue;
      }
      candidates.add(living);
      if (candidates.size() >= targetBudget) {
        break;
      }
    }

    SlowBatch batch = new SlowBatch(
        p,
        center,
        radius,
        Math.max(1, Math.min(1200, save.slowTicks())),
        Math.max(0, Math.min(4, save.slowAmplifier()))
    );
    for (LivingRef candidate : candidates) {
      Tasks.runEntity(candidate, () -> inspectSlowCandidateOwned(batch, candidate));
    }
  }

  private void inspectSlowCandidateOwned(SlowBatch batch, LivingRef target) {
    SlowTargetSnapshot snapshot = captureSlowTarget(batch, target);
    if (snapshot == null || !batch.hasCapacity()) {
      return;
    }
    Tasks.runEntity(batch.player, () -> authorizeSlowCandidateOwned(batch, target, snapshot));
  }

  private SlowTargetSnapshot captureSlowTarget(SlowBatch batch, LivingRef target) {
    if (!target.isValid() || target.isDead() || owner.isProtectedFriendly(null, target)) {
      return null;
    }
    if (ChronosHourglassGuard.isOwnPet(target, batch.playerId)) {
      return null;
    }
    Loc location = target.location();
    if (!isInsideSlowArea(batch, location)) {
      return null;
    }
    return new SlowTargetSnapshot(location, target instanceof PlayerRef);
  }

  private void authorizeSlowCandidateOwned(SlowBatch batch, LivingRef target, SlowTargetSnapshot snapshot) {
    if (!batch.player.isOnline() || !batch.hasCapacity()) {
      return;
    }
    boolean allowed = snapshot.playerTarget
        ? owner.canPVP(batch.player, snapshot.location)
        : owner.canPVE(batch.player, snapshot.location);
    if (allowed) {
      Tasks.runEntity(target, () -> applySlowOwned(batch, target));
    }
  }

  private void applySlowOwned(SlowBatch batch, LivingRef target) {
    if (captureSlowTarget(batch, target) == null || !batch.claimAffected()) {
      return;
    }
    target.addEffect(new EffectInstance(
        ChronosHourglassGuard.SLOWNESS,
        batch.slowTicks,
        batch.slowAmplifier,
        true,
        false,
        false
    ));
    if (batch.claimFx()) {
      ChronosHourglassGuard.slowFx(owner, target);
    }
  }

  private static boolean isInsideSlowArea(SlowBatch batch, Loc location) {
    return location.sameWorld(batch.center)
        && location.distanceSquared(batch.center) <= batch.radiusSquared;
  }

  private record SlowTargetSnapshot(Loc location, boolean playerTarget) {
  }

  private static final class SlowBatch {
    private final PlayerRef player;
    private final UUID playerId;
    private final Loc center;
    private final double radiusSquared;
    private final int slowTicks;
    private final int slowAmplifier;
    private final AtomicInteger affected = new AtomicInteger();
    private final AtomicInteger renderedFx = new AtomicInteger();

    private SlowBatch(PlayerRef player, Loc center, double radius, int slowTicks, int slowAmplifier) {
      this.player = player;
      this.playerId = player.uuid();
      this.center = center;
      this.radiusSquared = radius * radius;
      this.slowTicks = slowTicks;
      this.slowAmplifier = slowAmplifier;
    }

    private boolean hasCapacity() {
      return affected.get() < HARD_MAX_AFFECTED_PER_SAVE;
    }

    private boolean claimAffected() {
      return claim(affected, HARD_MAX_AFFECTED_PER_SAVE);
    }

    private boolean claimFx() {
      return claim(renderedFx, 8);
    }

    private boolean claim(AtomicInteger counter, int limit) {
      int current = counter.get();
      while (current < limit) {
        if (counter.compareAndSet(current, current + 1)) {
          return true;
        }
        current = counter.get();
      }
      return false;
    }
  }
}

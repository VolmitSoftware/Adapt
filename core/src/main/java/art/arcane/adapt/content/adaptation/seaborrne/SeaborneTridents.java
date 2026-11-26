package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.AdaptLog;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.content.adaptation.chronos.TemporalFreezeRegistry;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Trident Mastery's trident handling for one owning adaptation: thrown tridents carry the owner's level, hits with
 * them are recognised, and a recalled trident homes back to its thrower.
 */
public final class SeaborneTridents {
  public static final Id TRIDENT = Id.minecraft("trident");
  private static final int MAX_RECALL_TICKS = 120;
  private static final double PICKUP_DISTANCE = 1.6;
  private static final DataKey<Byte> RELEASED_KEY = DataKey.adapt("seaborne_trident_released", DataCodecs.BYTE);

  private final Adaptation<?> owner;
  private final DataKey<Integer> levelKey;
  private final Recall recall;

  public SeaborneTridents(Adaptation<?> owner, DataKey<Integer> levelKey, Recall recall) {
    this.owner = owner;
    this.levelKey = levelKey;
    this.recall = recall;
  }

  /** The thrower or wielder and the level this owner hit with, or null when the damage is not its trident hit. */
  public Hit resolveHit(EntityDamageByEntityEvent e) {
    EntityRef damager = e.damager();
    if (damager instanceof ProjectileRef trident && TRIDENT.equals(trident.type())
        && trident.shooter() instanceof PlayerRef shooter) {
      Integer level = trident.data().get(levelKey);
      return level == null || level <= 0 ? null : new Hit(shooter, level, trident);
    }

    if (damager instanceof PlayerRef melee && melee.inventory().mainHand().type().equals(TRIDENT)) {
      int level = owner.getActiveLevel(melee);
      return level <= 0 ? null : new Hit(melee, level, null);
    }

    return null;
  }

  public static void strike(EntityDamageByEntityEvent e, double bonus) {
    e.setDamage(e.damage() * (1D + bonus));
  }
  /** Tags a thrown trident with the owner's level and starts its recall after {@code delayTicks} when enabled. */
  public void launch(ProjectileRef trident, PlayerRef thrower, int level, int delayTicks) {
    trident.data().set(levelKey, level);
    if (recall.enabled(thrower)) {
      Tasks.runEntity(trident, () -> recallTick(trident, thrower, level, 0), delayTicks);
    }
  }

  /** Skips the remaining flight time of a recalled trident so it turns back on its next recall tick. */
  public static void release(ProjectileRef trident) {
    trident.data().set(RELEASED_KEY, (byte) 1);
  }

  private void recallTick(ProjectileRef trident, PlayerRef p, int level, int ticksLived) {
    if (!trident.isValid() || trident.isDead() || ticksLived > MAX_RECALL_TICKS) {
      return;
    }
    UUID tridentId = trident.uuid();
    if (!Tasks.runEntity(p, () -> captureRecallTargetOwned(trident, tridentId, p, level, ticksLived))) {
      AdaptLog.warn(owner.getName() + " could not schedule recall owner snapshot for " + tridentId + ".");
    }
  }

  private void captureRecallTargetOwned(ProjectileRef trident, UUID tridentId, PlayerRef p, int level, int ticksLived) {
    if (!p.isOnline() || owner.getActiveLevel(p) <= 0 || !recall.enabled(p)) {
      return;
    }
    Loc playerLocation = p.location().add(0D, 1.0D, 0D);
    if (!Tasks.runEntity(trident,
        () -> applyRecallTargetOwned(trident, tridentId, p, level, ticksLived, playerLocation))) {
      AdaptLog.warn(owner.getName() + " could not schedule recall continuation for " + tridentId + ".");
    }
  }

  private void applyRecallTargetOwned(ProjectileRef trident, UUID tridentId, PlayerRef p, int level, int ticksLived,
                                      Loc playerLocation) {
    if (!trident.isValid() || trident.isDead() || ticksLived > MAX_RECALL_TICKS) {
      return;
    }
    if (TemporalFreezeRegistry.isFrozen(trident)) {
      Tasks.runEntity(trident, () -> recallTick(trident, p, level, ticksLived), 1);
      return;
    }

    Loc tridentLocation = trident.location();
    if (!tridentLocation.sameWorld(playerLocation)) {
      return;
    }

    boolean stuck = trident.isInBlock() || trident.isOnGround();
    if (!stuck && !trident.data().has(RELEASED_KEY) && ticksLived < recall.graceTicks(level)) {
      scheduleNextRecall(trident, p, level, ticksLived);
      return;
    }

    Vec3 toPlayer = playerLocation.position().subtract(tridentLocation.position());
    double distance = toPlayer.length();
    if (distance <= PICKUP_DISTANCE) {
      return;
    }

    if (stuck) {
      Loc freed = tridentLocation
          .add(toPlayer.normalize().multiply(0.4D))
          .add(0D, 0.2D, 0D);
      beginReleaseTeleport(trident, tridentId, p, level, ticksLived, freed);
      return;
    }

    trident.setVelocity(toPlayer.normalize().multiply(recall.speed(level)));
    if ((ticksLived & 3) == 0) {
      recall.returning(tridentLocation);
    }
    scheduleNextRecall(trident, p, level, ticksLived);
  }

  private void beginReleaseTeleport(ProjectileRef trident, UUID tridentId, PlayerRef p, int level, int ticksLived,
                                    Loc freed) {
    CompletableFuture<Boolean> teleport;
    try {
      teleport = trident.teleport(freed, TeleportCause.PLUGIN);
    } catch (RuntimeException error) {
      AdaptLog.error(owner.getName() + " could not start release teleport for " + tridentId + ".");
      AdaptLog.error(error);
      return;
    }
    if (teleport == null) {
      return;
    }
    teleport.whenComplete((Boolean success, Throwable failure) ->
        completeReleaseTeleport(trident, tridentId, p, level, ticksLived, success, failure));
  }

  private void completeReleaseTeleport(ProjectileRef trident, UUID tridentId, PlayerRef p, int level, int ticksLived,
                                       Boolean success, Throwable failure) {
    if (failure != null) {
      AdaptLog.error(owner.getName() + " release teleport failed for " + tridentId + ".");
      AdaptLog.error(failure);
    }
    if (!successfulReleaseTeleport(success, failure)) {
      return;
    }
    if (!Tasks.runEntity(trident, () -> recallTick(trident, p, level, ticksLived))) {
      AdaptLog.warn(owner.getName() + " completed release teleport but could not schedule completion for "
          + tridentId + ".");
    }
  }

  private void scheduleNextRecall(ProjectileRef trident, PlayerRef p, int level, int ticksLived) {
    Tasks.runEntity(trident, () -> recallTick(trident, p, level, ticksLived + 1), 1);
  }

  public static boolean successfulReleaseTeleport(Boolean success, Throwable failure) {
    return failure == null && Boolean.TRUE.equals(success);
  }

  public static double damageBonus(double base, double factor, double levelPercent) {
    return Math.max(0D, base + (levelPercent * factor));
  }

  public static double recallSpeed(double base, double factor, double levelPercent) {
    return base + (levelPercent * factor);
  }

  public interface Recall {
    boolean enabled(PlayerRef thrower);

    double speed(int level);

    int graceTicks(int level);

    void returning(Loc tridentLocation);
  }

  /** {@code trident} is null for a melee hit. */
  public record Hit(PlayerRef attacker, int level, ProjectileRef trident) {
  }
}

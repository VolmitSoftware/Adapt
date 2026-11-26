package art.arcane.adapt.content.adaptation.sword;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.scheduling.Tasks;

import java.util.UUID;
import java.util.function.ObjDoubleConsumer;

/**
 * Magic-damage bleeding that pulses every 5 ticks for a sword adaptation. Each pulse is authorized on the source's
 * thread: it stops once the owner adaptation is no longer active for the source or the target became protected.
 */
public final class SwordBleed {
  static final int PERIOD_TICKS = 5;
  private static final Id MAGIC = Id.minecraft("magic");

  private final Adaptation<?> owner;
  private final ObjDoubleConsumer<PlayerRef> onDamage;

  /** {@code onDamage} runs on the source's thread with the health each pulse removed. */
  public SwordBleed(Adaptation<?> owner, ObjDoubleConsumer<PlayerRef> onDamage) {
    this.owner = owner;
    this.onDamage = onDamage;
  }

  /** Pulses that cover {@code durationMillis}, at least one. */
  public static int procCount(long durationMillis) {
    long durationTicks = Math.max(1L, (Math.max(0L, durationMillis) + 49L) / 50L);
    return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, (durationTicks + PERIOD_TICKS - 1L) / PERIOD_TICKS));
  }

  /** Starts bleeding; false when the first pulse could not be scheduled. */
  public boolean start(LivingRef target, PlayerRef source, double damagePerPulse, int pulses) {
    return schedule(new Pulse(target, source, damagePerPulse, pulses));
  }

  static void applyDamage(Pulse pulse) {
    pulse.target().damage(pulse.damage(), MAGIC, pulse.source());
  }

  private boolean schedule(Pulse pulse) {
    return Tasks.runEntity(pulse.target(), () -> run(pulse), PERIOD_TICKS);
  }

  private void run(Pulse pulse) {
    LivingRef target = pulse.target();
    if (pulse.remaining() <= 0 || !target.isValid() || target.isDead()) {
      return;
    }

    Authorization authorization = new Authorization(target.location(), target instanceof PlayerRef,
        owner.isProtectedFriendly(null, target), target.tameOwner());
    Tasks.runEntity(pulse.source(), () -> authorize(pulse, authorization));
  }

  private void authorize(Pulse pulse, Authorization authorization) {
    PlayerRef source = pulse.source();
    if (!source.isOnline() || owner.getActiveLevel(source) <= 0 || authorization.protectedFriendly()
        || source.uuid().equals(authorization.tameOwnerId())) {
      return;
    }

    boolean allowed = authorization.playerTarget()
        ? owner.canPVP(source, authorization.location())
        : owner.canPVE(source, authorization.location());
    if (!allowed) {
      return;
    }

    Tasks.runEntity(pulse.target(), () -> apply(pulse));
  }

  private void apply(Pulse pulse) {
    LivingRef target = pulse.target();
    if (!target.isValid() || target.isDead()) {
      return;
    }

    double before = target.health() + target.absorption();
    applyDamage(pulse);
    double dealt = Math.max(0D, before - target.health() - target.absorption());
    if (dealt > 0D) {
      Tasks.runEntity(pulse.source(), () -> onDamage.accept(pulse.source(), dealt));
      SwordsBloodyBlade.pulseFx(owner, target);
    }

    if (pulse.remaining() > 1 && target.isValid() && !target.isDead()) {
      schedule(new Pulse(target, pulse.source(), pulse.damage(), pulse.remaining() - 1));
    }
  }

  record Pulse(LivingRef target, PlayerRef source, double damage, int remaining) {
  }

  private record Authorization(Loc location, boolean playerTarget, boolean protectedFriendly, UUID tameOwnerId) {
  }
}

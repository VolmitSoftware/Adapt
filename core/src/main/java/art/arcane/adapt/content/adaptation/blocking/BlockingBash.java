package art.arcane.adapt.content.adaptation.blocking;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.platform.value.Vec3;

/** The shield slam shared by Bulwark Bash and Tempered Aegis: a shove away from the basher and a slowing stagger. */
public final class BlockingBash {
  private static final Id SLOWNESS = Id.minecraft("slowness");

  private BlockingBash() {
  }

  public static void impact(LivingRef target, Loc origin, Vec3 fallbackDirection, double knockback,
                            double upwardKnockback, int stunTicks, int stunAmplifier) {
    Vec3 kb = target.location().position().subtract(origin.position()).withY(0);
    if (kb.lengthSquared() <= 0.0001) {
      kb = fallbackDirection;
    }
    if (kb.lengthSquared() <= 0.0001) {
      kb = new Vec3(1, 0, 0);
    }

    kb = kb.normalize();
    target.setVelocity(target.velocity().multiply(0.25D).add(kb.multiply(knockback).withY(upwardKnockback)));
    target.addEffect(new EffectInstance(SLOWNESS, stunTicks, stunAmplifier, false, false, true));
  }
}

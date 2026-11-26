package art.arcane.adapt.content.adaptation.ranged;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;

/** The pin of Pinning Shot and the lift of Floaters, applied by whichever adaptation landed the hit. */
public final class RangedSnares {
  private static final Id LEVITATION = Id.minecraft("levitation");

  private RangedSnares() {
  }

  public static double pinSpeedScalar(int amplifier) {
    return -Math.min(1.0D, 0.15D * (Math.max(0, amplifier) + 1.0D));
  }

  /** Slows the target for {@code durationTicks} under {@code owner}'s name. */
  public static void pin(Adaptation<?> owner, LivingRef target, int amplifier, int durationTicks) {
    if (durationTicks > 0) {
      AdaptAttributeService.get().applyTimed(target, owner.getName(), "pin", Attributes.MOVEMENT_SPEED,
          pinSpeedScalar(amplifier), AttributeOperation.ADD_MULTIPLIED_TOTAL, durationTicks);
    }
  }

  public static void dampen(LivingRef target, double horizontalFactor) {
    Vec3 velocity = target.velocity();
    target.setVelocity(new Vec3(velocity.x() * horizontalFactor, velocity.y(), velocity.z() * horizontalFactor));
  }

  public static void levitate(LivingRef target, int durationTicks, int amplifier) {
    target.addEffect(new EffectInstance(LEVITATION, durationTicks, amplifier, true, true, true));
  }
}

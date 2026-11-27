package art.arcane.adapt.content.adaptation.excavation;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;

/** Heaves a living target out of the ground: damage dealt as the player, then a push and {@code slow} if it landed. */
public final class ExcavationHeave {
  private ExcavationHeave() {
  }

  /** True when the hit took health; a hit that took none restores the target's damage immunity and pushes nothing. */
  public static boolean heave(PlayerRef player, LivingRef target, double damage, Vec3 push, Id slow, int slowTicks,
                              int slowAmplifier) {
    int previousNoDamageTicks = target.noDamageTicks();
    double healthBefore = target.health() + target.absorption();
    target.setNoDamageTicks(0);
    target.damage(damage, player);
    double healthAfter = target.isDead() ? 0D : target.health() + target.absorption();
    if (healthAfter >= healthBefore) {
      target.setNoDamageTicks(previousNoDamageTicks);
      return false;
    }

    target.setVelocity(push);
    target.addEffect(EffectInstance.of(slow, slowTicks, slowAmplifier));
    return true;
  }
}

package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Vec3;

/** Aberrant Touch's root: heavy slowness and a dead stop. */
public final class ChronosRoot {
  private static final Id SLOWNESS = Id.minecraft("slowness");

  private ChronosRoot() {
  }

  public static void root(LivingRef target, int durationTicks, int amplifier) {
    target.addEffect(EffectInstance.of(SLOWNESS, durationTicks, amplifier).withAmbient(true));
    target.setVelocity(Vec3.ZERO);
  }
}

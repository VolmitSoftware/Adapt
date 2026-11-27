package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;

/** Stacking pressure-strike status effects shared by Pressure Point and Unbroken Combo. */
public final class UnarmedPressure {
  public static final Id SLOWNESS = Id.minecraft("slowness");
  public static final Id WEAKNESS = Id.minecraft("weakness");

  private UnarmedPressure() {
  }

  /** Raises the victim's {@code type} effect by one amplifier, capped at {@code maxAmplifier}. Returns the new amplifier. */
  public static int stack(LivingRef victim, Id type, int maxAmplifier, int durationTicks) {
    EffectInstance existing = victim.effect(type);
    int amplifier = existing == null ? 0 : Math.min(maxAmplifier, existing.amplifier() + 1);
    victim.addEffect(EffectInstance.of(type, durationTicks, amplifier));
    return amplifier;
  }
}

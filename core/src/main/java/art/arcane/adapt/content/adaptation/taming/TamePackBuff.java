package art.arcane.adapt.content.adaptation.taming;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;

/** The pack leader's timed speed and regeneration on a pet near its owner. */
public final class TamePackBuff {
  private static final Id REGENERATION = Id.minecraft("regeneration");

  private TamePackBuff() {
  }

  /** Grants the buffs for {@code durationTicks} on the pet's thread; speed is keyed to {@code owner}'s name. */
  public static void apply(Adaptation<?> owner, LivingRef pet, int amplifier, int durationTicks, boolean speed,
                           boolean regeneration) {
    if (durationTicks > 0 && speed) {
      AdaptAttributeService.get().applyTimed(
          pet,
          owner.getName(),
          "speed",
          Attributes.MOVEMENT_SPEED,
          speedScalar(amplifier),
          AttributeOperation.ADD_MULTIPLIED_TOTAL,
          durationTicks);
    }
    if (regeneration) {
      pet.addEffect(new EffectInstance(REGENERATION, durationTicks, amplifier, false, false, false));
    }
  }

  static double speedScalar(int amplifier) {
    return 0.2D * (Math.max(0, amplifier) + 1);
  }
}

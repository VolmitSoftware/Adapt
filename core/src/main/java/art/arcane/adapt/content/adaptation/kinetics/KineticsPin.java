package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;

/** Heavy slowness from a spear hit, applied on the target's thread while the owning adaptation is registered. */
public final class KineticsPin {
  private static final Id SLOWNESS = Id.minecraft("slowness");

  private final SimpleAdaptation<?> owner;

  public KineticsPin(SimpleAdaptation<?> owner) {
    this.owner = owner;
  }

  public void pin(LivingRef target, int durationTicks, int tier) {
    Tasks.runEntity(target, () -> {
      if (owner.isRuntimeRegistered() && target.isValid()) {
        target.addEffect(new EffectInstance(SLOWNESS, durationTicks, tier, true, true, true));
        KineticsImpalePin.pinFx(owner, target.location().add(0D, target.height() * 0.5D, 0D));
      }
    });
  }
}

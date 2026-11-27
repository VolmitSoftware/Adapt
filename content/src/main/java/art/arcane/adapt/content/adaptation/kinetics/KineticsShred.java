package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.LivingRef;

/** Timed armor and toughness loss on a smashed target, keyed to the owning adaptation. */
public final class KineticsShred {
  private static final String SLOT_SHRED = "shred";
  private static final String SLOT_SHRED_TOUGHNESS = "shred-toughness";

  private final SimpleAdaptation<?> owner;

  public KineticsShred(SimpleAdaptation<?> owner) {
    this.owner = owner;
  }

  public record Cut(double armor, double toughness, int ticks) {
  }

  /** Strips armor from the target; call on the target's thread. */
  public void apply(LivingRef target, Cut cut) {
    if (!owner.isRuntimeRegistered() || !target.isValid() || target.isDead()) {
      return;
    }

    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.applyTimed(target, owner.getId().toString(), SLOT_SHRED, Attributes.ARMOR, -cut.armor(), AttributeOperation.ADD_VALUE, cut.ticks());
    attributes.applyTimed(target, owner.getId().toString(), SLOT_SHRED_TOUGHNESS, Attributes.ARMOR_TOUGHNESS, -cut.toughness(), AttributeOperation.ADD_VALUE, cut.ticks());
  }
}

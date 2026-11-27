package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.platform.entity.AttributeMod;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

/** The body of a Mass Shift form: size, and for Titan a taller step and a farther camera, in the owner's attribute slots. */
public final class KineticsFormShape {
  private static final String SLOT_SCALE = "form-scale";
  private static final String SLOT_STEP = "form-step";
  private static final String SLOT_CAMERA = "form-camera";
  private static final double TITAN_STEP_HEIGHT = 1.0D;
  private static final double TITAN_CAMERA_DISTANCE = 2.0D;

  private final Adaptation<?> owner;

  public KineticsFormShape(Adaptation<?> owner) {
    this.owner = owner;
  }

  /** Grows the player by {@code sizeChange} in Titan form, shrinks them by it in Pocket form, and resets them otherwise. */
  public void shape(PlayerRef p, int form, double sizeChange) {
    clear(p);
    if (form == KineticsMassShift.FORM_TITAN) {
      apply(p, SLOT_SCALE, Attributes.SCALE, sizeChange, p.hasAttribute(Attributes.SCALE));
      apply(p, SLOT_STEP, Attributes.STEP_HEIGHT, TITAN_STEP_HEIGHT, p.hasAttribute(Attributes.STEP_HEIGHT));
      apply(p, SLOT_CAMERA, Attributes.CAMERA_DISTANCE, TITAN_CAMERA_DISTANCE, p.hasAttribute(Attributes.CAMERA_DISTANCE));
    } else if (form == KineticsMassShift.FORM_POCKET) {
      apply(p, SLOT_SCALE, Attributes.SCALE, -sizeChange, p.hasAttribute(Attributes.SCALE));
    }
  }

  /** Whether the player already wears this form at this size, so nothing needs reapplying. */
  public boolean holds(PlayerRef p, int form, double sizeChange) {
    AttributeMod scale = p.modifier(Attributes.SCALE, AdaptAttributeService.get().modifierKey(owner.getId().toString(), SLOT_SCALE));
    if (form == KineticsMassShift.FORM_NORMAL) {
      return scale == null;
    }
    double wanted = form == KineticsMassShift.FORM_TITAN ? sizeChange : -sizeChange;
    return scale != null && Double.compare(scale.amount(), wanted) == 0;
  }

  public void clear(PlayerRef p) {
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.remove(p, owner.getId().toString(), SLOT_SCALE, Attributes.SCALE);
    attributes.remove(p, owner.getId().toString(), SLOT_STEP, Attributes.STEP_HEIGHT);
    attributes.remove(p, owner.getId().toString(), SLOT_CAMERA, Attributes.CAMERA_DISTANCE);
  }

  private void apply(PlayerRef p, String slot, Id attribute, double amount, boolean present) {
    if (present) {
      AdaptAttributeService.get().apply(p, owner.getId().toString(), slot, attribute, amount, AttributeOperation.ADD_VALUE);
    }
  }
}

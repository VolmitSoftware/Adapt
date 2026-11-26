package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;

/** Extra entity reach while the owner's weapon is in hand, held in the owning adaptation's attribute slot. */
public final class KineticsReach {
  private static final String SLOT_REACH = "reach";

  private final Adaptation<?> owner;

  public KineticsReach(Adaptation<?> owner) {
    this.owner = owner;
  }

  /** Applies {@code reach} while the player is active and {@code armed}, removes it otherwise. */
  public void reconcile(PlayerRef p, boolean armed, double reach) {
    if (p == null || !p.isOnline() || !p.hasAttribute(Attributes.ENTITY_INTERACTION_RANGE)) {
      return;
    }

    if (KineticsPhalanxReach.shouldApplyReach(owner.getActiveLevel(p), armed)) {
      AdaptAttributeService.get().apply(p, owner.getName(), SLOT_REACH, Attributes.ENTITY_INTERACTION_RANGE, reach, AttributeOperation.ADD_VALUE);
    } else {
      AdaptAttributeService.get().remove(p, owner.getName(), SLOT_REACH, Attributes.ENTITY_INTERACTION_RANGE);
    }
  }
}

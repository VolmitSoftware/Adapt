package art.arcane.adapt.content.adaptation.ranged.upgraded;

import art.arcane.adapt.localization.catalog.RangedMessages;
import art.arcane.adapt.platform.value.Id;

/** Trueflight's arrow: four arrows and a slime ball make four, and only these bounce. */
public class RicochetArrow extends MunitionArrow {
  public static final String ID = "tool-ranged-ricochet-arrow";

  public RicochetArrow(Trueflight owner) {
    super(ID, owner, Id.minecraft("slime_ball"), RangedMessages.RICOCHET_ARROW_NAME);
  }
}

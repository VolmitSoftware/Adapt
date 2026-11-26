package art.arcane.adapt.content.adaptation.ranged.upgraded;

import art.arcane.adapt.localization.catalog.RangedMessages;
import art.arcane.adapt.platform.value.Id;

/** Hunter's Web's arrow: four arrows and a cobweb make four, and only these cage. */
public class SnareArrow extends MunitionArrow {
  public static final String ID = "tool-ranged-snare-arrow";

  public SnareArrow(HuntersWeb owner) {
    super(ID, owner, Id.minecraft("cobweb"), RangedMessages.SNARE_ARROW_NAME);
  }
}

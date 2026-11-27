package art.arcane.adapt.content.adaptation.ranged.upgraded;

import art.arcane.adapt.platform.value.Id;

/** Hunter's Web's arrow: four arrows and a cobweb make four, and only these cage. */
public class SnareArrow extends MunitionArrow {
  public static final Id ID = Id.adapt("ranged/tool/snare_arrow");

  public SnareArrow(HuntersWeb owner) {
    super(ID, owner, Id.minecraft("cobweb"));
  }
}

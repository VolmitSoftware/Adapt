package art.arcane.adapt.content.adaptation.ranged.upgraded;

import art.arcane.adapt.platform.value.Id;

/** Trueflight's arrow: four arrows and a slime ball make four, and only these bounce. */
public class RicochetArrow extends MunitionArrow {
  public static final Id ID = Id.adapt("ranged/tool/ricochet_arrow");

  public RicochetArrow(Trueflight owner) {
    super(ID, owner, Id.minecraft("slime_ball"));
  }
}

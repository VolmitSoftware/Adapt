package art.arcane.adapt.content.adaptation.unarmed;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.world.BlockRef;

/** The soft-block rule shared by Iron Fists and Titan Fists. */
public final class UnarmedFists {
  private UnarmedFists() {
  }

  /** A block bare hands can break: breakable and no harder than {@code maxHardness}. */
  public static boolean isSoftBlock(BlockRef block, double maxHardness) {
    float hardness = AdaptApi.platform().host().items().blockHardness(block.type());
    return hardness >= 0 && hardness <= maxHardness;
  }
}

package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Vec3;

import java.util.Objects;

/** Block ray trace hit: the exact hit position, the block and the face that was struck (null when unknown). */
public record BlockHit(Vec3 position, BlockRef block, BlockFace face) {
  public BlockHit {
    Objects.requireNonNull(position, "position");
    Objects.requireNonNull(block, "block");
  }
}

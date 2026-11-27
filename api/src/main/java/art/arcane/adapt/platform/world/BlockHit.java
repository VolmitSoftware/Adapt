package art.arcane.adapt.platform.world;

import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Vec3;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/**
 * Block ray trace hit: the exact hit position, the block and the face that was struck (null when unknown).
 *
 * @param position point where the ray met the block
 * @param block block that was hit
 * @param face face that was struck, or null
 */
@ApiStatus.AvailableSince("1.0.0")
public record BlockHit(Vec3 position, BlockRef block, BlockFace face) {
  public BlockHit {
    Objects.requireNonNull(position, "position");
    Objects.requireNonNull(block, "block");
  }
}

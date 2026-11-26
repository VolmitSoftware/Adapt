package art.arcane.adapt.platform.event.player;

import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;

import java.util.Objects;

/** A player empties a bucket; {@code block} is where its contents are about to go. Mutable: cancelled. */
public class PlayerBucketEmptyEvent extends PlayerEvent {
  private final BlockRef block;
  private final Id bucket;

  public PlayerBucketEmptyEvent(PlayerRef player, BlockRef block, Id bucket) {
    super(player);
    this.block = Objects.requireNonNull(block, "block");
    this.bucket = Objects.requireNonNull(bucket, "bucket");
  }

  public BlockRef block() {
    return block;
  }

  /** The filled bucket item being emptied. */
  public Id bucket() {
    return bucket;
  }
}

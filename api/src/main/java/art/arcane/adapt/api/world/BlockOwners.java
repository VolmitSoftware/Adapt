package art.arcane.adapt.api.world;

import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.platform.world.BlockRef;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The player a block belongs to, such as the brewer of a brewing stand, kept in the world's block data so it survives
 * restarts. Brewing reads the same record. Platforms without persistent block data keep nothing, and every block
 * reads as unowned.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class BlockOwners {
  private BlockOwners() {
  }

  /** The player the block belongs to; empty for an unowned block. */
  public static Optional<UUID> owner(BlockRef block) {
    return Optional.ofNullable(Engine.bridge().world().blockOwner(Objects.requireNonNull(block, "block")));
  }

  /** Records {@code owner} as the block's owner, replacing any earlier one. */
  public static void setOwner(BlockRef block, UUID owner) {
    Engine.bridge().world().setBlockOwner(Objects.requireNonNull(block, "block"), Objects.requireNonNull(owner, "owner"));
  }

  /** Removes the block's owner record. */
  public static void clearOwner(BlockRef block) {
    Engine.bridge().world().setBlockOwner(Objects.requireNonNull(block, "block"), null);
  }
}

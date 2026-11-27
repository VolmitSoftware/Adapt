package art.arcane.adapt.platform.protection;

import org.jetbrains.annotations.ApiStatus;

/**
 * What a player, or an adaptation on a player's behalf, is about to do in the world. Asked of a
 * {@link ProtectionHost} and of every {@code Protector}.
 *
 * <p>A minor release may add checks. A {@code switch} over this enum needs a {@code default} branch, and a protector
 * must deny a check it does not know.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum ProtectionCheck {
  /** Breaking the block at the location. */
  BLOCK_BREAK,
  /** Placing a block at the location. */
  BLOCK_PLACE,
  /** Harming another player. */
  PVP,
  /** Harming a mob or other non-player entity. */
  PVE,
  /** Using or acting on what is at the location, such as a block or a creature. */
  INTERACT,
  /** Opening or taking from a container at the location. */
  ACCESS_CHEST,
  /** Being at or moving to the location, such as a teleport destination. */
  REGION
}

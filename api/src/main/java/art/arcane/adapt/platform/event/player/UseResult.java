package art.arcane.adapt.platform.event.player;

import org.jetbrains.annotations.ApiStatus;

/**
 * Outcome a handler picks for one use in a {@link PlayerInteractEvent}, or for a smash in
 * {@code EntityAttemptSmashAttackEvent}. Every platform answers the same way.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum UseResult {
  /** The use does not happen. */
  DENY,
  /**
   * Vanilla decides. For a right click on a block: when the block use is denied, the item is not used on that block
   * either; the plain item use that follows the click (eating, throwing) still happens unless the item use is denied.
   */
  DEFAULT,
  /**
   * For a smash: the attack smashes. For the uses of a {@link PlayerInteractEvent} it means the same as
   * {@link #DEFAULT} on every platform: no platform forces a use vanilla refuses, and a denied block use stops the item
   * being used on that block whatever the item use says.
   */
  ALLOW
}

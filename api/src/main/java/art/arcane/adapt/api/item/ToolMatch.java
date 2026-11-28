package art.arcane.adapt.api.item;

import org.jetbrains.annotations.ApiStatus;

/**
 * Which rule of {@link Tools} decided that an item is, or is not, a kind of tool. The constants are in the order the
 * rules are asked; the first that answers wins.
 */
@ApiStatus.Experimental
public enum ToolMatch {
  /** An operator's excluded-tools list names the item; no later rule is asked. */
  EXCLUDED,
  /**
   * The stack can no longer dig: its damage reached its maximum, or it is a damageable modded tool its tags call this
   * kind that is no longer the correct tool for the kind's reference block, as a broken Tinkers' Construct tool that
   * stays in the inventory. No later rule is asked.
   */
  BROKEN,
  /** An operator's extra-tools list names the item or one of its tags. */
  OPERATOR,
  /** The item is in a vanilla or mod loader tool tag of the kind, such as {@code minecraft:pickaxes}. */
  TAG,
  /** The mod loader says the stack performs the kind's digging action. */
  CAPABILITY,
  /** The stack is the correct tool for the kind's reference block: stone, an oak log or dirt. */
  BEHAVIOUR,
  /** The item is one of the vanilla tools of the kind. */
  VANILLA,
  /** No rule matched. */
  NONE;

  /** True when the item counts as the kind. */
  public boolean matches() {
    return this != EXCLUDED && this != BROKEN && this != NONE;
  }
}

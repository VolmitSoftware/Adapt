package art.arcane.adapt.api.mutation;

import org.jetbrains.annotations.ApiStatus;

/**
 * The six pieces a skill brings to every mutation it is part of.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.Experimental
public enum BlockSlot {
  /** The action the skill reacts to. */
  TRIGGER,
  /** What the skill does to an enemy. */
  STRIKE_FIGHT,
  /** What the skill does to the blocks, crops or drops around the player. */
  STRIKE_WORK,
  /** What the skill gives the player. */
  BOON,
  /** What the skill pays with. */
  FUEL,
  /** The condition the skill cares about. */
  STATE
}

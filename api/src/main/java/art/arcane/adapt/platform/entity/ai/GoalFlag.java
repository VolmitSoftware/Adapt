package art.arcane.adapt.platform.entity.ai;

import org.jetbrains.annotations.ApiStatus;

/**
 * Goal control flags. {@link #TARGET} goals go to the target selector, all others to the goal
 * selector; this matches Paper's goal types and vanilla's two selectors.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum GoalFlag {
  MOVE,
  LOOK,
  JUMP,
  TARGET
}

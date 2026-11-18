package art.arcane.adapt.platform.entity.ai;

/**
 * Goal control flags. {@link #TARGET} goals go to the target selector, all others to the goal
 * selector; this matches Paper's goal types and vanilla's two selectors.
 */
public enum GoalFlag {
  MOVE,
  LOOK,
  JUMP,
  TARGET
}

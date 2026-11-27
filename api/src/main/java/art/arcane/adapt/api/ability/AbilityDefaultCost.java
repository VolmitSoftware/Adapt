package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

/**
 * The cost an adaptation takes itself when no cost provider waives or pays it, such as removing an item. Run at
 * most once per charge, and only after every provider has quoted.
 */
@ApiStatus.AvailableSince("1.0.0")
@FunctionalInterface
public interface AbilityDefaultCost {
  /** A default cost that takes nothing and always succeeds. */
  AbilityDefaultCost NONE = () -> true;

  /** Takes the cost from the player; false when they cannot pay it, which refuses the use. */
  boolean take();
}

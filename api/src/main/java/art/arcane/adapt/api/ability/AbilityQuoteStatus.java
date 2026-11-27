package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

/**
 * What a cost provider wants to happen to one cost.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum AbilityQuoteStatus {
  /** No opinion; the default cost stays unless another provider replaces it. */
  PASS,
  /** The cost is free; the default cost is not taken. */
  WAIVED,
  /** The provider takes its own price through a reservation instead of the default cost. */
  PAYABLE,
  /** The player cannot afford the provider's price; the use is refused. */
  INSUFFICIENT,
  /** The provider refuses the use. */
  DENIED
}

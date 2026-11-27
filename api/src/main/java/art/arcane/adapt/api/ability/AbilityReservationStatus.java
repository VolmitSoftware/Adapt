package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

/**
 * Whether a cost provider holds the quoted price.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum AbilityReservationStatus {
  RESERVED,
  FAILED
}

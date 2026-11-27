package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

/**
 * Whether a use policy lets an ability use go ahead.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum AbilityUseStatus {
  ALLOW,
  DENY
}

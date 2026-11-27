package art.arcane.adapt.api.ability;

import org.jetbrains.annotations.ApiStatus;

/** Which moment of an ability use an {@link AbilityContext} describes. */
@ApiStatus.AvailableSince("1.0.0")
public enum AbilityPhase {
  /** Asking whether the player may use the ability; nothing happens and nothing is charged. */
  CHECK,
  /** The ability is firing and its costs are being charged. Cost providers are only asked in this phase. */
  ACTIVATE
}

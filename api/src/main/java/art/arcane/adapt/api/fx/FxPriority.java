package art.arcane.adapt.api.fx;

import org.jetbrains.annotations.ApiStatus;

/**
 * How much of the shared effect budget an effect may use, highest first. When the server is busy or the budget runs
 * low, the lower priorities are thinned and dropped first.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum FxPriority {
  /** Effects that carry gameplay information; their sounds are never skipped. */
  GAMEPLAY,
  /** Hits and other combat feedback. */
  COMBAT,
  /** Charge-ups, level-ups and other one-off cues. */
  TRANSITION,
  /** Trails behind moving things. */
  TRAIL,
  /** Decoration; the first to go. */
  AMBIENT
}

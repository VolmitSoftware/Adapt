package art.arcane.adapt.api.hud;

import org.jetbrains.annotations.ApiStatus;

/**
 * How an ability's state is drawn next to the hotbar of a native client.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum HudGlyph {
  /** Unusable until the cooldown ends: a sweep that empties, then a short ready pulse. */
  COOLDOWN,
  /** Charges lit as pips; a recharge time, when set, sweeps toward the next pip. */
  CHARGES,
  /** An effect running for a time, or open-ended: a draining ring. */
  DURATION,
  /** An on or off state. */
  TOGGLE
}

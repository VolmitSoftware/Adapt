package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/**
 * Category of a mob effect type.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum EffectCategory {
  BENEFICIAL,
  HARMFUL,
  NEUTRAL
}

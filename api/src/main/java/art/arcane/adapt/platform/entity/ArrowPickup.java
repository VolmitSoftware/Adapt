package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/**
 * Who may pick an arrow up once it lands.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum ArrowPickup {
  DISALLOWED,
  ALLOWED,
  /** Only players in creative mode may pick it up. */
  CREATIVE_ONLY
}

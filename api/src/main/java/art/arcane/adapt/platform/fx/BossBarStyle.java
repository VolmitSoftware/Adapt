package art.arcane.adapt.platform.fx;

import org.jetbrains.annotations.ApiStatus;

/** Boss bar style: one solid bar or a bar split into 6, 10, 12 or 20 notches. */
@ApiStatus.AvailableSince("1.0.0")
public enum BossBarStyle {
  SOLID,
  SEGMENTED_6,
  SEGMENTED_10,
  SEGMENTED_12,
  SEGMENTED_20
}

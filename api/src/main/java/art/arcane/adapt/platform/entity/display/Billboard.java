package art.arcane.adapt.platform.entity.display;

import org.jetbrains.annotations.ApiStatus;

/** How a display entity turns to face the viewer's camera. */
@ApiStatus.AvailableSince("1.0.0")
public enum Billboard {
  /** Keeps its own rotation. */
  FIXED,
  /** Turns around the vertical axis only. */
  VERTICAL,
  /** Tilts around the horizontal axis only. */
  HORIZONTAL,
  /** Always faces the camera. */
  CENTER
}

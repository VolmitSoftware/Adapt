package art.arcane.adapt.platform.advancement;

import org.jetbrains.annotations.ApiStatus;

/**
 * The vanilla advancement frame, which sets the icon border and the toast text.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum AdvancementFrame {
  TASK,
  GOAL,
  CHALLENGE
}

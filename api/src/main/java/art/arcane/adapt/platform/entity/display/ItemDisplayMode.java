package art.arcane.adapt.platform.entity.display;

import org.jetbrains.annotations.ApiStatus;

/** Item model transform an item display renders with, matching the item model's display contexts. */
@ApiStatus.AvailableSince("1.0.0")
public enum ItemDisplayMode {
  NONE,
  THIRD_PERSON_LEFT_HAND,
  THIRD_PERSON_RIGHT_HAND,
  FIRST_PERSON_LEFT_HAND,
  FIRST_PERSON_RIGHT_HAND,
  HEAD,
  GUI,
  GROUND,
  FIXED
}

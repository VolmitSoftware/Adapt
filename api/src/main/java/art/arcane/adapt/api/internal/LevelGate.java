package art.arcane.adapt.api.internal;

import org.jetbrains.annotations.ApiStatus;

/** Which extra condition an active-level query applies on top of the ones every query applies. */
@ApiStatus.Internal
public enum LevelGate {
  NONE,
  /** As {@link #NONE}, but a dead player still counts; for death-save adaptations. */
  DEATH,
  INTERACT,
  BLOCK_BREAK,
  BLOCK_PLACE,
  /** Both interact and block break are allowed. */
  INTERACT_BREAK,
  /** PVP or PVE against the target is allowed. */
  DAMAGE
}

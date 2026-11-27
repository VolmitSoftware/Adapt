package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/**
 * Entity pose, named as in Bukkit; vanilla {@code CROUCHING} is {@link #SNEAKING}.
 * A {@code switch} over this enum needs a default branch: minor releases add constants.
 */
@ApiStatus.AvailableSince("1.0.0")
public enum Pose {
  STANDING,
  FALL_FLYING,
  SLEEPING,
  SWIMMING,
  SPIN_ATTACK,
  SNEAKING,
  LONG_JUMPING,
  DYING,
  CROAKING,
  USING_TONGUE,
  SITTING,
  ROARING,
  SNIFFING,
  EMERGING,
  DIGGING,
  SLIDING,
  SHOOTING,
  INHALING
}

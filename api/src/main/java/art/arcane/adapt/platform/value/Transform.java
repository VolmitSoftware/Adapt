package art.arcane.adapt.platform.value;

import org.jetbrains.annotations.ApiStatus;

/**
 * Display entity transformation, same shape as the vanilla and Bukkit transformation.
 *
 * @param translation offset from the entity's position
 * @param leftRotation rotation applied after scaling
 * @param scale scale per axis
 * @param rightRotation rotation applied before scaling
 */
@ApiStatus.AvailableSince("1.0.0")
public record Transform(Vec3 translation, Quat leftRotation, Vec3 scale, Quat rightRotation) {
  /** No offset, no rotation, scale 1. */
  public static final Transform IDENTITY = new Transform(Vec3.ZERO, Quat.IDENTITY, new Vec3(1.0D, 1.0D, 1.0D), Quat.IDENTITY);

  /** This transform with another translation. */
  public Transform withTranslation(Vec3 value) {
    return new Transform(value, leftRotation, scale, rightRotation);
  }

  /** This transform with another scale. */
  public Transform withScale(Vec3 value) {
    return new Transform(translation, leftRotation, value, rightRotation);
  }

  /** This transform with another left rotation. */
  public Transform withLeftRotation(Quat value) {
    return new Transform(translation, value, scale, rightRotation);
  }
}

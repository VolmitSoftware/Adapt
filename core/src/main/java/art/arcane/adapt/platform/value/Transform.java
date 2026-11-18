package art.arcane.adapt.platform.value;

/** Display entity transformation, same shape as the vanilla and Bukkit transformation. */
public record Transform(Vec3 translation, Quat leftRotation, Vec3 scale, Quat rightRotation) {
  public static final Transform IDENTITY = new Transform(Vec3.ZERO, Quat.IDENTITY, new Vec3(1.0D, 1.0D, 1.0D), Quat.IDENTITY);

  public Transform withTranslation(Vec3 value) {
    return new Transform(value, leftRotation, scale, rightRotation);
  }

  public Transform withScale(Vec3 value) {
    return new Transform(translation, leftRotation, value, rightRotation);
  }

  public Transform withLeftRotation(Quat value) {
    return new Transform(translation, value, scale, rightRotation);
  }
}

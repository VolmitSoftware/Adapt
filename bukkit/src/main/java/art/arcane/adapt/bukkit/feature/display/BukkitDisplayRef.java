package art.arcane.adapt.bukkit.feature.display;

import art.arcane.adapt.bukkit.platform.entity.BukkitEntityRef;
import art.arcane.adapt.platform.entity.display.Billboard;
import art.arcane.adapt.platform.entity.display.Brightness;
import art.arcane.adapt.platform.entity.display.DisplayRef;
import art.arcane.adapt.platform.value.Quat;
import art.arcane.adapt.platform.value.Rgb;
import art.arcane.adapt.platform.value.Transform;
import art.arcane.adapt.platform.value.Vec3;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Bukkit display entity behind {@link DisplayRef}. */
class BukkitDisplayRef extends BukkitEntityRef implements DisplayRef {
  BukkitDisplayRef(Display display) {
    super(display);
  }

  Display display() {
    return (Display) handle();
  }

  @Override
  public Transform transform() {
    Transformation transformation = display().getTransformation();
    return new Transform(vec(transformation.getTranslation()), quat(transformation.getLeftRotation()),
        vec(transformation.getScale()), quat(transformation.getRightRotation()));
  }

  @Override
  public void setTransform(Transform transform) {
    display().setTransformation(transformation(transform));
  }

  @Override
  public Billboard billboard() {
    return switch (display().getBillboard()) {
      case FIXED -> Billboard.FIXED;
      case VERTICAL -> Billboard.VERTICAL;
      case HORIZONTAL -> Billboard.HORIZONTAL;
      case CENTER -> Billboard.CENTER;
    };
  }

  @Override
  public void setBillboard(Billboard billboard) {
    display().setBillboard(switch (billboard) {
      case FIXED -> Display.Billboard.FIXED;
      case VERTICAL -> Display.Billboard.VERTICAL;
      case HORIZONTAL -> Display.Billboard.HORIZONTAL;
      case CENTER -> Display.Billboard.CENTER;
    });
  }

  @Override
  public Brightness brightness() {
    Display.Brightness brightness = display().getBrightness();
    return brightness == null ? null : new Brightness(brightness.getBlockLight(), brightness.getSkyLight());
  }

  @Override
  public void setBrightness(Brightness brightness) {
    display().setBrightness(brightness == null ? null : new Display.Brightness(brightness.block(), brightness.sky()));
  }

  @Override
  public float viewRange() {
    return display().getViewRange();
  }

  @Override
  public void setViewRange(float range) {
    display().setViewRange(range);
  }

  @Override
  public float shadowRadius() {
    return display().getShadowRadius();
  }

  @Override
  public void setShadowRadius(float radius) {
    display().setShadowRadius(radius);
  }

  @Override
  public Rgb glowColorOverride() {
    Color color = display().getGlowColorOverride();
    return color == null ? null : new Rgb(color.getRed(), color.getGreen(), color.getBlue());
  }

  @Override
  public void setGlowColorOverride(Rgb color) {
    display().setGlowColorOverride(color == null ? null : Color.fromRGB(color.red(), color.green(), color.blue()));
  }

  @Override
  public int interpolationDuration() {
    return display().getInterpolationDuration();
  }

  @Override
  public void setInterpolationDuration(int ticks) {
    display().setInterpolationDuration(ticks);
  }

  @Override
  public int interpolationDelay() {
    return display().getInterpolationDelay();
  }

  @Override
  public void setInterpolationDelay(int ticks) {
    display().setInterpolationDelay(ticks);
  }

  @Override
  public int teleportDuration() {
    return display().getTeleportDuration();
  }

  @Override
  public void setTeleportDuration(int ticks) {
    display().setTeleportDuration(ticks);
  }

  @Override
  public boolean isVisibleByDefault() {
    return display().isVisibleByDefault();
  }

  @Override
  public void setVisibleByDefault(boolean visible) {
    display().setVisibleByDefault(visible);
  }

  static Transformation transformation(Transform transform) {
    return new Transformation(vector(transform.translation()), quaternion(transform.leftRotation()),
        vector(transform.scale()), quaternion(transform.rightRotation()));
  }

  private static Vec3 vec(Vector3f vector) {
    return new Vec3(vector.x(), vector.y(), vector.z());
  }

  private static Quat quat(Quaternionf quaternion) {
    return new Quat(quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w());
  }

  private static Vector3f vector(Vec3 vec) {
    return new Vector3f((float) vec.x(), (float) vec.y(), (float) vec.z());
  }

  private static Quaternionf quaternion(Quat quat) {
    return new Quaternionf(quat.x(), quat.y(), quat.z(), quat.w());
  }
}

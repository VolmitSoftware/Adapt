package art.arcane.adapt.platform.entity;

import org.jetbrains.annotations.ApiStatus;

/**
 * Armor stand body options applied before the stand enters the world. Start from {@link #bare()}; each {@code with}
 * method returns a new style.
 */
@ApiStatus.AvailableSince("1.0.0")
public final class ArmorStandStyle {
  private static final ArmorStandStyle BARE = new ArmorStandStyle(false, false, false, false, false);

  private final boolean small;
  private final boolean basePlate;
  private final boolean marker;
  private final boolean collidable;
  private final boolean arms;

  private ArmorStandStyle(boolean small, boolean basePlate, boolean marker, boolean collidable, boolean arms) {
    this.small = small;
    this.basePlate = basePlate;
    this.marker = marker;
    this.collidable = collidable;
    this.arms = arms;
  }

  /** A full-size stand with no base plate, no arms and no collision, not a marker. */
  public static ArmorStandStyle bare() {
    return BARE;
  }

  /** This style at half size or full size. */
  public ArmorStandStyle withSmall(boolean newSmall) {
    return new ArmorStandStyle(newSmall, basePlate, marker, collidable, arms);
  }

  /** This style with or without the stone base plate. */
  public ArmorStandStyle withBasePlate(boolean newBasePlate) {
    return new ArmorStandStyle(small, newBasePlate, marker, collidable, arms);
  }

  /** This style as a marker stand or not. */
  public ArmorStandStyle withMarker(boolean newMarker) {
    return new ArmorStandStyle(small, basePlate, newMarker, collidable, arms);
  }

  /** This style with or without collision. */
  public ArmorStandStyle withCollidable(boolean newCollidable) {
    return new ArmorStandStyle(small, basePlate, marker, newCollidable, arms);
  }

  /** This style with or without arms. */
  public ArmorStandStyle withArms(boolean newArms) {
    return new ArmorStandStyle(small, basePlate, marker, collidable, newArms);
  }

  /** Half-size stand. */
  public boolean small() {
    return small;
  }

  /** Shows the stone base plate. */
  public boolean basePlate() {
    return basePlate;
  }

  /** Marker stand: a zero-size hitbox that cannot be hit or collided with. */
  public boolean marker() {
    return marker;
  }

  /** Other entities push against it. */
  public boolean collidable() {
    return collidable;
  }

  /** Shows the arms. */
  public boolean arms() {
    return arms;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof ArmorStandStyle style && small == style.small && basePlate == style.basePlate
        && marker == style.marker && collidable == style.collidable && arms == style.arms;
  }

  @Override
  public int hashCode() {
    return (small ? 16 : 0) | (basePlate ? 8 : 0) | (marker ? 4 : 0) | (collidable ? 2 : 0) | (arms ? 1 : 0);
  }

  @Override
  public String toString() {
    return "ArmorStandStyle[small=" + small + ", basePlate=" + basePlate + ", marker=" + marker + ", collidable="
        + collidable + ", arms=" + arms + "]";
  }
}

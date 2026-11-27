package art.arcane.adapt.platform.value;

import org.jetbrains.annotations.ApiStatus;

/** Axis-aligned bounding box. Constructor normalises min and max. */
@ApiStatus.AvailableSince("1.0.0")
public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
  public Box {
    if (minX > maxX) {
      double swap = minX;
      minX = maxX;
      maxX = swap;
    }
    if (minY > maxY) {
      double swap = minY;
      minY = maxY;
      maxY = swap;
    }
    if (minZ > maxZ) {
      double swap = minZ;
      minZ = maxZ;
      maxZ = swap;
    }
  }

  /** A box reaching the given radius from {@code center} on each axis. */
  public static Box around(Vec3 center, double radiusX, double radiusY, double radiusZ) {
    return new Box(center.x() - radiusX, center.y() - radiusY, center.z() - radiusZ,
        center.x() + radiusX, center.y() + radiusY, center.z() + radiusZ);
  }

  /** This box moved by the offset. */
  public Box shift(double dx, double dy, double dz) {
    return new Box(minX + dx, minY + dy, minZ + dz, maxX + dx, maxY + dy, maxZ + dz);
  }

  /** This box grown by {@code amount} on every side; a negative amount shrinks it. */
  public Box expand(double amount) {
    return new Box(minX - amount, minY - amount, minZ - amount, maxX + amount, maxY + amount, maxZ + amount);
  }

  /** True when the boxes share volume; boxes that only touch at a face do not overlap. */
  public boolean overlaps(Box other) {
    return minX < other.maxX && maxX > other.minX
        && minY < other.maxY && maxY > other.minY
        && minZ < other.maxZ && maxZ > other.minZ;
  }

  /** True when the point is inside or on the surface of the box. */
  public boolean contains(Vec3 point) {
    return point.x() >= minX && point.x() <= maxX
        && point.y() >= minY && point.y() <= maxY
        && point.z() >= minZ && point.z() <= maxZ;
  }

  /** Size along X. */
  public double widthX() {
    return maxX - minX;
  }

  /** Size along Y. */
  public double height() {
    return maxY - minY;
  }

  /** Size along Z. */
  public double widthZ() {
    return maxZ - minZ;
  }

  /** The centre point. */
  public Vec3 center() {
    return new Vec3((minX + maxX) * 0.5D, (minY + maxY) * 0.5D, (minZ + maxZ) * 0.5D);
  }
}

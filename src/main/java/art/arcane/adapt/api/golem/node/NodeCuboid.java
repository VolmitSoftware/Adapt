package art.arcane.adapt.api.golem.node;

public record NodeCuboid(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
  private static final NodeEdits.Axis[] AXES = NodeEdits.Axis.values();
  private static final Limit FITS = new Limit(Verdict.FITS, NodeEdits.Axis.X, false, 0);

  public static NodeCuboid symmetric(int radiusX, int radiusY, int radiusZ) {
    return new NodeCuboid(-radiusX, -radiusY, -radiusZ, radiusX, radiusY, radiusZ);
  }

  public int spanX() {
    return maxX - minX + 1;
  }

  public int spanY() {
    return maxY - minY + 1;
  }

  public int spanZ() {
    return maxZ - minZ + 1;
  }

  public int volume() {
    return spanX() * spanY() * spanZ();
  }

  public boolean contains(int offsetX, int offsetY, int offsetZ) {
    return offsetX >= minX && offsetX <= maxX && offsetY >= minY && offsetY <= maxY && offsetZ >= minZ && offsetZ <= maxZ;
  }

  public int min(NodeEdits.Axis axis) {
    return switch (axis) {
      case X -> minX;
      case Y -> minY;
      case Z -> minZ;
    };
  }

  public int max(NodeEdits.Axis axis) {
    return switch (axis) {
      case X -> maxX;
      case Y -> maxY;
      case Z -> maxZ;
    };
  }

  public NodeCuboid withAxis(NodeEdits.Axis axis, int min, int max) {
    return switch (axis) {
      case X -> new NodeCuboid(min, minY, minZ, max, maxY, maxZ);
      case Y -> new NodeCuboid(minX, min, minZ, maxX, max, maxZ);
      case Z -> new NodeCuboid(minX, minY, min, maxX, maxY, max);
    };
  }

  public int horizontalReach() {
    return Math.max(Math.max(-minX, maxX), Math.max(-minZ, maxZ));
  }

  public NodeCuboid clamp(int maxRadius) {
    return new NodeCuboid(Math.clamp(minX, -maxRadius, 0), Math.clamp(minY, -maxRadius, 0), Math.clamp(minZ, -maxRadius, 0),
        Math.clamp(maxX, 0, maxRadius), Math.clamp(maxY, 0, maxRadius), Math.clamp(maxZ, 0, maxRadius));
  }

  public Limit check(int maxRadius) {
    for (NodeEdits.Axis axis : AXES) {
      if (min(axis) > 0 || max(axis) < 0) {
        return new Limit(Verdict.MISSING_CENTER, axis, max(axis) < 0, 0);
      }
    }
    Limit farthest = FITS;
    int reach = maxRadius;
    for (NodeEdits.Axis axis : AXES) {
      if (-min(axis) > reach) {
        reach = -min(axis);
        farthest = new Limit(Verdict.TOO_FAR, axis, true, reach);
      }
      if (max(axis) > reach) {
        reach = max(axis);
        farthest = new Limit(Verdict.TOO_FAR, axis, false, reach);
      }
    }
    return farthest;
  }

  public enum Verdict {
    FITS,
    MISSING_CENTER,
    TOO_FAR
  }

  public record Limit(Verdict verdict, NodeEdits.Axis axis, boolean negative, int distance) {
    public boolean fits() {
      return verdict == Verdict.FITS;
    }

    public String face() {
      return (negative ? "-" : "+") + axis.name();
    }
  }
}

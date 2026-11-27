package art.arcane.adapt.content.golem;

import art.arcane.adapt.platform.value.Loc;

import java.util.Objects;
import java.util.UUID;

public final class GolemAnchor {
  private UUID world;
  private int x;
  private int y;
  private int z;

  private GolemAnchor() {
  }

  public static GolemAnchor of(UUID world, int x, int y, int z) {
    GolemAnchor anchor = new GolemAnchor();
    anchor.world = Objects.requireNonNull(world);
    anchor.x = x;
    anchor.y = y;
    anchor.z = z;
    return anchor;
  }

  public static GolemAnchor at(Loc location) {
    return of(location.world().uid(), location.blockX(), location.blockY(), location.blockZ());
  }

  public UUID world() {
    return world;
  }

  public int x() {
    return x;
  }

  public int y() {
    return y;
  }

  public int z() {
    return z;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof GolemAnchor anchor && x == anchor.x && y == anchor.y && z == anchor.z
        && Objects.equals(world, anchor.world);
  }

  @Override
  public int hashCode() {
    return Objects.hash(world, x, y, z);
  }

  @Override
  public String toString() {
    return world + ":" + x + "," + y + "," + z;
  }
}

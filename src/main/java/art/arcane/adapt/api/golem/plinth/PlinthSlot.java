package art.arcane.adapt.api.golem.plinth;

import java.util.List;

public enum PlinthSlot {
  CENTER(0, 0),
  NORTH(0, -2),
  EAST(2, 0),
  SOUTH(0, 2),
  WEST(-2, 0);

  public static final List<PlinthSlot> PEDESTALS = List.of(NORTH, EAST, SOUTH, WEST);

  private final int dx;
  private final int dz;

  PlinthSlot(int dx, int dz) {
    this.dx = dx;
    this.dz = dz;
  }

  public int dx() {
    return dx;
  }

  public int dz() {
    return dz;
  }
}

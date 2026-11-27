package art.arcane.adapt.platform.value;

import org.jetbrains.annotations.ApiStatus;

/** Block faces with the same names and offsets as Bukkit's: the six axis faces, the horizontal diagonals and {@link #SELF}. */
@ApiStatus.AvailableSince("1.0.0")
public enum BlockFace {
  DOWN(0, -1, 0),
  UP(0, 1, 0),
  NORTH(0, 0, -1),
  SOUTH(0, 0, 1),
  WEST(-1, 0, 0),
  EAST(1, 0, 0),
  NORTH_EAST(1, 0, -1),
  NORTH_WEST(-1, 0, -1),
  SOUTH_EAST(1, 0, 1),
  SOUTH_WEST(-1, 0, 1),
  /** Two blocks west, one north. This and the seven faces after it are the in-between points of a 16-point compass. */
  WEST_NORTH_WEST(-2, 0, -1),
  NORTH_NORTH_WEST(-1, 0, -2),
  NORTH_NORTH_EAST(1, 0, -2),
  EAST_NORTH_EAST(2, 0, -1),
  EAST_SOUTH_EAST(2, 0, 1),
  SOUTH_SOUTH_EAST(1, 0, 2),
  SOUTH_SOUTH_WEST(-1, 0, 2),
  WEST_SOUTH_WEST(-2, 0, 1),
  /** No offset: the block itself. */
  SELF(0, 0, 0);

  private final int dx;
  private final int dy;
  private final int dz;

  BlockFace(int dx, int dy, int dz) {
    this.dx = dx;
    this.dy = dy;
    this.dz = dz;
  }

  /** Block offset along X, from -2 to 2; east is positive. */
  public int dx() {
    return dx;
  }

  /** Block offset along Y: 1 for {@link #UP}, -1 for {@link #DOWN}, else 0. */
  public int dy() {
    return dy;
  }

  /** Block offset along Z, from -2 to 2; south is positive. */
  public int dz() {
    return dz;
  }

  /** The face pointing the other way; {@link #SELF} stays {@link #SELF}. */
  public BlockFace opposite() {
    return switch (this) {
      case DOWN -> UP;
      case UP -> DOWN;
      case NORTH -> SOUTH;
      case SOUTH -> NORTH;
      case WEST -> EAST;
      case EAST -> WEST;
      case NORTH_EAST -> SOUTH_WEST;
      case NORTH_WEST -> SOUTH_EAST;
      case SOUTH_EAST -> NORTH_WEST;
      case SOUTH_WEST -> NORTH_EAST;
      case WEST_NORTH_WEST -> EAST_SOUTH_EAST;
      case NORTH_NORTH_WEST -> SOUTH_SOUTH_EAST;
      case NORTH_NORTH_EAST -> SOUTH_SOUTH_WEST;
      case EAST_NORTH_EAST -> WEST_SOUTH_WEST;
      case EAST_SOUTH_EAST -> WEST_NORTH_WEST;
      case SOUTH_SOUTH_EAST -> NORTH_NORTH_WEST;
      case SOUTH_SOUTH_WEST -> NORTH_NORTH_EAST;
      case WEST_SOUTH_WEST -> EAST_NORTH_EAST;
      case SELF -> SELF;
    };
  }

  /** True for every face except {@link #UP}, {@link #DOWN} and {@link #SELF}. */
  public boolean isHorizontal() {
    return dy == 0 && this != SELF;
  }
}

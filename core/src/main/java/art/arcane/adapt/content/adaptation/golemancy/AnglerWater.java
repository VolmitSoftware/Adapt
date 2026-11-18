package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.node.NodeArea;
import art.arcane.adapt.api.golem.node.NodeKey;
import art.arcane.adapt.platform.value.BlockFace;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;

import java.util.Set;

final class AnglerWater {
  static final int MAX_SEATS = 5;
  static final Id WATER = Id.minecraft("water");

  private static final int HALF_WIDTH = 2;
  private static final int FORWARD = 3;
  private static final int LOWEST_LAYER = -1;
  private static final int HIGHEST_LAYER = 2;
  private static final int SURFACE_REACH = 8;
  private static final int[] SEAT_OFFSETS = {0, 1, -1, 2, -2};
  private static final double RAIN_MIN_TEMPERATURE = 0.15D;
  private static final double RAIN_MAX_TEMPERATURE = 1.5D;

  private static final Set<Id> AIR_CELLS = Set.of(Id.minecraft("air"), Id.minecraft("cave_air"), Id.minecraft("void_air"),
      Id.minecraft("lily_pad"));
  private static final Set<Id> WATER_CELLS = Set.of(Id.minecraft("seagrass"), Id.minecraft("tall_seagrass"), Id.minecraft("kelp"),
      Id.minecraft("kelp_plant"), Id.minecraft("bubble_column"));

  private AnglerWater() {
  }

  static int[] center(NodeKey key) {
    BlockFace face = key.face();
    return new int[]{key.x() + face.dx() * FORWARD, key.y(), key.z() + face.dz() * FORWARD};
  }

  static int[] seat(NodeKey key, int index) {
    int[] near = NodeArea.center(key);
    return lateral(key, near, offset(index));
  }

  static int[] bobber(NodeKey key, Survey survey, int index) {
    return lateral(key, new int[]{survey.x(), survey.surface(), survey.z()}, offset(index));
  }

  static Survey survey(View view, NodeKey key) {
    int[] center = center(key);
    boolean open = key.face().dy() == 0 && open(view, center[0], center[1], center[2]);
    int surface = open ? surface(view, center[0], center[1], center[2]) : center[1];
    return new Survey(open, center[0], center[1], center[2], surface, false);
  }

  static boolean open(View view, int x, int y, int z) {
    Cell previous = null;
    for (int dy = LOWEST_LAYER; dy <= HIGHEST_LAYER; dy++) {
      Cell layer = layer(view, x, y + dy, z);
      if (layer == Cell.OTHER || (layer == Cell.AIR && previous == null) || (layer == Cell.WATER && previous == Cell.AIR)) {
        return false;
      }
      previous = layer;
    }
    return true;
  }

  static int surface(View view, int x, int y, int z) {
    int top = y + LOWEST_LAYER;
    int limit = y + HIGHEST_LAYER + SURFACE_REACH;
    while (top < limit && view.cell(x, top + 1, z) == Cell.WATER) {
      top++;
    }
    return top;
  }

  static Cell cell(Id type, BlockStateRef data) {
    if (AIR_CELLS.contains(type)) {
      return Cell.AIR;
    }
    if (WATER_CELLS.contains(type)) {
      return Cell.WATER;
    }
    if (WATER.equals(type)) {
      return data != null && "0".equals(data.property("level")) ? Cell.WATER : Cell.OTHER;
    }
    return Cell.OTHER;
  }

  static boolean raining(boolean storm, int highestY, int surface, double temperature) {
    return storm && highestY <= surface && temperature >= RAIN_MIN_TEMPERATURE && temperature < RAIN_MAX_TEMPERATURE;
  }

  private static Cell layer(View view, int x, int y, int z) {
    Cell found = null;
    for (int dx = -HALF_WIDTH; dx <= HALF_WIDTH; dx++) {
      for (int dz = -HALF_WIDTH; dz <= HALF_WIDTH; dz++) {
        Cell cell = view.cell(x + dx, y, z + dz);
        if (cell == Cell.OTHER || (found != null && found != cell)) {
          return Cell.OTHER;
        }
        found = cell;
      }
    }
    return found;
  }

  private static int[] lateral(NodeKey key, int[] at, int offset) {
    return key.face().dx() != 0
        ? new int[]{at[0], at[1], at[2] + offset}
        : new int[]{at[0] + offset, at[1], at[2]};
  }

  private static int offset(int index) {
    return SEAT_OFFSETS[Math.clamp(index, 0, MAX_SEATS - 1)];
  }

  enum Cell {
    WATER,
    AIR,
    OTHER
  }

  @FunctionalInterface
  interface View {
    Cell cell(int x, int y, int z);
  }

  record Survey(boolean open, int x, int y, int z, int surface, boolean raining) {
    Survey raining(boolean next) {
      return new Survey(open, x, y, z, surface, next);
    }
  }
}

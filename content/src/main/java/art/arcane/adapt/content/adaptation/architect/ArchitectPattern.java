package art.arcane.adapt.content.adaptation.architect;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockRef;
import art.arcane.adapt.platform.world.BlockStateRef;
import art.arcane.adapt.platform.world.WorldRef;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A copied build: block types and their states in a box, never items. Copying skips anything with contents or a
 * single-item cost that would not hold (containers, spawners, signs, banners, heads, double blocks, multi-face
 * blocks) and resets states that would give something for free (double slabs, stacked candles, waterlogging, filled
 * composters, charged anchors). Stamping turns the copy in quarter turns and mirrors it, states included.
 */
public final class ArchitectPattern {
  private static final List<String> YAW = List.of("north", "east", "south", "west");
  private static final Set<String> KEPT = Set.of("spawner", "trial_spawner", "vault", "lectern", "jukebox",
      "decorated_pot", "chiseled_bookshelf", "beehive", "bee_nest", "campfire", "soul_campfire", "structure_block",
      "jigsaw", "end_portal", "end_portal_frame", "end_gateway", "nether_portal", "bedrock", "barrier", "light",
      "moving_piston", "piston_head", "glow_lichen", "sculk_vein", "resin_clump", "creaking_heart", "reinforced_deepslate",
      "budding_amethyst", "frogspawn", "big_dripleaf_stem");
  private static final Map<String, String> SINGLE = Map.of("candles", "1", "pickles", "1", "eggs", "1",
      "layers", "1", "flower_amount", "1", "segment_amount", "1", "charges", "0", "waterlogged", "false",
      "can_summon", "false", "lit", "false");

  int width;
  int height;
  int depth;
  List<Entry> palette = new ArrayList<>();
  int[] cells = new int[0];

  /** One palette state: a block type and its properties. */
  static final class Entry {
    String type;
    Map<String, String> properties = new LinkedHashMap<>();
  }

  /** A block to place: where, and the turned state. */
  public record Placement(BlockRef block, BlockStateRef state) {
  }

  /** Copies the box between two corners in loaded chunks, or null when nothing in it can be copied. */
  public static @Nullable ArchitectPattern copy(WorldRef world, int ax, int ay, int az, int bx, int by, int bz) {
    ArchitectPattern pattern = new ArchitectPattern();
    int minX = Math.min(ax, bx);
    int minY = Math.min(ay, by);
    int minZ = Math.min(az, bz);
    pattern.width = Math.abs(ax - bx) + 1;
    pattern.height = Math.abs(ay - by) + 1;
    pattern.depth = Math.abs(az - bz) + 1;
    pattern.cells = new int[pattern.width * pattern.height * pattern.depth];
    Map<String, Integer> indices = new LinkedHashMap<>();
    int copied = 0;
    for (int y = 0; y < pattern.height; y++) {
      for (int z = 0; z < pattern.depth; z++) {
        for (int x = 0; x < pattern.width; x++) {
          int wx = minX + x;
          int wy = minY + y;
          int wz = minZ + z;
          if (wy < world.minHeight() || wy >= world.maxHeight() || !world.isChunkLoaded(wx >> 4, wz >> 4)) {
            continue;
          }
          BlockStateRef state = copyable(world.blockAt(wx, wy, wz));
          if (state == null) {
            continue;
          }
          String key = state.asString() + state.properties();
          Integer index = indices.get(key);
          if (index == null) {
            Entry entry = new Entry();
            entry.type = state.type().toString();
            entry.properties.putAll(state.properties());
            pattern.palette.add(entry);
            index = pattern.palette.size();
            indices.put(key, index);
          }
          pattern.cells[pattern.index(x, y, z)] = index;
          copied++;
        }
      }
    }
    return copied == 0 ? null : pattern;
  }

  /** The state a stamp may lay for this block, reset where it would give something for free; null when it cannot be copied. */
  static @Nullable BlockStateRef copyable(BlockRef block) {
    if (block.isAir() || block.isLiquid() || block.container() != null) {
      return null;
    }
    BlockStateRef state = block.state();
    Id type = state.type();
    String path = type.path();
    if (KEPT.contains(path) || path.endsWith("_sign") || path.endsWith("_banner") || path.endsWith("_head")
        || path.endsWith("_skull") || path.startsWith("suspicious_") || path.contains("command_block")
        || path.contains("shulker_box") || !type.equals(state.placementItem())
        || state.property("part") != null || "upper".equals(state.property("half"))
        || "lower".equals(state.property("half"))) {
      return null;
    }
    return single(state);
  }

  /** The state as one item lays it: single slab, one candle, dry, unlit, empty composter, uncharged anchor. */
  static BlockStateRef single(BlockStateRef state) {
    BlockStateRef reset = state;
    String path = state.type().path();
    if ("double".equals(state.property("type")) && path.endsWith("_slab")) {
      reset = reset.with("type", "bottom");
    }
    if (path.equals("composter") && state.property("level") != null) {
      reset = reset.with("level", "0");
    }
    for (Map.Entry<String, String> single : SINGLE.entrySet()) {
      if (state.property(single.getKey()) != null) {
        reset = reset.with(single.getKey(), single.getValue());
      }
    }
    return reset;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public int depth() {
    return depth;
  }

  /** Blocks the copy lays. */
  public int count() {
    int count = 0;
    for (int cell : cells) {
      count += cell > 0 ? 1 : 0;
    }
    return count;
  }

  /** Equal for equal copies, so a copy read back from the item is recognised. */
  public int fingerprint() {
    int hash = Arrays.hashCode(cells);
    hash = (31 * hash) + width;
    hash = (31 * hash) + height;
    hash = (31 * hash) + depth;
    for (Entry entry : palette) {
      hash = (31 * hash) + String.valueOf(entry.type).hashCode();
      hash = (31 * hash) + (entry.properties == null ? 0 : entry.properties.hashCode());
    }
    return hash;
  }

  /** Whether the stored data is a usable copy. */
  public boolean isValid() {
    if (width <= 0 || height <= 0 || depth <= 0 || palette == null || cells == null
        || cells.length != width * height * depth) {
      return false;
    }
    for (int cell : cells) {
      if (cell < 0 || cell > palette.size()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Where each copied block goes when stamped with its bottom centered on {@code anchor}, mirrored east to west first
   * when {@code mirror}, then turned {@code turns} quarter turns clockwise seen from above. Bottom layer first.
   */
  public List<Placement> placements(BlockRef anchor, int turns, boolean mirror) {
    int quarter = Math.floorMod(turns, 4);
    int footprintX = quarter % 2 == 0 ? width : depth;
    int footprintZ = quarter % 2 == 0 ? depth : width;
    int originX = anchor.x() - (footprintX / 2);
    int originZ = anchor.z() - (footprintZ / 2);
    List<BlockStateRef> states = new ArrayList<>(palette.size());
    for (Entry entry : palette) {
      BlockStateRef state = state(entry);
      states.add(state == null ? null : turn(state, quarter, mirror));
    }
    List<Placement> placements = new ArrayList<>(count());
    WorldRef world = anchor.world();
    for (int y = 0; y < height; y++) {
      for (int z = 0; z < depth; z++) {
        for (int x = 0; x < width; x++) {
          int cell = cells[index(x, y, z)];
          if (cell <= 0 || states.get(cell - 1) == null) {
            continue;
          }
          int localX = mirror ? width - 1 - x : x;
          int localZ = z;
          int sizeX = width;
          int sizeZ = depth;
          for (int step = 0; step < quarter; step++) {
            int turned = sizeZ - 1 - localZ;
            localZ = localX;
            localX = turned;
            int swap = sizeX;
            sizeX = sizeZ;
            sizeZ = swap;
          }
          placements.add(new Placement(world.blockAt(originX + localX, anchor.y() + y, originZ + localZ),
              states.get(cell - 1)));
        }
      }
    }
    return placements;
  }

  /** The state turned like its block: mirrored east to west, then turned clockwise. */
  static BlockStateRef turn(BlockStateRef state, int quarterTurns, boolean mirror) {
    BlockStateRef turned = mirror ? mirror(state) : state;
    for (int step = 0; step < Math.floorMod(quarterTurns, 4); step++) {
      turned = clockwise(turned);
    }
    return turned;
  }

  private static BlockStateRef clockwise(BlockStateRef state) {
    BlockStateRef turned = state;
    String facing = state.property("facing");
    if (facing != null && YAW.contains(facing)) {
      turned = turned.with("facing", YAW.get((YAW.indexOf(facing) + 1) % 4));
    }
    String axis = state.property("axis");
    if ("x".equals(axis) || "z".equals(axis)) {
      turned = turned.with("axis", "x".equals(axis) ? "z" : "x");
    }
    String rotation = state.property("rotation");
    if (rotation != null) {
      turned = turned.with("rotation", Integer.toString((parse(rotation) + 4) % 16));
    }
    if (hasSides(state)) {
      for (int side = 0; side < 4; side++) {
        turned = turned.with(YAW.get((side + 1) % 4), state.property(YAW.get(side)));
      }
    }
    String shape = state.property("shape");
    if (shape != null && !state.type().path().endsWith("_stairs")) {
      turned = turned.with("shape", railClockwise(shape));
    }
    return turned;
  }

  private static BlockStateRef mirror(BlockStateRef state) {
    BlockStateRef mirrored = state;
    String facing = state.property("facing");
    if ("east".equals(facing) || "west".equals(facing)) {
      mirrored = mirrored.with("facing", "east".equals(facing) ? "west" : "east");
    }
    String rotation = state.property("rotation");
    if (rotation != null) {
      mirrored = mirrored.with("rotation", Integer.toString((16 - parse(rotation)) % 16));
    }
    if (hasSides(state)) {
      mirrored = mirrored.with("east", state.property("west")).with("west", state.property("east"));
    }
    String shape = state.property("shape");
    if (shape != null) {
      mirrored = mirrored.with("shape", state.type().path().endsWith("_stairs")
          ? swapSides(shape) : shape.replace("east", "#").replace("west", "east").replace("#", "west"));
    }
    String hinge = state.property("hinge");
    if (hinge != null) {
      mirrored = mirrored.with("hinge", "left".equals(hinge) ? "right" : "left");
    }
    return mirrored;
  }

  private static boolean hasSides(BlockStateRef state) {
    for (String side : YAW) {
      if (state.property(side) == null) {
        return false;
      }
    }
    return true;
  }

  private static String swapSides(String shape) {
    return shape.replace("left", "#").replace("right", "left").replace("#", "right");
  }

  /** Rail shapes turned clockwise: north to east, east to south. */
  static String railClockwise(String shape) {
    StringBuilder turned = new StringBuilder();
    for (String part : shape.split("_")) {
      if (turned.length() > 0) {
        turned.append('_');
      }
      int yaw = YAW.indexOf(part);
      turned.append(yaw < 0 ? part : YAW.get((yaw + 1) % 4));
    }
    String result = turned.toString();
    return switch (result) {
      case "east_south" -> "south_east";
      case "west_south" -> "south_west";
      case "east_north" -> "north_east";
      case "west_north" -> "north_west";
      case "south_north" -> "north_south";
      default -> result;
    };
  }

  private static int parse(String value) {
    try {
      return Math.floorMod(Integer.parseInt(value), 16);
    } catch (NumberFormatException invalid) {
      return 0;
    }
  }

  private static @Nullable BlockStateRef state(Entry entry) {
    Id type = entry.type == null ? null : Id.tryParse(entry.type);
    if (type == null) {
      return null;
    }
    BlockStateRef state = AdaptApi.platform().host().items().blockState(type);
    if (state == null || state.isAir() || !type.equals(state.placementItem())) {
      return null;
    }
    if (entry.properties != null) {
      for (Map.Entry<String, String> property : entry.properties.entrySet()) {
        state = state.with(property.getKey(), property.getValue());
      }
    }
    return single(state);
  }

  private int index(int x, int y, int z) {
    return ((y * depth) + z) * width + x;
  }
}

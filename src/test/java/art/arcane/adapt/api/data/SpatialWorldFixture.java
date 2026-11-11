package art.arcane.adapt.api.data;

import art.arcane.adapt.api.data.unit.Earnings;
import art.arcane.adapt.api.data.unit.PlacementStamp;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeCuboid;
import art.arcane.adapt.api.golem.node.NodeFaces;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.RedstoneMode;
import art.arcane.adapt.api.golem.plinth.PlinthSlotItem;
import art.arcane.adapt.content.matter.BrewingStandOwner;
import org.bukkit.DyeColor;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * The operations behind the golden Spatial world in src/test/resources/art/arcane/adapt/api/data/spatial/world.
 * Adapt 2.2.7 with the Spatial 22.11.2 jar it shipped applied exactly these operations and saved one file per region.
 * A null value in an operation is a removal. The world also holds scaffold values, a slice later releases dropped:
 * Scaffold stands in for the removed value class so the operation indexes still match the files.
 */
final class SpatialWorldFixture {
  static final int MIN_HEIGHT = -64;
  static final int MAX_HEIGHT = 320;
  static final List<Class<?>> WRITTEN_TYPES = List.of(
      Earnings.class, PlacementStamp.class, BrewingStandOwner.class,
      Scaffold.class, PlinthSlotItem.class, NodeFaces.class);
  static final List<Class<?>> TYPES = List.of(
      Earnings.class, PlacementStamp.class, BrewingStandOwner.class, PlinthSlotItem.class, NodeFaces.class);
  static final int[] EDGES = {-513, -512, -1, 0, 31, 32, 511, 512};
  static final int[] HEIGHTS = {-64, -49, -48, -1, 0, 15, 16, 319};
  static final int[] PALETTE_SIZES = {1, 2, 3, 4, 5, 8, 9, 16, 17, 32, 33, 64, 65, 128, 129, 256, 257, 513};
  static final Region VALUES = new Region(3, 3);
  static final Region PALETTE = new Region(-3, 2);
  static final Region REMOVED = new Region(2, -3);
  static final List<Region> EDGE_REGIONS = List.of(
      new Region(-2, -2), new Region(-1, -1), new Region(0, 0), new Region(1, 1),
      new Region(-1, 0), new Region(0, -1), new Region(-2, 1), new Region(1, -2));
  static final List<Region> REGIONS = regions();
  static final List<String> SECTIONS = List.of("six", "cleared", "empty");

  private static final NodeType[] NODE_TYPES = NodeType.values();
  private static final RedstoneMode[] REDSTONE = RedstoneMode.values();
  private static final DyeColor[] COLORS = DyeColor.values();
  private static final BlockFace[] FACES = {
      BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
  private static final int[][] EDGE_PAIRS = {
      {-513, -513}, {-512, -512}, {-1, -1}, {0, 0}, {31, 31}, {32, 32}, {511, 511}, {512, 512},
      {-1, 0}, {0, -1}, {-513, 512}, {512, -513}, {31, -1}, {-1, 31}, {-512, 511}, {511, -512}};

  record Region(int x, int z) {
    String file() {
      return x + "." + z + ".ttp";
    }

    boolean holds(int blockX, int blockZ) {
      return blockX >> 9 == x && blockZ >> 9 == z;
    }
  }

  record Cell(int x, int y, int z, Class<?> type) {
  }

  record Scaffold(UUID owner, long placedAt) {
  }

  record Op(int x, int y, int z, Class<?> type, Object value) {
    Cell cell() {
      return new Cell(x, y, z, type);
    }
  }

  private SpatialWorldFixture() {
  }

  static String resource(Region region) {
    return "/art/arcane/adapt/api/data/spatial/world/" + region.file();
  }

  static String sectionResource(String name) {
    return "/art/arcane/adapt/api/data/spatial/world/" + name + ".section";
  }

  static List<Op> ops(Function<String, ItemStack> items) {
    List<Op> ops = new ArrayList<>();
    edges(ops, items);
    values(ops, items);
    palettes(ops);
    removals(ops, items);
    return ops;
  }

  /**
   * Operations on one 16x16x16 section in local coordinates.
   */
  static List<Op> sectionOps(String name, Function<String, ItemStack> items) {
    List<Op> ops = new ArrayList<>();
    switch (name) {
      case "six" -> {
        for (int type = 0; type < WRITTEN_TYPES.size(); type++) {
          ops.add(set(1, 2, 3, sample(type, 40 + type, items)));
        }
        ops.add(set(0, 0, 0, new Earnings(-128)));
        ops.add(set(15, 15, 15, new Earnings(127)));
      }
      case "cleared" -> {
        ops.add(set(4, 4, 4, new Earnings(9)));
        ops.add(remove(4, 4, 4, Earnings.class));
        ops.add(set(5, 5, 5, new PlacementStamp(1, 2, 3)));
      }
      case "empty" -> ops.add(remove(6, 6, 6, Earnings.class));
      default -> throw new IllegalArgumentException(name);
    }
    return ops;
  }

  /**
   * What the operations leave behind once the scaffold slice is gone.
   */
  static Map<Cell, Object> replay(List<Op> ops) {
    Map<Cell, Object> state = new LinkedHashMap<>();
    for (Op op : ops) {
      if (op.type() == Scaffold.class) {
        continue;
      }
      if (op.value() == null) {
        state.remove(op.cell());
      } else {
        state.put(op.cell(), op.value());
      }
    }
    return state;
  }

  // An expected item was never saved, so it has no bytes to compare: items are matched by what they decode to.
  static boolean same(Object expected, Object actual) {
    if (expected instanceof PlinthSlotItem slot) {
      return actual instanceof PlinthSlotItem read
          && read.id().equals(slot.id())
          && read.lost() == slot.lost()
          && read.item() == slot.item();
    }
    if (expected instanceof NodeFaces faces) {
      return actual instanceof NodeFaces read && sameNodes(faces, read);
    }
    return expected == null ? actual == null : expected.equals(actual);
  }

  private static boolean sameNodes(NodeFaces expected, NodeFaces actual) {
    if (expected.size() != actual.size()) {
      return false;
    }
    for (NodeRecord record : expected.records()) {
      NodeRecord read = actual.get(record.face());
      if (read == null
          || !read.filterItems().equals(record.filterItems())
          || read.unreadableFilters() != record.unreadableFilters()
          || !read.toBuilder().filterEntries(List.of()).build().equals(record.toBuilder().filterEntries(List.of()).build())) {
        return false;
      }
    }
    return true;
  }

  static Object sample(int type, int n, Function<String, ItemStack> items) {
    UUID id = new UUID(n * 0x9E3779B97F4A7C15L, ~(long) n);
    return switch (type) {
      case 0 -> new Earnings((n * 37) % 256 - 128);
      case 1 -> new PlacementStamp(n * 1_000_003, -n, n ^ 0x55555555);
      case 2 -> new BrewingStandOwner(id);
      case 3 -> new Scaffold(id, n * 1_000_000_007L - 5L);
      case 4 -> n % 3 == 0 ? PlinthSlotItem.stored(id, new byte[0]) : PlinthSlotItem.of(id, items.apply("item_" + n % 4));
      case 5 -> NodeFaces.EMPTY.with(NodeRecord.builder(id, NODE_TYPES[n % NODE_TYPES.length], FACES[n % FACES.length])
          .channel(n % 5 == 0 ? null : COLORS[n % COLORS.length])
          .priority(1 + n % 5)
          .redstone(REDSTONE[n % REDSTONE.length])
          .whitelist(n % 2 == 0)
          .build());
      default -> throw new IllegalArgumentException("type " + type);
    };
  }

  private static List<Region> regions() {
    List<Region> regions = new ArrayList<>(EDGE_REGIONS);
    regions.add(VALUES);
    regions.add(PALETTE);
    regions.add(REMOVED);
    return List.copyOf(regions);
  }

  // Every region and chunk edge on both axes, at the bottom, the top and each side of a section seam.
  private static void edges(List<Op> ops, Function<String, ItemStack> items) {
    for (int pair = 0; pair < EDGE_PAIRS.length; pair++) {
      int x = EDGE_PAIRS[pair][0];
      int z = EDGE_PAIRS[pair][1];
      for (int height = 0; height < HEIGHTS.length; height++) {
        ops.add(set(x, HEIGHTS[height], z, sample((pair + height) % WRITTEN_TYPES.size(), pair * 8 + height, items)));
      }
      for (int type = 0; type < WRITTEN_TYPES.size(); type++) {
        ops.add(set(x, 64, z, sample(type, 200 + pair * 6 + type, items)));
      }
    }
  }

  // The extremes of every stored field, one value per block.
  private static void values(List<Op> ops, Function<String, ItemStack> items) {
    int baseX = VALUES.x() << 9;
    int baseZ = VALUES.z() << 9;
    List<List<Object>> columns = List.of(
        earnings(), stamps(), owners(), scaffolds(), plinthSlots(items), nodes(items));
    for (int column = 0; column < columns.size(); column++) {
      List<Object> values = columns.get(column);
      for (int index = 0; index < values.size(); index++) {
        ops.add(set(baseX + index, 70, baseZ + column, values.get(index)));
      }
    }
  }

  /**
   * The extremes of every field of the five stored value types.
   */
  static List<Object> extremes(Function<String, ItemStack> items) {
    List<Object> values = new ArrayList<>();
    values.addAll(earnings());
    values.addAll(stamps());
    values.addAll(owners());
    values.addAll(plinthSlots(items));
    values.addAll(nodes(items));
    return values;
  }

  private static List<Object> earnings() {
    return List.of(new Earnings(-128), new Earnings(-1), new Earnings(0), new Earnings(1), new Earnings(64), new Earnings(127));
  }

  private static List<Object> stamps() {
    return List.of(
        new PlacementStamp(0, 0, 0),
        new PlacementStamp(-1, -1, -1),
        new PlacementStamp(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE),
        new PlacementStamp(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE),
        new PlacementStamp(Integer.MAX_VALUE, Integer.MIN_VALUE, 0),
        new PlacementStamp(1, 2, 3),
        new PlacementStamp(3, 2, 1));
  }

  private static List<UUID> ids() {
    return List.of(
        new UUID(0L, 0L),
        new UUID(-1L, -1L),
        new UUID(Long.MIN_VALUE, Long.MAX_VALUE),
        new UUID(Long.MAX_VALUE, Long.MIN_VALUE),
        UUID.fromString("6f1c2a4e-0b7d-4d8e-9a51-2f3e4d5c6b7a"));
  }

  private static List<Object> owners() {
    List<Object> owners = new ArrayList<>();
    for (UUID id : ids()) {
      owners.add(new BrewingStandOwner(id));
    }
    return owners;
  }

  private static List<Object> scaffolds() {
    long[] times = {0L, -1L, 1L, Long.MAX_VALUE, Long.MIN_VALUE};
    List<Object> scaffolds = new ArrayList<>();
    List<UUID> ids = ids();
    for (int index = 0; index < times.length; index++) {
      scaffolds.add(new Scaffold(ids.get(index), times[index]));
    }
    scaffolds.add(new Scaffold(ids.get(0), 1_700_000_000_000L));
    return scaffolds;
  }

  private static List<Object> plinthSlots(Function<String, ItemStack> items) {
    List<UUID> ids = ids();
    return List.of(
        PlinthSlotItem.stored(ids.get(0), new byte[0]),
        PlinthSlotItem.of(ids.get(1), items.apply("a")),
        PlinthSlotItem.of(ids.get(2), items.apply("x".repeat(5_000))),
        PlinthSlotItem.of(ids.get(3), items.apply("item with spaces/+=")),
        PlinthSlotItem.stored(ids.get(4), new byte[0]));
  }

  private static List<Object> nodes(Function<String, ItemStack> items) {
    List<UUID> ids = ids();
    List<Object> nodes = new ArrayList<>();
    NodeCuboid[] areas = {
        NodeCuboid.symmetric(0, 0, 0),
        NodeCuboid.symmetric(NodeRecord.MAX_RADIUS, NodeRecord.MAX_RADIUS, NodeRecord.MAX_RADIUS),
        new NodeCuboid(-NodeRecord.MAX_RADIUS, 0, 0, 0, 0, NodeRecord.MAX_RADIUS),
        new NodeCuboid(0, -NodeRecord.MAX_RADIUS, -1, 1, 0, 0),
        new NodeCuboid(-1, -2, -3, 4, 5, 6),
        NodeRecord.DEFAULT_AREA};
    NodeFaces everyFace = NodeFaces.EMPTY;
    for (int face = 0; face < FACES.length; face++) {
      everyFace = everyFace.with(NodeRecord.builder(ids.get(face % ids.size()), NODE_TYPES[face], FACES[face])
          .channel(face == 0 ? null : COLORS[face])
          .priority(face)
          .redstone(REDSTONE[face % REDSTONE.length])
          .area(areas[face])
          .whitelist(face % 2 == 0)
          .build());
    }
    nodes.add(everyFace);
    for (int type = 0; type < NODE_TYPES.length; type++) {
      nodes.add(NodeFaces.EMPTY.with(NodeRecord.builder(ids.get(type % ids.size()), NODE_TYPES[type], FACES[type % FACES.length])
          .channel(COLORS[type % COLORS.length])
          .build()));
    }
    for (int color = NODE_TYPES.length; color < COLORS.length; color++) {
      nodes.add(NodeFaces.EMPTY.with(NodeRecord.builder(ids.get(0), NodeType.DEPOSIT, BlockFace.DOWN).channel(COLORS[color]).build()));
    }
    List<ItemStack> filterItems = new ArrayList<>();
    for (int index = 0; index < NodeRecord.MAX_FILTER_ENTRIES; index++) {
      filterItems.add(items.apply("filter_" + index));
    }
    nodes.add(NodeFaces.EMPTY.with(NodeRecord.builder(ids.get(1), NodeType.FILTER, BlockFace.NORTH)
        .filterItems(filterItems)
        .whitelist(false)
        .build()));
    List<String> tags = new ArrayList<>(List.of(
        "#a", "#minecraft:logs", "#ключ:日本語", "#🙂 emoji", "#nul\u0000inside", "#" + "t".repeat(5_000)));
    while (tags.size() < NodeRecord.MAX_FILTER_ENTRIES) {
      tags.add("#tag_" + tags.size());
    }
    nodes.add(NodeFaces.EMPTY.with(NodeRecord.builder(ids.get(2), NodeType.FILTER, BlockFace.SOUTH)
        .filterTags(tags)
        .build()));
    Map<String, String> data = new LinkedHashMap<>();
    data.put("", "");
    data.put("empty", "");
    data.put("mode", "strict");
    data.put("ключ", "значение");
    data.put("日本語", "🙂🚀");
    data.put("nul", "a\u0000b");
    data.put("long", "v".repeat(5_000));
    for (int index = 0; index < 40; index++) {
      data.put("key_" + index, "value_" + index);
    }
    nodes.add(NodeFaces.EMPTY.with(NodeRecord.builder(ids.get(3), NodeType.INPUT, BlockFace.WEST)
        .filterItems(List.of(items.apply("filter_0")))
        .filterTags(List.of("#minecraft:planks"))
        .data(data)
        .build()));
    return nodes;
  }

  // One section per palette size; even entries fill only as many cells as values, odd ones fill the whole section.
  private static void palettes(List<Op> ops) {
    int baseX = PALETTE.x() << 9;
    int baseZ = PALETTE.z() << 9;
    for (int index = 0; index < PALETTE_SIZES.length; index++) {
      int size = PALETTE_SIZES[index];
      int cells = index % 2 == 0 ? size : 4096;
      int x = baseX + index * 16;
      int y = MIN_HEIGHT + index * 16;
      for (int cell = 0; cell < cells; cell++) {
        int value = cell % size;
        ops.add(set(x + (cell & 15), y + ((cell >> 4) & 15), baseZ + (cell >> 8), new PlacementStamp(index, value, -value)));
      }
    }
    for (int cell = 0; cell < 4096; cell++) {
      ops.add(set(baseX + (cell & 15), 96 + ((cell >> 4) & 15), baseZ + 16 + (cell >> 8), new Earnings(7)));
    }
    ops.add(set(baseX + 16, 96, baseZ + 16, new Earnings(8)));
  }

  private static void removals(List<Op> ops, Function<String, ItemStack> items) {
    int baseX = REMOVED.x() << 9;
    int baseZ = REMOVED.z() << 9;
    for (int index = 0; index < 10; index++) {
      ops.add(set(baseX + index, 10, baseZ, new Earnings(index + 1)));
    }
    for (int index = 0; index < 10; index++) {
      ops.add(remove(baseX + index, 10, baseZ, Earnings.class));
    }
    for (int index = 0; index < 10; index++) {
      ops.add(set(baseX + 16 + index, 10, baseZ, new PlacementStamp(index, index, index)));
    }
    for (int index = 1; index < 10; index += 2) {
      ops.add(remove(baseX + 16 + index, 10, baseZ, PlacementStamp.class));
    }
    ops.add(set(baseX + 32, 10, baseZ, new Earnings(1)));
    ops.add(remove(baseX + 32, 10, baseZ, Earnings.class));
    ops.add(set(baseX + 32, 10, baseZ, new Earnings(2)));
    for (int type = 0; type < WRITTEN_TYPES.size(); type++) {
      ops.add(set(baseX + 48, 10, baseZ, sample(type, 300 + type, items)));
    }
    for (int type = 0; type < WRITTEN_TYPES.size(); type += 2) {
      ops.add(remove(baseX + 48, 10, baseZ, WRITTEN_TYPES.get(type)));
    }
    for (int index = 0; index < 20; index++) {
      ops.add(set(baseX + 64, 10, baseZ, new PlacementStamp(index, 0, 0)));
    }
    ops.add(remove(baseX + 80, 10, baseZ, Earnings.class));
  }

  private static Op set(int x, int y, int z, Object value) {
    return new Op(x, y, z, value.getClass(), value);
  }

  private static Op remove(int x, int y, int z, Class<?> type) {
    return new Op(x, y, z, type, null);
  }
}

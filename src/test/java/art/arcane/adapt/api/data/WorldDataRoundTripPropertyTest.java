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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Seeded random writes, removals and reopens on the current format, checked against a plain map.
 * A failure names its seed, and the same seed replays the same operations.
 */
class WorldDataRoundTripPropertyTest extends MatterWorldTestBase {
  static final int[] PALETTE_SIZES = {1, 2, 3, 5, 17, 257, 513};

  private static final int CASES = 100;
  private static final int ROUNDS = 3;
  private static final int[] EDGES = SpatialWorldFixture.EDGES;
  private static final int[] HEIGHTS = SpatialWorldFixture.HEIGHTS;
  private static final int[] INTS = {0, -1, 1, Integer.MAX_VALUE, Integer.MIN_VALUE};
  private static final long[] LONGS = {0L, -1L, 1L, Long.MAX_VALUE, Long.MIN_VALUE};
  private static final String[] ITEM_NAMES = {"", "a", "item with spaces/+=", "ключ 日本語", "i".repeat(5_000), "stone", "torch"};
  private static final String[] TAGS = {"#a", "#minecraft:logs", "#ключ:日本語", "#🙂 emoji", "#nul\u0000inside", "#" + "t".repeat(5_000)};
  private static final String[] TEXTS = {"", "strict", "значение", "🙂🚀", "a\u0000b", "v".repeat(5_000)};
  private static final BlockFace[] FACES = {
      BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

  @Test
  void randomWritesAndRemovalsMatchAModelThroughEveryReopen() {
    for (int seed = 0; seed < CASES; seed++) {
      Random random = new Random(seed);
      String key = "case_" + seed;
      List<int[]> columns = columns(random);
      Map<SpatialWorldFixture.Cell, Object> model = new LinkedHashMap<>();
      Set<SpatialWorldFixture.Cell> touched = new HashSet<>();
      WorldData data = open(key);
      for (int round = 0; round < ROUNDS; round++) {
        int operations = 20 + random.nextInt(60);
        for (int operation = 0; operation < operations; operation++) {
          int[] column = columns.get(random.nextInt(columns.size()));
          int y = HEIGHTS[random.nextInt(HEIGHTS.length)];
          Class<?> type = SpatialWorldFixture.TYPES.get(random.nextInt(SpatialWorldFixture.TYPES.size()));
          SpatialWorldFixture.Cell cell = new SpatialWorldFixture.Cell(column[0], y, column[1], type);
          touched.add(cell);
          if (random.nextInt(4) == 0) {
            data.remove(cell.x(), cell.y(), cell.z(), type);
            model.remove(cell);
          } else {
            Object value = value(random, type);
            data.set(cell.x(), cell.y(), cell.z(), value);
            model.put(cell, value);
          }
          Object read = data.get(cell.x(), cell.y(), cell.z(), type);
          assertThat(SpatialWorldFixture.same(model.get(cell), read))
              .as("seed %d round %d operation %d: %s holds %s, expected %s", seed, round, operation, cell, read, model.get(cell))
              .isTrue();
        }
        check(data, model, touched, "seed " + seed + " round " + round + " before the reopen");
        close(data);
        data = open(key);
        check(data, model, touched, "seed " + seed + " round " + round + " after the reopen");
      }
      close(data);
    }
  }

  @ParameterizedTest
  @FieldSource("PALETTE_SIZES")
  void aSparseSectionKeepsEveryValueAsItsPaletteGrowsAndEmpties(int size) {
    assertPaletteLifecycle(size, false, index -> new PlacementStamp(index, -index, size));
    assertPaletteLifecycle(size, false, index -> new BrewingStandOwner(new UUID(index, size)));
  }

  @ParameterizedTest
  @FieldSource("PALETTE_SIZES")
  void aFullSectionKeepsEveryValueAsItsPaletteGrowsAndEmpties(int size) {
    assertPaletteLifecycle(size, true, index -> new PlacementStamp(index, -index, size));
    if (size <= 256) {
      assertPaletteLifecycle(size, true, index -> new Earnings(index - 128));
    }
  }

  @Test
  void theExtremesOfEveryFieldRoundTripAtTheBottomAndTopOfTheWorld() {
    List<Object> values = SpatialWorldFixture.extremes(this::item);
    Map<SpatialWorldFixture.Cell, Object> expected = new LinkedHashMap<>();
    WorldData data = open(WORLD);
    for (int index = 0; index < values.size(); index++) {
      Object value = values.get(index);
      int x = -520 + index;
      for (int y : new int[] {SpatialWorldFixture.MIN_HEIGHT, 70, SpatialWorldFixture.MAX_HEIGHT - 1}) {
        data.set(x, y, -1, value);
        expected.put(new SpatialWorldFixture.Cell(x, y, -1, value.getClass()), value);
      }
    }
    assertThat(expected.keySet().stream().map(SpatialWorldFixture.Cell::type).distinct())
        .containsExactlyInAnyOrderElementsOf(SpatialWorldFixture.TYPES);

    assertChunksHold(data, expected, expected.keySet());
    close(data);
    assertChunksHold(open(WORLD), expected, expected.keySet());
  }

  @Test
  void blocksBelowAndAboveTheWorldStoreNothing() {
    WorldData data = open(WORLD);
    int[] outside = {SpatialWorldFixture.MIN_HEIGHT - 1, SpatialWorldFixture.MAX_HEIGHT, Integer.MIN_VALUE, Integer.MAX_VALUE};
    for (int y : outside) {
      data.set(-1, y, -1, new Earnings(5));
      data.remove(-1, y, -1, Earnings.class);
      data.set(-1, y, -1, new PlacementStamp(1, 2, 3));
      assertThat(data.get(-1, y, -1, PlacementStamp.class)).as("y %d", y).isNull();
    }
    data.set(-1, SpatialWorldFixture.MIN_HEIGHT, -1, new Earnings(1));
    data.set(-1, SpatialWorldFixture.MAX_HEIGHT - 1, -1, new Earnings(2));
    Map<SpatialWorldFixture.Cell, Object> expected = new LinkedHashMap<>();
    expected.put(new SpatialWorldFixture.Cell(-1, SpatialWorldFixture.MIN_HEIGHT, -1, Earnings.class), new Earnings(1));
    expected.put(new SpatialWorldFixture.Cell(-1, SpatialWorldFixture.MAX_HEIGHT - 1, -1, Earnings.class), new Earnings(2));

    assertChunksHold(data, expected, expected.keySet());
    close(data);
    assertChunksHold(open(WORLD), expected, expected.keySet());
  }

  @Test
  void aBlockHoldsOneValueOfEverySliceAndLosesOnlyTheOneRemoved() {
    WorldData data = open(WORLD);
    Random random = new Random(7L);
    Map<SpatialWorldFixture.Cell, Object> expected = new LinkedHashMap<>();
    for (Class<?> type : SpatialWorldFixture.TYPES) {
      Object value = value(random, type);
      data.set(-513, -64, 512, value);
      expected.put(new SpatialWorldFixture.Cell(-513, -64, 512, type), value);
    }
    Set<SpatialWorldFixture.Cell> touched = new HashSet<>(expected.keySet());
    assertChunksHold(data, expected, touched);

    for (Class<?> type : SpatialWorldFixture.TYPES) {
      close(data);
      data = open(WORLD);
      assertChunksHold(data, expected, touched);
      data.remove(-513, -64, 512, type);
      expected.remove(new SpatialWorldFixture.Cell(-513, -64, 512, type));
      assertChunksHold(data, expected, touched);
    }
    close(data);
    assertChunksHold(open(WORLD), expected, touched);
  }

  // Write the values, reopen, grow the palette past the size it was loaded with, shrink to one value, then to none.
  private void assertPaletteLifecycle(int size, boolean full, IntFunction<Object> values) {
    Object sample = values.apply(0);
    String key = "palette_" + size + "_" + (full ? "full_" : "sparse_") + sample.getClass().getSimpleName().toLowerCase();
    int baseX = -16;
    int baseY = full ? SpatialWorldFixture.MAX_HEIGHT - 16 : SpatialWorldFixture.MIN_HEIGHT;
    int baseZ = 512;
    List<Integer> order = cellOrder(size);
    int first = full ? 4096 : size;
    Map<SpatialWorldFixture.Cell, Object> expected = new LinkedHashMap<>();
    Set<SpatialWorldFixture.Cell> touched = new HashSet<>();
    WorldData data = open(key);
    for (int index = 0; index < first; index++) {
      put(data, expected, touched, cell(baseX, baseY, baseZ, order.get(index), sample.getClass()), values.apply(index % size));
    }
    check(data, expected, touched, key + " written");
    close(data);
    data = open(key);
    check(data, expected, touched, key + " reopened");

    int grown = full ? 2048 : Math.min(size, 4096 - size);
    for (int index = 0; index < grown; index++) {
      int position = full ? order.get(index) : order.get(4095 - index);
      put(data, expected, touched, cell(baseX, baseY, baseZ, position, sample.getClass()), values.apply(size + index % size));
    }
    check(data, expected, touched, key + " grown after the reopen");
    close(data);
    data = open(key);
    check(data, expected, touched, key + " grown and reopened");

    SpatialWorldFixture.Cell last = cell(baseX, baseY, baseZ, order.get(0), sample.getClass());
    for (SpatialWorldFixture.Cell cell : new ArrayList<>(expected.keySet())) {
      if (!cell.equals(last)) {
        data.remove(cell.x(), cell.y(), cell.z(), cell.type());
        expected.remove(cell);
      }
    }
    assertThat(expected).hasSize(1);
    check(data, expected, touched, key + " shrunk to one value");
    close(data);
    data = open(key);
    check(data, expected, touched, key + " shrunk and reopened");

    data.remove(last.x(), last.y(), last.z(), last.type());
    expected.clear();
    check(data, expected, touched, key + " emptied");
    close(data);
    data = open(key);
    check(data, expected, touched, key + " emptied and reopened");
    put(data, expected, touched, last, values.apply(1 % size));
    close(data);
    data = open(key);
    check(data, expected, touched, key + " reused after emptying");
    close(data);
  }

  private static void put(WorldData data,
                          Map<SpatialWorldFixture.Cell, Object> expected,
                          Set<SpatialWorldFixture.Cell> touched,
                          SpatialWorldFixture.Cell cell,
                          Object value) {
    data.set(cell.x(), cell.y(), cell.z(), value);
    expected.put(cell, value);
    touched.add(cell);
  }

  private static void check(WorldData data,
                            Map<SpatialWorldFixture.Cell, Object> expected,
                            Set<SpatialWorldFixture.Cell> touched,
                            String step) {
    try {
      assertChunksHold(data, expected, touched);
    } catch (AssertionError failure) {
      throw new AssertionError(step + ": " + failure.getMessage(), failure);
    }
  }

  private static SpatialWorldFixture.Cell cell(int baseX, int baseY, int baseZ, int position, Class<?> type) {
    return new SpatialWorldFixture.Cell(baseX + (position & 15), baseY + ((position >> 4) & 15), baseZ + (position >> 8), type);
  }

  // The 64 cells whose coordinates are all multiples of four come first: a section holding only those is stored compactly.
  private static List<Integer> cellOrder(int seed) {
    List<Integer> aligned = new ArrayList<>();
    List<Integer> others = new ArrayList<>();
    for (int position = 0; position < 4096; position++) {
      if ((position & 0x333) == 0) {
        aligned.add(position);
      } else {
        others.add(position);
      }
    }
    Random random = new Random(seed);
    Collections.shuffle(aligned, random);
    Collections.shuffle(others, random);
    aligned.addAll(others);
    return aligned;
  }

  // A few block columns near region and chunk edges, close enough together that writes collide.
  private static List<int[]> columns(Random random) {
    List<int[]> columns = new ArrayList<>();
    int count = 4 + random.nextInt(8);
    for (int index = 0; index < count; index++) {
      int x = EDGES[random.nextInt(EDGES.length)] + random.nextInt(3) - 1;
      int z = EDGES[random.nextInt(EDGES.length)] + random.nextInt(3) - 1;
      columns.add(new int[] {x, z});
    }
    return columns;
  }

  private Object value(Random random, Class<?> type) {
    if (type == Earnings.class) {
      return new Earnings(random.nextBoolean() ? random.nextInt(256) - 128 : new int[] {-128, -1, 0, 1, 127}[random.nextInt(5)]);
    }
    if (type == PlacementStamp.class) {
      return new PlacementStamp(anyInt(random), anyInt(random), anyInt(random));
    }
    if (type == BrewingStandOwner.class) {
      return new BrewingStandOwner(new UUID(anyLong(random), anyLong(random)));
    }
    if (type == PlinthSlotItem.class) {
      // An id here always means one item, as in the game, where every stored slot gets its own random id.
      if (random.nextInt(4) == 0) {
        return PlinthSlotItem.stored(new UUID(random.nextInt(8), 1L), new byte[0]);
      }
      String name = ITEM_NAMES[random.nextInt(ITEM_NAMES.length)];
      return PlinthSlotItem.of(UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8)), item(name));
    }
    if (type == NodeFaces.class) {
      return nodes(random);
    }
    throw new IllegalArgumentException(type.getName());
  }

  private NodeFaces nodes(Random random) {
    NodeFaces faces = NodeFaces.EMPTY;
    int count = 1 + random.nextInt(random.nextInt(4) == 0 ? FACES.length : 2);
    for (int index = 0; index < count; index++) {
      NodeType[] types = NodeType.values();
      RedstoneMode[] modes = RedstoneMode.values();
      DyeColor[] colors = DyeColor.values();
      int radius = NodeRecord.MAX_RADIUS;
      List<ItemStack> items = new ArrayList<>();
      for (int entry = random.nextInt(3); entry > 0; entry--) {
        items.add(item(ITEM_NAMES[random.nextInt(ITEM_NAMES.length)]));
      }
      List<String> tags = new ArrayList<>();
      for (int entry = random.nextInt(3); entry > 0; entry--) {
        tags.add(TAGS[random.nextInt(TAGS.length)]);
      }
      Map<String, String> entries = new LinkedHashMap<>();
      for (int entry = random.nextInt(3); entry > 0; entry--) {
        entries.put(TEXTS[random.nextInt(TEXTS.length)], TEXTS[random.nextInt(TEXTS.length)]);
      }
      faces = faces.with(NodeRecord.builder(new UUID(anyLong(random), anyLong(random)), types[random.nextInt(types.length)], FACES[random.nextInt(FACES.length)])
          .channel(random.nextInt(4) == 0 ? null : colors[random.nextInt(colors.length)])
          .priority(NodeRecord.MIN_PRIORITY + random.nextInt(NodeRecord.MAX_PRIORITY))
          .redstone(modes[random.nextInt(modes.length)])
          .area(new NodeCuboid(-random.nextInt(radius + 1), -random.nextInt(radius + 1), -random.nextInt(radius + 1),
              random.nextInt(radius + 1), random.nextInt(radius + 1), random.nextInt(radius + 1)))
          .whitelist(random.nextBoolean())
          .filterItems(items)
          .filterTags(tags)
          .data(entries)
          .build());
    }
    return faces;
  }

  private static int anyInt(Random random) {
    return random.nextBoolean() ? INTS[random.nextInt(INTS.length)] : random.nextInt();
  }

  private static long anyLong(Random random) {
    return random.nextBoolean() ? LONGS[random.nextInt(LONGS.length)] : random.nextLong();
  }
}

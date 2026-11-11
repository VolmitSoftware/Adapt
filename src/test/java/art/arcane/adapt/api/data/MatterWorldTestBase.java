package art.arcane.adapt.api.data;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.nms.NMS;
import art.arcane.volmlib.util.mantle.Mantle;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamConstants;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Opens WorldData against a temporary plugin folder with item stacks stood in by named mocks.
 * A mock serializes through the real NMS codec with its name inside, so a save on a mantle worker thread,
 * where the static mock does not apply, writes the same bytes as a save on the test thread.
 * Items are decoded where a test asks for them, which is the test thread, so the stand-in decoder below applies.
 */
abstract class MatterWorldTestBase extends AdaptTestBase {
  static final String WORLD = "overworld";

  /**
   * Names of stored items the stand-in decoder refuses, as a platform that no longer knows them would.
   */
  Set<String> undecodable = Set.of();
  /**
   * Every item name handed to the stand-in decoder so far, refused or not.
   */
  final List<String> decodeCalls = new CopyOnWriteArrayList<>();

  private final Map<String, ItemStack> items = new ConcurrentHashMap<>();
  private final List<WorldData> opened = new ArrayList<>();
  private MockedStatic<NMS> nms;

  @BeforeAll
  static void registerSlices() {
    AdaptSlices.register();
  }

  @BeforeEach
  void installWorldPlumbing() {
    nms = mockStatic(NMS.class);
    nms.when(() -> NMS.serializeStack(any(ItemStack.class))).thenCallRealMethod();
    nms.when(() -> NMS.deserializeStack(anyString())).thenAnswer(call -> decode(itemName(call.<String>getArgument(0))));
    lenient().when(plugin.getDataFolder(any(String[].class))).thenAnswer(invocation -> {
      File folder = dataFolder;
      for (Object argument : invocation.getArguments()) {
        folder = new File(folder, String.valueOf(argument));
      }
      folder.mkdirs();
      return folder;
    });
  }

  @AfterEach
  void closeWorlds() {
    for (WorldData data : new ArrayList<>(opened)) {
      data.unregister();
    }
    opened.clear();
    nms.close();
  }

  // What NMS.deserializeStack answers for the stored item of that name.
  private ItemStack decode(String name) {
    decodeCalls.add(name);
    if (undecodable.contains(name)) {
      throw new IllegalStateException("item " + name + " no longer loads");
    }
    return item(name);
  }

  /**
   * The bytes the item of that name is stored as, in every format.
   */
  byte[] itemBytes(String name) {
    return NMS.serializeStack(item(name)).getBytes(StandardCharsets.US_ASCII);
  }

  // The name is the last string in the serialized map, right after the ItemStack class alias.
  private static String itemName(String encoded) {
    byte[] bytes = Base64.getUrlDecoder().decode(encoded);
    byte[] alias = ItemStack.class.getName().getBytes(StandardCharsets.UTF_8);
    int start = -1;
    for (int index = bytes.length - alias.length; index >= 0 && start < 0; index--) {
      if (Arrays.equals(bytes, index, index + alias.length, alias, 0, alias.length)) {
        start = index + alias.length;
      }
    }
    if (start < 0 || bytes[start] != ObjectStreamConstants.TC_STRING) {
      throw new IllegalStateException("Not an item written by this test: " + encoded);
    }
    int length = ((bytes[start + 1] & 0xFF) << 8) | (bytes[start + 2] & 0xFF);
    return new String(bytes, start + 3, length, StandardCharsets.UTF_8);
  }

  MockedStatic<NMS> nms() {
    return nms;
  }

  World world(String key) {
    return world(key, SpatialWorldFixture.MIN_HEIGHT, SpatialWorldFixture.MAX_HEIGHT);
  }

  World world(String key, int minHeight, int maxHeight) {
    World world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)));
    lenient().when(world.getKey()).thenReturn(new NamespacedKey("minecraft", key));
    lenient().when(world.getName()).thenReturn(key);
    lenient().when(world.getMinHeight()).thenReturn(minHeight);
    lenient().when(world.getMaxHeight()).thenReturn(maxHeight);
    return world;
  }

  File mantleFolder(String key) {
    File folder = new File(dataFolder, "data/mantle/minecraft/" + key);
    folder.mkdirs();
    return folder;
  }

  File regionFile(String key, SpatialWorldFixture.Region region) {
    return Mantle.fileForRegion(mantleFolder(key), region.x(), region.z());
  }

  File backupFile(String key, SpatialWorldFixture.Region region) {
    return new File(mantleFolder(key), SpatialMantleMigration.BACKUP_FOLDER + "/" + regionFile(key, region).getName());
  }

  File damagedFile(String key, SpatialWorldFixture.Region region) {
    return new File(mantleFolder(key), SpatialMantleMigration.DAMAGED_FOLDER + "/" + regionFile(key, region).getName());
  }

  File marker(String key) {
    return new File(mantleFolder(key), SpatialMantleMigration.MARKER);
  }

  File install(String key, SpatialWorldFixture.Region region, byte[] bytes) throws IOException {
    File file = regionFile(key, region);
    Files.write(file.toPath(), bytes);
    return file;
  }

  WorldData open(World world) {
    WorldData data = new WorldData(world);
    opened.add(data);
    return data;
  }

  WorldData open(String key) {
    return open(world(key));
  }

  void close(WorldData data) {
    data.unregister();
    opened.remove(data);
  }

  ItemStack item(String name) {
    return items.computeIfAbsent(name, key -> {
      ItemStack item = mock(ItemStack.class);
      lenient().when(item.clone()).thenReturn(item);
      lenient().when(item.serialize()).thenReturn(Map.of("fixture", key));
      return item;
    });
  }

  static byte[] resource(String path) throws IOException {
    try (InputStream in = MatterWorldTestBase.class.getResourceAsStream(path)) {
      assertThat(in).as(path).isNotNull();
      return in.readAllBytes();
    }
  }

  static void apply(WorldData data, List<SpatialWorldFixture.Op> ops) {
    for (SpatialWorldFixture.Op op : ops) {
      if (op.value() == null) {
        data.remove(op.x(), op.y(), op.z(), op.type());
      } else {
        data.set(op.x(), op.y(), op.z(), op.value());
      }
    }
  }

  static Map<SpatialWorldFixture.Cell, Object> within(Map<SpatialWorldFixture.Cell, Object> cells,
                                                      Collection<SpatialWorldFixture.Region> regions) {
    Map<SpatialWorldFixture.Cell, Object> kept = new HashMap<>();
    for (Map.Entry<SpatialWorldFixture.Cell, Object> entry : cells.entrySet()) {
      for (SpatialWorldFixture.Region region : regions) {
        if (region.holds(entry.getKey().x(), entry.getKey().z())) {
          kept.put(entry.getKey(), entry.getValue());
        }
      }
    }
    return kept;
  }

  /**
   * Everything chunk iteration reports for the given regions, keyed by world position and slice type.
   */
  static Map<SpatialWorldFixture.Cell, Object> stored(WorldData data, Collection<SpatialWorldFixture.Region> regions) {
    Map<SpatialWorldFixture.Cell, Object> found = new HashMap<>();
    for (SpatialWorldFixture.Region region : regions) {
      int baseX = region.x() << 5;
      int baseZ = region.z() << 5;
      for (Class<?> type : SpatialWorldFixture.TYPES) {
        for (int chunkX = baseX; chunkX < baseX + 32; chunkX++) {
          for (int chunkZ = baseZ; chunkZ < baseZ + 32; chunkZ++) {
            data.iterateChunk(chunkX, chunkZ, type, (x, y, z, value) -> {
              Object previous = found.put(new SpatialWorldFixture.Cell(x, y, z, type), value);
              assertThat(previous).as("reported twice: " + x + "," + y + "," + z + " " + type.getSimpleName()).isNull();
            });
          }
        }
      }
    }
    return found;
  }

  /**
   * Everything chunk iteration reports for the chunks of the given cells.
   */
  static Map<SpatialWorldFixture.Cell, Object> storedAround(WorldData data, Collection<SpatialWorldFixture.Cell> cells) {
    Set<Long> chunks = new HashSet<>();
    for (SpatialWorldFixture.Cell cell : cells) {
      chunks.add(((long) (cell.x() >> 4) << 32) | ((cell.z() >> 4) & 0xFFFFFFFFL));
    }
    Map<SpatialWorldFixture.Cell, Object> found = new HashMap<>();
    for (long chunk : chunks) {
      for (Class<?> type : SpatialWorldFixture.TYPES) {
        data.iterateChunk((int) (chunk >> 32), (int) chunk, type, (x, y, z, value) -> {
          Object previous = found.put(new SpatialWorldFixture.Cell(x, y, z, type), value);
          assertThat(previous).as("reported twice: " + x + "," + y + "," + z + " " + type.getSimpleName()).isNull();
        });
      }
    }
    return found;
  }

  /**
   * The regions hold exactly the expected cells: every one reads back through get, and iteration finds nothing else.
   */
  static void assertHolds(WorldData data,
                          Map<SpatialWorldFixture.Cell, Object> expected,
                          Collection<SpatialWorldFixture.Region> regions) {
    assertReads(data, expected);
    assertIterates(stored(data, regions), expected);
  }

  /**
   * The chunks of the touched cells hold exactly the expected cells and nothing a removal should have cleared.
   */
  static void assertChunksHold(WorldData data,
                               Map<SpatialWorldFixture.Cell, Object> expected,
                               Collection<SpatialWorldFixture.Cell> touched) {
    assertReads(data, expected);
    for (SpatialWorldFixture.Cell cell : touched) {
      if (!expected.containsKey(cell)) {
        assertThat((Object) data.get(cell.x(), cell.y(), cell.z(), cell.type())).as("removed %s", cell).isNull();
      }
    }
    assertIterates(storedAround(data, touched), expected);
  }

  private static void assertReads(WorldData data, Map<SpatialWorldFixture.Cell, Object> expected) {
    List<String> wrong = new ArrayList<>();
    for (Map.Entry<SpatialWorldFixture.Cell, Object> entry : expected.entrySet()) {
      SpatialWorldFixture.Cell cell = entry.getKey();
      Object read = data.get(cell.x(), cell.y(), cell.z(), cell.type());
      if (!SpatialWorldFixture.same(entry.getValue(), read)) {
        wrong.add(cell + " holds " + read + ", expected " + entry.getValue());
      }
    }
    assertThat(wrong).as("values read through get, of %d expected", expected.size()).isEmpty();
  }

  private static void assertIterates(Map<SpatialWorldFixture.Cell, Object> found, Map<SpatialWorldFixture.Cell, Object> expected) {
    assertThat(found.keySet()).as("cells reported by chunk iteration").isEqualTo(expected.keySet());
    for (Map.Entry<SpatialWorldFixture.Cell, Object> entry : expected.entrySet()) {
      assertThat(SpatialWorldFixture.same(entry.getValue(), found.get(entry.getKey())))
          .as("iteration at %s holds %s, expected %s", entry.getKey(), found.get(entry.getKey()), entry.getValue())
          .isTrue();
    }
  }
}

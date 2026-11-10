package art.arcane.adapt.api.data;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.data.unit.Earnings;
import art.arcane.adapt.api.data.unit.PlacementStamp;
import art.arcane.adapt.api.golem.plinth.PlinthSlotItem;
import art.arcane.adapt.nms.NMS;
import art.arcane.volmlib.util.data.Varint;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.mantle.Mantle;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import art.arcane.volmlib.util.matter.slices.RawMatter;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class SpatialMantleMigrationTest extends AdaptTestBase {
  static final List<String> FIXTURES = SpatialFixtures.NAMES;

  private final Map<String, ItemStack> items = new HashMap<>();
  private final Map<ItemStack, String> itemNames = new HashMap<>();
  private final List<WorldData> opened = new ArrayList<>();
  private MockedStatic<NMS> nms;
  private World world;

  @BeforeEach
  void setUp() {
    nms = mockStatic(NMS.class);
    nms.when(() -> NMS.serializeStack(any(ItemStack.class))).thenAnswer(call -> itemNames.get(call.<ItemStack>getArgument(0)));
    nms.when(() -> NMS.deserializeStack(anyString())).thenAnswer(call -> items.get(call.<String>getArgument(0)));
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000e1"));
    lenient().when(world.getKey()).thenReturn(new NamespacedKey("minecraft", "overworld"));
    lenient().when(world.getName()).thenReturn("world");
    lenient().when(world.getMinHeight()).thenReturn(SpatialFixtures.MIN_HEIGHT);
    lenient().when(world.getMaxHeight()).thenReturn(SpatialFixtures.MAX_HEIGHT);
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
  void tearDown() {
    for (WorldData data : opened) {
      data.unregister();
    }
    nms.close();
  }

  @ParameterizedTest
  @FieldSource("FIXTURES")
  void aSpatialRegionMigratesWithEveryValueAndKeepsTheOriginal(String fixture) throws IOException {
    byte[] original = resource(SpatialFixtures.resource(fixture));
    File region = install(original);
    List<SpatialFixtures.Entry> expected = SpatialFixtures.entries(fixture, this::item);

    WorldData migrated = open();

    assertThat(new File(mantleFolder(), SpatialMantleMigration.BACKUP_FOLDER + "/" + region.getName())).hasBinaryContent(original);
    assertThat(Files.readAllBytes(region.toPath())).isNotEqualTo(original);
    assertThat(new File(mantleFolder(), SpatialMantleMigration.MARKER)).exists();
    assertStored(migrated, expected);

    close(migrated);
    WorldData reloaded = open();
    assertStored(reloaded, expected);
  }

  @Test
  void paletteAndBitPackedCellsAreCarriedOverByteForByte() throws IOException {
    assertSectionCarriesOver("earnings", Earnings.class, AdaptSlices.EARNINGS, 127);
    assertSectionCarriesOver("placement_stamp", PlacementStamp.class, AdaptSlices.PLACEMENT_STAMP, 316);
  }

  @Test
  void aRegionAlreadyInTheVolmLibFormatIsLeftUntouched() throws IOException {
    WorldData writer = open();
    writer.set(-48, 16, 1120, new Earnings(9));
    assertThat(writer.savePlate(-48, 1120)).isTrue();
    close(writer);
    File region = Mantle.fileForRegion(mantleFolder(), SpatialFixtures.REGION_X, SpatialFixtures.REGION_Z);
    byte[] current = Files.readAllBytes(region.toPath());
    long modified = region.lastModified();
    Files.delete(new File(mantleFolder(), SpatialMantleMigration.MARKER).toPath());

    WorldData reopened = open();

    assertThat(region).hasBinaryContent(current);
    assertThat(region.lastModified()).isEqualTo(modified);
    assertThat(new File(mantleFolder(), SpatialMantleMigration.BACKUP_FOLDER)).doesNotExist();
    assertThat(reopened.get(-48, 16, 1120, Earnings.class)).isEqualTo(new Earnings(9));
  }

  @Test
  void aDamagedSpatialRegionIsSetAsideWithoutStoppingTheWorld() throws IOException {
    byte[] original = resource(SpatialFixtures.resource("earnings"));
    byte[] damaged = Arrays.copyOf(original, original.length / 2);
    File region = install(damaged);

    WorldData data = open();

    assertThat(region).doesNotExist();
    assertThat(new File(mantleFolder(), SpatialMantleMigration.DAMAGED_FOLDER + "/" + region.getName())).hasBinaryContent(damaged);
    assertThat(data.get(-48, 16, 1120, Earnings.class)).isNull();
    data.set(-48, 16, 1120, new Earnings(3));
    assertThat(data.get(-48, 16, 1120, Earnings.class)).isEqualTo(new Earnings(3));
  }

  // 127 entries pack at 7 bits per cell and 316 at 9, so both fixtures leave the 4-bit linear palette.
  private void assertSectionCarriesOver(String fixture, Class<?> type, String id, int paletteSize) throws IOException {
    byte[] spatial = resource("/art/arcane/adapt/api/data/spatial/" + fixture + ".section");
    Matter section = WorldData.readSpatialSection(CountingDataInputStream.wrap(new ByteArrayInputStream(spatial)));
    assertThat(((RawMatter<?>) section.getSlice(type)).isPalette()).isTrue();

    ByteArrayOutputStream rewritten = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(rewritten)) {
      section.writeDos(out);
    }
    byte[] volmlib = rewritten.toByteArray();
    String spatialName = type.getCanonicalName();
    byte[] spatialBody = sliceBody(spatial, spatialName, false);
    byte[] volmlibBody = sliceBody(volmlib, id, true);
    assertThat(volmlibBody).isEqualTo(spatialBody);
    DataInputStream body = new DataInputStream(new ByteArrayInputStream(spatialBody));
    assertThat(Varint.readUnsignedVarInt(body)).isEqualTo(4096);
    assertThat(Varint.readUnsignedVarInt(body)).isEqualTo(paletteSize);

    Matter reread = WorldData.readSection(CountingDataInputStream.wrap(new ByteArrayInputStream(volmlib)));
    MatterSlice<?> before = section.getSlice(type);
    MatterSlice<?> after = reread.getSlice(type);
    Set<Object> distinct = new HashSet<>();
    for (int x = 0; x < 16; x++) {
      for (int y = 0; y < 16; y++) {
        for (int z = 0; z < 16; z++) {
          assertThat((Object) after.get(x, y, z)).isEqualTo(before.get(x, y, z));
          if (after.get(x, y, z) != null) {
            distinct.add(after.get(x, y, z));
          }
        }
      }
    }
    assertThat(distinct).hasSize(paletteSize);
  }

  // A single-slice section: 16x16x16 size, slice count, header, then the slice name and its palette body.
  private static byte[] sliceBody(byte[] section, String name, boolean lengthPrefixed) throws IOException {
    DataInputStream in = new DataInputStream(new ByteArrayInputStream(section));
    in.skipNBytes(12);
    assertThat(in.readUnsignedByte()).isEqualTo(1);
    in.readUTF();
    in.readLong();
    in.readShort();
    if (lengthPrefixed) {
      assertThat(in.readInt()).isEqualTo(in.available());
    }
    assertThat(in.readUTF()).isEqualTo(name);
    return in.readAllBytes();
  }

  private void assertStored(WorldData data, List<SpatialFixtures.Entry> expected) {
    Map<Class<?>, Integer> counts = new LinkedHashMap<>();
    for (SpatialFixtures.Entry entry : expected) {
      Object value = entry.value();
      Object stored = data.get(entry.x(), entry.y(), entry.z(), value.getClass());
      if (value instanceof PlinthSlotItem slot) {
        PlinthSlotItem read = (PlinthSlotItem) stored;
        assertThat(read.id()).isEqualTo(slot.id());
        assertThat(read.readable()).isEqualTo(slot.readable());
        assertThat(read.item()).isSameAs(slot.item());
      } else {
        assertThat(stored).as(entry.toString()).isEqualTo(value);
      }
      counts.merge(value.getClass(), 1, Integer::sum);
    }
    for (Map.Entry<Class<?>, Integer> count : counts.entrySet()) {
      assertThat(countRegion(data, count.getKey())).as(count.getKey().getSimpleName()).isEqualTo(count.getValue());
    }
  }

  private static int countRegion(WorldData data, Class<?> type) {
    int[] found = new int[1];
    int baseX = SpatialFixtures.REGION_X << 5;
    int baseZ = SpatialFixtures.REGION_Z << 5;
    for (int chunkX = baseX; chunkX < baseX + 32; chunkX++) {
      for (int chunkZ = baseZ; chunkZ < baseZ + 32; chunkZ++) {
        data.iterateChunk(chunkX, chunkZ, type, (x, y, z, value) -> found[0]++);
      }
    }
    return found[0];
  }

  private ItemStack item(String name) {
    return items.computeIfAbsent(name, key -> {
      ItemStack item = mock(ItemStack.class);
      lenient().when(item.clone()).thenReturn(item);
      itemNames.put(item, key);
      return item;
    });
  }

  private File install(byte[] bytes) throws IOException {
    File folder = mantleFolder();
    folder.mkdirs();
    File region = Mantle.fileForRegion(folder, SpatialFixtures.REGION_X, SpatialFixtures.REGION_Z);
    Files.write(region.toPath(), bytes);
    return region;
  }

  private File mantleFolder() {
    return new File(dataFolder, "data/mantle/minecraft/overworld");
  }

  private WorldData open() {
    WorldData data = new WorldData(world);
    opened.add(data);
    return data;
  }

  private void close(WorldData data) {
    data.unregister();
    opened.remove(data);
  }

  private static byte[] resource(String path) throws IOException {
    try (InputStream in = SpatialMantleMigrationTest.class.getResourceAsStream(path)) {
      assertThat(in).as(path).isNotNull();
      return in.readAllBytes();
    }
  }
}

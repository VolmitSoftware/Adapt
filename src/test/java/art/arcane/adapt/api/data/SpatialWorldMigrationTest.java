package art.arcane.adapt.api.data;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.data.unit.Earnings;
import art.arcane.adapt.api.data.unit.PlacementStamp;
import art.arcane.adapt.api.golem.NodeType;
import art.arcane.adapt.api.golem.node.NodeFaces;
import art.arcane.adapt.api.golem.node.NodeFilters;
import art.arcane.adapt.api.golem.node.NodeRecord;
import art.arcane.adapt.api.golem.node.NodeTagLookup;
import art.arcane.adapt.api.golem.plinth.PlinthSlotItem;
import art.arcane.adapt.nms.NMS;
import art.arcane.volmlib.util.io.CountingDataInputStream;
import art.arcane.volmlib.util.matter.Matter;
import art.arcane.volmlib.util.matter.MatterSlice;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalMatchers.and;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * Migrates the golden world that Adapt 2.2.7 saved with Spatial and checks every value against the operations that made it.
 */
class SpatialWorldMigrationTest extends MatterWorldTestBase {
  static final List<SpatialWorldFixture.Region> REGIONS = SpatialWorldFixture.REGIONS;
  static final List<String> SECTIONS = SpatialWorldFixture.SECTIONS;

  private static final int SAVE_CYCLES = 4;
  private static final NodeTagLookup NO_TAGS = new NodeTagLookup() {
    @Override
    public boolean exists(String tag) {
      return false;
    }

    @Override
    public boolean tagged(String tag, Material material) {
      return false;
    }
  };

  @Test
  void theWholeWorldMigratesWithEveryValueInEveryRegion() throws IOException {
    Map<SpatialWorldFixture.Region, byte[]> originals = installWorld(WORLD, REGIONS);
    Map<SpatialWorldFixture.Cell, Object> expected = expected();

    WorldData migrated = open(WORLD);

    assertHolds(migrated, expected, REGIONS);
    assertThat(marker(WORLD)).exists();
    assertThat(new File(mantleFolder(WORLD), SpatialMantleMigration.DAMAGED_FOLDER)).doesNotExist();
    for (SpatialWorldFixture.Region region : REGIONS) {
      assertThat(backupFile(WORLD, region)).as(region.file()).hasBinaryContent(originals.get(region));
      assertThat(Files.readAllBytes(regionFile(WORLD, region).toPath())).as(region.file()).isNotEqualTo(originals.get(region));
    }
    assertThat(new File(mantleFolder(WORLD), SpatialMantleMigration.BACKUP_FOLDER).list()).hasSize(REGIONS.size());
    assertThat(new String(originals.get(SpatialWorldFixture.VALUES), StandardCharsets.ISO_8859_1)).contains(AdaptSlices.SPATIAL_SCAFFOLD);
    for (SpatialWorldFixture.Region region : REGIONS) {
      String rewritten = new String(Files.readAllBytes(regionFile(WORLD, region).toPath()), StandardCharsets.ISO_8859_1);
      assertThat(rewritten).as(region.file()).doesNotContainIgnoringCase("scaffold");
    }

    close(migrated);
    WorldData reloaded = open(WORLD);
    assertHolds(reloaded, expected, REGIONS);
  }

  @ParameterizedTest
  @FieldSource("REGIONS")
  void eachRegionMigratesOnItsOwn(SpatialWorldFixture.Region region) throws IOException {
    installWorld(WORLD, List.of(region));
    Map<SpatialWorldFixture.Cell, Object> expected = within(expected(), List.of(region));
    assertThat(expected).isNotEmpty();

    WorldData migrated = open(WORLD);

    assertHolds(migrated, expected, List.of(region));
    close(migrated);
    assertHolds(open(WORLD), expected, List.of(region));
  }

  @Test
  void theFixtureCoversEveryEdgeHeightSliceAndPaletteWidth() {
    Map<SpatialWorldFixture.Cell, Object> expected = expected();
    Set<Integer> xs = new HashSet<>();
    Set<Integer> zs = new HashSet<>();
    Set<Integer> ys = new HashSet<>();
    Map<Class<?>, Integer> perType = new HashMap<>();
    Map<String, Set<Class<?>>> perBlock = new HashMap<>();
    for (SpatialWorldFixture.Cell cell : expected.keySet()) {
      xs.add(cell.x());
      zs.add(cell.z());
      ys.add(cell.y());
      perType.merge(cell.type(), 1, Integer::sum);
      perBlock.computeIfAbsent(cell.x() + "," + cell.y() + "," + cell.z(), key -> new HashSet<>()).add(cell.type());
    }
    for (int edge : SpatialWorldFixture.EDGES) {
      assertThat(xs).contains(edge);
      assertThat(zs).contains(edge);
    }
    for (int height : SpatialWorldFixture.HEIGHTS) {
      assertThat(ys).contains(height);
    }
    assertThat(perType.keySet()).containsExactlyInAnyOrderElementsOf(SpatialWorldFixture.TYPES);
    assertThat(perBlock.values()).anyMatch(types -> types.size() == SpatialWorldFixture.TYPES.size());
    Map<Integer, Set<Object>> stampsPerSection = new TreeMap<>();
    for (Map.Entry<SpatialWorldFixture.Cell, Object> entry : within(expected, List.of(SpatialWorldFixture.PALETTE)).entrySet()) {
      if (entry.getValue() instanceof PlacementStamp stamp) {
        stampsPerSection.computeIfAbsent(stamp.getPlacedAt(), key -> new HashSet<>()).add(stamp);
      }
    }
    assertThat(stampsPerSection.values().stream().map(Set::size))
        .containsExactlyElementsOf(Arrays.stream(SpatialWorldFixture.PALETTE_SIZES).boxed().toList());
  }

  @Test
  void removedValuesStayRemovedAndOverwrittenValuesKeepTheLastWrite() throws IOException {
    installWorld(WORLD, List.of(SpatialWorldFixture.REMOVED));
    int baseX = SpatialWorldFixture.REMOVED.x() << 9;
    int baseZ = SpatialWorldFixture.REMOVED.z() << 9;

    WorldData data = open(WORLD);

    for (int index = 0; index < 10; index++) {
      assertThat(data.get(baseX + index, 10, baseZ, Earnings.class)).isNull();
      PlacementStamp stamp = data.get(baseX + 16 + index, 10, baseZ, PlacementStamp.class);
      assertThat(stamp).isEqualTo(index % 2 == 0 ? new PlacementStamp(index, index, index) : null);
    }
    assertThat(data.get(baseX + 32, 10, baseZ, Earnings.class)).isEqualTo(new Earnings(2));
    assertThat(data.get(baseX + 64, 10, baseZ, PlacementStamp.class)).isEqualTo(new PlacementStamp(19, 0, 0));
    assertThat(data.get(baseX + 80, 10, baseZ, Earnings.class)).isNull();
    for (int type = 0; type < SpatialWorldFixture.WRITTEN_TYPES.size(); type++) {
      Class<?> written = SpatialWorldFixture.WRITTEN_TYPES.get(type);
      if (written == SpatialWorldFixture.Scaffold.class) {
        continue;
      }
      Object read = data.get(baseX + 48, 10, baseZ, written);
      if (type % 2 == 0) {
        assertThat(read).isNull();
      } else {
        assertThat(SpatialWorldFixture.same(SpatialWorldFixture.sample(type, 300 + type, this::item), read)).isTrue();
      }
    }
  }

  @ParameterizedTest
  @FieldSource("SECTIONS")
  void aSectionWrittenBySpatialReadsBackCellForCell(String name) throws IOException {
    byte[] spatial = resource(SpatialWorldFixture.sectionResource(name));
    Map<SpatialWorldFixture.Cell, Object> expected = SpatialWorldFixture.replay(SpatialWorldFixture.sectionOps(name, this::item));

    Matter section = readSpatial(spatial);
    assertSection(section, expected);

    ByteArrayOutputStream rewritten = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(rewritten)) {
      section.writeDos(out);
    }
    Matter reread = WorldData.readSection(CountingDataInputStream.wrap(new ByteArrayInputStream(rewritten.toByteArray())));
    assertSection(reread, expected);
  }

  @Test
  void aSliceSpatialSavedEmptyIsDroppedWhenTheSectionIsRewritten() throws IOException {
    Matter section = readSpatial(resource(SpatialWorldFixture.sectionResource("empty")));
    assertThat(section.hasSlice(Earnings.class)).isTrue();

    ByteArrayOutputStream rewritten = new ByteArrayOutputStream();
    try (DataOutputStream out = new DataOutputStream(rewritten)) {
      section.writeDos(out);
    }

    Matter reread = WorldData.readSection(CountingDataInputStream.wrap(new ByteArrayInputStream(rewritten.toByteArray())));
    assertThat(reread.getSliceMap()).isEmpty();
  }

  @Test
  void emptyTruncatedAndGarbageRegionsAreSetAsideWhileTheRestMigrates() throws IOException {
    List<SpatialWorldFixture.Region> healthy = List.of(new SpatialWorldFixture.Region(-1, -1), SpatialWorldFixture.VALUES);
    installWorld(WORLD, healthy);
    byte[] source = resource(SpatialWorldFixture.resource(SpatialWorldFixture.REMOVED));
    byte[] garbage = new byte[4_096];
    new java.util.Random(11L).nextBytes(garbage);
    byte[] trailing = Arrays.copyOf(source, source.length + 1);
    Map<SpatialWorldFixture.Region, byte[]> broken = new HashMap<>();
    broken.put(new SpatialWorldFixture.Region(-9, -9), new byte[0]);
    broken.put(new SpatialWorldFixture.Region(-8, 9), Arrays.copyOf(source, source.length / 2));
    broken.put(new SpatialWorldFixture.Region(0, 9), Arrays.copyOf(source, source.length - 1));
    broken.put(new SpatialWorldFixture.Region(8, 9), Arrays.copyOf(source, 3));
    broken.put(new SpatialWorldFixture.Region(9, -9), garbage);
    broken.put(new SpatialWorldFixture.Region(9, 9), trailing);
    for (Map.Entry<SpatialWorldFixture.Region, byte[]> entry : broken.entrySet()) {
      install(WORLD, entry.getKey(), entry.getValue());
    }

    WorldData data = open(WORLD);

    for (Map.Entry<SpatialWorldFixture.Region, byte[]> entry : broken.entrySet()) {
      SpatialWorldFixture.Region region = entry.getKey();
      assertThat(regionFile(WORLD, region)).as(region.file()).doesNotExist();
      assertThat(damagedFile(WORLD, region)).as(region.file()).hasBinaryContent(entry.getValue());
      assertThat(backupFile(WORLD, region)).as(region.file()).doesNotExist();
      int x = region.x() << 9;
      int z = region.z() << 9;
      assertThat(data.get(x, 10, z, Earnings.class)).isNull();
      data.set(x, 10, z, new Earnings(5));
      assertThat(data.get(x, 10, z, Earnings.class)).isEqualTo(new Earnings(5));
    }
    assertThat(marker(WORLD)).exists();
    assertHolds(data, within(expected(), healthy), healthy);
  }

  @Test
  void aSecondStartLeavesEveryFileAlone() throws IOException {
    installWorld(WORLD, REGIONS);
    close(open(WORLD));
    Map<String, byte[]> before = snapshot(mantleFolder(WORLD));
    Map<String, Long> stamps = modified(mantleFolder(WORLD));

    WorldData second = open(WORLD);
    assertThat(second.get(0, 64, 0, Earnings.class)).isNotNull();
    close(second);

    assertThat(snapshot(mantleFolder(WORLD)).keySet()).isEqualTo(before.keySet());
    for (Map.Entry<String, byte[]> entry : before.entrySet()) {
      assertThat(snapshot(mantleFolder(WORLD)).get(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
    }
    for (SpatialWorldFixture.Region region : REGIONS) {
      String backup = SpatialMantleMigration.BACKUP_FOLDER + "/" + regionFile(WORLD, region).getName();
      assertThat(modified(mantleFolder(WORLD)).get(backup)).as(backup).isEqualTo(stamps.get(backup));
    }
  }

  @Test
  void aWorldWithNoSavedDataGetsOnlyTheMarker() {
    WorldData data = open(WORLD);

    assertThat(mantleFolder(WORLD).list()).containsExactly(SpatialMantleMigration.MARKER);
    assertThat(data.get(0, 64, 0, Earnings.class)).isNull();
    data.set(0, 64, 0, new Earnings(4));
    close(data);
    assertThat(open(WORLD).get(0, 64, 0, Earnings.class)).isEqualTo(new Earnings(4));
  }

  @Test
  void aWorldAlreadyInTheNewFormatIsNotRewrittenWhenItsMarkerIsMissing() throws IOException {
    WorldData writer = open(WORLD);
    apply(writer, SpatialWorldFixture.ops(this::item).stream()
        .filter(op -> op.type() != SpatialWorldFixture.Scaffold.class)
        .toList());
    close(writer);
    Files.delete(marker(WORLD).toPath());
    Map<String, byte[]> before = snapshot(mantleFolder(WORLD));
    assertThat(before).hasSize(REGIONS.size());

    WorldData reopened = open(WORLD);

    Map<String, byte[]> after = snapshot(mantleFolder(WORLD));
    after.remove(SpatialMantleMigration.MARKER);
    assertThat(after.keySet()).isEqualTo(before.keySet());
    for (Map.Entry<String, byte[]> entry : before.entrySet()) {
      assertThat(after.get(entry.getKey())).as(entry.getKey()).isEqualTo(entry.getValue());
    }
    assertThat(marker(WORLD)).exists();
    assertHolds(reopened, expected(), REGIONS);
  }

  @Test
  void aMigrationInterruptedPartWayFinishesOnTheNextStart() throws IOException {
    Map<SpatialWorldFixture.Region, byte[]> originals = installWorld("finished", REGIONS);
    close(open("finished"));
    Path stale = new File(mantleFolder(WORLD), regionFile(WORLD, REGIONS.get(0)).getName() + ".migrating").toPath();
    Files.write(stale, new byte[] {1, 2, 3});
    Map<SpatialWorldFixture.Region, byte[]> converted = new HashMap<>();
    for (int index = 0; index < REGIONS.size(); index++) {
      SpatialWorldFixture.Region region = REGIONS.get(index);
      byte[] original = originals.get(region);
      if (index % 3 == 0) {
        // Converted before the interruption: new bytes in place, backup written.
        byte[] current = Files.readAllBytes(regionFile("finished", region).toPath());
        converted.put(region, current);
        install(WORLD, region, current);
        Files.createDirectories(backupFile(WORLD, region).getParentFile().toPath());
        Files.write(backupFile(WORLD, region).toPath(), original);
      } else if (index % 3 == 1) {
        // Interrupted between writing the backup and replacing the region.
        install(WORLD, region, original);
        Files.createDirectories(backupFile(WORLD, region).getParentFile().toPath());
        Files.write(backupFile(WORLD, region).toPath(), original);
      } else {
        install(WORLD, region, original);
      }
    }
    assertThat(marker(WORLD)).doesNotExist();

    WorldData resumed = open(WORLD);

    assertThat(marker(WORLD)).exists();
    assertHolds(resumed, expected(), REGIONS);
    for (SpatialWorldFixture.Region region : REGIONS) {
      assertThat(backupFile(WORLD, region)).as(region.file()).hasBinaryContent(originals.get(region));
    }
    close(resumed);
    for (Map.Entry<SpatialWorldFixture.Region, byte[]> entry : converted.entrySet()) {
      assertThat(regionFile(WORLD, entry.getKey())).as(entry.getKey().file()).hasBinaryContent(entry.getValue());
    }
    assertThat(new File(mantleFolder(WORLD), SpatialMantleMigration.DAMAGED_FOLDER)).doesNotExist();
    assertThat(stale).hasBinaryContent(new byte[] {1, 2, 3});
    assertHolds(open(WORLD), expected(), REGIONS);
  }

  @Test
  void spatialAndNewFormatRegionsInOneFolderBothSurvive() throws IOException {
    WorldData writer = open("donor");
    writer.set(5_000, 70, 5_000, new Earnings(33));
    writer.set(5_000, 70, 5_000, new PlacementStamp(7, 8, 9));
    close(writer);
    SpatialWorldFixture.Region modern = new SpatialWorldFixture.Region(5_000 >> 9, 5_000 >> 9);
    byte[] current = Files.readAllBytes(regionFile("donor", modern).toPath());
    install(WORLD, modern, current);
    installWorld(WORLD, SpatialWorldFixture.EDGE_REGIONS);

    WorldData data = open(WORLD);

    assertHolds(data, within(expected(), SpatialWorldFixture.EDGE_REGIONS), SpatialWorldFixture.EDGE_REGIONS);
    assertThat(data.get(5_000, 70, 5_000, Earnings.class)).isEqualTo(new Earnings(33));
    assertThat(data.get(5_000, 70, 5_000, PlacementStamp.class)).isEqualTo(new PlacementStamp(7, 8, 9));
    assertThat(backupFile(WORLD, modern)).doesNotExist();
    close(data);
    assertThat(regionFile(WORLD, modern)).hasBinaryContent(current);
  }

  @Test
  void aRegionUnderTheOlderFileNameIsMigratedInPlace() throws IOException {
    SpatialWorldFixture.Region region = SpatialWorldFixture.VALUES;
    byte[] original = resource(SpatialWorldFixture.resource(region));
    File modern = regionFile(WORLD, region);
    File legacy = new File(mantleFolder(WORLD), modern.getName().replaceFirst("^pv\\.", "p."));
    Files.write(legacy.toPath(), original);

    WorldData data = open(WORLD);

    assertThat(new File(mantleFolder(WORLD), SpatialMantleMigration.BACKUP_FOLDER + "/" + legacy.getName())).hasBinaryContent(original);
    assertHolds(data, within(expected(), List.of(region)), List.of(region));
  }

  @Test
  void filesThatAreNotRegionsAreIgnored() throws IOException {
    installWorld(WORLD, List.of(SpatialWorldFixture.VALUES));
    Map<String, byte[]> strays = Map.of(
        "notes.txt", new byte[] {9},
        "pv.12.ttp.lz4b.bak", new byte[0],
        "pv.x.ttp.lz4b", new byte[] {1, 2},
        "region.ttp", new byte[] {3});
    for (Map.Entry<String, byte[]> stray : strays.entrySet()) {
      Files.write(new File(mantleFolder(WORLD), stray.getKey()).toPath(), stray.getValue());
    }
    Files.createDirectories(new File(mantleFolder(WORLD), "pv.77.ttp.lz4b").toPath());

    open(WORLD);

    for (Map.Entry<String, byte[]> stray : strays.entrySet()) {
      assertThat(new File(mantleFolder(WORLD), stray.getKey())).hasBinaryContent(stray.getValue());
    }
    assertThat(new File(mantleFolder(WORLD), "pv.77.ttp.lz4b")).isDirectory();
    assertThat(new File(mantleFolder(WORLD), SpatialMantleMigration.DAMAGED_FOLDER)).doesNotExist();
    assertThat(marker(WORLD)).exists();
  }

  @Test
  void aSecondDamagedFileWithTheSameNameDoesNotReplaceTheFirst() throws IOException {
    SpatialWorldFixture.Region region = new SpatialWorldFixture.Region(4, 4);
    File earlier = damagedFile(WORLD, region);
    Files.createDirectories(earlier.getParentFile().toPath());
    Files.write(earlier.toPath(), new byte[] {7, 7, 7});
    install(WORLD, region, new byte[] {8, 8});

    open(WORLD);

    assertThat(earlier).hasBinaryContent(new byte[] {7, 7, 7});
    File[] damaged = earlier.getParentFile().listFiles();
    assertThat(damaged).hasSize(2);
    for (File file : damaged) {
      if (!file.equals(earlier)) {
        assertThat(file.getName()).startsWith(earlier.getName() + ".");
        assertThat(file).hasBinaryContent(new byte[] {8, 8});
      }
    }
  }

  @Test
  void eachWorldFolderMigratesIndependently() throws IOException {
    installWorld("overworld", List.of(SpatialWorldFixture.VALUES));
    installWorld("the_nether", List.of(SpatialWorldFixture.REMOVED));
    install("the_end", new SpatialWorldFixture.Region(0, 0), new byte[] {1});

    WorldData end = open("the_end");
    WorldData overworld = open("overworld");
    WorldData nether = open("the_nether");

    assertThat(damagedFile("the_end", new SpatialWorldFixture.Region(0, 0))).exists();
    assertThat(end.get(0, 0, 0, Earnings.class)).isNull();
    assertHolds(overworld, within(expected(), List.of(SpatialWorldFixture.VALUES)), List.of(SpatialWorldFixture.VALUES));
    assertHolds(nether, within(expected(), List.of(SpatialWorldFixture.REMOVED)), List.of(SpatialWorldFixture.REMOVED));
    assertThat(stored(overworld, List.of(SpatialWorldFixture.REMOVED))).isEmpty();
  }

  @Test
  void theMigrationDecodesNoItems() throws IOException {
    installWorld(WORLD, REGIONS);
    Map<SpatialWorldFixture.Cell, Object> expected = expected();
    assertThat(expected.values()).anyMatch(value -> value instanceof PlinthSlotItem slot && !slot.lost());
    assertThat(expected.values()).anyMatch(value -> value instanceof NodeFaces faces
        && faces.records().stream().anyMatch(record -> !record.filterEntries().isEmpty()));
    decodeCalls.clear();

    WorldData migrated = open(WORLD);
    close(migrated);

    assertThat(new File(mantleFolder(WORLD), SpatialMantleMigration.BACKUP_FOLDER).list()).hasSize(REGIONS.size());
    assertThat(decodeCalls).as("items decoded while migrating and saving").isEmpty();
    nms().verify(() -> NMS.deserializeStack(anyString()), never());

    WorldData reopened = open(WORLD);
    assertThat(reopened.get(SpatialWorldFixture.VALUES.x() << 9, 70, (SpatialWorldFixture.VALUES.z() << 9) + 4, PlinthSlotItem.class)).isNotNull();
    assertThat(decodeCalls).as("items decoded while loading a region").isEmpty();
    assertHolds(reopened, expected, REGIONS);
    assertThat(decodeCalls).as("items decoded once their values are used").isNotEmpty();
  }

  @Test
  void aPlinthItemThatCannotBeDecodedMigratesWithItsBytesUnchanged() throws IOException {
    SpatialWorldFixture.Region region = new SpatialWorldFixture.Region(0, 0);
    UUID id = new UUID(1L, 2L);
    byte[] fragile = itemBytes("fragile");
    Map<SpatialWorldFixture.Cell, Object> cells = new HashMap<>();
    cells.put(new SpatialWorldFixture.Cell(1, 64, 1, PlinthSlotItem.class), PlinthSlotItem.of(id, item("fragile")));
    cells.put(new SpatialWorldFixture.Cell(2, 64, 1, Earnings.class), new Earnings(3));
    byte[] original = LegacySpatialWriter.region(region, SpatialWorldFixture.MIN_HEIGHT, SpatialWorldFixture.MAX_HEIGHT, cells);
    install(WORLD, region, original);
    assertThat(holds(original, framed(fragile))).as("the Spatial file holds the item behind its length").isTrue();
    undecodable = Set.of("fragile");
    decodeCalls.clear();

    WorldData data = open(WORLD);

    assertThat(decodeCalls).isEmpty();
    assertThat(backupFile(WORLD, region)).hasBinaryContent(original);
    assertThat(damagedFile(WORLD, region)).doesNotExist();
    assertThat(holds(Files.readAllBytes(regionFile(WORLD, region).toPath()), framed(fragile))).as("the migrated file holds the same item bytes").isTrue();
    assertThat(data.get(2, 64, 1, Earnings.class)).isEqualTo(new Earnings(3));
    PlinthSlotItem migrated = data.get(1, 64, 1, PlinthSlotItem.class);
    assertThat(migrated.id()).isEqualTo(id);
    assertThat(migrated.stored().bytes()).isEqualTo(fragile);
    assertThat(migrated.lost()).isFalse();
    assertThat(migrated.readable()).isFalse();
    assertThat(migrated.item()).isNull();

    for (int cycle = 0; cycle < SAVE_CYCLES; cycle++) {
      data.set(3, 64, 1, new Earnings(cycle));
      close(data);
      assertThat(holds(Files.readAllBytes(regionFile(WORLD, region).toPath()), framed(fragile))).as("file after save %d", cycle).isTrue();
      data = open(WORLD);
      PlinthSlotItem reloaded = data.get(1, 64, 1, PlinthSlotItem.class);
      assertThat(reloaded.readable()).as("save %d", cycle).isFalse();
      assertThat(reloaded.stored().bytes()).as("save %d", cycle).isEqualTo(fragile);
      assertThat(reloaded).as("save %d", cycle).isEqualTo(migrated);
    }
    close(data);

    undecodable = Set.of();
    PlinthSlotItem recovered = open(WORLD).get(1, 64, 1, PlinthSlotItem.class);
    assertThat(recovered.id()).isEqualTo(id);
    assertThat(recovered.readable()).isTrue();
    assertThat(recovered.item()).isSameAs(item("fragile"));
    assertThat(recovered.stored().bytes()).isEqualTo(fragile);
  }

  @Test
  void aNodeFilterItemThatCannotBeDecodedMigratesWithItsBytesUnchanged() throws IOException {
    SpatialWorldFixture.Region region = new SpatialWorldFixture.Region(0, 0);
    UUID owner = new UUID(1L, 2L);
    UUID slot = new UUID(3L, 4L);
    byte[] sturdy = itemBytes("sturdy");
    byte[] fragile = itemBytes("fragile");
    NodeRecord guard = NodeRecord.builder(owner, NodeType.GUARD, BlockFace.NORTH).priority(5).build();
    Map<SpatialWorldFixture.Cell, Object> cells = new HashMap<>();
    cells.put(new SpatialWorldFixture.Cell(1, 64, 1, NodeFaces.class), NodeFaces.EMPTY.with(guard).with(
        NodeRecord.builder(owner, NodeType.FILTER, BlockFace.UP).filterItems(List.of(item("sturdy"), item("fragile")))
            .filterTags(List.of("#logs")).build()));
    cells.put(new SpatialWorldFixture.Cell(200, 64, 200, Earnings.class), new Earnings(3));
    cells.put(new SpatialWorldFixture.Cell(300, 70, 9, PlinthSlotItem.class), PlinthSlotItem.of(slot, item("sturdy")));
    byte[] original = LegacySpatialWriter.region(region, SpatialWorldFixture.MIN_HEIGHT, SpatialWorldFixture.MAX_HEIGHT, cells);
    install(WORLD, region, original);
    assertThat(holds(original, join(framed(sturdy), framed(fragile)))).as("the Spatial file holds both filter items behind their lengths").isTrue();
    undecodable = Set.of("fragile");
    decodeCalls.clear();

    WorldData data;
    NodeRecord filter;
    try (MockedStatic<Adapt> log = mockStatic(Adapt.class, CALLS_REAL_METHODS)) {
      data = open(WORLD);
      log.verify(() -> Adapt.warn(anyString(), any(Throwable.class)), never());
      log.verify(() -> Adapt.warn(anyString()), never());
      assertThat(decodeCalls).isEmpty();

      filter = data.get(1, 64, 1, NodeFaces.class).get(BlockFace.UP);
      for (int use = 0; use < 3; use++) {
        assertThat(filter.filterItems()).containsExactly(item("sturdy"));
        assertThat(filter.unreadableFilters()).isEqualTo(1);
        assertThat(data.get(1, 64, 1, NodeFaces.class).get(BlockFace.UP).unreadableFilters()).isEqualTo(1);
      }
      log.verify(() -> Adapt.warn(and(and(contains("filter node on face UP"), contains(owner.toString())), contains("could not be read")),
          any(IllegalStateException.class)), times(1));
      log.verify(() -> Adapt.warn(anyString(), any(Throwable.class)), times(1));
      log.verify(() -> Adapt.warn(anyString()), never());
    }

    assertThat(damagedFile(WORLD, region)).doesNotExist();
    assertThat(backupFile(WORLD, region)).hasBinaryContent(original);
    assertThat(holds(Files.readAllBytes(regionFile(WORLD, region).toPath()), join(framed(sturdy), framed(fragile))))
        .as("the migrated file holds the same filter item bytes").isTrue();
    assertThat(data.get(200, 64, 200, Earnings.class)).isEqualTo(new Earnings(3));
    assertThat(data.get(300, 70, 9, PlinthSlotItem.class).item()).isSameAs(item("sturdy"));
    assertThat(data.get(1, 64, 1, NodeFaces.class).get(BlockFace.NORTH)).isEqualTo(guard);
    assertThat(filter.owner()).isEqualTo(owner);
    assertThat(filter.type()).isEqualTo(NodeType.FILTER);
    assertThat(filter.filterTags()).containsExactly("#logs");
    assertThat(filter.filterCount()).isEqualTo(3);
    assertThat(filter.filterEntries()).extracting(StoredItem::bytes).containsExactly(sturdy, fragile);
    assertThat(NodeFilters.accepts(filter, item("sturdy"), NO_TAGS, (stored, offered) -> stored == offered)).isTrue();
    assertThat(NodeFilters.accepts(filter, item("fragile"), NO_TAGS, (stored, offered) -> stored == offered)).isFalse();
    assertThat(NodeFilters.accepts(filter.toBuilder().whitelist(false).build(), item("other"), NO_TAGS, (stored, offered) -> stored == offered))
        .as("a blacklist that cannot read an entry accepts nothing").isFalse();

    NodeFaces migrated = data.get(1, 64, 1, NodeFaces.class);
    for (int cycle = 0; cycle < SAVE_CYCLES; cycle++) {
      data.set(3, 64, 1, new Earnings(cycle));
      close(data);
      assertThat(holds(Files.readAllBytes(regionFile(WORLD, region).toPath()), join(framed(sturdy), framed(fragile)))).as("file after save %d", cycle).isTrue();
      data = open(WORLD);
      NodeFaces reloaded = data.get(1, 64, 1, NodeFaces.class);
      assertThat(reloaded).as("save %d", cycle).isEqualTo(migrated);
      assertThat(reloaded.get(BlockFace.UP).filterEntries()).as("save %d", cycle).extracting(StoredItem::bytes).containsExactly(sturdy, fragile);
      assertThat(reloaded.get(BlockFace.UP).unreadableFilters()).as("save %d", cycle).isEqualTo(1);
    }
    close(data);

    undecodable = Set.of();
    NodeRecord recovered = open(WORLD).get(1, 64, 1, NodeFaces.class).get(BlockFace.UP);
    assertThat(recovered.unreadableFilters()).isZero();
    assertThat(recovered.filterItems()).containsExactly(item("sturdy"), item("fragile"));
    assertThat(recovered.filterEntries()).extracting(StoredItem::bytes).containsExactly(sturdy, fragile);
    assertThat(NodeFilters.accepts(recovered, item("fragile"), NO_TAGS, (stored, offered) -> stored == offered)).isTrue();
    assertThat(NodeFilters.accepts(recovered, item("other"), NO_TAGS, (stored, offered) -> stored == offered)).isFalse();
  }

  @Test
  void zeroLengthMarkersInASpatialFileMigrateAsTheyAre() throws IOException {
    SpatialWorldFixture.Region region = new SpatialWorldFixture.Region(0, 0);
    UUID id = new UUID(5L, 6L);
    UUID owner = new UUID(7L, 8L);
    Map<SpatialWorldFixture.Cell, Object> cells = new HashMap<>();
    cells.put(new SpatialWorldFixture.Cell(1, 64, 1, PlinthSlotItem.class), PlinthSlotItem.stored(id, new byte[0]));
    cells.put(new SpatialWorldFixture.Cell(2, 64, 1, NodeFaces.class), NodeFaces.EMPTY.with(NodeRecord.builder(owner, NodeType.FILTER, BlockFace.UP)
        .filterEntries(List.of(StoredItem.of(item("sturdy")), StoredItem.stored(new byte[0]))).build()));
    install(WORLD, region, LegacySpatialWriter.region(region, SpatialWorldFixture.MIN_HEIGHT, SpatialWorldFixture.MAX_HEIGHT, cells));

    WorldData data = open(WORLD);
    close(data);
    data = open(WORLD);

    assertThat(damagedFile(WORLD, region)).doesNotExist();
    PlinthSlotItem slot = data.get(1, 64, 1, PlinthSlotItem.class);
    assertThat(slot.id()).isEqualTo(id);
    assertThat(slot.lost()).isTrue();
    assertThat(slot.readable()).isFalse();
    NodeRecord filter = data.get(2, 64, 1, NodeFaces.class).get(BlockFace.UP);
    assertThat(filter.filterItems()).containsExactly(item("sturdy"));
    assertThat(filter.unreadableFilters()).isEqualTo(1);
    assertThat(filter.filterEntries().get(1).isEmpty()).isTrue();
  }

  @Test
  void aRegionSavedForATallerWorldIsSetAsideNotTruncated() throws IOException {
    SpatialWorldFixture.Region region = SpatialWorldFixture.VALUES;
    byte[] original = resource(SpatialWorldFixture.resource(region));
    install(WORLD, region, original);

    WorldData data = open(world(WORLD, 0, 256));

    Stream<File> kept = Stream.of(regionFile(WORLD, region), backupFile(WORLD, region), damagedFile(WORLD, region));
    assertThat(kept.filter(File::exists).anyMatch(file -> sameBytes(file, original)))
        .as("the original bytes survive somewhere in the folder")
        .isTrue();
    assertThat(data).isNotNull();
  }

  // An item as both formats frame it: a four-byte length, then the bytes.
  private static byte[] framed(byte[] item) {
    return ByteBuffer.allocate(Integer.BYTES + item.length).putInt(item.length).put(item).array();
  }

  private static byte[] join(byte[] first, byte[] second) {
    return ByteBuffer.allocate(first.length + second.length).put(first).put(second).array();
  }

  private static boolean holds(byte[] file, byte[] run) {
    return new String(file, StandardCharsets.ISO_8859_1).contains(new String(run, StandardCharsets.ISO_8859_1));
  }

  private static boolean sameBytes(File file, byte[] expected) {
    try {
      return Arrays.equals(Files.readAllBytes(file.toPath()), expected);
    } catch (IOException failure) {
      throw new IllegalStateException(failure);
    }
  }

  private Map<SpatialWorldFixture.Cell, Object> expected() {
    return SpatialWorldFixture.replay(SpatialWorldFixture.ops(this::item));
  }

  private Map<SpatialWorldFixture.Region, byte[]> installWorld(String key, List<SpatialWorldFixture.Region> regions) throws IOException {
    Map<SpatialWorldFixture.Region, byte[]> originals = new HashMap<>();
    for (SpatialWorldFixture.Region region : regions) {
      byte[] bytes = resource(SpatialWorldFixture.resource(region));
      install(key, region, bytes);
      originals.put(region, bytes);
    }
    return originals;
  }

  private static Matter readSpatial(byte[] bytes) throws IOException {
    try (CountingDataInputStream in = CountingDataInputStream.wrap(new ByteArrayInputStream(bytes))) {
      Matter section = WorldData.readSpatialSection(in);
      assertThat(in.count()).isEqualTo(bytes.length);
      return section;
    }
  }

  private static void assertSection(Matter section, Map<SpatialWorldFixture.Cell, Object> expected) {
    for (Class<?> type : SpatialWorldFixture.TYPES) {
      MatterSlice<?> slice = section.getSlice(type);
      for (int x = 0; x < 16; x++) {
        for (int y = 0; y < 16; y++) {
          for (int z = 0; z < 16; z++) {
            Object want = expected.get(new SpatialWorldFixture.Cell(x, y, z, type));
            Object read = slice == null ? null : slice.get(x, y, z);
            assertThat(SpatialWorldFixture.same(want, read))
                .as("%s at %d,%d,%d holds %s, expected %s", type.getSimpleName(), x, y, z, read, want)
                .isTrue();
          }
        }
      }
    }
  }

  private static Map<String, byte[]> snapshot(File folder) throws IOException {
    Map<String, byte[]> files = new TreeMap<>();
    try (Stream<Path> paths = Files.walk(folder.toPath())) {
      for (Path path : paths.filter(Files::isRegularFile).toList()) {
        files.put(folder.toPath().relativize(path).toString(), Files.readAllBytes(path));
      }
    }
    return files;
  }

  private static Map<String, Long> modified(File folder) throws IOException {
    Map<String, Long> stamps = new TreeMap<>();
    try (Stream<Path> paths = Files.walk(folder.toPath())) {
      for (Path path : paths.filter(Files::isRegularFile).toList()) {
        stamps.put(folder.toPath().relativize(path).toString(), path.toFile().lastModified());
      }
    }
    return stamps;
  }
}

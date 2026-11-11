package art.arcane.adapt.api.data;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.data.unit.Earnings;
import art.arcane.adapt.api.data.unit.PlacementStamp;
import art.arcane.adapt.content.matter.BrewingStandOwner;
import art.arcane.adapt.nms.NMS;
import art.arcane.volmlib.util.mantle.Mantle;
import art.arcane.volmlib.util.matter.IrisMatter;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class SpatialMantleMigrationSafetyTest extends AdaptTestBase {
  private static final String WORLD_NAME = "survival_main";

  private final List<WorldData> opened = new ArrayList<>();
  private final List<LogRecord> records = new ArrayList<>();
  private MockedStatic<NMS> nms;
  private MockedStatic<ComponentLogger> componentLogger;
  private Logger logger;
  private Handler handler;
  private World world;

  @BeforeEach
  void setUp() {
    ItemStack item = mock(ItemStack.class);
    lenient().when(item.clone()).thenReturn(item);
    nms = mockStatic(NMS.class);
    nms.when(() -> NMS.deserializeStack(anyString())).thenReturn(item);
    nms.when(() -> NMS.serializeStack(any(ItemStack.class))).thenReturn("item");
    componentLogger = mockStatic(ComponentLogger.class);
    componentLogger.when(ComponentLogger::logger).thenReturn(null);
    logger = Logger.getLogger("SpatialMantleMigrationSafetyTest-" + UUID.randomUUID());
    logger.setUseParentHandlers(false);
    logger.setLevel(Level.ALL);
    handler = new Handler() {
      @Override
      public synchronized void publish(LogRecord record) {
        records.add(record);
      }

      @Override
      public void flush() {
      }

      @Override
      public void close() {
      }
    };
    logger.addHandler(handler);
    lenient().when(plugin.getLogger()).thenReturn(logger);
    world = mock(World.class);
    lenient().when(world.getUID()).thenReturn(UUID.fromString("00000000-0000-0000-0000-0000000000f1"));
    lenient().when(world.getKey()).thenReturn(new NamespacedKey("minecraft", "overworld"));
    lenient().when(world.getName()).thenReturn(WORLD_NAME);
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
    logger.removeHandler(handler);
    componentLogger.close();
    nms.close();
  }

  @Test
  void aRegionSetAsideIsLoggedWithTheReasonAndItsNewLocation() throws IOException {
    byte[] original = fixture("earnings");
    byte[] damaged = Arrays.copyOf(original, original.length / 2);
    File region = install(damaged);

    open();

    File setAside = new File(mantleFolder(), SpatialMantleMigration.DAMAGED_FOLDER + "/" + region.getName());
    assertThat(setAside).hasBinaryContent(damaged);
    assertThat(messages(Level.WARNING))
        .anySatisfy(message -> assertThat(message).contains(region.getName()).contains("Exception"));
    assertThat(messages(Level.WARNING))
        .anySatisfy(message -> assertThat(message).contains("1 ").contains(setAside.getParentFile().getPath()));
  }

  @Test
  void aTruncatedBackupLeftByAnInterruptedRunIsRewrittenFromTheSource() throws IOException {
    byte[] original = fixture("earnings");
    File region = install(original);
    File backup = new File(mantleFolder(), SpatialMantleMigration.BACKUP_FOLDER + "/" + region.getName());
    backup.getParentFile().mkdirs();
    Files.write(backup.toPath(), Arrays.copyOf(original, original.length / 3));

    WorldData data = open();

    assertThat(backup).hasBinaryContent(original);
    assertThat(backup.getParentFile().list()).containsExactly(region.getName());
    assertThat(mantleFolder().list()).noneMatch(name -> name.endsWith(".migrating"));
    assertThat(data.get(-480, -64, 1530, Earnings.class)).isEqualTo(new Earnings(127));
  }

  @Test
  void aSpatialRegionThatAppearsAfterTheMarkerIsStillConverted() throws IOException {
    close(open());
    assertThat(new File(mantleFolder(), SpatialMantleMigration.MARKER)).exists();
    byte[] original = fixture("earnings");
    File region = install(original);

    WorldData data = open();

    assertThat(data.get(-480, -64, 1530, Earnings.class)).isEqualTo(new Earnings(127));
    assertThat(new File(mantleFolder(), SpatialMantleMigration.BACKUP_FOLDER + "/" + region.getName())).hasBinaryContent(original);
    assertThat(new File(mantleFolder(), SpatialMantleMigration.DAMAGED_FOLDER)).doesNotExist();
  }

  @Test
  void aMarkedFolderLeavesCurrentRegionsUntouched() throws IOException {
    WorldData writer = open();
    writer.set(-48, 16, 1120, new Earnings(9));
    writer.set(-48, 17, 1120, new PlacementStamp(1, 2, 3));
    assertThat(writer.savePlate(-48, 1120)).isTrue();
    close(writer);
    File region = Mantle.fileForRegion(mantleFolder(), SpatialFixtures.REGION_X, SpatialFixtures.REGION_Z);
    byte[] current = Files.readAllBytes(region.toPath());
    long modified = region.lastModified();
    records.clear();

    WorldData reopened = open();

    assertThat(region).hasBinaryContent(current);
    assertThat(region.lastModified()).isEqualTo(modified);
    assertThat(new File(mantleFolder(), SpatialMantleMigration.BACKUP_FOLDER)).doesNotExist();
    assertThat(records).isEmpty();
    assertThat(reopened.get(-48, 16, 1120, Earnings.class)).isEqualTo(new Earnings(9));
  }

  @Test
  void anAbortedMigrationWritesNoMarkerAndNamesTheWorld() throws IOException {
    install(fixture("earnings"));
    Files.write(new File(mantleFolder(), SpatialMantleMigration.BACKUP_FOLDER).toPath(), new byte[]{1});

    open();

    assertThat(new File(mantleFolder(), SpatialMantleMigration.MARKER)).doesNotExist();
    assertThat(messages(Level.SEVERE)).anySatisfy(message -> assertThat(message).contains(WORLD_NAME));
  }

  @Test
  void aScaffoldSliceInAnOldRegionIsDroppedWithoutSettingTheRegionAside() throws IOException {
    byte[] original = fixture("mixed");
    assertThat(new String(original, StandardCharsets.ISO_8859_1)).contains("ScaffoldMatter.ScaffoldData");
    File region = install(original);

    WorldData data = open();

    assertThat(new File(mantleFolder(), SpatialMantleMigration.DAMAGED_FOLDER)).doesNotExist();
    assertThat(data.get(-40, 70, 1070, Earnings.class)).isEqualTo(new Earnings(12));
    assertThat(data.get(-40, 70, 1070, PlacementStamp.class)).isEqualTo(new PlacementStamp(5, 6, 7));
    assertThat(data.get(-39, 71, 1071, BrewingStandOwner.class)).isNotNull();
    assertThat(new String(Files.readAllBytes(region.toPath()), StandardCharsets.ISO_8859_1)).doesNotContainIgnoringCase("scaffold");
    assertThat(IrisMatter.getSliceType("adapt:scaffold")).isNull();
  }

  @Test
  void theHeaderProbeTellsSpatialRegionsFromCurrentOnes() throws IOException {
    for (String name : SpatialFixtures.NAMES) {
      File spatial = new File(dataFolder, name + ".ttp");
      Files.write(spatial.toPath(), fixture(name));
      assertThat(SpatialMantleMigration.isSpatial(spatial.toPath())).as(name).isTrue();
    }
    File region = install(fixture("mixed"));
    close(open());
    assertThat(SpatialMantleMigration.isSpatial(region.toPath())).isFalse();
    File truncated = new File(dataFolder, "truncated.ttp");
    Files.write(truncated.toPath(), Arrays.copyOf(fixture("earnings"), 9));
    assertThat(SpatialMantleMigration.isSpatial(truncated.toPath())).isFalse();
  }

  @Test
  void aWorldLoadMigratesItsRegionsBeforeAnythingAsksForData() throws IOException {
    byte[] original = fixture("earnings");
    File region = install(original);

    try {
      new WorldDataLoader().on(new WorldLoadEvent(world));

      assertThat(new File(mantleFolder(), SpatialMantleMigration.MARKER)).exists();
      assertThat(Files.readAllBytes(region.toPath())).isNotEqualTo(original);
      assertThat(SpatialMantleMigration.isSpatial(region.toPath())).isFalse();
      List<String> info = messages(Level.INFO);
      assertThat(info).hasSize(2);
      assertThat(info.get(0)).contains("Checking 1 mantle region file(s)").contains(WORLD_NAME);
      assertThat(info.get(1)).contains("1 converted").contains(SpatialMantleMigration.BACKUP_FOLDER);
      records.clear();
      WorldDataLoader.load(List.of(world));
      assertThat(records).isEmpty();
    } finally {
      WorldData.stop();
    }
  }

  @Test
  void twoThreadsOpeningTheSameWorldMigrateItOnce() throws Exception {
    byte[] original = fixture("earnings");
    File region = install(original);
    CyclicBarrier start = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      List<Future<WorldData>> results = new ArrayList<>();
      for (int index = 0; index < 2; index++) {
        results.add(pool.submit(() -> {
          start.await(10, TimeUnit.SECONDS);
          return new WorldData(world);
        }));
      }
      for (Future<WorldData> result : results) {
        opened.add(result.get(30, TimeUnit.SECONDS));
      }
    } finally {
      pool.shutdownNow();
    }

    assertThat(messages(Level.SEVERE)).isEmpty();
    assertThat(new File(mantleFolder(), SpatialMantleMigration.BACKUP_FOLDER + "/" + region.getName())).hasBinaryContent(original);
    assertThat(new File(mantleFolder(), SpatialMantleMigration.DAMAGED_FOLDER)).doesNotExist();
    assertThat(mantleFolder().list()).noneMatch(name -> name.endsWith(".migrating"));
    assertThat(opened.get(0).get(-480, -64, 1530, Earnings.class)).isEqualTo(new Earnings(127));
  }

  private List<String> messages(Level level) {
    List<String> messages = new ArrayList<>();
    synchronized (handler) {
      for (LogRecord record : records) {
        if (record.getLevel() == level) {
          messages.add(record.getMessage());
        }
      }
    }
    return messages;
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

  private static byte[] fixture(String name) throws IOException {
    try (InputStream in = SpatialMantleMigrationSafetyTest.class.getResourceAsStream(SpatialFixtures.resource(name))) {
      assertThat(in).as(name).isNotNull();
      return in.readAllBytes();
    }
  }
}

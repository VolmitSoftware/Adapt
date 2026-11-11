package art.arcane.adapt.api.data;

import art.arcane.adapt.api.data.unit.Earnings;
import art.arcane.adapt.api.data.unit.PlacementStamp;
import art.arcane.adapt.content.matter.BrewingStandOwner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Threads released together write and read one world. Every thread owns its blocks or its slice, so each
 * block has exactly one correct final value. Values carry no item stacks: the static NMS stand-in exists
 * only on the test thread.
 */
@Timeout(120)
class WorldDataConcurrencyTest extends MatterWorldTestBase {
  private static final int THREADS = 8;
  private static final int[] LAYERS = {SpatialWorldFixture.MIN_HEIGHT, 0, SpatialWorldFixture.MAX_HEIGHT - 1};
  // Four chunks that meet at the corner of four regions, then four more inside one of those regions.
  private static final int[][] CHUNKS = {{-1, -1}, {-1, 0}, {0, -1}, {0, 0}, {1, 0}, {0, 1}, {1, 1}, {2, 0}};

  @Test
  void writersSharingOneNewSectionLoseNoValue() throws Exception {
    WorldData data = open(WORLD);
    Map<SpatialWorldFixture.Cell, Object> expected = new LinkedHashMap<>();
    List<Callable<Void>> tasks = new ArrayList<>();
    for (int thread = 0; thread < THREADS; thread++) {
      int owner = thread;
      tasks.add(() -> {
        for (int position = owner; position < 4096; position += THREADS) {
          data.set(-16 + (position & 15), (position >> 4) & 15, -16 + (position >> 8), stamp(position));
          data.set(-16 + (position & 15), (position >> 4) & 15, -16 + (position >> 8), new Earnings(position % 100));
        }
        return null;
      });
    }
    for (int position = 0; position < 4096; position++) {
      int x = -16 + (position & 15);
      int y = (position >> 4) & 15;
      int z = -16 + (position >> 8);
      expected.put(new SpatialWorldFixture.Cell(x, y, z, PlacementStamp.class), stamp(position));
      expected.put(new SpatialWorldFixture.Cell(x, y, z, Earnings.class), new Earnings(position % 100));
    }

    runTogether(tasks);

    assertChunksHold(data, expected, expected.keySet());
    close(data);
    assertChunksHold(open(WORLD), expected, expected.keySet());
  }

  @Test
  void writersOnNeighbouringChunksAndRegionsLoseNoValueWhileReadersSeeOnlyWrittenValues() throws Exception {
    WorldData data = open(WORLD);
    Map<SpatialWorldFixture.Cell, Object> expected = chunkLayers();
    CountDownLatch writing = new CountDownLatch(CHUNKS.length);
    List<Callable<Void>> tasks = new ArrayList<>();
    for (int[] chunk : CHUNKS) {
      tasks.add(() -> {
        try {
          writeChunkLayers(data, chunk);
        } finally {
          writing.countDown();
        }
        return null;
      });
    }
    for (int reader = 0; reader < 4; reader++) {
      tasks.add(() -> {
        boolean last = false;
        while (!last) {
          last = writing.getCount() == 0;
          for (Map.Entry<SpatialWorldFixture.Cell, Object> entry : expected.entrySet()) {
            SpatialWorldFixture.Cell cell = entry.getKey();
            Object read = data.get(cell.x(), cell.y(), cell.z(), cell.type());
            if (read != null || last) {
              assertThat(read).as("%s", cell).isEqualTo(entry.getValue());
            }
          }
        }
        return null;
      });
    }

    runTogether(tasks);

    assertChunksHold(data, expected, expected.keySet());
    close(data);
    assertChunksHold(open(WORLD), expected, expected.keySet());
  }

  @Test
  void savesAndTrimsRacingTheWritersKeepEveryValue() throws Exception {
    WorldData data = open(WORLD);
    Map<SpatialWorldFixture.Cell, Object> expected = chunkLayers();
    CountDownLatch writing = new CountDownLatch(CHUNKS.length);
    List<Callable<Void>> tasks = new ArrayList<>();
    for (int[] chunk : CHUNKS) {
      tasks.add(() -> {
        try {
          writeChunkLayers(data, chunk);
        } finally {
          writing.countDown();
        }
        return null;
      });
    }
    tasks.add(() -> {
      while (writing.getCount() > 0) {
        for (int[] chunk : CHUNKS) {
          data.savePlate(chunk[0] << 4, chunk[1] << 4);
        }
        data.onTick();
      }
      return null;
    });

    runTogether(tasks);

    assertChunksHold(data, expected, expected.keySet());
    for (int[] chunk : CHUNKS) {
      data.savePlate(chunk[0] << 4, chunk[1] << 4);
    }
    assertChunksHold(data, expected, expected.keySet());
    close(data);
    assertChunksHold(open(WORLD), expected, expected.keySet());
  }

  @Test
  void threadsWritingDifferentSlicesOfTheSameBlocksKeepAllOfThem() throws Exception {
    WorldData data = open(WORLD);
    Map<SpatialWorldFixture.Cell, Object> expected = new LinkedHashMap<>();
    List<Callable<Void>> tasks = new ArrayList<>();
    for (int slice = 0; slice < 3; slice++) {
      int kind = slice;
      tasks.add(() -> {
        for (int position = 0; position < 256; position++) {
          for (int y : LAYERS) {
            data.set(496 + (position & 15), y, -512 + (position >> 4), sliceValue(kind, position));
          }
        }
        return null;
      });
      for (int position = 0; position < 256; position++) {
        for (int y : LAYERS) {
          Object value = sliceValue(kind, position);
          expected.put(new SpatialWorldFixture.Cell(496 + (position & 15), y, -512 + (position >> 4), value.getClass()), value);
        }
      }
    }

    runTogether(tasks);

    assertChunksHold(data, expected, expected.keySet());
    close(data);
    assertChunksHold(open(WORLD), expected, expected.keySet());
  }

  @Test
  void threadsOverwritingOneBlockLeaveOneOfTheirLastValues() throws Exception {
    WorldData data = open(WORLD);
    List<Callable<Void>> tasks = new ArrayList<>();
    List<PlacementStamp> finals = new ArrayList<>();
    for (int thread = 0; thread < THREADS; thread++) {
      int owner = thread;
      finals.add(new PlacementStamp(owner, 499, 0));
      tasks.add(() -> {
        for (int write = 0; write < 500; write++) {
          data.set(-1, -64, -1, new PlacementStamp(owner, write, 0));
          assertThat(data.get(-1, -64, -1, PlacementStamp.class)).isNotNull();
        }
        return null;
      });
    }

    runTogether(tasks);

    PlacementStamp kept = data.get(-1, -64, -1, PlacementStamp.class);
    assertThat(finals).contains(kept);
    Map<SpatialWorldFixture.Cell, Object> expected = Map.of(new SpatialWorldFixture.Cell(-1, -64, -1, PlacementStamp.class), kept);
    assertChunksHold(data, expected, expected.keySet());
    close(data);
    assertChunksHold(open(WORLD), expected, expected.keySet());
  }

  private static void writeChunkLayers(WorldData data, int[] chunk) {
    for (int position = 0; position < 256; position++) {
      int x = (chunk[0] << 4) + (position & 15);
      int z = (chunk[1] << 4) + (position >> 4);
      for (int y : LAYERS) {
        data.set(x, y, z, new PlacementStamp(x, y, z));
      }
    }
  }

  private static Map<SpatialWorldFixture.Cell, Object> chunkLayers() {
    Map<SpatialWorldFixture.Cell, Object> expected = new LinkedHashMap<>();
    for (int[] chunk : CHUNKS) {
      for (int position = 0; position < 256; position++) {
        int x = (chunk[0] << 4) + (position & 15);
        int z = (chunk[1] << 4) + (position >> 4);
        for (int y : LAYERS) {
          expected.put(new SpatialWorldFixture.Cell(x, y, z, PlacementStamp.class), new PlacementStamp(x, y, z));
        }
      }
    }
    return expected;
  }

  private static PlacementStamp stamp(int position) {
    return new PlacementStamp(position, -position, position % THREADS);
  }

  private static Object sliceValue(int kind, int position) {
    return switch (kind) {
      case 0 -> new Earnings(position % 128);
      case 1 -> new PlacementStamp(position, position, position);
      default -> new BrewingStandOwner(new UUID(position, kind));
    };
  }

  // Releases every task at once and rethrows the first failure. A task that never finishes fails the test.
  private static void runTogether(List<Callable<Void>> tasks) throws InterruptedException, TimeoutException {
    CyclicBarrier start = new CyclicBarrier(tasks.size());
    ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
    try {
      List<Future<Void>> results = new ArrayList<>();
      for (Callable<Void> task : tasks) {
        results.add(pool.submit(() -> {
          start.await(30, TimeUnit.SECONDS);
          return task.call();
        }));
      }
      List<Throwable> failures = new ArrayList<>();
      for (Future<Void> result : results) {
        try {
          result.get(90, TimeUnit.SECONDS);
        } catch (ExecutionException failure) {
          failures.add(failure.getCause());
        }
      }
      assertThat(failures).as("failures on worker threads").isEmpty();
    } finally {
      pool.shutdownNow();
    }
  }
}

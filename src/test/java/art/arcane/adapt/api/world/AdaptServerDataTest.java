package art.arcane.adapt.api.world;

import art.arcane.adapt.api.xp.XPMultiplier;
import art.arcane.adapt.util.common.io.Json;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptServerDataTest {
  @Test
  void globalBoostsRemainSafeDuringConcurrentReadsAndWrites() throws Exception {
    AdaptServerData data = new AdaptServerData();
    ExecutorService executor = Executors.newFixedThreadPool(8);
    List<Future<?>> futures = new ArrayList<>();
    try {
      for (int worker = 0; worker < 8; worker++) {
        futures.add(executor.submit(() -> {
          for (int boost = 0; boost < 100; boost++) {
            data.getMultipliers().add(new XPMultiplier(0.01D, 60000L));
            for (XPMultiplier multiplier : data.getMultipliers()) {
              assertThat(multiplier.isActive()).isTrue();
            }
          }
        }));
      }
      for (Future<?> future : futures) {
        future.get();
      }
    } finally {
      executor.shutdownNow();
    }

    assertThat(data.getMultipliers()).hasSize(800);
  }

  @Test
  void serializationRetainsConcurrentStorageAndNormalizationPrunesInvalidEntries() {
    AdaptServerData data = new AdaptServerData();
    data.getMultipliers().add(new XPMultiplier(0.5D, 60000L));
    data.getMultipliers().add(new XPMultiplier(Double.NaN, 60000L));
    data.getMultipliers().add(new XPMultiplier(0.5D, -60000L));
    data.normalize();

    AdaptServerData restored = Json.fromJson(Json.toJson(data, false), AdaptServerData.class);
    restored.normalize();

    assertThat(restored.getMultipliers()).isInstanceOf(CopyOnWriteArrayList.class);
    assertThat(restored.getMultipliers()).hasSize(1);
    assertThat(restored.getMultipliers().getFirst().getMultiplier()).isEqualTo(0.5D);
  }
}

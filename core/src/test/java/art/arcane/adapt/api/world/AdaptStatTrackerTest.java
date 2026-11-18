package art.arcane.adapt.api.world;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdaptStatTrackerTest {
  @Test
  void rewardSupplierReadsTheCurrentConfigurationValue() {
    AtomicReference<Double> configuredReward = new AtomicReference<>(100.0D);
    AdaptStatTracker tracker = AdaptStatTracker.builder()
        .reward(25.0D)
        .rewardSupplier(configuredReward::get)
        .build();

    assertEquals(100.0D, tracker.getReward());
    configuredReward.set(225.0D);
    assertEquals(225.0D, tracker.getReward());
  }

  @Test
  void fixedRewardRemainsAvailableWithoutASupplier() {
    AdaptStatTracker tracker = AdaptStatTracker.builder().reward(75.0D).build();

    assertEquals(75.0D, tracker.getReward());
  }
}

package art.arcane.adapt.api.fx;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class FxBudgetTest {
  @AfterEach
  void resetBudget() throws ReflectiveOperationException {
    setStatic("appliedBand", 0);
    setStatic("candidateBand", 0);
    setStatic("candidateSince", 0L);
    setStatic("lastSampleAt", 0L);
  }

  @Test
  void publishesTheAveragePacketsPerTickOfTheLastCompletedSecond() {
    long second = 1_000_000L;
    for (int tick = 0; tick < 20; tick++) {
      FxBudget.foldTick(second + (tick * 50L), tick % 2 == 0 ? 300 : 100);
    }

    FxBudget.foldTick(second + 1_000L, 0);

    assertThat(FxBudget.averagePacketsPerTick(second + 1_050L)).isEqualTo(200D);
    assertThat(FxBudget.averagePacketsPerTick(second + 2_900L)).isEqualTo(200D);
  }

  @Test
  void aStoppedDirectorStopsPublishingItsLastAverage() {
    long second = 2_000_000L;
    FxBudget.foldTick(second, 400);
    FxBudget.foldTick(second + 1_000L, 0);

    assertThat(FxBudget.averagePacketsPerTick(second + 1_500L)).isEqualTo(400D);
    assertThat(FxBudget.averagePacketsPerTick(second + 10_000L)).isZero();
  }

  @Test
  void readingTheShedBandRefreshesItAfterTheServerRecovers() throws ReflectiveOperationException {
    setStatic("appliedBand", 4);
    setStatic("candidateBand", 0);
    setStatic("candidateSince", 0L);
    setStatic("lastSampleAt", 0L);

    assertThat(FxBudget.shedBand()).isZero();
  }

  @Test
  void globalPacketBudgetIsAHardCeilingForGameplayEffects() {
    FxBudget.resetTick();

    assertThat(FxBudget.tryConsume(FxPriority.GAMEPLAY, FxBudget.GLOBAL_PACKET_BUDGET))
        .isEqualTo(FxBudget.GLOBAL_PACKET_BUDGET);
    assertThat(FxBudget.tryConsume(FxPriority.GAMEPLAY, 1)).isZero();
  }

  private static void setStatic(String name, Object value) throws ReflectiveOperationException {
    Field field = FxBudget.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(null, value);
  }
}

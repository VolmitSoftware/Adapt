package art.arcane.adapt.api.protection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorldPolicyLatencyTelemetryTest {
  @AfterEach
  void clearTelemetry() {
    WorldPolicyLatencyTelemetry.clear();
  }

  @Test
  void reportsAverageAcrossCurrentWindow() {
    WorldPolicyLatencyTelemetry.recordNanos(1_000_000L);
    WorldPolicyLatencyTelemetry.recordNanos(3_000_000L);

    assertThat(WorldPolicyLatencyTelemetry.averageMillis(System.currentTimeMillis())).isEqualTo(2D);
  }

  @Test
  void clearRemovesRecordedSamples() {
    WorldPolicyLatencyTelemetry.recordNanos(1_000_000L);

    WorldPolicyLatencyTelemetry.clear();

    assertThat(WorldPolicyLatencyTelemetry.averageMillis(System.currentTimeMillis())).isZero();
  }
}

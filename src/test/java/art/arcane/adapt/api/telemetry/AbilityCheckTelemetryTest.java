package art.arcane.adapt.api.telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AbilityCheckTelemetryTest {
  @AfterEach
  void clearTelemetry() {
    AbilityCheckTelemetry.clear();
  }

  @Test
  void recordsCacheAndUncachedOutcomesWithOneTimestamp() {
    long now = System.currentTimeMillis();

    AbilityCheckTelemetry.recordCacheHit(now);
    AbilityCheckTelemetry.recordUncachedCheck(now, 2_000L, true);
    AbilityCheckTelemetry.recordUncachedCheck(now, 4_000L, false);

    assertThat(AbilityCheckTelemetry.cacheHitsPerMinute(now)).isEqualTo(1L);
    assertThat(AbilityCheckTelemetry.cacheMissesPerMinute(now)).isEqualTo(2L);
    assertThat(AbilityCheckTelemetry.checksPerMinute(now)).isEqualTo(2L);
    assertThat(AbilityCheckTelemetry.successfulChecksPerMinute(now)).isEqualTo(1L);
    assertThat(AbilityCheckTelemetry.averageCheckMicros(now)).isEqualTo(3D);
  }
}

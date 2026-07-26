package art.arcane.adapt.api.telemetry;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
    AbilityCheckTelemetry.recordUncachedCheck("agility-wall-jump", now, 2_000L, true);
    AbilityCheckTelemetry.recordUncachedCheck("agility-wall-jump", now, 4_000L, false);
    AbilityCheckTelemetry.recordExecution("agility-wall-jump", now, 2_000_000L);
    AbilityCheckTelemetry.recordExecution("agility-wall-jump", now, 4_000_000L);

    assertThat(AbilityCheckTelemetry.cacheHitsPerMinute(now)).isEqualTo(1L);
    assertThat(AbilityCheckTelemetry.cacheMissesPerMinute(now)).isEqualTo(2L);
    assertThat(AbilityCheckTelemetry.checksPerMinute(now)).isEqualTo(2L);
    assertThat(AbilityCheckTelemetry.successfulChecksPerMinute(now)).isEqualTo(1L);
    assertThat(AbilityCheckTelemetry.averageCheckMicros(now)).isEqualTo(3D);
    assertThat(AbilityCheckTelemetry.abilityIds(now)).containsExactly("agility-wall-jump");
    AbilityCheckTelemetry.AbilitySnapshot snapshot = AbilityCheckTelemetry.abilitySnapshots(now).get("agility-wall-jump");
    assertThat(snapshot.executionOps()).isEqualTo(2L);
    assertThat(snapshot.executionTimingMillis()).isEqualTo(6D);
    assertThat(snapshot.guardChecks()).isEqualTo(2L);
    assertThat(snapshot.guardTimingMillis()).isEqualTo(0.006D);
  }

  @Test
  void keepsAbilityWindowsSeparateAndExpiresOldBuckets() {
    long now = System.currentTimeMillis();
    long expired = now - 60_000L;

    AbilityCheckTelemetry.recordUncachedCheck("agility-wall-jump", expired, 5_000_000L, true);
    AbilityCheckTelemetry.recordUncachedCheck("excavation-earth-mover", now, 2_000_000L, false);

    Map<String, AbilityCheckTelemetry.AbilitySnapshot> snapshots = AbilityCheckTelemetry.abilitySnapshots(now);
    assertThat(snapshots).doesNotContainKey("agility-wall-jump");
    assertThat(AbilityCheckTelemetry.abilityIds(now)).containsExactly("excavation-earth-mover");
    assertThat(snapshots.get("excavation-earth-mover").guardChecks()).isEqualTo(1L);
    assertThat(snapshots.get("excavation-earth-mover").guardTimingMillis()).isEqualTo(2D);
  }

  @Test
  void nestedExecutionScopesRecordOnlyTheOuterCallback() {
    long outer = AbilityCheckTelemetry.beginExecution("agility-wall-jump");
    long nested = AbilityCheckTelemetry.beginExecution("agility-wall-jump");

    AbilityCheckTelemetry.endExecution("agility-wall-jump", nested);
    AbilityCheckTelemetry.endExecution("agility-wall-jump", outer);

    Map<String, AbilityCheckTelemetry.AbilitySnapshot> snapshots =
        AbilityCheckTelemetry.abilitySnapshots(System.currentTimeMillis());
    assertThat(snapshots.get("agility-wall-jump").executionOps()).isEqualTo(1L);
  }

  @Test
  void sampledExecutionTimingKeepsOperationCountsExact() {
    for (int i = 0; i < 32; i++) {
      long started = AbilityCheckTelemetry.beginExecution("agility-wall-jump");
      AbilityCheckTelemetry.endExecution("agility-wall-jump", started);
    }

    AbilityCheckTelemetry.AbilitySnapshot snapshot =
        AbilityCheckTelemetry.abilitySnapshots(System.currentTimeMillis()).get("agility-wall-jump");
    assertThat(snapshot.executionOps()).isEqualTo(32L);
    assertThat(snapshot.executionTimingMillis()).isGreaterThan(0D);
  }

  @Test
  void nestedDifferentAbilitiesKeepTheirOwnMeasuredExecution() {
    long outer = AbilityCheckTelemetry.beginExecution("agility-wall-jump");
    long nested = AbilityCheckTelemetry.beginExecution("excavation-earth-mover");

    AbilityCheckTelemetry.endExecution("excavation-earth-mover", nested);
    AbilityCheckTelemetry.endExecution("agility-wall-jump", outer);

    Map<String, AbilityCheckTelemetry.AbilitySnapshot> snapshots =
        AbilityCheckTelemetry.abilitySnapshots(System.currentTimeMillis());
    assertThat(snapshots.get("agility-wall-jump").executionOps()).isEqualTo(1L);
    assertThat(snapshots.get("excavation-earth-mover").executionOps()).isEqualTo(1L);
  }
}

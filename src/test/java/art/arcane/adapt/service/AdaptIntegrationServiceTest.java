package art.arcane.adapt.service;

import art.arcane.adapt.api.telemetry.AbilityCheckTelemetry;
import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptIntegrationServiceTest {
  @AfterEach
  void clearTelemetry() {
    AbilityCheckTelemetry.clear();
  }

  @Test
  void publishesObservedAbilityBreakdownMetrics() {
    long now = System.currentTimeMillis();
    AbilityCheckTelemetry.recordUncachedCheck("excavation-earth-mover", now, 2_000_000L, true);
    AbilityCheckTelemetry.recordExecution("excavation-earth-mover", now, 4_000_000L);
    AdaptIntegrationService service = new AdaptIntegrationService();
    Set<String> keys = IntegrationMetricSchema.adaptAbilityDetailKeys("excavation-earth-mover");

    Set<IntegrationMetricDescriptor> descriptors = service.metricDescriptors();
    Map<String, IntegrationMetricSample> samples = service.sampleMetrics(keys);

    assertThat(descriptors)
        .extracting(IntegrationMetricDescriptor::key)
        .containsAll(keys);
    assertThat(samples.get("adapt.ability-detail.excavation-earth-mover.execution-ops").valueOr(-1D)).isEqualTo(1D);
    assertThat(samples.get("adapt.ability-detail.excavation-earth-mover.execution-timing-ms").valueOr(-1D)).isEqualTo(4D);
    assertThat(samples.get("adapt.ability-detail.excavation-earth-mover.guard-checks").valueOr(-1D)).isEqualTo(1D);
    assertThat(samples.get("adapt.ability-detail.excavation-earth-mover.guard-timing-ms").valueOr(-1D)).isEqualTo(2D);
  }

  @Test
  void publishesRollingGuardCheckTimingBudget() {
    long now = System.currentTimeMillis();
    for (int i = 0; i < 60; i++) {
      AbilityCheckTelemetry.recordUncachedCheck("excavation-earth-mover", now, 50_000_000L, true);
    }
    AdaptIntegrationService service = new AdaptIntegrationService();

    Map<String, IntegrationMetricSample> samples = service.sampleMetrics(Set.of(
        IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET
    ));

    assertThat(samples.get(IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET).valueOr(-1D)).isBetween(99D, 101D);
  }
}

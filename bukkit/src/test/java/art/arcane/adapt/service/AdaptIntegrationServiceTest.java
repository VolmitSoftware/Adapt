package art.arcane.adapt.service;

import art.arcane.adapt.engine.telemetry.AbilityCheckTelemetry;
import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSnapshot;
import art.arcane.volmlib.integration.IntegrationSnapshotProvider;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicesManager;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptIntegrationServiceTest {
  @AfterEach
  void clearTelemetry() {
    AbilityCheckTelemetry.clear();
  }

  @Test
  void disableClearsPublishedSnapshots() {
    AdaptIntegrationService service = new AdaptIntegrationService();
    Set<String> keys = Set.of(IntegrationMetricSchema.ADAPT_ABILITY_OPS);
    service.snapshotMetrics(keys);
    service.publishSnapshots();
    long generation = service.snapshotMetrics(keys).generation();
    try (MockedStatic<Bukkit> bukkit = Mockito.mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getServicesManager).thenReturn(Mockito.mock(ServicesManager.class));
      service.onDisable();
    }
    assertThat(service.snapshotMetrics(keys).samples()).isEmpty();
    assertThat(service.snapshotMetrics(keys).generation()).isGreaterThan(generation);
  }

  @Test
  void snapshotReadsOnlyDemandCachedPublications() {
    long now = System.currentTimeMillis();
    String key = "adapt.ability-detail.adapt-excavation-earth_mover.execution-ops";
    AbilityCheckTelemetry.recordExecution("adapt:excavation/earth_mover", now, 4_000_000L);
    AdaptIntegrationService service = new AdaptIntegrationService();
    assertThat(service.capabilities()).contains(IntegrationSnapshotProvider.CAPABILITY);
    assertThat(service.snapshotMetrics(Set.of(key)).samples()).isEmpty();

    service.publishSnapshots();
    IntegrationMetricSnapshot first = service.snapshotMetrics(Set.of(key));
    assertThat(first.samples().get(key).valueOr(-1D)).isEqualTo(1D);
    AbilityCheckTelemetry.recordExecution("adapt:excavation/earth_mover", now, 4_000_000L);
    assertThat(service.snapshotMetrics(Set.of(key))).isEqualTo(first);

    service.publishSnapshots();
    IntegrationMetricSnapshot second = service.snapshotMetrics(Set.of(key));
    assertThat(second.generation()).isGreaterThan(first.generation());
    assertThat(second.samples().get(key).valueOr(-1D)).isEqualTo(2D);
    assertThat(second.samples()).containsOnlyKeys(key);
  }

  @Test
  void selectedAbilitySnapshotsExcludeOtherObservedAbilities() {
    long now = System.currentTimeMillis();
    AbilityCheckTelemetry.recordExecution("selected", now, 1_000_000L);
    AbilityCheckTelemetry.recordExecution("other", now, 1_000_000L);
    assertThat(AbilityCheckTelemetry.abilitySnapshots(Set.of("selected"), now)).containsOnlyKeys("selected");
    assertThat(AbilityCheckTelemetry.abilitySnapshots(Set.of(), now)).isEmpty();
  }

  @Test
  void publishesObservedAbilityBreakdownMetrics() {
    long now = System.currentTimeMillis();
    AbilityCheckTelemetry.recordUncachedCheck("adapt:excavation/earth_mover", now, 2_000_000L, true);
    AbilityCheckTelemetry.recordExecution("adapt:excavation/earth_mover", now, 4_000_000L);
    AdaptIntegrationService service = new AdaptIntegrationService();
    Set<String> keys = IntegrationMetricSchema.adaptAbilityDetailKeys("adapt:excavation/earth_mover");

    Set<IntegrationMetricDescriptor> descriptors = service.metricDescriptors();
    Map<String, IntegrationMetricSample> samples = service.sampleMetrics(keys);

    assertThat(descriptors)
        .extracting(IntegrationMetricDescriptor::key)
        .containsAll(keys);
    assertThat(samples.get("adapt.ability-detail.adapt-excavation-earth_mover.execution-ops").valueOr(-1D)).isEqualTo(1D);
    assertThat(samples.get("adapt.ability-detail.adapt-excavation-earth_mover.execution-timing-ms").valueOr(-1D)).isEqualTo(4D);
    assertThat(samples.get("adapt.ability-detail.adapt-excavation-earth_mover.guard-checks").valueOr(-1D)).isEqualTo(1D);
    assertThat(samples.get("adapt.ability-detail.adapt-excavation-earth_mover.guard-timing-ms").valueOr(-1D)).isEqualTo(2D);
  }

  @Test
  void publishesRollingGuardCheckTimingBudget() {
    long now = System.currentTimeMillis();
    for (int i = 0; i < 60; i++) {
      AbilityCheckTelemetry.recordUncachedCheck("adapt:excavation/earth_mover", now, 50_000_000L, true);
    }
    AdaptIntegrationService service = new AdaptIntegrationService();

    Map<String, IntegrationMetricSample> samples = service.sampleMetrics(Set.of(
        IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET
    ));

    assertThat(samples.get(IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET).valueOr(-1D)).isBetween(99D, 101D);
  }
}

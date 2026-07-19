package art.arcane.adapt.service;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.protection.WorldPolicyLatencyTelemetry;
import art.arcane.adapt.api.telemetry.AbilityCheckTelemetry;
import art.arcane.adapt.util.common.plugin.AdaptService;
import art.arcane.volmlib.integration.IntegrationHandshakeRequest;
import art.arcane.volmlib.integration.IntegrationHandshakeResponse;
import art.arcane.volmlib.integration.IntegrationHeartbeat;
import art.arcane.volmlib.integration.IntegrationMetricDescriptor;
import art.arcane.volmlib.integration.IntegrationMetricSample;
import art.arcane.volmlib.integration.IntegrationMetricSchema;
import art.arcane.volmlib.integration.IntegrationProtocolNegotiator;
import art.arcane.volmlib.integration.IntegrationProtocolVersion;
import art.arcane.volmlib.integration.IntegrationServiceContract;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class AdaptIntegrationService implements AdaptService, IntegrationServiceContract {
  private static final Set<IntegrationProtocolVersion> SUPPORTED_PROTOCOLS = Set.of(
      new IntegrationProtocolVersion(1, 0),
      new IntegrationProtocolVersion(1, 1)
  );
  private static final Set<String> CAPABILITIES = Set.of(
      "handshake",
      "heartbeat",
      "metrics",
      "adapt-runtime-metrics",
      "adapt-ability-execution-metrics"
  );

  private volatile IntegrationProtocolVersion negotiatedProtocol = new IntegrationProtocolVersion(1, 1);

  @Override
  public void onEnable() {
    Bukkit.getServicesManager().register(IntegrationServiceContract.class, this, Adapt.instance, ServicePriority.Normal);
    Adapt.verbose("Integration provider registered for Adapt");
  }

  @Override
  public void onDisable() {
    Bukkit.getServicesManager().unregister(IntegrationServiceContract.class, this);
    AbilityCheckTelemetry.clear();
    WorldPolicyLatencyTelemetry.clear();
  }

  @Override
  public String pluginId() {
    return "adapt";
  }

  @Override
  public String pluginVersion() {
    return Adapt.instance.getDescription().getVersion();
  }

  @Override
  public Set<IntegrationProtocolVersion> supportedProtocols() {
    return SUPPORTED_PROTOCOLS;
  }

  @Override
  public Set<String> capabilities() {
    return CAPABILITIES;
  }

  @Override
  public Set<IntegrationMetricDescriptor> metricDescriptors() {
    Set<IntegrationMetricDescriptor> descriptors = new HashSet<>();
    for (IntegrationMetricDescriptor descriptor : IntegrationMetricSchema.descriptors()) {
      if (descriptor.key().startsWith("adapt.")) {
        descriptors.add(descriptor);
      }
    }
    long now = System.currentTimeMillis();
    for (String abilityId : AbilityCheckTelemetry.abilityIds(now)) {
      for (String key : IntegrationMetricSchema.adaptAbilityDetailKeys(abilityId)) {
        descriptors.add(IntegrationMetricSchema.descriptor(key));
      }
    }
    return Set.copyOf(descriptors);
  }

  @Override
  public IntegrationHandshakeResponse handshake(IntegrationHandshakeRequest request) {
    long now = System.currentTimeMillis();
    if (request == null) {
      return new IntegrationHandshakeResponse(
          pluginId(),
          pluginVersion(),
          false,
          null,
          SUPPORTED_PROTOCOLS,
          CAPABILITIES,
          "missing request",
          now
      );
    }

    Optional<IntegrationProtocolVersion> negotiated = IntegrationProtocolNegotiator.negotiate(
        SUPPORTED_PROTOCOLS,
        request.supportedProtocols()
    );
    if (negotiated.isEmpty()) {
      return new IntegrationHandshakeResponse(
          pluginId(),
          pluginVersion(),
          false,
          null,
          SUPPORTED_PROTOCOLS,
          CAPABILITIES,
          "no-common-protocol",
          now
      );
    }

    negotiatedProtocol = negotiated.get();
    return new IntegrationHandshakeResponse(
        pluginId(),
        pluginVersion(),
        true,
        negotiatedProtocol,
        SUPPORTED_PROTOCOLS,
        CAPABILITIES,
        "ok",
        now
    );
  }

  @Override
  public IntegrationHeartbeat heartbeat() {
    long now = System.currentTimeMillis();
    return new IntegrationHeartbeat(negotiatedProtocol, true, now, "ok");
  }

  @Override
  public Map<String, IntegrationMetricSample> sampleMetrics(Set<String> metricKeys) {
    long now = System.currentTimeMillis();
    Map<String, AbilityCheckTelemetry.AbilitySnapshot> abilitySnapshots = AbilityCheckTelemetry.abilitySnapshots(now);
    Set<String> requested;
    if (metricKeys == null || metricKeys.isEmpty()) {
      Set<String> allKeys = new HashSet<>(IntegrationMetricSchema.adaptKeys());
      for (String abilityId : abilitySnapshots.keySet()) {
        allKeys.addAll(IntegrationMetricSchema.adaptAbilityDetailKeys(abilityId));
      }
      requested = Set.copyOf(allKeys);
    } else {
      requested = metricKeys;
    }
    Map<String, IntegrationMetricSample> out = new HashMap<>();

    for (String key : requested) {
      switch (key) {
        case IntegrationMetricSchema.ADAPT_SESSION_LOAD ->
            out.put(key, sampleSessionLoad(now));
        case IntegrationMetricSchema.ADAPT_ABILITY_OPS ->
            out.put(key, sampleAbilityOps(now));
        case IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS ->
            out.put(key, sampleAbilityCheckOps(now));
        case IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS_TICK ->
            out.put(key, sampleAbilityCheckOpsTick(now));
        case IntegrationMetricSchema.ADAPT_WORLD_POLICY_LATENCY ->
            out.put(key, sampleWorldPolicyLatency(now));
        default -> {
          if (IntegrationMetricSchema.isAdaptAbilityDetailKey(key)) {
            out.put(key, sampleAbilityDetail(key, now, abilitySnapshots));
          } else {
            out.put(key, IntegrationMetricSample.unavailable(
                IntegrationMetricSchema.descriptor(key),
                "unsupported-key",
                now
            ));
          }
        }
      }
    }

    return out;
  }

  private IntegrationMetricSample sampleSessionLoad(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_SESSION_LOAD);
    if (Adapt.instance.getTicker() == null) {
      return IntegrationMetricSample.unavailable(descriptor, "ticker-not-ready", now);
    }

    double load = Adapt.instance.getTicker().getWindowLoadPercent();
    return IntegrationMetricSample.available(descriptor, load, now);
  }

  private IntegrationMetricSample sampleAbilityOps(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_ABILITY_OPS);
    long count = AbilityCheckTelemetry.successfulChecksPerMinute(now);
    return IntegrationMetricSample.available(descriptor, count, now);
  }

  private IntegrationMetricSample sampleAbilityCheckOps(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS);
    long count = AbilityCheckTelemetry.checksPerMinute(now);
    return IntegrationMetricSample.available(descriptor, count, now);
  }

  private IntegrationMetricSample sampleAbilityCheckOpsTick(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_ABILITY_CHECK_OPS_TICK);
    double count = AbilityCheckTelemetry.checksPerTick(now);
    return IntegrationMetricSample.available(descriptor, count, now);
  }

  private IntegrationMetricSample sampleWorldPolicyLatency(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_WORLD_POLICY_LATENCY);
    double averageMs = WorldPolicyLatencyTelemetry.averageMillis(now);
    return IntegrationMetricSample.available(descriptor, averageMs, now);
  }

  private IntegrationMetricSample sampleAbilityDetail(
      String key,
      long now,
      Map<String, AbilityCheckTelemetry.AbilitySnapshot> abilitySnapshots
  ) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(key);
    String abilityId = IntegrationMetricSchema.adaptAbilityId(key);
    String signal = IntegrationMetricSchema.adaptAbilitySignal(key);
    AbilityCheckTelemetry.AbilitySnapshot snapshot = abilitySnapshots.get(abilityId);
    if (snapshot == null) {
      return IntegrationMetricSample.unavailable(descriptor, "ability-window-expired", now);
    }
    double value = switch (signal) {
      case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_OPS -> snapshot.executionOps();
      case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS -> snapshot.executionTimingMillis();
      case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_GUARD_CHECKS -> snapshot.guardChecks();
      case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_GUARD_TIMING_MS -> snapshot.guardTimingMillis();
      default -> 0D;
    };
    return IntegrationMetricSample.available(descriptor, value, now);
  }
}

package art.arcane.adapt.service;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.fx.FxBudget;
import art.arcane.adapt.api.fx.FxDirector;
import art.arcane.adapt.api.minion.MinionBurden;
import art.arcane.adapt.api.protection.WorldPolicyLatencyTelemetry;
import art.arcane.adapt.api.telemetry.AbilityCheckTelemetry;
import art.arcane.adapt.api.telemetry.AdaptRuntimeTelemetry;
import art.arcane.adapt.api.world.AdaptServer;
import art.arcane.adapt.api.world.PlayerDataPersistenceQueue;
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
import java.util.concurrent.ConcurrentHashMap;

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
  private static final Set<String> STATIC_ADAPT_KEYS = IntegrationMetricSchema.adaptKeys();
  private static final Set<IntegrationMetricDescriptor> STATIC_ADAPT_DESCRIPTORS = buildStaticAdaptDescriptors();
  private static final AbilityDetailBinding NOT_ABILITY_DETAIL = new AbilityDetailBinding(null, null, null);
  private static final int BINDING_CACHE_LIMIT = 8192;

  private final Map<String, Set<String>> abilityKeyCache = new ConcurrentHashMap<>();
  private final Map<String, Set<IntegrationMetricDescriptor>> abilityDescriptorCache = new ConcurrentHashMap<>();
  private final Map<String, AbilityDetailBinding> abilityDetailBindings = new ConcurrentHashMap<>();
  private volatile IntegrationProtocolVersion negotiatedProtocol = new IntegrationProtocolVersion(1, 1);
  private volatile Set<String> cachedDescriptorAbilityIds = Set.of();
  private volatile Set<IntegrationMetricDescriptor> cachedDescriptors = STATIC_ADAPT_DESCRIPTORS;
  private volatile Set<String> cachedSampleAbilityIds = Set.of();
  private volatile Set<String> cachedSampleKeys = STATIC_ADAPT_KEYS;

  private static Set<IntegrationMetricDescriptor> buildStaticAdaptDescriptors() {
    Set<IntegrationMetricDescriptor> descriptors = new HashSet<>();
    for (IntegrationMetricDescriptor descriptor : IntegrationMetricSchema.descriptors()) {
      if (descriptor.key().startsWith("adapt.")) {
        descriptors.add(descriptor);
      }
    }
    return Set.copyOf(descriptors);
  }

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
    AdaptRuntimeTelemetry.clear();
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
    Set<String> abilityIds = AbilityCheckTelemetry.abilityIds(System.currentTimeMillis());
    if (abilityIds.equals(cachedDescriptorAbilityIds)) {
      return cachedDescriptors;
    }

    Set<IntegrationMetricDescriptor> descriptors = new HashSet<>(STATIC_ADAPT_DESCRIPTORS);
    for (String abilityId : abilityIds) {
      descriptors.addAll(abilityDescriptors(abilityId));
    }

    Set<IntegrationMetricDescriptor> resolved = Set.copyOf(descriptors);
    cachedDescriptors = resolved;
    cachedDescriptorAbilityIds = abilityIds;
    return resolved;
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
    Set<String> requested = metricKeys == null || metricKeys.isEmpty()
        ? allSampleKeys(abilitySnapshots.keySet())
        : metricKeys;
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
        case IntegrationMetricSchema.ADAPT_ABILITY_CACHE_HIT_RATIO ->
            out.put(key, sampleAbilityCacheHitRatio(now));
        case IntegrationMetricSchema.ADAPT_ABILITY_CHECK_LATENCY_US ->
            out.put(key, sampleAbilityCheckLatencyUs(now));
        case IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET ->
            out.put(key, sampleAbilityTimingBudget(now));
        case IntegrationMetricSchema.ADAPT_PLAYER_SESSIONS ->
            out.put(key, samplePlayerSessions(now));
        case IntegrationMetricSchema.ADAPT_LEARNED_ADAPTATIONS_ONLINE ->
            out.put(key, sampleLearnedAdaptationsOnline(now));
        case IntegrationMetricSchema.ADAPT_SPATIAL_XP_TICKETS ->
            out.put(key, sampleSpatialXpTickets(now));
        case IntegrationMetricSchema.ADAPT_FX_TIMELINES_ACTIVE ->
            out.put(key, sampleFxTimelinesActive(now));
        case IntegrationMetricSchema.ADAPT_FX_PACKETS_USED ->
            out.put(key, sampleFxPacketsUsed(now));
        case IntegrationMetricSchema.ADAPT_FX_SHED_BAND ->
            out.put(key, sampleFxShedBand(now));
        case IntegrationMetricSchema.ADAPT_MINIONS_ACTIVE ->
            out.put(key, sampleMinionsActive(now));
        case IntegrationMetricSchema.ADAPT_PERSISTENCE_QUEUE_DEPTH ->
            out.put(key, samplePersistenceQueueDepth(now));
        case IntegrationMetricSchema.ADAPT_XP_PER_MINUTE ->
            out.put(key, sampleXpPerMinute(now));
        case IntegrationMetricSchema.ADAPT_XP_PAYOUT_OPS ->
            out.put(key, sampleXpPayoutOps(now));
        case IntegrationMetricSchema.ADAPT_PROVENANCE_OPS ->
            out.put(key, sampleProvenanceOps(now));
        case IntegrationMetricSchema.ADAPT_EVENT_HANDLER_OPS ->
            out.put(key, sampleEventHandlerOps(now));
        default -> {
          AbilityDetailBinding binding = abilityDetailBinding(key);
          if (binding == NOT_ABILITY_DETAIL) {
            out.put(key, IntegrationMetricSample.unavailable(
                IntegrationMetricSchema.descriptor(key),
                "unsupported-key",
                now
            ));
          } else {
            out.put(key, sampleAbilityDetail(binding, now, abilitySnapshots));
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

  private IntegrationMetricSample sampleAbilityCacheHitRatio(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_ABILITY_CACHE_HIT_RATIO);
    return IntegrationMetricSample.available(descriptor, AbilityCheckTelemetry.cacheHitRatio(now), now);
  }

  private IntegrationMetricSample sampleAbilityCheckLatencyUs(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_ABILITY_CHECK_LATENCY_US);
    return IntegrationMetricSample.available(descriptor, AbilityCheckTelemetry.averageCheckMicros(now), now);
  }

  private IntegrationMetricSample sampleAbilityTimingBudget(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_ABILITY_TIMING_BUDGET);
    return IntegrationMetricSample.available(descriptor, AbilityCheckTelemetry.timingBudgetPercent(now), now);
  }

  private IntegrationMetricSample samplePlayerSessions(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_PLAYER_SESSIONS);
    AdaptServer server = adaptServer();
    if (server == null) {
      return IntegrationMetricSample.unavailable(descriptor, "adapt-server-not-ready", now);
    }
    return IntegrationMetricSample.available(descriptor, server.getOnlineAdaptPlayerSnapshot().size(), now);
  }

  private IntegrationMetricSample sampleLearnedAdaptationsOnline(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_LEARNED_ADAPTATIONS_ONLINE);
    AdaptServer server = adaptServer();
    if (server == null) {
      return IntegrationMetricSample.unavailable(descriptor, "adapt-server-not-ready", now);
    }
    return IntegrationMetricSample.available(descriptor, server.getLearnedAdaptationCount(), now);
  }

  private IntegrationMetricSample sampleSpatialXpTickets(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_SPATIAL_XP_TICKETS);
    AdaptServer server = adaptServer();
    if (server == null) {
      return IntegrationMetricSample.unavailable(descriptor, "adapt-server-not-ready", now);
    }
    return IntegrationMetricSample.available(descriptor, server.getSpatialTicketCount(), now);
  }

  private IntegrationMetricSample sampleFxTimelinesActive(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_FX_TIMELINES_ACTIVE);
    FxDirector director = Adapt.instance == null ? null : Adapt.instance.getFxDirector();
    if (director == null) {
      return IntegrationMetricSample.unavailable(descriptor, "fx-director-not-ready", now);
    }
    return IntegrationMetricSample.available(descriptor, director.activeTimelineCount(), now);
  }

  private IntegrationMetricSample sampleFxPacketsUsed(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_FX_PACKETS_USED);
    return IntegrationMetricSample.available(descriptor, FxBudget.usedPackets(), now);
  }

  private IntegrationMetricSample sampleFxShedBand(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_FX_SHED_BAND);
    return IntegrationMetricSample.available(descriptor, FxBudget.shedBand(), now);
  }

  private IntegrationMetricSample sampleMinionsActive(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_MINIONS_ACTIVE);
    int total = MinionBurden.activeTotal();
    if (total < 0) {
      return IntegrationMetricSample.unavailable(descriptor, "minion-burden-not-ready", now);
    }
    return IntegrationMetricSample.available(descriptor, total, now);
  }

  private IntegrationMetricSample samplePersistenceQueueDepth(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_PERSISTENCE_QUEUE_DEPTH);
    PlayerDataPersistenceQueue queue = Adapt.instance == null ? null : Adapt.instance.getPlayerDataPersistenceQueue();
    if (queue == null) {
      return IntegrationMetricSample.unavailable(descriptor, "persistence-queue-not-ready", now);
    }
    return IntegrationMetricSample.available(descriptor, queue.pendingCount(), now);
  }

  private IntegrationMetricSample sampleXpPerMinute(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_XP_PER_MINUTE);
    return IntegrationMetricSample.available(descriptor, AdaptRuntimeTelemetry.xpPerMinute(now), now);
  }

  private IntegrationMetricSample sampleXpPayoutOps(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_XP_PAYOUT_OPS);
    return IntegrationMetricSample.available(descriptor, AdaptRuntimeTelemetry.xpPayoutOpsPerMinute(now), now);
  }

  private IntegrationMetricSample sampleProvenanceOps(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_PROVENANCE_OPS);
    return IntegrationMetricSample.available(descriptor, AdaptRuntimeTelemetry.provenanceOpsPerMinute(now), now);
  }

  private IntegrationMetricSample sampleEventHandlerOps(long now) {
    IntegrationMetricDescriptor descriptor = IntegrationMetricSchema.descriptor(IntegrationMetricSchema.ADAPT_EVENT_HANDLER_OPS);
    return IntegrationMetricSample.available(descriptor, AdaptRuntimeTelemetry.eventHandlerOpsPerMinute(now), now);
  }

  private AdaptServer adaptServer() {
    return Adapt.instance == null ? null : Adapt.instance.getAdaptServer();
  }

  private IntegrationMetricSample sampleAbilityDetail(
      AbilityDetailBinding binding,
      long now,
      Map<String, AbilityCheckTelemetry.AbilitySnapshot> abilitySnapshots
  ) {
    AbilityCheckTelemetry.AbilitySnapshot snapshot = abilitySnapshots.get(binding.abilityId());
    if (snapshot == null) {
      return IntegrationMetricSample.unavailable(binding.descriptor(), "ability-window-expired", now);
    }
    double value = switch (binding.signal()) {
      case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_OPS -> snapshot.executionOps();
      case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_EXECUTION_TIMING_MS -> snapshot.executionTimingMillis();
      case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_GUARD_CHECKS -> snapshot.guardChecks();
      case IntegrationMetricSchema.ADAPT_ABILITY_DETAIL_GUARD_TIMING_MS -> snapshot.guardTimingMillis();
      default -> 0D;
    };
    return IntegrationMetricSample.available(binding.descriptor(), value, now);
  }

  private Set<String> allSampleKeys(Set<String> abilityIds) {
    if (abilityIds.equals(cachedSampleAbilityIds)) {
      return cachedSampleKeys;
    }

    Set<String> keys = new HashSet<>(STATIC_ADAPT_KEYS);
    for (String abilityId : abilityIds) {
      keys.addAll(abilityKeys(abilityId));
    }

    Set<String> resolved = Set.copyOf(keys);
    cachedSampleKeys = resolved;
    cachedSampleAbilityIds = Set.copyOf(abilityIds);
    return resolved;
  }

  private Set<String> abilityKeys(String abilityId) {
    return abilityKeyCache.computeIfAbsent(abilityId, IntegrationMetricSchema::adaptAbilityDetailKeys);
  }

  private Set<IntegrationMetricDescriptor> abilityDescriptors(String abilityId) {
    return abilityDescriptorCache.computeIfAbsent(abilityId, this::buildAbilityDescriptors);
  }

  private Set<IntegrationMetricDescriptor> buildAbilityDescriptors(String abilityId) {
    Set<String> keys = abilityKeys(abilityId);
    Set<IntegrationMetricDescriptor> descriptors = new HashSet<>(keys.size());
    for (String key : keys) {
      descriptors.add(IntegrationMetricSchema.descriptor(key));
    }
    return Set.copyOf(descriptors);
  }

  private AbilityDetailBinding abilityDetailBinding(String key) {
    if (abilityDetailBindings.size() > BINDING_CACHE_LIMIT) {
      abilityDetailBindings.clear();
    }

    return abilityDetailBindings.computeIfAbsent(key, resolved -> {
      String abilityId = IntegrationMetricSchema.adaptAbilityId(resolved);
      if (abilityId.isEmpty()) {
        return NOT_ABILITY_DETAIL;
      }

      return new AbilityDetailBinding(
          IntegrationMetricSchema.descriptor(resolved),
          abilityId,
          IntegrationMetricSchema.adaptAbilitySignal(resolved)
      );
    });
  }

  private record AbilityDetailBinding(IntegrationMetricDescriptor descriptor, String abilityId, String signal) {
  }
}

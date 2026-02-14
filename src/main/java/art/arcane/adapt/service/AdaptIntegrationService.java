package art.arcane.adapt.service;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.content.event.AdaptAdaptationUseEvent;
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
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.ServicePriority;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class AdaptIntegrationService implements AdaptService, IntegrationServiceContract {
    private static final long ABILITY_WINDOW_MS = 60_000L;
    private static final Set<IntegrationProtocolVersion> SUPPORTED_PROTOCOLS = Set.of(
            new IntegrationProtocolVersion(1, 0),
            new IntegrationProtocolVersion(1, 1)
    );
    private static final Set<String> CAPABILITIES = Set.of(
            "handshake",
            "heartbeat",
            "metrics",
            "adapt-runtime-metrics"
    );

    private final ArrayDeque<Long> abilityOps = new ArrayDeque<>();
    private volatile IntegrationProtocolVersion negotiatedProtocol = new IntegrationProtocolVersion(1, 1);

    @Override
    public void onEnable() {
        Bukkit.getServicesManager().register(IntegrationServiceContract.class, this, Adapt.instance, ServicePriority.Normal);
        Adapt.verbose("Integration provider registered for Adapt");
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregister(IntegrationServiceContract.class, this);
        synchronized (abilityOps) {
            abilityOps.clear();
        }
    }

    @EventHandler
    public void onAdaptationUse(AdaptAdaptationUseEvent event) {
        long now = System.currentTimeMillis();
        synchronized (abilityOps) {
            abilityOps.addLast(now);
            trimAbilityOps(now);
        }
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
        return IntegrationMetricSchema.descriptors().stream()
                .filter(descriptor -> descriptor.key().startsWith("adapt."))
                .collect(java.util.stream.Collectors.toSet());
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
        Set<String> requested = metricKeys == null || metricKeys.isEmpty()
                ? IntegrationMetricSchema.adaptKeys()
                : metricKeys;
        long now = System.currentTimeMillis();
        Map<String, IntegrationMetricSample> out = new HashMap<>();

        for (String key : requested) {
            switch (key) {
                case IntegrationMetricSchema.ADAPT_SESSION_LOAD -> out.put(key, sampleSessionLoad(now));
                case IntegrationMetricSchema.ADAPT_ABILITY_OPS -> out.put(key, sampleAbilityOps(now));
                case IntegrationMetricSchema.ADAPT_WORLD_POLICY_LATENCY -> out.put(key, IntegrationMetricSample.unavailable(
                        IntegrationMetricSchema.descriptor(key),
                        "world-policy-latency-not-instrumented",
                        now
                ));
                default -> out.put(key, IntegrationMetricSample.unavailable(
                        IntegrationMetricSchema.descriptor(key),
                        "unsupported-key",
                        now
                ));
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
        long count;
        synchronized (abilityOps) {
            trimAbilityOps(now);
            count = abilityOps.size();
        }

        return IntegrationMetricSample.available(descriptor, count, now);
    }

    private void trimAbilityOps(long now) {
        while (!abilityOps.isEmpty() && (now - abilityOps.peekFirst()) > ABILITY_WINDOW_MS) {
            abilityOps.removeFirst();
        }
    }
}

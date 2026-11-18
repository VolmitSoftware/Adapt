package art.arcane.adapt.api.ability.internal;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AbilityProviderGuard {
  private static final long WARN_INTERVAL_MILLIS = 60_000L;

  private final String lane;
  private final Logger logger;
  private final LongSupplier clock;
  private final ConcurrentHashMap<String, AtomicInteger> faults = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> warnedAt = new ConcurrentHashMap<>();
  private final Set<String> quarantined = ConcurrentHashMap.newKeySet();

  private volatile boolean anyQuarantined;

  public AbilityProviderGuard(String lane, Logger logger, LongSupplier clock) {
    this.lane = Objects.requireNonNull(lane, "lane");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public boolean anyQuarantined() {
    return anyQuarantined;
  }

  public boolean isQuarantined(String providerId) {
    return anyQuarantined && quarantined.contains(providerId);
  }

  public int faultCount(String providerId) {
    AtomicInteger count = faults.get(providerId);
    return count == null ? 0 : count.get();
  }

  public void fault(AbilityProviderRegistration<?> registration, AbilityApiPolicy policy, String phase,
                    Throwable error, String detail) {
    String message = "Adapt " + lane + " provider '" + registration.providerId() + "' from plugin '"
        + registration.pluginName() + "' failed during " + phase
        + (detail == null || detail.isEmpty() ? "" : ": " + detail);

    if (error == null) {
      logger.warning(message);
    } else {
      logger.log(Level.WARNING, message, error);
    }

    strike(registration, policy);
  }

  public void observe(AbilityProviderRegistration<?> registration, AbilityApiPolicy policy, String phase,
                      long elapsedMillis) {
    if (!policy.watchdogEnabled() || elapsedMillis < policy.slowProviderMillis()) {
      return;
    }

    if (!throttle(registration.providerId() + "#slow#" + phase)) {
      return;
    }

    logger.warning("Adapt " + lane + " provider '" + registration.providerId() + "' from plugin '"
        + registration.pluginName() + "' spent " + elapsedMillis + "ms in " + phase
        + "; provider calls run on the tick thread that owns the player and must not block");
  }

  public boolean throttle(String key) {
    long now = clock.getAsLong();
    AtomicLong last = warnedAt.computeIfAbsent(key, ignored -> new AtomicLong());
    long previous = last.get();

    if (previous != 0L && now - previous < WARN_INTERVAL_MILLIS) {
      return false;
    }

    return last.compareAndSet(previous, now);
  }

  public void forgetAbsent(List<? extends AbilityProviderRegistration<?>> present) {
    if (quarantined.isEmpty() && faults.isEmpty()) {
      return;
    }

    Set<String> ids = new HashSet<>(present.size());

    for (int index = 0; index < present.size(); index++) {
      ids.add(present.get(index).providerId());
    }

    quarantined.retainAll(ids);
    faults.keySet().retainAll(ids);
    warnedAt.keySet().removeIf(key -> {
      int marker = key.indexOf('#');
      return marker > 0 && !ids.contains(key.substring(0, marker));
    });
    anyQuarantined = !quarantined.isEmpty();
  }

  public void clear() {
    quarantined.clear();
    faults.clear();
    warnedAt.clear();
    anyQuarantined = false;
  }

  private void strike(AbilityProviderRegistration<?> registration, AbilityApiPolicy policy) {
    if (!policy.quarantineEnabled()) {
      return;
    }

    int count = faults.computeIfAbsent(registration.providerId(), ignored -> new AtomicInteger()).incrementAndGet();

    if (count < policy.providerFaultLimit() || !quarantined.add(registration.providerId())) {
      return;
    }

    anyQuarantined = true;
    logger.severe("Disabled Adapt " + lane + " provider '" + registration.providerId() + "' from plugin '"
        + registration.pluginName() + "' after " + count + " failures");
  }
}

package art.arcane.adapt.api.ability.internal;

import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class AbilityUsePolicyGateway {
  private final AbilityProviderSource<AbilityUsePolicy> source;
  private final Supplier<AbilityApiPolicy> policy;
  private final AbilityProviderGuard guard;
  private final Logger logger;
  private final LongSupplier clock;
  private final Predicate<Player> onOwningThread;
  private final Map<UUID, AbilityDenial> denials;

  private final AtomicLong denied = new AtomicLong();
  private final AtomicLong faulted = new AtomicLong();
  private final AtomicLong reentrant = new AtomicLong();
  private final AtomicLong offThread = new AtomicLong();
  private final ThreadLocal<Boolean> inPipeline = new ThreadLocal<>();

  private volatile List<AbilityProviderRegistration<AbilityUsePolicy>> knownRegistrations = List.of();

  public AbilityUsePolicyGateway(AbilityProviderSource<AbilityUsePolicy> source, Supplier<AbilityApiPolicy> policy,
                                 AbilityProviderGuard guard, Logger logger, LongSupplier clock,
                                 Predicate<Player> onOwningThread, Map<UUID, AbilityDenial> denials) {
    this.source = Objects.requireNonNull(source, "source");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.guard = Objects.requireNonNull(guard, "guard");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.onOwningThread = Objects.requireNonNull(onOwningThread, "onOwningThread");
    this.denials = Objects.requireNonNull(denials, "denials");
  }

  public boolean hasPoliciesFor(String abilityId, String skillId) {
    AbilityProviderIndex<AbilityUsePolicy> index = source.index();

    if (index.isEmpty()) {
      return false;
    }

    forget(index);
    return !index.matching(abilityId, skillId).isEmpty();
  }

  public AbilityUseDecision evaluate(AbilityContext context) {
    Objects.requireNonNull(context, "context");
    AbilityApiPolicy active = activePolicy();

    if (!active.enabled()) {
      return AbilityUseDecision.allow();
    }

    AbilityProviderIndex<AbilityUsePolicy> index = source.index();

    if (index.isEmpty()) {
      return AbilityUseDecision.allow();
    }

    forget(index);
    List<AbilityProviderRegistration<AbilityUsePolicy>> matched =
        index.matching(context.abilityId(), context.skillId());

    if (matched.isEmpty()) {
      return AbilityUseDecision.allow();
    }

    if (!onOwningThread.test(context.player())) {
      offThread.incrementAndGet();

      if (guard.throttle("@offthread")) {
        logger.warning("Skipped Adapt use policy providers for " + context.abilityId()
            + " because the check ran off the tick thread that owns the player");
      }

      return AbilityUseDecision.allow();
    }

    if (Boolean.TRUE.equals(inPipeline.get())) {
      reentrant.incrementAndGet();

      if (guard.throttle("@reentrant")) {
        logger.warning("Allowed a reentrant Adapt use policy evaluation for " + context.abilityId()
            + "; a registered AbilityUsePolicy re-entered the ability check path");
      }

      return AbilityUseDecision.allow();
    }

    inPipeline.set(Boolean.TRUE);

    try {
      return evaluateInternal(context, matched, active);
    } finally {
      inPipeline.remove();
    }
  }

  public AbilityDenial lastDenial(UUID playerId) {
    return playerId == null ? null : denials.get(playerId);
  }

  public long deniedCount() {
    return denied.get();
  }

  public long faultCount() {
    return faulted.get();
  }

  public long reentrantCount() {
    return reentrant.get();
  }

  public long offThreadCount() {
    return offThread.get();
  }

  private AbilityUseDecision evaluateInternal(AbilityContext context,
                                              List<AbilityProviderRegistration<AbilityUsePolicy>> matched,
                                              AbilityApiPolicy active) {
    boolean anyQuarantined = guard.anyQuarantined();

    for (int index = 0; index < matched.size(); index++) {
      AbilityProviderRegistration<AbilityUsePolicy> registration = matched.get(index);

      if (anyQuarantined && guard.isQuarantined(registration.providerId())) {
        continue;
      }

      if (!registration.ownerEnabled()) {
        continue;
      }

      AbilityUseDecision decision = callEvaluate(registration, context, active);

      if (decision == null) {
        faulted.incrementAndGet();

        if (active.useFailClosed()) {
          record(context, registration.providerId(), "");
          return AbilityUseDecision.deny("");
        }

        continue;
      }

      if (!decision.allowed()) {
        denied.incrementAndGet();
        record(context, registration.providerId(), decision.reason());
        return decision;
      }
    }

    return AbilityUseDecision.allow();
  }

  private AbilityUseDecision callEvaluate(AbilityProviderRegistration<AbilityUsePolicy> registration,
                                          AbilityContext context, AbilityApiPolicy active) {
    long started = clock.getAsLong();

    try {
      AbilityUseDecision decision = registration.provider().evaluate(context);

      if (decision == null) {
        guard.fault(registration, active, "evaluate", null, "returned null");
      }

      return decision;
    } catch (Throwable error) {
      guard.fault(registration, active, "evaluate", error, "");
      return null;
    } finally {
      guard.observe(registration, active, "evaluate", clock.getAsLong() - started);
    }
  }

  private void record(AbilityContext context, String providerId, String reason) {
    UUID playerId = context.playerId();
    AbilityDenial existing = denials.get(playerId);
    long now = clock.getAsLong();

    if (existing != null
        && existing.abilityId().equals(context.abilityId())
        && existing.providerId().equals(providerId)
        && existing.reason().equals(reason)
        && now - existing.atMillis() < activePolicy().denyThrottleMillis()) {
      return;
    }

    denials.put(playerId, new AbilityDenial(context.abilityId(), providerId, reason, now));
  }

  private void forget(AbilityProviderIndex<AbilityUsePolicy> index) {
    List<AbilityProviderRegistration<AbilityUsePolicy>> current = index.all();

    if (knownRegistrations == current) {
      return;
    }

    knownRegistrations = current;
    guard.forgetAbsent(current);
  }

  private AbilityApiPolicy activePolicy() {
    AbilityApiPolicy active = policy.get();
    return active == null ? AbilityApiPolicy.defaults() : active;
  }
}

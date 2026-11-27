package art.arcane.adapt.engine.ability.internal;

import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class AbilityUsePolicyGateway {
  private final AbilityProviderSource<AbilityUsePolicy> source;
  private final Supplier<AbilityApiPolicy> policy;
  private final AbilityProviderGuard guard;
  private final PlatformLog log;
  private final LongSupplier clock;
  private final Predicate<PlayerRef> onOwningThread;
  private final Map<UUID, AbilityDenial> denials;

  private final AtomicLong denied = new AtomicLong();
  private final AtomicLong faulted = new AtomicLong();
  private final AtomicLong reentrant = new AtomicLong();
  private final AtomicLong offThread = new AtomicLong();
  private final ThreadLocal<Boolean> inPipeline = new ThreadLocal<>();
  private final Set<String> reported = ConcurrentHashMap.newKeySet();

  private volatile List<AbilityProviderRegistration<AbilityUsePolicy>> knownRegistrations = List.of();

  public AbilityUsePolicyGateway(AbilityProviderSource<AbilityUsePolicy> source, Supplier<AbilityApiPolicy> policy,
                                 AbilityProviderGuard guard, PlatformLog log, LongSupplier clock,
                                 Predicate<PlayerRef> onOwningThread, Map<UUID, AbilityDenial> denials) {
    this.source = Objects.requireNonNull(source, "source");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.guard = Objects.requireNonNull(guard, "guard");
    this.log = Objects.requireNonNull(log, "log");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.onOwningThread = Objects.requireNonNull(onOwningThread, "onOwningThread");
    this.denials = Objects.requireNonNull(denials, "denials");
  }

  public boolean hasPoliciesFor(Id ability, Id skill) {
    AbilityProviderIndex<AbilityUsePolicy> index = source.index();

    if (index.isEmpty()) {
      return false;
    }

    forget(index);
    return !index.matching(ability, skill).isEmpty();
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
        index.matching(context.ability(), context.skill());

    if (matched.isEmpty()) {
      return AbilityUseDecision.allow();
    }

    if (!onOwningThread.test(context.player())) {
      offThread.incrementAndGet();
      return unsafe(context, matched.get(0), active, "off the tick thread that owns the player");
    }

    if (Boolean.TRUE.equals(inPipeline.get())) {
      reentrant.incrementAndGet();
      return unsafe(context, matched.get(0), active, "inside another use policy check");
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
        if (active.useFailClosed()) {
          return unsafe(context, registration, active, "while the policy is quarantined");
        }

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

  /**
   * A check the policies cannot answer safely: off the owning thread, re-entered from a policy, or for a quarantined
   * policy. The failure mode decides; each case is logged once per policy.
   */
  private AbilityUseDecision unsafe(AbilityContext context, AbilityProviderRegistration<AbilityUsePolicy> registration,
                                    AbilityApiPolicy active, String why) {
    boolean deny = active.useFailClosed();
    String providerId = registration.providerId();
    if (reported.add(why + '|' + providerId)) {
      log.warn((deny ? "Denied " : "Allowed ") + context.ability() + " for use policy " + providerId
          + " because the check ran " + why + " (use-policy failure mode " + (deny ? "deny" : "allow") + ")");
    }
    if (!deny) {
      return AbilityUseDecision.allow();
    }
    record(context, providerId, "");
    return AbilityUseDecision.deny("");
  }

  private void record(AbilityContext context, String providerId, String reason) {
    UUID playerId = context.player().uuid();
    String abilityId = context.ability().toString();
    AbilityDenial existing = denials.get(playerId);
    long now = clock.getAsLong();

    if (existing != null
        && existing.abilityId().equals(abilityId)
        && existing.providerId().equals(providerId)
        && existing.reason().equals(reason)
        && now - existing.atMillis() < activePolicy().denyThrottleMillis()) {
      return;
    }

    denials.put(playerId, new AbilityDenial(abilityId, providerId, reason, now));
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

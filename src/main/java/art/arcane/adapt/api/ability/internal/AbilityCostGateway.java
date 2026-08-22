package art.arcane.adapt.api.ability.internal;

import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityCostContext;
import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityDefaultCost;
import art.arcane.adapt.api.ability.AbilityOutcome;
import art.arcane.adapt.api.ability.AbilityPhase;
import art.arcane.adapt.api.ability.AbilityQuote;
import art.arcane.adapt.api.ability.AbilityReceipt;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.ability.AbilityReservation;
import art.arcane.adapt.api.ability.AdaptAbilityActivateEvent;
import art.arcane.adapt.api.ability.AdaptAbilityActivatedEvent;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class AbilityCostGateway {
  public static final long TICKET_TTL_MILLIS = 30_000L;

  private static final long SWEEP_INTERVAL_MILLIS = 1_000L;

  private final AbilityProviderSource<AbilityCostProvider> source;
  private final Supplier<AbilityApiPolicy> policy;
  private final AbilityProviderGuard guard;
  private final AbilityEventSink events;
  private final Logger logger;
  private final LongSupplier clock;
  private final Predicate<Player> onOwningThread;

  private final ConcurrentMap<UUID, Ticket> tickets = new ConcurrentHashMap<>();
  private final AtomicLong lastSweep = new AtomicLong();
  private final AtomicLong charged = new AtomicLong();
  private final AtomicLong refused = new AtomicLong();
  private final AtomicLong expired = new AtomicLong();
  private final ThreadLocal<Boolean> inPipeline = new ThreadLocal<>();

  private volatile List<AbilityProviderRegistration<AbilityCostProvider>> knownRegistrations = List.of();

  public AbilityCostGateway(AbilityProviderSource<AbilityCostProvider> source, Supplier<AbilityApiPolicy> policy,
                            AbilityProviderGuard guard, AbilityEventSink events, Logger logger, LongSupplier clock,
                            Predicate<Player> onOwningThread) {
    this.source = Objects.requireNonNull(source, "source");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.guard = Objects.requireNonNull(guard, "guard");
    this.events = Objects.requireNonNull(events, "events");
    this.logger = Objects.requireNonNull(logger, "logger");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.onOwningThread = Objects.requireNonNull(onOwningThread, "onOwningThread");
  }

  public boolean idle() {
    return source.index().isEmpty()
        && AdaptAbilityActivateEvent.getHandlerList().getRegisteredListeners().length == 0
        && AdaptAbilityActivatedEvent.getHandlerList().getRegisteredListeners().length == 0;
  }

  public AbilityCharge charge(AbilityCostContext context, AbilityDefaultCost defaultCost) {
    return run(context, defaultCost, false);
  }

  public AbilityCharge chargeDeferred(AbilityCostContext context, AbilityDefaultCost defaultCost) {
    return run(context, defaultCost, true);
  }

  public boolean settle(UUID activationId) {
    Ticket ticket = claim(activationId);

    if (ticket == null) {
      return false;
    }

    AbilityApiPolicy active = activePolicy();
    List<String> chargedIds = new ArrayList<>(ticket.charges().size());

    for (int index = 0; index < ticket.charges().size(); index++) {
      ChargedEntry entry = ticket.charges().get(index);
      guardedCommit(entry, active);
      chargedIds.add(entry.registration().providerId());
    }

    events.fire(new AdaptAbilityActivatedEvent(ticket.context().ability(), ticket.context(),
        new AbilityCharge(ticket.activationId(), ticket.outcome(), ticket.defaultSuppressed(), "", ticket.providerId(),
            chargedIds)));
    return true;
  }

  public boolean refund(UUID activationId, AbilityRefundReason reason) {
    Ticket ticket = claim(activationId);

    if (ticket == null) {
      return false;
    }

    rollback(ticket.charges(), reason == null ? AbilityRefundReason.ACTIVATION_ABORTED : reason, activePolicy());
    return true;
  }

  public boolean isOpen(UUID activationId) {
    return activationId != null && tickets.containsKey(activationId);
  }

  public int openTickets() {
    return tickets.size();
  }

  public long chargedCount() {
    return charged.get();
  }

  public long refusedCount() {
    return refused.get();
  }

  public long expiredCount() {
    return expired.get();
  }

  public int sweep() {
    if (tickets.isEmpty()) {
      return 0;
    }

    long now = clock.getAsLong();
    int reclaimed = 0;

    for (Ticket ticket : tickets.values()) {
      if (now - ticket.openedAt() < TICKET_TTL_MILLIS) {
        continue;
      }

      if (!tickets.remove(ticket.activationId(), ticket)) {
        continue;
      }

      expired.incrementAndGet();
      logger.warning("Adapt cost ticket " + ticket.activationId() + " for " + ticket.context().costKey()
          + " was never settled or refunded; reclaiming it after " + (now - ticket.openedAt()) + "ms");
      rollback(ticket.charges(), AbilityRefundReason.EXPIRED, activePolicy());
      reclaimed++;
    }

    return reclaimed;
  }

  public int drain(AbilityRefundReason reason) {
    if (tickets.isEmpty()) {
      return 0;
    }

    int drained = 0;

    for (Ticket ticket : tickets.values()) {
      if (!tickets.remove(ticket.activationId(), ticket)) {
        continue;
      }

      rollback(ticket.charges(), reason, activePolicy());
      drained++;
    }

    return drained;
  }

  public int drainAbsent(Set<String> presentProviderIds, AbilityRefundReason reason) {
    if (presentProviderIds == null || tickets.isEmpty()) {
      return 0;
    }

    int drained = 0;

    for (Ticket ticket : tickets.values()) {
      boolean orphaned = false;

      for (int index = 0; index < ticket.charges().size(); index++) {
        if (!presentProviderIds.contains(ticket.charges().get(index).registration().providerId())) {
          orphaned = true;
          break;
        }
      }

      if (!orphaned || !tickets.remove(ticket.activationId(), ticket)) {
        continue;
      }

      rollback(ticket.charges(), reason, activePolicy());
      drained++;
    }

    return drained;
  }

  private AbilityCharge run(AbilityCostContext context, AbilityDefaultCost defaultCost, boolean deferred) {
    Objects.requireNonNull(context, "context");
    AbilityDefaultCost fallback = defaultCost == null ? AbilityDefaultCost.NONE : defaultCost;
    AbilityContext ability = context.ability();
    UUID activationId = ability.activationId();
    AbilityApiPolicy active = activePolicy();

    if (!active.enabled()) {
      return settleDefault(activationId, fallback, AbilityOutcome.DISABLED);
    }

    if (ability.phase() != AbilityPhase.ACTIVATE) {
      if (guard.throttle("@phase#" + ability.abilityId())) {
        logger.warning("Adapt cost gateway was called for " + context.costKey() + " outside AbilityPhase.ACTIVATE; "
            + "charging the built-in cost and skipping providers");
      }

      return settleDefault(activationId, fallback, AbilityOutcome.ALLOWED_DEFAULT);
    }

    if (ability.level() <= 0) {
      if (guard.throttle("@level#" + ability.abilityId())) {
        logger.warning("Adapt cost gateway was called for " + context.costKey() + " while " + ability.abilityId()
            + " was not learned by the player; charging the built-in cost and skipping providers");
      }

      return settleDefault(activationId, fallback, AbilityOutcome.ALLOWED_DEFAULT);
    }

    if (!onOwningThread.test(ability.player())) {
      if (guard.throttle("@offthread")) {
        logger.warning("Adapt cost gateway was called for " + context.costKey() + " off the tick thread that owns "
            + "the player; charging the built-in cost and skipping providers");
      }

      return settleDefault(activationId, fallback, AbilityOutcome.ALLOWED_DEFAULT);
    }

    if (Boolean.TRUE.equals(inPipeline.get())) {
      refused.incrementAndGet();

      if (guard.throttle("@reentrant")) {
        logger.warning("Refused a reentrant Adapt cost evaluation for " + context.costKey()
            + "; a registered AbilityCostProvider re-entered the cost path");
      }

      return new AbilityCharge(activationId, AbilityOutcome.DENIED_REENTRANT, false, "", "", List.of());
    }

    inPipeline.set(Boolean.TRUE);

    try {
      return runInternal(context, fallback, deferred, active);
    } finally {
      inPipeline.remove();
    }
  }

  private AbilityCharge runInternal(AbilityCostContext context, AbilityDefaultCost defaultCost, boolean deferred,
                                    AbilityApiPolicy active) {
    sweepIfDue();
    AbilityContext ability = context.ability();
    UUID activationId = ability.activationId();
    AdaptAbilityActivateEvent event = new AdaptAbilityActivateEvent(ability, context);

    if (events.hasListeners(event)) {
      events.fire(event);

      if (event.isCancelled()) {
        refused.incrementAndGet();
        return new AbilityCharge(activationId, AbilityOutcome.DENIED_BY_LISTENER, false, event.getCancelReason(), "",
            List.of());
      }
    }

    AbilityProviderIndex<AbilityCostProvider> index = source.index();
    forget(index);
    List<AbilityProviderRegistration<AbilityCostProvider>> matched =
        index.matching(ability.abilityId(), ability.skillId());
    List<PayableQuote> payable = new ArrayList<>(matched.size());
    boolean anyQuarantined = guard.anyQuarantined();
    boolean waived = false;
    boolean faulted = false;
    String faultProviderId = "";

    for (int position = 0; position < matched.size(); position++) {
      AbilityProviderRegistration<AbilityCostProvider> registration = matched.get(position);

      if (anyQuarantined && guard.isQuarantined(registration.providerId())) {
        continue;
      }

      if (!registration.ownerEnabled()) {
        continue;
      }

      AbilityQuote quote = callQuote(registration, context, active);

      if (quote == null) {
        faulted = true;
        faultProviderId = registration.providerId();

        if (active.costFailClosed()) {
          refused.incrementAndGet();
          return new AbilityCharge(activationId, AbilityOutcome.DENIED_PROVIDER_FAILED, false, "", faultProviderId,
              List.of());
        }

        continue;
      }

      switch (quote.status()) {
        case PASS -> {
        }
        case WAIVED -> waived = true;
        case PAYABLE -> payable.add(new PayableQuote(registration, quote));
        case INSUFFICIENT -> {
          refused.incrementAndGet();
          return new AbilityCharge(activationId, AbilityOutcome.DENIED_INSUFFICIENT, true, quote.description(),
              registration.providerId(), List.of());
        }
        case DENIED -> {
          refused.incrementAndGet();
          return new AbilityCharge(activationId, AbilityOutcome.DENIED_BY_PROVIDER, true, quote.description(),
              registration.providerId(), List.of());
        }
      }
    }

    List<ChargedEntry> reserved = new ArrayList<>(payable.size());
    AbilityCharge refusal = null;
    boolean reserveFaulted = false;

    for (int position = 0; position < payable.size(); position++) {
      PayableQuote entry = payable.get(position);
      AbilityReservation reservation = callReserve(entry.registration(), context, entry.quote(), active);

      if (reservation != null && reservation.reserved()) {
        reserved.add(new ChargedEntry(entry.registration(), reservation.receipt()));
        continue;
      }

      if (reservation != null) {
        refusal = new AbilityCharge(activationId, AbilityOutcome.DENIED_INSUFFICIENT, true, reservation.reason(),
            entry.registration().providerId(), List.of());
      } else {
        reserveFaulted = true;
        faultProviderId = entry.registration().providerId();
      }

      rollback(reserved, AbilityRefundReason.CHARGE_ROLLBACK, active);
      reserved.clear();
      break;
    }

    if (refusal != null) {
      refused.incrementAndGet();
      return refusal;
    }

    if (reserveFaulted) {
      faulted = true;

      if (active.costFailClosed()) {
        refused.incrementAndGet();
        return new AbilityCharge(activationId, AbilityOutcome.DENIED_PROVIDER_FAILED, false, "", faultProviderId,
            List.of());
      }
    }

    boolean defaultSuppressed = waived || !reserved.isEmpty();

    if (!defaultSuppressed && !defaultCost.take()) {
      rollback(reserved, AbilityRefundReason.CHARGE_ROLLBACK, active);
      refused.incrementAndGet();
      return new AbilityCharge(activationId, AbilityOutcome.DENIED_INSUFFICIENT, false, "", "", List.of());
    }

    AbilityOutcome outcome = faulted
        ? AbilityOutcome.ALLOWED_PROVIDER_FAILED
        : !reserved.isEmpty() ? AbilityOutcome.ALLOWED_CHARGED
        : waived ? AbilityOutcome.ALLOWED_WAIVED : AbilityOutcome.ALLOWED_DEFAULT;
    charged.incrementAndGet();

    if (deferred) {
      tickets.put(activationId, new Ticket(activationId, context, outcome, defaultSuppressed,
          faulted ? faultProviderId : "", List.copyOf(reserved), clock.getAsLong()));
      return new AbilityCharge(activationId, outcome, defaultSuppressed, "", faulted ? faultProviderId : "",
          providerIds(reserved));
    }

    for (int position = 0; position < reserved.size(); position++) {
      guardedCommit(reserved.get(position), active);
    }

    AbilityCharge result = new AbilityCharge(activationId, outcome, defaultSuppressed, "",
        faulted ? faultProviderId : "", providerIds(reserved));
    events.fire(new AdaptAbilityActivatedEvent(ability, context, result));
    return result;
  }

  private AbilityCharge settleDefault(UUID activationId, AbilityDefaultCost defaultCost, AbilityOutcome outcome) {
    if (defaultCost.take()) {
      return new AbilityCharge(activationId, outcome, false, "", "", List.of());
    }

    return new AbilityCharge(activationId, AbilityOutcome.DENIED_INSUFFICIENT, false, "", "", List.of());
  }

  private List<String> providerIds(List<ChargedEntry> entries) {
    if (entries.isEmpty()) {
      return List.of();
    }

    List<String> ids = new ArrayList<>(entries.size());

    for (int index = 0; index < entries.size(); index++) {
      ids.add(entries.get(index).registration().providerId());
    }

    return ids;
  }

  private Ticket claim(UUID activationId) {
    return activationId == null ? null : tickets.remove(activationId);
  }

  private AbilityQuote callQuote(AbilityProviderRegistration<AbilityCostProvider> registration,
                                 AbilityCostContext context, AbilityApiPolicy active) {
    long started = clock.getAsLong();

    try {
      AbilityQuote quote = registration.provider().quote(context);

      if (quote == null) {
        guard.fault(registration, active, "quote", null, "returned null");
        return null;
      }

      return quote;
    } catch (Throwable error) {
      guard.fault(registration, active, "quote", error, "");
      return null;
    } finally {
      guard.observe(registration, active, "quote", clock.getAsLong() - started);
    }
  }

  private AbilityReservation callReserve(AbilityProviderRegistration<AbilityCostProvider> registration,
                                         AbilityCostContext context, AbilityQuote quote, AbilityApiPolicy active) {
    long started = clock.getAsLong();

    try {
      AbilityReservation reservation = registration.provider().reserve(context, quote);

      if (reservation == null) {
        guard.fault(registration, active, "reserve", null, "returned null");
      }

      return reservation;
    } catch (Throwable error) {
      guard.fault(registration, active, "reserve", error, "");
      return null;
    } finally {
      guard.observe(registration, active, "reserve", clock.getAsLong() - started);
    }
  }

  private void guardedCommit(ChargedEntry entry, AbilityApiPolicy active) {
    long started = clock.getAsLong();

    try {
      entry.registration().provider().commit(entry.receipt());
    } catch (Throwable error) {
      guard.fault(entry.registration(), active, "commit", error, "");
    } finally {
      guard.observe(entry.registration(), active, "commit", clock.getAsLong() - started);
    }
  }

  private void rollback(List<ChargedEntry> entries, AbilityRefundReason reason, AbilityApiPolicy active) {
    for (int index = entries.size() - 1; index >= 0; index--) {
      guardedRefund(entries.get(index), reason, active);
    }
  }

  private void guardedRefund(ChargedEntry entry, AbilityRefundReason reason, AbilityApiPolicy active) {
    AbilityProviderRegistration<AbilityCostProvider> registration = entry.registration();

    if (!registration.ownerEnabled()) {
      logger.warning("Refunding through Adapt cost provider '" + registration.providerId() + "' whose plugin '"
          + registration.pluginName() + "' is disabled");
    }

    long started = clock.getAsLong();

    try {
      registration.provider().refund(entry.receipt(), reason);
    } catch (Throwable error) {
      logger.log(Level.WARNING, "Adapt cost provider '" + registration.providerId() + "' from plugin '"
          + registration.pluginName() + "' failed to refund " + reason, error);
      guard.fault(registration, active, "refund", null, "threw while refunding");
    } finally {
      guard.observe(registration, active, "refund", clock.getAsLong() - started);
    }
  }

  private void forget(AbilityProviderIndex<AbilityCostProvider> index) {
    List<AbilityProviderRegistration<AbilityCostProvider>> current = index.all();

    if (knownRegistrations == current) {
      return;
    }

    knownRegistrations = current;
    guard.forgetAbsent(current);
  }

  private void sweepIfDue() {
    if (tickets.isEmpty()) {
      return;
    }

    long now = clock.getAsLong();
    long last = lastSweep.get();

    if (now - last < SWEEP_INTERVAL_MILLIS || !lastSweep.compareAndSet(last, now)) {
      return;
    }

    sweep();
  }

  private AbilityApiPolicy activePolicy() {
    AbilityApiPolicy active = policy.get();
    return active == null ? AbilityApiPolicy.defaults() : active;
  }

  private record PayableQuote(AbilityProviderRegistration<AbilityCostProvider> registration, AbilityQuote quote) {
  }

  private record ChargedEntry(AbilityProviderRegistration<AbilityCostProvider> registration, AbilityReceipt receipt) {
  }

  private record Ticket(UUID activationId, AbilityCostContext context, AbilityOutcome outcome,
                        boolean defaultSuppressed, String providerId, List<ChargedEntry> charges, long openedAt) {
  }
}

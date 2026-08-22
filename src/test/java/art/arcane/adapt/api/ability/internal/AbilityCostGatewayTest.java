package art.arcane.adapt.api.ability.internal;

import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityCostContext;
import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityOutcome;
import art.arcane.adapt.api.ability.AbilityQuote;
import art.arcane.adapt.api.ability.AbilityReceipt;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.ability.AbilityReservation;
import art.arcane.adapt.api.ability.AbilityScope;
import art.arcane.adapt.api.ability.AdaptAbilityActivateEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class AbilityCostGatewayTest {
  private final AtomicLong clock = new AtomicLong(1_000L);
  private final Logger logger = AbilityGatewayFixture.silentLogger();
  private final RecordingSink sink = new RecordingSink();

  private AbilityCostGateway gateway(List<AbilityProviderRegistration<AbilityCostProvider>> registrations,
                                     AbilityApiPolicy policy, boolean onOwningThread) {
    return new AbilityCostGateway(AbilityGatewayFixture.costSource(registrations), () -> policy,
        new AbilityProviderGuard("cost", logger, clock::get), sink, logger, clock::get, player -> onOwningThread);
  }

  private AbilityCostContext context(Player player, int level) {
    return new AbilityCostContext(AbilityContext.activate("tragoul-skeletal-servant", "tragoul", level, player, null),
        "adaptation:tragoul-skeletal-servant:summon", AbilityCostKind.ITEM, Optional.empty(), 4);
  }

  @Test
  void withNoProvidersTheBuiltInCostIsTaken() {
    AtomicBoolean taken = new AtomicBoolean();
    AbilityCostGateway gateway = gateway(List.of(), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> {
      taken.set(true);
      return true;
    });

    assertThat(charge.allowed()).isTrue();
    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.ALLOWED_DEFAULT);
    assertThat(taken).isTrue();
  }

  @Test
  void passLeavesTheBuiltInCostAlone() {
    AtomicBoolean taken = new AtomicBoolean();
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("pass", AbilityScope.everything(),
        costContext -> AbilityQuote.pass())), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> {
      taken.set(true);
      return true;
    });

    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.ALLOWED_DEFAULT);
    assertThat(charge.defaultCostSuppressed()).isFalse();
    assertThat(taken).isTrue();
  }

  @Test
  void waivedSuppressesTheBuiltInCost() {
    AtomicBoolean taken = new AtomicBoolean();
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("free", AbilityScope.everything(),
        costContext -> AbilityQuote.waived("free in the arena"))), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> {
      taken.set(true);
      return true;
    });

    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.ALLOWED_WAIVED);
    assertThat(charge.defaultCostSuppressed()).isTrue();
    assertThat(taken).isFalse();
  }

  @Test
  void payableReservesCommitsAndSuppressesTheBuiltInCost() {
    AtomicBoolean taken = new AtomicBoolean();
    RecordingProvider provider = new RecordingProvider(costContext -> AbilityQuote.payable("3 Mana"));
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("mana", AbilityScope.everything(),
        provider)), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> {
      taken.set(true);
      return true;
    });

    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.ALLOWED_CHARGED);
    assertThat(charge.chargedProviderIds()).containsExactly("mana");
    assertThat(taken).isFalse();
    assertThat(provider.reserves.get()).isOne();
    assertThat(provider.commits.get()).isOne();
    assertThat(provider.refunds.get()).isZero();
  }

  @Test
  void deniedAbortsAndNothingIsTaken() {
    AtomicBoolean taken = new AtomicBoolean();
    RecordingProvider provider = new RecordingProvider(costContext -> AbilityQuote.denied("no summoning in spawn"));
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("region", AbilityScope.everything(),
        provider)), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> {
      taken.set(true);
      return true;
    });

    assertThat(charge.allowed()).isFalse();
    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.DENIED_BY_PROVIDER);
    assertThat(charge.reason()).isEqualTo("no summoning in spawn");
    assertThat(taken).isFalse();
    assertThat(provider.reserves.get()).isZero();
  }

  @Test
  void insufficientAbortsAndNothingIsTaken() {
    AtomicBoolean taken = new AtomicBoolean();
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("mana", AbilityScope.everything(),
        costContext -> AbilityQuote.insufficient("3 Mana"))), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> {
      taken.set(true);
      return true;
    });

    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.DENIED_INSUFFICIENT);
    assertThat(taken).isFalse();
  }

  @Test
  void aFailedReserveRollsBackEveryEarlierProviderInReverseOrder() {
    List<String> refunded = new ArrayList<>();
    AbilityProviderRegistration<AbilityCostProvider> first = AbilityGatewayFixture.provider("first",
        AbilityScope.everything(), new AbilityCostProvider() {
          @Override
          public AbilityQuote quote(AbilityCostContext costContext) {
            return AbilityQuote.payable("first");
          }

          @Override
          public AbilityReservation reserve(AbilityCostContext costContext, AbilityQuote quote) {
            return AbilityReservation.reserved(AbilityReceipt.of("first"));
          }

          @Override
          public void refund(AbilityReceipt receipt, AbilityRefundReason reason) {
            refunded.add("first:" + reason);
          }
        });
    AbilityProviderRegistration<AbilityCostProvider> second = AbilityGatewayFixture.provider("second",
        AbilityScope.everything(), new AbilityCostProvider() {
          @Override
          public AbilityQuote quote(AbilityCostContext costContext) {
            return AbilityQuote.payable("second");
          }

          @Override
          public AbilityReservation reserve(AbilityCostContext costContext, AbilityQuote quote) {
            return AbilityReservation.failed("out of mana");
          }
        });
    AtomicBoolean taken = new AtomicBoolean();
    AbilityCostGateway gateway = gateway(AbilityGatewayFixture.list(first, second), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> {
      taken.set(true);
      return true;
    });

    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.DENIED_INSUFFICIENT);
    assertThat(charge.reason()).isEqualTo("out of mana");
    assertThat(refunded).containsExactly("first:CHARGE_ROLLBACK");
    assertThat(taken).isFalse();
  }

  @Test
  void aBuiltInCostThePlayerCannotAffordRollsBackProviderCharges() {
    RecordingProvider provider = new RecordingProvider(costContext -> AbilityQuote.pass());
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("watcher", AbilityScope.everything(),
        provider)), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> false);

    assertThat(charge.allowed()).isFalse();
    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.DENIED_INSUFFICIENT);
  }

  @Test
  void commitIsFinalSoARefundAfterAnImmediateChargeIsANoOp() {
    RecordingProvider provider = new RecordingProvider(costContext -> AbilityQuote.payable("3 Mana"));
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("mana", AbilityScope.everything(),
        provider)), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> true);

    assertThat(gateway.isOpen(charge.activationId())).isFalse();
    assertThat(gateway.refund(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED)).isFalse();
    assertThat(gateway.refund(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED)).isFalse();
    assertThat(provider.refunds.get()).isZero();
    assertThat(provider.commits.get()).isOne();
  }

  @Test
  void aDeferredChargeCommitsOnSettleAndNeverTwice() {
    RecordingProvider provider = new RecordingProvider(costContext -> AbilityQuote.payable("3 Mana"));
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("mana", AbilityScope.everything(),
        provider)), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.chargeDeferred(context(AbilityGatewayFixture.player(), 3), () -> true);

    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.ALLOWED_CHARGED);
    assertThat(gateway.isOpen(charge.activationId())).isTrue();
    assertThat(provider.commits.get()).isZero();
    assertThat(gateway.settle(charge.activationId())).isTrue();
    assertThat(provider.commits.get()).isOne();
    assertThat(gateway.settle(charge.activationId())).isFalse();
    assertThat(gateway.refund(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED)).isFalse();
    assertThat(provider.refunds.get()).isZero();
  }

  @Test
  void aDeferredChargeRefundsOnceAndOnlyOnce() {
    RecordingProvider provider = new RecordingProvider(costContext -> AbilityQuote.payable("3 Mana"));
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("mana", AbilityScope.everything(),
        provider)), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.chargeDeferred(context(AbilityGatewayFixture.player(), 3), () -> true);

    assertThat(gateway.refund(charge.activationId(), AbilityRefundReason.TARGET_LOST)).isTrue();
    assertThat(provider.refunds.get()).isOne();
    assertThat(gateway.refund(charge.activationId(), AbilityRefundReason.TARGET_LOST)).isFalse();
    assertThat(gateway.settle(charge.activationId())).isFalse();
    assertThat(provider.commits.get()).isZero();
    assertThat(provider.refunds.get()).isOne();
  }

  @Test
  void anAbandonedTicketIsReclaimedAfterTheTtl() {
    RecordingProvider provider = new RecordingProvider(costContext -> AbilityQuote.payable("3 Mana"));
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("mana", AbilityScope.everything(),
        provider)), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.chargeDeferred(context(AbilityGatewayFixture.player(), 3), () -> true);
    clock.addAndGet(AbilityCostGateway.TICKET_TTL_MILLIS + 1L);

    assertThat(gateway.sweep()).isOne();
    assertThat(provider.refunds.get()).isOne();
    assertThat(gateway.isOpen(charge.activationId())).isFalse();
    assertThat(gateway.expiredCount()).isOne();
  }

  @Test
  void shutdownDrainsEveryOpenTicket() {
    RecordingProvider provider = new RecordingProvider(costContext -> AbilityQuote.payable("3 Mana"));
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("mana", AbilityScope.everything(),
        provider)), AbilityApiPolicy.defaults(), true);

    gateway.chargeDeferred(context(AbilityGatewayFixture.player(), 3), () -> true);
    gateway.chargeDeferred(context(AbilityGatewayFixture.player(), 3), () -> true);

    assertThat(gateway.openTickets()).isEqualTo(2);
    assertThat(gateway.drain(AbilityRefundReason.SERVER_SHUTDOWN)).isEqualTo(2);
    assertThat(provider.refunds.get()).isEqualTo(2);
    assertThat(gateway.openTickets()).isZero();
  }

  @Test
  void anUnregisteredProviderLeavesNoOpenTicketBehind() {
    RecordingProvider provider = new RecordingProvider(costContext -> AbilityQuote.payable("3 Mana"));
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("mana", AbilityScope.everything(),
        provider)), AbilityApiPolicy.defaults(), true);

    gateway.chargeDeferred(context(AbilityGatewayFixture.player(), 3), () -> true);

    assertThat(gateway.drainAbsent(Set.of(), AbilityRefundReason.ADAPTATION_DISABLED)).isOne();
    assertThat(provider.refunds.get()).isOne();
  }

  @Test
  void aThrowingProviderFailsOpenSoTheAbilityStillWorks() {
    AtomicBoolean taken = new AtomicBoolean();
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("broken", AbilityScope.everything(),
        costContext -> {
          throw new IllegalStateException("boom");
        })), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> {
      taken.set(true);
      return true;
    });

    assertThat(charge.allowed()).isTrue();
    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.ALLOWED_PROVIDER_FAILED);
    assertThat(charge.providerId()).isEqualTo("broken");
    assertThat(taken).isTrue();
  }

  @Test
  void aThrowingProviderDeniesWhenTheFailureModeIsClosed() {
    AtomicBoolean taken = new AtomicBoolean();
    AbilityApiPolicy closed = AbilityApiPolicy.of(true, "deny", "deny", 5, 2L, 2_000L);
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("broken", AbilityScope.everything(),
        costContext -> {
          throw new IllegalStateException("boom");
        })), closed, true);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> {
      taken.set(true);
      return true;
    });

    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.DENIED_PROVIDER_FAILED);
    assertThat(taken).isFalse();
  }

  @Test
  void aCancelledActivateEventDeniesAndTakesNothing() {
    AtomicBoolean taken = new AtomicBoolean();
    sink.cancel.set(true);
    AtomicInteger quotes = new AtomicInteger();
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("mana", AbilityScope.everything(),
        costContext -> {
          quotes.incrementAndGet();
          return AbilityQuote.payable("3 Mana");
        })), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> {
      taken.set(true);
      return true;
    });

    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.DENIED_BY_LISTENER);
    assertThat(taken).isFalse();
    assertThat(quotes.get()).isZero();
  }

  @Test
  void anUnlearnedAbilityChargesTheBuiltInCostAndSkipsProviders() {
    AtomicInteger quotes = new AtomicInteger();
    AtomicBoolean taken = new AtomicBoolean();
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("mana", AbilityScope.everything(),
        costContext -> {
          quotes.incrementAndGet();
          return AbilityQuote.waived("free");
        })), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 0), () -> {
      taken.set(true);
      return true;
    });

    assertThat(quotes.get()).isZero();
    assertThat(taken).isTrue();
    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.ALLOWED_DEFAULT);
  }

  @Test
  void aCheckPhaseContextNeverReachesProviders() {
    AtomicInteger quotes = new AtomicInteger();
    AbilityCostContext checkContext = new AbilityCostContext(
        AbilityContext.check("tragoul-skeletal-servant", "tragoul", 3, AbilityGatewayFixture.player()),
        "adaptation:tragoul-skeletal-servant:summon", AbilityCostKind.ITEM, Optional.empty(), 1);
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("mana", AbilityScope.everything(),
        costContext -> {
          quotes.incrementAndGet();
          return AbilityQuote.waived("free");
        })), AbilityApiPolicy.defaults(), true);

    assertThat(gateway.charge(checkContext, () -> true).outcome()).isEqualTo(AbilityOutcome.ALLOWED_DEFAULT);
    assertThat(quotes.get()).isZero();
  }

  @Test
  void aReentrantChargeIsDenied() {
    List<AbilityCostGateway> holder = new ArrayList<>(1);
    List<AbilityOutcome> inner = new ArrayList<>(1);
    Player player = AbilityGatewayFixture.player();
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("recursive",
        AbilityScope.everything(), costContext -> {
          if (inner.isEmpty()) {
            inner.add(holder.get(0).charge(context(player, 3), () -> true).outcome());
          }

          return AbilityQuote.pass();
        })), AbilityApiPolicy.defaults(), true);
    holder.add(gateway);

    assertThat(gateway.charge(context(player, 3), () -> true).allowed()).isTrue();
    assertThat(inner).containsExactly(AbilityOutcome.DENIED_REENTRANT);
  }

  @Test
  void aScopedProviderIsNeverQuotedForAnotherSkill() {
    AtomicInteger quotes = new AtomicInteger();
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("rift-only",
        AbilityScope.skill("rift"), costContext -> {
          quotes.incrementAndGet();
          return AbilityQuote.denied("nope");
        })), AbilityApiPolicy.defaults(), true);

    assertThat(gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> true).allowed()).isTrue();
    assertThat(quotes.get()).isZero();
  }

  @Test
  void aDisabledApiJustChargesTheBuiltInCost() {
    AtomicBoolean taken = new AtomicBoolean();
    AtomicInteger quotes = new AtomicInteger();
    AbilityApiPolicy disabled = AbilityApiPolicy.of(false, "deny", "allow", 5, 2L, 2_000L);
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("mana", AbilityScope.everything(),
        costContext -> {
          quotes.incrementAndGet();
          return AbilityQuote.waived("free");
        })), disabled, true);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> {
      taken.set(true);
      return true;
    });

    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.DISABLED);
    assertThat(taken).isTrue();
    assertThat(quotes.get()).isZero();
  }

  @Test
  void theOwningThreadCheckIsAskedAboutThePlayerBeingCharged() {
    Player player = AbilityGatewayFixture.player();
    AtomicReference<Player> asked = new AtomicReference<>();
    AbilityCostGateway gateway = new AbilityCostGateway(AbilityGatewayFixture.costSource(List.of()),
        AbilityApiPolicy::defaults, new AbilityProviderGuard("cost", logger, clock::get), sink, logger, clock::get,
        candidate -> {
          asked.set(candidate);
          return true;
        });

    assertThat(gateway.charge(context(player, 3), () -> true).allowed()).isTrue();
    assertThat(asked.get()).isSameAs(player);
  }

  @Test
  void offTheOwningThreadProvidersAreSkippedAndTheBuiltInCostIsTaken() {
    AtomicInteger quotes = new AtomicInteger();
    AtomicBoolean taken = new AtomicBoolean();
    AbilityCostGateway gateway = gateway(List.of(AbilityGatewayFixture.provider("mana", AbilityScope.everything(),
        costContext -> {
          quotes.incrementAndGet();
          return AbilityQuote.waived("free");
        })), AbilityApiPolicy.defaults(), false);

    AbilityCharge charge = gateway.charge(context(AbilityGatewayFixture.player(), 3), () -> {
      taken.set(true);
      return true;
    });

    assertThat(quotes.get()).isZero();
    assertThat(taken).isTrue();
    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.ALLOWED_DEFAULT);
  }

  @Test
  void aProviderThatThrowsFromRefundDoesNotStopTheRestOfTheRollback() {
    List<String> refunded = new ArrayList<>();
    AbilityProviderRegistration<AbilityCostProvider> first = AbilityGatewayFixture.provider("first",
        AbilityScope.everything(), new AbilityCostProvider() {
          @Override
          public AbilityQuote quote(AbilityCostContext costContext) {
            return AbilityQuote.payable("first");
          }

          @Override
          public AbilityReservation reserve(AbilityCostContext costContext, AbilityQuote quote) {
            return AbilityReservation.reserved(AbilityReceipt.of("first"));
          }

          @Override
          public void refund(AbilityReceipt receipt, AbilityRefundReason reason) {
            refunded.add("first");
          }
        });
    AbilityProviderRegistration<AbilityCostProvider> second = AbilityGatewayFixture.provider("second",
        AbilityScope.everything(), new AbilityCostProvider() {
          @Override
          public AbilityQuote quote(AbilityCostContext costContext) {
            return AbilityQuote.payable("second");
          }

          @Override
          public AbilityReservation reserve(AbilityCostContext costContext, AbilityQuote quote) {
            return AbilityReservation.reserved(AbilityReceipt.of("second"));
          }

          @Override
          public void refund(AbilityReceipt receipt, AbilityRefundReason reason) {
            refunded.add("second");
            throw new IllegalStateException("boom");
          }
        });
    AbilityCostGateway gateway = gateway(AbilityGatewayFixture.list(first, second), AbilityApiPolicy.defaults(), true);

    AbilityCharge charge = gateway.chargeDeferred(context(AbilityGatewayFixture.player(), 3), () -> true);
    gateway.refund(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED);

    assertThat(refunded).containsExactly("second", "first");
  }

  private static final class RecordingProvider implements AbilityCostProvider {
    private final java.util.function.Function<AbilityCostContext, AbilityQuote> quoter;
    private final AtomicInteger reserves = new AtomicInteger();
    private final AtomicInteger commits = new AtomicInteger();
    private final AtomicInteger refunds = new AtomicInteger();

    private RecordingProvider(java.util.function.Function<AbilityCostContext, AbilityQuote> quoter) {
      this.quoter = quoter;
    }

    @Override
    public AbilityQuote quote(AbilityCostContext context) {
      return quoter.apply(context);
    }

    @Override
    public AbilityReservation reserve(AbilityCostContext context, AbilityQuote quote) {
      reserves.incrementAndGet();
      return AbilityReservation.reserved(AbilityReceipt.of("receipt-" + UUID.randomUUID()));
    }

    @Override
    public void commit(AbilityReceipt receipt) {
      commits.incrementAndGet();
    }

    @Override
    public void refund(AbilityReceipt receipt, AbilityRefundReason reason) {
      refunds.incrementAndGet();
    }
  }

  private static final class RecordingSink implements AbilityEventSink {
    private final AtomicBoolean cancel = new AtomicBoolean();
    private final List<Event> fired = new ArrayList<>();

    @Override
    public boolean hasListeners(Event event) {
      return true;
    }

    @Override
    public void fire(Event event) {
      fired.add(event);

      if (event instanceof AdaptAbilityActivateEvent activate && cancel.get()) {
        activate.setCancelReason("no skills during a duel");
        activate.setCancelled(true);
      }
    }
  }
}

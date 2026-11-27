package art.arcane.adapt.engine.ability.internal;

import art.arcane.adapt.SchedulerForwarding;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityCostContext;
import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityOutcome;
import art.arcane.adapt.api.ability.AbilityQuote;
import art.arcane.adapt.api.ability.AbilityReceipt;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.ability.AbilityReservation;
import art.arcane.adapt.api.ability.AbilityScope;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.util.common.scheduling.Tasks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AbilityHooksRuntimeTest {
  private final AbilityHooksRuntime runtime = AbilityHooksRuntime.create(Engine.abilities(),
      AbilityApiPolicy::defaults, AbilityGatewayFixture.silentLog(), System::currentTimeMillis, player -> true,
      new ConcurrentHashMap<>());
  private final List<AbilityUsePolicy> policies = new ArrayList<>();
  private final List<AbilityCostProvider> providers = new ArrayList<>();

  @AfterEach
  void unregister() {
    policies.forEach(AdaptApi.abilities()::unregisterPolicy);
    providers.forEach(AdaptApi.abilities()::unregisterCostProvider);
  }

  @Test
  void aPolicyRegisteredThroughTheApiDeniesOnlyInItsScope() {
    registerPolicy(new AbilityUsePolicy() {
      @Override
      public AbilityUseDecision evaluate(AbilityContext context) {
        return AbilityUseDecision.deny("jailed");
      }

      @Override
      public String providerId() {
        return "jail";
      }

      @Override
      public AbilityScope scope() {
        return AbilityScope.skills(AbilityGatewayFixture.TRAGOUL);
      }
    });
    PlayerRef player = AbilityGatewayFixture.player();

    assertThat(runtime.usePolicy().evaluate(AbilityGatewayFixture.check(AbilityGatewayFixture.SERVANT,
        AbilityGatewayFixture.TRAGOUL, player)).reason()).isEqualTo("jailed");
    assertThat(runtime.usePolicy().hasPoliciesFor(AbilityGatewayFixture.SERVANT, AbilityGatewayFixture.RIFT))
        .isFalse();
  }

  @Test
  void anUnregisteredPolicyStopsBeingAsked() {
    AbilityUsePolicy policy = context -> AbilityUseDecision.deny("no");
    registerPolicy(policy);
    PlayerRef player = AbilityGatewayFixture.player();
    assertThat(runtime.usePolicy().evaluate(AbilityGatewayFixture.check(AbilityGatewayFixture.SERVANT,
        AbilityGatewayFixture.TRAGOUL, player)).allowed()).isFalse();

    AdaptApi.abilities().unregisterPolicy(policy);

    assertThat(runtime.usePolicy().evaluate(AbilityGatewayFixture.check(AbilityGatewayFixture.SERVANT,
        AbilityGatewayFixture.TRAGOUL, player)).allowed()).isTrue();
  }

  @Test
  void aProviderRegisteredThroughTheApiWaivesTheBuiltInCost() {
    registerProvider(context -> AbilityQuote.waived("free"));
    AtomicInteger taken = new AtomicInteger();

    AbilityCharge charge = runtime.cost().charge(summon(), () -> taken.incrementAndGet() > 0);

    assertThat(runtime.cost().idle()).isFalse();
    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.ALLOWED_WAIVED);
    assertThat(taken.get()).isZero();
  }

  @Test
  void aProviderRegisteredThroughTheApiChargesAndRefundsADeferredCost() {
    Mana mana = new Mana();
    registerProvider(mana);

    AbilityCharge charge = runtime.cost().chargeDeferred(summon(), () -> true);

    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.ALLOWED_CHARGED);
    assertThat(mana.balance).isEqualTo(6);
    assertThat(runtime.cost().refund(charge.activationId(), AbilityRefundReason.TARGET_LOST)).isTrue();
    assertThat(mana.balance).isEqualTo(10);
  }

  @Test
  void removingAProviderRefundsItsOpenTickets() {
    Mana mana = new Mana();
    registerProvider(mana);
    AbilityCharge charge = runtime.cost().chargeDeferred(summon(), () -> true);

    AdaptApi.abilities().unregisterCostProvider(mana);

    assertThat(runtime.cost().settle(charge.activationId())).isFalse();
    assertThat(mana.balance).isEqualTo(10);
    assertThat(mana.reasons).containsExactly(AbilityRefundReason.ADAPTATION_DISABLED);
  }

  @Test
  void withoutProvidersTheCostGatewayIsIdle() {
    assertThat(runtime.cost().idle()).isTrue();
  }

  @Test
  void thePlayerIsOwnedOnlyOnTheTickThreadThatOwnsTheirRegion() {
    PlayerRef player = AbilityGatewayFixture.player();
    try (MockedStatic<Tasks> tasks = SchedulerForwarding.mockTasks()) {
      tasks.when(Tasks::isPrimaryThread).thenReturn(false);
      assertThat(AbilityHooksRuntime.ownsPlayer(player)).isFalse();

      tasks.when(Tasks::isPrimaryThread).thenReturn(true);
      tasks.when(Tasks::isFoliaThreading).thenReturn(false);
      assertThat(AbilityHooksRuntime.ownsPlayer(player)).isTrue();
      assertThat(AbilityHooksRuntime.ownsPlayer(null)).isFalse();

      tasks.when(Tasks::isFoliaThreading).thenReturn(true);
      tasks.when(() -> Tasks.isOwnedByCurrentRegion(player)).thenReturn(false);
      assertThat(AbilityHooksRuntime.ownsPlayer(player)).isFalse();
      tasks.when(() -> Tasks.isOwnedByCurrentRegion(player)).thenReturn(true);
      assertThat(AbilityHooksRuntime.ownsPlayer(player)).isTrue();
    }
  }

  private void registerPolicy(AbilityUsePolicy policy) {
    AdaptApi.abilities().registerPolicy(policy);
    policies.add(policy);
  }

  private void registerProvider(AbilityCostProvider provider) {
    AdaptApi.abilities().registerCostProvider(provider);
    providers.add(provider);
  }

  private static AbilityCostContext summon() {
    return AbilityGatewayFixture.cost(AbilityGatewayFixture.activate(AbilityGatewayFixture.SERVANT,
        AbilityGatewayFixture.TRAGOUL, 3, AbilityGatewayFixture.player()), "summon", 1);
  }

  private static final class Mana implements AbilityCostProvider {
    private final List<AbilityRefundReason> reasons = new ArrayList<>();
    private int balance = 10;

    @Override
    public AbilityQuote quote(AbilityCostContext context) {
      return AbilityQuote.payable("4 Mana").withPrice(4, "Mana");
    }

    @Override
    public String providerId() {
      return "mana";
    }

    @Override
    public AbilityReservation reserve(AbilityCostContext context, AbilityQuote quote) {
      balance -= 4;
      return AbilityReservation.reserved(AbilityReceipt.of("mana-" + UUID.randomUUID()));
    }

    @Override
    public void refund(AbilityReceipt receipt, AbilityRefundReason reason) {
      balance += 4;
      reasons.add(reason);
    }
  }
}

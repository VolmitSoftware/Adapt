package art.arcane.adapt.engine.ability.internal;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.ability.AbilityCharge;
import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityCostContext;
import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityOutcome;
import art.arcane.adapt.api.ability.AbilityQuote;
import art.arcane.adapt.api.ability.AbilityQuoteStatus;
import art.arcane.adapt.api.ability.AbilityReceipt;
import art.arcane.adapt.api.ability.AbilityRefundReason;
import art.arcane.adapt.api.ability.AbilityReservation;
import art.arcane.adapt.api.ability.AbilityScope;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.platform.entity.PlayerRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/** A third-party plugin that seals jailed players' skills and prices Tragoul abilities in mana, end to end. */
class WardenExampleTest {
  private final Set<UUID> jailed = new HashSet<>();
  private final ManaPool pool = new ManaPool();
  private final JailUsePolicy policy = new JailUsePolicy(jailed);
  private final ManaCostProvider provider = new ManaCostProvider(pool);
  private final AbilityHooksRuntime runtime;

  WardenExampleTest() {
    AdaptApi.abilities().registerPolicy(policy);
    AdaptApi.abilities().registerCostProvider(provider);
    runtime = AbilityHooksRuntime.create(Engine.abilities(), AbilityApiPolicy::defaults,
        AbilityGatewayFixture.silentLog(), System::currentTimeMillis, player -> true, new ConcurrentHashMap<>());
  }

  @AfterEach
  void unregister() {
    AdaptApi.abilities().unregisterPolicy(policy);
    AdaptApi.abilities().unregisterCostProvider(provider);
  }

  @Test
  void theJailPolicyDeniesOnlyJailedPlayers() {
    PlayerRef free = AbilityGatewayFixture.player();
    PlayerRef prisoner = AbilityGatewayFixture.player();
    jailed.add(prisoner.uuid());

    assertThat(runtime.usePolicy().evaluate(check(free)).allowed()).isTrue();
    assertThat(runtime.usePolicy().evaluate(check(prisoner)).reason())
        .isEqualTo("Your skills are sealed while you are jailed");
  }

  @Test
  void theManaProviderPricesTragoulInManaAndRefundsOnFailure() {
    PlayerRef player = AbilityGatewayFixture.player();
    pool.attune(player.uuid(), 20);
    AbilityCostContext context = summon(player, 2);

    assertThat(provider.quote(context).amount()).hasValue(8L);
    AbilityCharge charge = runtime.cost().chargeDeferred(context, () -> true);

    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.ALLOWED_CHARGED);
    assertThat(charge.chargedProviderIds()).containsExactly("warden-mana");
    assertThat(pool.balance(player.uuid())).isEqualTo(12);
    assertThat(runtime.cost().refund(charge.activationId(), AbilityRefundReason.ACTIVATION_FAILED)).isTrue();
    assertThat(pool.balance(player.uuid())).isEqualTo(20);
  }

  @Test
  void anUnattunedPlayerGetsPassSoAdaptTakesItsOwnBones() {
    PlayerRef player = AbilityGatewayFixture.player();
    boolean[] bones = {false};

    AbilityCharge charge = runtime.cost().charge(summon(player, 1), () -> bones[0] = true);

    assertThat(provider.quote(summon(player, 1)).status()).isEqualTo(AbilityQuoteStatus.PASS);
    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.ALLOWED_DEFAULT);
    assertThat(bones[0]).isTrue();
  }

  @Test
  void anEmptyPoolQuotesInsufficientAndTheAbilityIsRefused() {
    PlayerRef player = AbilityGatewayFixture.player();
    pool.attune(player.uuid(), 1);

    AbilityCharge charge = runtime.cost().charge(summon(player, 1), () -> true);

    assertThat(charge.outcome()).isEqualTo(AbilityOutcome.DENIED_INSUFFICIENT);
    assertThat(charge.reason()).isEqualTo("4 Mana");
  }

  private static AbilityContext check(PlayerRef player) {
    return AbilityGatewayFixture.check(AbilityGatewayFixture.SERVANT, AbilityGatewayFixture.TRAGOUL, player);
  }

  private static AbilityCostContext summon(PlayerRef player, int bones) {
    return AbilityGatewayFixture.cost(AbilityGatewayFixture.activate(AbilityGatewayFixture.SERVANT,
        AbilityGatewayFixture.TRAGOUL, 5, player), "adaptation:adapt:tragoul/skeletal_servant:summon", bones);
  }

  static final class ManaPool {
    private final Map<UUID, Integer> balances = new HashMap<>();

    void attune(UUID playerId, int amount) {
      balances.put(playerId, amount);
    }

    boolean isAttuned(UUID playerId) {
      return balances.containsKey(playerId);
    }

    int balance(UUID playerId) {
      return balances.getOrDefault(playerId, 0);
    }

    boolean withdraw(UUID playerId, int amount) {
      int current = balance(playerId);
      if (current < amount) {
        return false;
      }
      balances.put(playerId, current - amount);
      return true;
    }

    void deposit(UUID playerId, int amount) {
      balances.merge(playerId, amount, Integer::sum);
    }
  }

  record ManaReceipt(UUID playerId, int amount) implements AbilityReceipt {
  }

  static final class JailUsePolicy implements AbilityUsePolicy {
    private final Set<UUID> jailed;

    JailUsePolicy(Set<UUID> jailed) {
      this.jailed = jailed;
    }

    @Override
    public String providerId() {
      return "warden-jail";
    }

    @Override
    public AbilityUseDecision evaluate(AbilityContext context) {
      return jailed.contains(context.player().uuid())
          ? AbilityUseDecision.deny("Your skills are sealed while you are jailed")
          : AbilityUseDecision.allow();
    }
  }

  static final class ManaCostProvider implements AbilityCostProvider {
    private static final AbilityScope SCOPE = AbilityScope.skills(AbilityGatewayFixture.TRAGOUL);

    private final ManaPool pool;

    ManaCostProvider(ManaPool pool) {
      this.pool = pool;
    }

    @Override
    public String providerId() {
      return "warden-mana";
    }

    @Override
    public AbilityScope scope() {
      return SCOPE;
    }

    @Override
    public AbilityQuote quote(AbilityCostContext context) {
      UUID playerId = context.ability().player().uuid();
      if (!pool.isAttuned(playerId)) {
        return AbilityQuote.pass();
      }
      int price = 4 * Math.max(1, context.defaultAmount());
      if (pool.balance(playerId) < price) {
        return AbilityQuote.insufficient(price + " Mana").withPrice(price, "Mana");
      }
      return AbilityQuote.payable(price + " Mana").withPrice(price, "Mana");
    }

    @Override
    public AbilityReservation reserve(AbilityCostContext context, AbilityQuote quote) {
      UUID playerId = context.ability().player().uuid();
      int price = (int) quote.amount().orElse(0L);
      if (!pool.withdraw(playerId, price)) {
        return AbilityReservation.failed("Your mana ran out");
      }
      return AbilityReservation.reserved(new ManaReceipt(playerId, price));
    }

    @Override
    public void refund(AbilityReceipt receipt, AbilityRefundReason reason) {
      if (receipt instanceof ManaReceipt mana) {
        pool.deposit(mana.playerId(), mana.amount());
      }
    }
  }
}

package art.arcane.adapt.api.ability;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WardenExampleTest {
  @Test
  void theJailPolicyDeniesOnlyJailedPlayers() {
    JailIndex jails = new JailIndex();
    JailUsePolicy policy = new JailUsePolicy(jails);
    Player free = player();
    Player jailed = player();
    jails.jail(jailed.getUniqueId());

    assertThat(policy.evaluate(AbilityContext.check("tragoul-lance", "tragoul", 3, free)).allowed()).isTrue();
    assertThat(policy.evaluate(AbilityContext.check("tragoul-lance", "tragoul", 3, jailed)).allowed()).isFalse();
    assertThat(policy.providerId()).isEqualTo("warden-jail");
    assertThat(policy.scope().unscoped()).isTrue();
  }

  @Test
  void theManaProviderPricesTragoulInManaAndRefundsOnFailure() {
    ManaPool pool = new ManaPool();
    ManaCostProvider provider = new ManaCostProvider(pool);
    Player player = player();
    pool.attune(player.getUniqueId(), 20);
    AbilityCostContext context = summon(player, 2);

    assertThat(provider.scope()).isEqualTo(AbilityScope.skill("tragoul"));
    AbilityQuote quote = provider.quote(context);
    assertThat(quote.status()).isEqualTo(AbilityQuoteStatus.PAYABLE);
    assertThat(quote.suppressesDefaultCost()).isTrue();
    assertThat(quote.amount()).hasValue(8L);

    AbilityReservation reservation = provider.reserve(context, quote);
    assertThat(reservation.reserved()).isTrue();
    assertThat(pool.balance(player.getUniqueId())).isEqualTo(12);

    provider.refund(reservation.receipt(), AbilityRefundReason.ACTIVATION_FAILED);
    assertThat(pool.balance(player.getUniqueId())).isEqualTo(20);
  }

  @Test
  void anUnattunedPlayerGetsPassSoAdaptTakesItsOwnBones() {
    ManaCostProvider provider = new ManaCostProvider(new ManaPool());

    assertThat(provider.quote(summon(player(), 1)).status()).isEqualTo(AbilityQuoteStatus.PASS);
    assertThat(provider.quote(summon(player(), 1)).suppressesDefaultCost()).isFalse();
  }

  @Test
  void anEmptyPoolQuotesInsufficient() {
    ManaPool pool = new ManaPool();
    ManaCostProvider provider = new ManaCostProvider(pool);
    Player player = player();
    pool.attune(player.getUniqueId(), 1);

    assertThat(provider.quote(summon(player, 1)).status()).isEqualTo(AbilityQuoteStatus.INSUFFICIENT);
  }

  @Test
  void theListenerVetoesDuelsAndDescribesOutcomes() {
    ArenaIndex arenas = new ArenaIndex();
    WardenListener listener = new WardenListener(arenas);
    Player duelist = player();
    arenas.startDuel(duelist.getUniqueId());
    AbilityCostContext context = summon(duelist, 1);
    AdaptAbilityActivateEvent event = new AdaptAbilityActivateEvent(context.ability(), context);

    listener.onActivate(event);

    assertThat(event.isCancelled()).isTrue();
    assertThat(event.getCancelReason()).isEqualTo("Skills are disabled during a duel");
    assertThat(listener.describe(new AbilityCharge(UUID.randomUUID(), AbilityOutcome.ALLOWED_CHARGED, true, "",
        "warden-mana", java.util.List.of("warden-mana")))).isEqualTo("paid [warden-mana]");
    assertThat(listener.describe(new AbilityCharge(UUID.randomUUID(), AbilityOutcome.ALLOWED_DEFAULT, false, "", "",
        java.util.List.of()))).isEqualTo("paid in items");
  }

  private static Player player() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    return player;
  }

  private static AbilityCostContext summon(Player player, int bones) {
    return new AbilityCostContext(AbilityContext.activate("tragoul-skeletal-servant", "tragoul", 5, player, null),
        "adaptation:tragoul-skeletal-servant:summon", AbilityCostKind.ITEM, java.util.Optional.empty(), bones);
  }

  static final class JailIndex {
    private final Set<UUID> jailed = new java.util.HashSet<>();

    void jail(UUID playerId) {
      jailed.add(playerId);
    }

    boolean isJailed(UUID playerId) {
      return jailed.contains(playerId);
    }
  }

  static final class ArenaIndex {
    private final Set<UUID> duelling = new java.util.HashSet<>();

    void startDuel(UUID playerId) {
      duelling.add(playerId);
    }

    boolean isDuelling(UUID playerId) {
      return duelling.contains(playerId);
    }
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

    void recordSpend(UUID playerId, int amount) {
      balances.putIfAbsent(playerId, 0);
    }
  }

  record ManaReceipt(UUID playerId, int amount) implements AbilityReceipt {
  }

  static final class JailUsePolicy implements AbilityUsePolicy {
    private final JailIndex jails;

    JailUsePolicy(JailIndex jails) {
      this.jails = jails;
    }

    @Override
    public String providerId() {
      return "warden-jail";
    }

    @Override
    public AbilityUseDecision evaluate(AbilityContext context) {
      if (jails.isJailed(context.playerId())) {
        return AbilityUseDecision.deny("Your skills are sealed while you are jailed");
      }

      return AbilityUseDecision.allow();
    }
  }

  static final class ManaCostProvider implements AbilityCostProvider {
    private static final AbilityScope SCOPE = AbilityScope.skill("tragoul");

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
      if (!pool.isAttuned(context.ability().playerId())) {
        return AbilityQuote.pass();
      }

      int price = priceOf(context);

      if (price <= 0) {
        return AbilityQuote.waived("Blood magic is free for the attuned");
      }

      if (pool.balance(context.ability().playerId()) < price) {
        return AbilityQuote.insufficient(price + " Mana").withPrice(price, "Mana");
      }

      return AbilityQuote.payable(price + " Mana").withPrice(price, "Mana");
    }

    @Override
    public AbilityReservation reserve(AbilityCostContext context, AbilityQuote quote) {
      UUID playerId = context.ability().playerId();
      int price = priceOf(context);

      if (!pool.withdraw(playerId, price)) {
        return AbilityReservation.failed("Your mana ran out");
      }

      return AbilityReservation.reserved(new ManaReceipt(playerId, price));
    }

    @Override
    public void commit(AbilityReceipt receipt) {
      if (receipt instanceof ManaReceipt mana) {
        pool.recordSpend(mana.playerId(), mana.amount());
      }
    }

    @Override
    public void refund(AbilityReceipt receipt, AbilityRefundReason reason) {
      if (receipt instanceof ManaReceipt mana) {
        pool.deposit(mana.playerId(), mana.amount());
      }
    }

    private int priceOf(AbilityCostContext context) {
      return switch (context.costKey()) {
        case "adaptation:tragoul-skeletal-servant:summon" -> 4 * Math.max(1, context.defaultAmount());
        case "adaptation:tragoul-marrow-armor:absorb" -> 2;
        default -> 0;
      };
    }
  }

  static final class WardenListener implements Listener {
    private final ArenaIndex arenas;

    WardenListener(ArenaIndex arenas) {
      this.arenas = arenas;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onActivate(AdaptAbilityActivateEvent event) {
      if (arenas.isDuelling(event.getContext().playerId())) {
        event.setCancelReason("Skills are disabled during a duel");
        event.setCancelled(true);
      }
    }

    @EventHandler
    public void onActivated(AdaptAbilityActivatedEvent event) {
      describe(event.getCharge());
    }

    String describe(AbilityCharge charge) {
      return switch (charge.outcome()) {
        case ALLOWED_CHARGED -> "paid " + charge.chargedProviderIds();
        case ALLOWED_WAIVED -> "waived";
        case ALLOWED_DEFAULT -> "paid in items";
        default -> "";
      };
    }
  }
}

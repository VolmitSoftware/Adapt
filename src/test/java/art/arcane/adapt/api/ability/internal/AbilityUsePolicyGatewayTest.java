package art.arcane.adapt.api.ability.internal;

import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityScope;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class AbilityUsePolicyGatewayTest {
  private final AtomicLong clock = new AtomicLong(1_000L);
  private final Logger logger = AbilityGatewayFixture.silentLogger();
  private final Map<UUID, AbilityDenial> denials = new ConcurrentHashMap<>();

  private AbilityUsePolicyGateway gateway(List<AbilityProviderRegistration<AbilityUsePolicy>> registrations,
                                          AbilityApiPolicy policy, boolean onOwningThread) {
    return new AbilityUsePolicyGateway(AbilityGatewayFixture.useSource(registrations), () -> policy,
        new AbilityProviderGuard("use policy", logger, clock::get), logger, clock::get, player -> onOwningThread,
        denials);
  }

  @Test
  void aDenyingPolicyDeniesAndTheReasonIsRecorded() {
    Player player = AbilityGatewayFixture.player();
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("jail", AbilityScope.everything(),
        context -> AbilityUseDecision.deny("You are jailed"))), AbilityApiPolicy.defaults(), true);

    AbilityUseDecision decision =
        gateway.evaluate(AbilityGatewayFixture.check("tragoul-lance", "tragoul", player));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo("You are jailed");
    assertThat(gateway.lastDenial(player.getUniqueId()).providerId()).isEqualTo("jail");
    assertThat(gateway.lastDenial(player.getUniqueId()).abilityId()).isEqualTo("tragoul-lance");
  }

  @Test
  void aScopedPolicyIsNeverCalledForAnUnrelatedAbility() {
    Player player = AbilityGatewayFixture.player();
    AtomicInteger calls = new AtomicInteger();
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("tragoul-only",
        AbilityScope.skill("tragoul"), context -> {
          calls.incrementAndGet();
          return AbilityUseDecision.deny("scoped");
        })), AbilityApiPolicy.defaults(), true);

    assertThat(gateway.hasPoliciesFor("rift-blink", "rift")).isFalse();
    assertThat(gateway.evaluate(AbilityGatewayFixture.check("rift-blink", "rift", player)).allowed()).isTrue();
    assertThat(calls.get()).isZero();

    assertThat(gateway.hasPoliciesFor("tragoul-lance", "tragoul")).isTrue();
    assertThat(gateway.evaluate(AbilityGatewayFixture.check("tragoul-lance", "tragoul", player)).allowed()).isFalse();
    assertThat(calls.get()).isOne();
  }

  @Test
  void anAbilityScopedPolicyMatchesOnlyThatAbility() {
    Player player = AbilityGatewayFixture.player();
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("one",
        AbilityScope.abilities("tragoul-lance"), context -> AbilityUseDecision.deny("no"))),
        AbilityApiPolicy.defaults(), true);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check("tragoul-lance", "tragoul", player)).allowed()).isFalse();
    assertThat(gateway.evaluate(AbilityGatewayFixture.check("tragoul-marrow-armor", "tragoul", player)).allowed())
        .isTrue();
  }

  @Test
  void aThrowingPolicyDeniesUnderTheDefaultFailClosedMode() {
    Player player = AbilityGatewayFixture.player();
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("broken",
        AbilityScope.everything(), context -> {
          throw new IllegalStateException("boom");
        })), AbilityApiPolicy.defaults(), true);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check("tragoul-lance", "tragoul", player)).allowed()).isFalse();
    assertThat(gateway.faultCount()).isOne();
  }

  @Test
  void aThrowingPolicyAllowsWhenTheFailureModeIsOpen() {
    Player player = AbilityGatewayFixture.player();
    AbilityApiPolicy open = AbilityApiPolicy.of(true, "allow", "allow", 5, 2L, 2_000L);
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("broken",
        AbilityScope.everything(), context -> {
          throw new IllegalStateException("boom");
        })), open, true);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check("tragoul-lance", "tragoul", player)).allowed()).isTrue();
  }

  @Test
  void aPolicyReturningNullIsAFaultRatherThanACrash() {
    Player player = AbilityGatewayFixture.player();
    AbilityApiPolicy open = AbilityApiPolicy.of(true, "allow", "allow", 5, 2L, 2_000L);
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("nully",
        AbilityScope.everything(), context -> null)), open, true);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check("tragoul-lance", "tragoul", player)).allowed()).isTrue();
    assertThat(gateway.faultCount()).isOne();
  }

  @Test
  void aRepeatedlyBrokenPolicyIsQuarantinedAndStopsDenying() {
    Player player = AbilityGatewayFixture.player();
    AtomicInteger calls = new AtomicInteger();
    AbilityProviderGuard guard = new AbilityProviderGuard("use policy", logger, clock::get);
    AbilityProviderRegistration<AbilityUsePolicy> registration = AbilityGatewayFixture.policy("broken",
        AbilityScope.everything(), context -> {
          calls.incrementAndGet();
          throw new IllegalStateException("boom");
        });
    AbilityUsePolicyGateway gateway = new AbilityUsePolicyGateway(
        AbilityGatewayFixture.useSource(List.of(registration)), AbilityApiPolicy::defaults, guard, logger,
        clock::get, ignored -> true, denials);

    for (int attempt = 0; attempt < 5; attempt++) {
      assertThat(gateway.evaluate(AbilityGatewayFixture.check("tragoul-lance", "tragoul", player)).allowed())
          .isFalse();
    }

    assertThat(calls.get()).isEqualTo(5);
    assertThat(guard.isQuarantined("broken")).isTrue();
    assertThat(gateway.evaluate(AbilityGatewayFixture.check("tragoul-lance", "tragoul", player)).allowed()).isTrue();
    assertThat(calls.get()).isEqualTo(5);
  }

  @Test
  void aReentrantEvaluationIsAllowedAndCounted() {
    Player player = AbilityGatewayFixture.player();
    List<AbilityUsePolicyGateway> holder = new ArrayList<>(1);
    AtomicInteger depth = new AtomicInteger();
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("recursive",
        AbilityScope.everything(), context -> {
          if (depth.incrementAndGet() == 1) {
            holder.get(0).evaluate(AbilityGatewayFixture.check("other-ability", "tragoul", player));
          }

          return AbilityUseDecision.allow();
        })), AbilityApiPolicy.defaults(), true);
    holder.add(gateway);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check("tragoul-lance", "tragoul", player)).allowed()).isTrue();
    assertThat(gateway.reentrantCount()).isOne();
    assertThat(depth.get()).isOne();
  }

  @Test
  void theOwningThreadCheckIsAskedAboutThePlayerBeingChecked() {
    Player player = AbilityGatewayFixture.player();
    AtomicReference<Player> asked = new AtomicReference<>();
    AbilityUsePolicyGateway gateway = new AbilityUsePolicyGateway(
        AbilityGatewayFixture.useSource(List.of(AbilityGatewayFixture.policy("jail", AbilityScope.everything(),
            context -> AbilityUseDecision.deny("You are jailed")))), AbilityApiPolicy::defaults,
        new AbilityProviderGuard("use policy", logger, clock::get), logger, clock::get, candidate -> {
          asked.set(candidate);
          return true;
        }, denials);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check("tragoul-lance", "tragoul", player)).allowed()).isFalse();
    assertThat(asked.get()).isSameAs(player);
  }

  @Test
  void policiesAreSkippedEntirelyOffTheOwningThread() {
    Player player = AbilityGatewayFixture.player();
    AtomicInteger calls = new AtomicInteger();
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("jail",
        AbilityScope.everything(), context -> {
          calls.incrementAndGet();
          return AbilityUseDecision.deny("jailed");
        })), AbilityApiPolicy.defaults(), false);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check("tragoul-lance", "tragoul", player)).allowed()).isTrue();
    assertThat(calls.get()).isZero();
    assertThat(gateway.offThreadCount()).isOne();
  }

  @Test
  void aDisabledApiNeverConsultsProviders() {
    Player player = AbilityGatewayFixture.player();
    AtomicInteger calls = new AtomicInteger();
    AbilityApiPolicy disabled = AbilityApiPolicy.of(false, "deny", "allow", 5, 2L, 2_000L);
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("jail",
        AbilityScope.everything(), context -> {
          calls.incrementAndGet();
          return AbilityUseDecision.deny("jailed");
        })), disabled, true);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check("tragoul-lance", "tragoul", player)).allowed()).isTrue();
    assertThat(calls.get()).isZero();
  }

  @Test
  void anEmptyRegistryIsTheFastPath() {
    Player player = AbilityGatewayFixture.player();
    AbilityUsePolicyGateway gateway = gateway(List.of(), AbilityApiPolicy.defaults(), true);
    AbilityContext context = AbilityGatewayFixture.check("tragoul-lance", "tragoul", player);

    assertThat(gateway.hasPoliciesFor("tragoul-lance", "tragoul")).isFalse();
    assertThat(gateway.evaluate(context).allowed()).isTrue();
    assertThat(gateway.lastDenial(player.getUniqueId())).isNull();
  }

  @Test
  void theFirstDenyWinsAndLaterPoliciesAreNotConsulted() {
    Player player = AbilityGatewayFixture.player();
    AtomicInteger second = new AtomicInteger();
    List<AbilityProviderRegistration<AbilityUsePolicy>> registrations = AbilityGatewayFixture.list(
        AbilityGatewayFixture.policy("a", AbilityScope.everything(), context -> AbilityUseDecision.deny("first")),
        AbilityGatewayFixture.policy("b", AbilityScope.everything(), context -> {
          second.incrementAndGet();
          return AbilityUseDecision.allow();
        }));
    AbilityUsePolicyGateway gateway = gateway(registrations, AbilityApiPolicy.defaults(), true);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check("tragoul-lance", "tragoul", player)).reason())
        .isEqualTo("first");
    assertThat(second.get()).isZero();
  }

  @Test
  void aDisabledOwnerPluginIsSkipped() {
    Player player = AbilityGatewayFixture.player();
    AbilityProviderRegistration<AbilityUsePolicy> registration = new AbilityProviderRegistration<>(
        context -> AbilityUseDecision.deny("jailed"), "jail", "TestPlugin",
        org.bukkit.plugin.ServicePriority.Normal, AbilityScope.everything(), () -> false);
    AbilityUsePolicyGateway gateway = gateway(List.of(registration), AbilityApiPolicy.defaults(), true);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check("tragoul-lance", "tragoul", player)).allowed()).isTrue();
  }
}

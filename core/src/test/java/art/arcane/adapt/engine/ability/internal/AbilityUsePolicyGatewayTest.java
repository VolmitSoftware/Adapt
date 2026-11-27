package art.arcane.adapt.engine.ability.internal;

import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityScope;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AbilityUsePolicyGatewayTest {
  private static final Id LANCE = Id.adapt("tragoul/lance");

  private final AtomicLong clock = new AtomicLong(1_000L);
  private final PlatformLog logger = AbilityGatewayFixture.silentLog();
  private final Map<UUID, AbilityDenial> denials = new ConcurrentHashMap<>();

  private AbilityUsePolicyGateway gateway(List<AbilityProviderRegistration<AbilityUsePolicy>> registrations,
                                          AbilityApiPolicy policy, boolean onOwningThread) {
    return new AbilityUsePolicyGateway(AbilityGatewayFixture.useSource(registrations), () -> policy,
        new AbilityProviderGuard("use policy", logger, clock::get), logger, clock::get, player -> onOwningThread,
        denials);
  }

  @Test
  void aDenyingPolicyDeniesAndTheReasonIsRecorded() {
    PlayerRef player = AbilityGatewayFixture.player();
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("jail", AbilityScope.everything(),
        context -> AbilityUseDecision.deny("You are jailed"))), AbilityApiPolicy.defaults(), true);

    AbilityUseDecision decision =
        gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player));

    assertThat(decision.allowed()).isFalse();
    assertThat(decision.reason()).isEqualTo("You are jailed");
    assertThat(gateway.lastDenial(player.uuid()).providerId()).isEqualTo("jail");
    assertThat(gateway.lastDenial(player.uuid()).abilityId()).isEqualTo("adapt:tragoul/lance");
  }

  @Test
  void aScopedPolicyIsNeverCalledForAnUnrelatedAbility() {
    PlayerRef player = AbilityGatewayFixture.player();
    AtomicInteger calls = new AtomicInteger();
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("tragoul-only",
        AbilityScope.skills(AbilityGatewayFixture.TRAGOUL), context -> {
          calls.incrementAndGet();
          return AbilityUseDecision.deny("scoped");
        })), AbilityApiPolicy.defaults(), true);

    assertThat(gateway.hasPoliciesFor(Id.adapt("rift/blink"), AbilityGatewayFixture.RIFT)).isFalse();
    assertThat(gateway.evaluate(AbilityGatewayFixture.check(Id.adapt("rift/blink"), AbilityGatewayFixture.RIFT, player)).allowed()).isTrue();
    assertThat(calls.get()).isZero();

    assertThat(gateway.hasPoliciesFor(LANCE, AbilityGatewayFixture.TRAGOUL)).isTrue();
    assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).allowed()).isFalse();
    assertThat(calls.get()).isOne();
  }

  @Test
  void anAbilityScopedPolicyMatchesOnlyThatAbility() {
    PlayerRef player = AbilityGatewayFixture.player();
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("one",
        AbilityScope.abilities(LANCE), context -> AbilityUseDecision.deny("no"))),
        AbilityApiPolicy.defaults(), true);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).allowed()).isFalse();
    assertThat(gateway.evaluate(AbilityGatewayFixture.check(Id.adapt("tragoul/marrow_armor"), AbilityGatewayFixture.TRAGOUL, player)).allowed())
        .isTrue();
  }

  @Test
  void aThrowingPolicyDeniesUnderTheDefaultFailClosedMode() {
    PlayerRef player = AbilityGatewayFixture.player();
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("broken",
        AbilityScope.everything(), context -> {
          throw new IllegalStateException("boom");
        })), AbilityApiPolicy.defaults(), true);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).allowed()).isFalse();
    assertThat(gateway.faultCount()).isOne();
  }

  @Test
  void aThrowingPolicyAllowsWhenTheFailureModeIsOpen() {
    PlayerRef player = AbilityGatewayFixture.player();
    AbilityApiPolicy open = AbilityApiPolicy.of(true, "allow", "allow", 5, 2L, 2_000L);
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("broken",
        AbilityScope.everything(), context -> {
          throw new IllegalStateException("boom");
        })), open, true);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).allowed()).isTrue();
  }

  @Test
  void aPolicyReturningNullIsAFaultRatherThanACrash() {
    PlayerRef player = AbilityGatewayFixture.player();
    AbilityApiPolicy open = AbilityApiPolicy.of(true, "allow", "allow", 5, 2L, 2_000L);
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("nully",
        AbilityScope.everything(), context -> null)), open, true);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).allowed()).isTrue();
    assertThat(gateway.faultCount()).isOne();
  }

  @Test
  void aQuarantinedPolicyDeniesUnderTheDefaultFailClosedModeWithoutBeingCalled() {
    PlayerRef player = AbilityGatewayFixture.player();
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
      assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).allowed())
          .isFalse();
    }

    assertThat(calls.get()).isEqualTo(5);
    assertThat(guard.isQuarantined("broken")).isTrue();
    assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).allowed()).isFalse();
    assertThat(calls.get()).isEqualTo(5);
    assertThat(gateway.lastDenial(player.uuid()).providerId()).isEqualTo("broken");
  }

  @Test
  void aQuarantinedPolicyIsSkippedWhenTheFailureModeIsOpen() {
    PlayerRef player = AbilityGatewayFixture.player();
    AbilityApiPolicy open = AbilityApiPolicy.of(true, "allow", "allow", 5, 2L, 2_000L);
    AbilityProviderGuard guard = new AbilityProviderGuard("use policy", logger, clock::get);
    AbilityUsePolicyGateway gateway = new AbilityUsePolicyGateway(
        AbilityGatewayFixture.useSource(List.of(AbilityGatewayFixture.policy("broken", AbilityScope.everything(),
            context -> {
              throw new IllegalStateException("boom");
            }))), () -> open, guard, logger, clock::get, ignored -> true, denials);

    for (int attempt = 0; attempt < 5; attempt++) {
      gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player));
    }

    assertThat(guard.isQuarantined("broken")).isTrue();
    assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).allowed()).isTrue();
  }

  @Test
  void aReentrantEvaluationDeniesUnderTheDefaultFailClosedModeAndIsCounted() {
    PlayerRef player = AbilityGatewayFixture.player();
    List<AbilityUsePolicyGateway> holder = new ArrayList<>(1);
    List<Boolean> inner = new ArrayList<>(1);
    AtomicInteger depth = new AtomicInteger();
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("recursive",
        AbilityScope.everything(), context -> {
          if (depth.incrementAndGet() == 1) {
            inner.add(holder.get(0).evaluate(AbilityGatewayFixture.check(Id.adapt("tragoul/other"),
                AbilityGatewayFixture.TRAGOUL, player)).allowed());
          }

          return AbilityUseDecision.allow();
        })), AbilityApiPolicy.defaults(), true);
    holder.add(gateway);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).allowed()).isTrue();
    assertThat(inner).containsExactly(false);
    assertThat(gateway.reentrantCount()).isOne();
    assertThat(depth.get()).isOne();
  }

  @Test
  void aReentrantEvaluationIsAllowedWhenTheFailureModeIsOpen() {
    PlayerRef player = AbilityGatewayFixture.player();
    AbilityApiPolicy open = AbilityApiPolicy.of(true, "allow", "allow", 5, 2L, 2_000L);
    List<AbilityUsePolicyGateway> holder = new ArrayList<>(1);
    List<Boolean> inner = new ArrayList<>(1);
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("recursive",
        AbilityScope.everything(), context -> {
          if (inner.isEmpty()) {
            inner.add(holder.get(0).evaluate(AbilityGatewayFixture.check(Id.adapt("tragoul/other"),
                AbilityGatewayFixture.TRAGOUL, player)).allowed());
          }

          return AbilityUseDecision.allow();
        })), open, true);
    holder.add(gateway);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).allowed()).isTrue();
    assertThat(inner).containsExactly(true);
  }

  @Test
  void theOwningThreadCheckIsAskedAboutThePlayerBeingChecked() {
    PlayerRef player = AbilityGatewayFixture.player();
    AtomicReference<PlayerRef> asked = new AtomicReference<>();
    AbilityUsePolicyGateway gateway = new AbilityUsePolicyGateway(
        AbilityGatewayFixture.useSource(List.of(AbilityGatewayFixture.policy("jail", AbilityScope.everything(),
            context -> AbilityUseDecision.deny("You are jailed")))), AbilityApiPolicy::defaults,
        new AbilityProviderGuard("use policy", logger, clock::get), logger, clock::get, candidate -> {
          asked.set(candidate);
          return true;
        }, denials);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).allowed()).isFalse();
    assertThat(asked.get()).isSameAs(player);
  }

  @Test
  void offTheOwningThreadTheDefaultFailClosedModeDeniesWithoutCallingPolicies() {
    PlayerRef player = AbilityGatewayFixture.player();
    AtomicInteger calls = new AtomicInteger();
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("jail",
        AbilityScope.everything(), context -> {
          calls.incrementAndGet();
          return AbilityUseDecision.allow();
        })), AbilityApiPolicy.defaults(), false);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).allowed()).isFalse();
    assertThat(calls.get()).isZero();
    assertThat(gateway.offThreadCount()).isOne();
    assertThat(gateway.lastDenial(player.uuid()).providerId()).isEqualTo("jail");
  }

  @Test
  void offTheOwningThreadPoliciesAreSkippedWhenTheFailureModeIsOpen() {
    PlayerRef player = AbilityGatewayFixture.player();
    AtomicInteger calls = new AtomicInteger();
    AbilityApiPolicy open = AbilityApiPolicy.of(true, "allow", "allow", 5, 2L, 2_000L);
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("jail",
        AbilityScope.everything(), context -> {
          calls.incrementAndGet();
          return AbilityUseDecision.deny("jailed");
        })), open, false);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).allowed()).isTrue();
    assertThat(calls.get()).isZero();
  }

  @Test
  void eachUnsafeCaseIsLoggedOnceNamingThePolicy() {
    AbilityGatewayFixture.RecordingLog recording = new AbilityGatewayFixture.RecordingLog();
    PlayerRef player = AbilityGatewayFixture.player();
    AbilityUsePolicyGateway gateway = new AbilityUsePolicyGateway(
        AbilityGatewayFixture.useSource(List.of(AbilityGatewayFixture.policy("jail", AbilityScope.everything(),
            context -> AbilityUseDecision.allow()))), AbilityApiPolicy::defaults,
        new AbilityProviderGuard("use policy", recording, clock::get), recording, clock::get, ignored -> false,
        denials);

    for (int attempt = 0; attempt < 3; attempt++) {
      clock.addAndGet(60_000L);
      gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player));
    }

    assertThat(recording.warnings).hasSize(1);
    assertThat(recording.warnings.get(0)).contains("jail").startsWith("Denied").contains("off the tick thread");
  }

  @Test
  void aDisabledApiNeverConsultsProviders() {
    PlayerRef player = AbilityGatewayFixture.player();
    AtomicInteger calls = new AtomicInteger();
    AbilityApiPolicy disabled = AbilityApiPolicy.of(false, "deny", "allow", 5, 2L, 2_000L);
    AbilityUsePolicyGateway gateway = gateway(List.of(AbilityGatewayFixture.policy("jail",
        AbilityScope.everything(), context -> {
          calls.incrementAndGet();
          return AbilityUseDecision.deny("jailed");
        })), disabled, true);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).allowed()).isTrue();
    assertThat(calls.get()).isZero();
  }

  @Test
  void anEmptyRegistryIsTheFastPath() {
    PlayerRef player = AbilityGatewayFixture.player();
    AbilityUsePolicyGateway gateway = gateway(List.of(), AbilityApiPolicy.defaults(), true);
    AbilityContext context = AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player);

    assertThat(gateway.hasPoliciesFor(LANCE, AbilityGatewayFixture.TRAGOUL)).isFalse();
    assertThat(gateway.evaluate(context).allowed()).isTrue();
    assertThat(gateway.lastDenial(player.uuid())).isNull();
  }

  @Test
  void theFirstDenyWinsAndLaterPoliciesAreNotConsulted() {
    PlayerRef player = AbilityGatewayFixture.player();
    AtomicInteger second = new AtomicInteger();
    List<AbilityProviderRegistration<AbilityUsePolicy>> registrations = AbilityGatewayFixture.list(
        AbilityGatewayFixture.policy("a", AbilityScope.everything(), context -> AbilityUseDecision.deny("first")),
        AbilityGatewayFixture.policy("b", AbilityScope.everything(), context -> {
          second.incrementAndGet();
          return AbilityUseDecision.allow();
        }));
    AbilityUsePolicyGateway gateway = gateway(registrations, AbilityApiPolicy.defaults(), true);

    assertThat(gateway.evaluate(AbilityGatewayFixture.check(LANCE, AbilityGatewayFixture.TRAGOUL, player)).reason())
        .isEqualTo("first");
    assertThat(second.get()).isZero();
  }
}

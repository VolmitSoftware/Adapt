package art.arcane.adapt.api.ability.internal;

import art.arcane.adapt.api.ability.AbilityScope;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AbilityProviderIndexTest {
  @Test
  void theScopeMemoIsNotReusedAcrossTwoSkillsThatShareAnAbilityId() {
    AbilityProviderRegistration<AbilityUsePolicy> tragoul = AbilityGatewayFixture.policy("tragoul-side",
        AbilityScope.skill("tragoul"), context -> AbilityUseDecision.allow());
    AbilityProviderRegistration<AbilityUsePolicy> rift = AbilityGatewayFixture.policy("rift-side",
        AbilityScope.skill("rift"), context -> AbilityUseDecision.allow());
    AbilityProviderIndex<AbilityUsePolicy> index = new AbilityProviderIndex<>(List.of(tragoul, rift));

    assertThat(index.matching("shared-ability", "tragoul")).containsExactly(tragoul);
    assertThat(index.matching("shared-ability", "rift")).containsExactly(rift);
    assertThat(index.matching("shared-ability", "tragoul")).containsExactly(tragoul);
  }

  @Test
  void theScopeMemoStillAnswersRepeatedChecksForTheSameAbility() {
    AbilityProviderRegistration<AbilityUsePolicy> tragoul = AbilityGatewayFixture.policy("tragoul-side",
        AbilityScope.skill("tragoul"), context -> AbilityUseDecision.allow());
    AbilityProviderIndex<AbilityUsePolicy> index = new AbilityProviderIndex<>(List.of(tragoul));

    List<AbilityProviderRegistration<AbilityUsePolicy>> first = index.matching("tragoul-lance", "tragoul");

    assertThat(index.matching("tragoul-lance", "tragoul")).isSameAs(first);
    assertThat(index.matching("rift-gate", "rift")).isEmpty();
  }
}

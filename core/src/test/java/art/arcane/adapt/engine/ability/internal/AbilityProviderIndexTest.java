package art.arcane.adapt.engine.ability.internal;

import art.arcane.adapt.api.ability.AbilityScope;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AbilityProviderIndexTest {
  private static final Id SHARED = Id.adapt("shared/ability");
  private static final Id LANCE = Id.adapt("tragoul/lance");

  @Test
  void theScopeMemoIsNotReusedAcrossTwoSkillsThatShareAnAbilityId() {
    AbilityProviderRegistration<AbilityUsePolicy> tragoul = AbilityGatewayFixture.policy("tragoul-side",
        AbilityScope.skills(AbilityGatewayFixture.TRAGOUL), context -> AbilityUseDecision.allow());
    AbilityProviderRegistration<AbilityUsePolicy> rift = AbilityGatewayFixture.policy("rift-side",
        AbilityScope.skills(AbilityGatewayFixture.RIFT), context -> AbilityUseDecision.allow());
    AbilityProviderIndex<AbilityUsePolicy> index = new AbilityProviderIndex<>(List.of(tragoul, rift));

    assertThat(index.matching(SHARED, AbilityGatewayFixture.TRAGOUL)).containsExactly(tragoul);
    assertThat(index.matching(SHARED, AbilityGatewayFixture.RIFT)).containsExactly(rift);
    assertThat(index.matching(SHARED, AbilityGatewayFixture.TRAGOUL)).containsExactly(tragoul);
  }

  @Test
  void theScopeMemoStillAnswersRepeatedChecksForTheSameAbility() {
    AbilityProviderRegistration<AbilityUsePolicy> tragoul = AbilityGatewayFixture.policy("tragoul-side",
        AbilityScope.skills(AbilityGatewayFixture.TRAGOUL), context -> AbilityUseDecision.allow());
    AbilityProviderIndex<AbilityUsePolicy> index = new AbilityProviderIndex<>(List.of(tragoul));

    List<AbilityProviderRegistration<AbilityUsePolicy>> first = index.matching(LANCE, AbilityGatewayFixture.TRAGOUL);

    assertThat(index.matching(LANCE, AbilityGatewayFixture.TRAGOUL)).isSameAs(first);
    assertThat(index.matching(Id.adapt("rift/gate"), AbilityGatewayFixture.RIFT)).isEmpty();
  }
}

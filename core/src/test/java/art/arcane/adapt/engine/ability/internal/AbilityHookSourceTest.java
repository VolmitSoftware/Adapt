package art.arcane.adapt.engine.ability.internal;

import art.arcane.adapt.api.ability.AbilityScope;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AbilityHookSourceTest {
  private final List<AbilityUsePolicy> hooks = new ArrayList<>();
  private final AbilityGatewayFixture.RecordingLog log = new AbilityGatewayFixture.RecordingLog();
  private final AbilityHookSource<AbilityUsePolicy> source = new AbilityHookSource<>("use policy",
      () -> List.copyOf(hooks), AbilityUsePolicy::providerId, AbilityUsePolicy::scope, log);

  @Test
  void anUnchangedListKeepsItsIndex() {
    hooks.add(named("jail"));

    AbilityProviderIndex<AbilityUsePolicy> first = source.index();

    assertThat(source.index()).isSameAs(first);
    assertThat(first.size()).isOne();
  }

  @Test
  void aChangedListIsIndexedAgain() {
    hooks.add(named("jail"));
    AbilityProviderIndex<AbilityUsePolicy> first = source.index();
    hooks.add(named("arena"));

    AbilityProviderIndex<AbilityUsePolicy> second = source.index();

    assertThat(second).isNotSameAs(first);
    assertThat(second.all()).extracting(AbilityProviderRegistration::providerId).containsExactly("jail", "arena");
    hooks.clear();
    assertThat(source.index().isEmpty()).isTrue();
  }

  @Test
  void aSecondHookWithTheSameProviderIdIsIgnoredWithAWarning() {
    hooks.add(named("jail"));
    hooks.add(named("jail"));

    assertThat(source.index().size()).isOne();
    assertThat(log.warnings).anyMatch(message -> message.contains("'jail'"));
  }

  @Test
  void aHookWhoseScopeThrowsIsSkipped() {
    hooks.add(new AbilityUsePolicy() {
      @Override
      public AbilityUseDecision evaluate(art.arcane.adapt.api.ability.AbilityContext context) {
        return AbilityUseDecision.allow();
      }

      @Override
      public AbilityScope scope() {
        throw new IllegalStateException("boom");
      }
    });
    hooks.add(named("jail"));

    assertThat(source.index().all()).extracting(AbilityProviderRegistration::providerId).containsExactly("jail");
    assertThat(log.errors).hasSize(1);
  }

  private static AbilityUsePolicy named(String id) {
    return new AbilityUsePolicy() {
      @Override
      public AbilityUseDecision evaluate(art.arcane.adapt.api.ability.AbilityContext context) {
        return AbilityUseDecision.allow();
      }

      @Override
      public String providerId() {
        return id;
      }
    };
  }
}

package art.arcane.adapt.api.ability;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AbilityScopeTest {
  @Test
  void everythingMatchesEveryAbility() {
    assertThat(AbilityScope.everything().unscoped()).isTrue();
    assertThat(AbilityScope.everything().matches("tragoul-lance", "tragoul")).isTrue();
    assertThat(AbilityScope.everything().size()).isZero();
  }

  @Test
  void abilitiesNormalizesAndDedupesInsteadOfThrowing() {
    assertThatCode(() -> AbilityScope.abilities("Tragoul-Lance", "tragoul-lance", null, "  "))
        .doesNotThrowAnyException();

    AbilityScope scope = AbilityScope.abilities("Tragoul-Lance", "tragoul-lance");

    assertThat(scope.size()).isEqualTo(1);
    assertThat(scope.matches("tragoul-lance", "tragoul")).isTrue();
    assertThat(scope.matches("rift-gate", "rift")).isFalse();
  }

  @Test
  void skillEntriesMatchEveryAbilityOfThatSkill() {
    assertThat(AbilityScope.skill("Tragoul").matches("tragoul-lance", "tragoul")).isTrue();
    assertThat(AbilityScope.skill("Tragoul").matches("rift-gate", "rift")).isFalse();
    assertThat(AbilityScope.skills("tragoul", "rift").size()).isEqualTo(2);
    assertThat(AbilityScope.skills("tragoul", "rift").matches("rift-gate", "rift")).isTrue();
  }

  @Test
  void theAbilityAndSkillNamespacesAreDisjoint() {
    assertThat(AbilityScope.abilities("tragoul").matches("rift-gate", "tragoul")).isFalse();
    assertThat(AbilityScope.skill("tragoul").matches("tragoul", null)).isFalse();
    assertThat(AbilityScope.skill("tragoul").matches("skill:tragoul", null)).isFalse();
  }

  @Test
  void twoScopesCanBeUnioned() {
    AbilityScope merged = AbilityScope.skill("tragoul").and(AbilityScope.abilities("rift-gate"));

    assertThat(merged.matches("tragoul-lance", "tragoul")).isTrue();
    assertThat(merged.matches("rift-gate", "rift")).isTrue();
    assertThat(merged.matches("stealth-core", "stealth")).isFalse();
    assertThat(merged.size()).isEqualTo(2);
    assertThat(AbilityScope.skill("tragoul").and(AbilityScope.everything()).unscoped()).isTrue();
    assertThat(AbilityScope.skill("tragoul").and(null)).isEqualTo(AbilityScope.skill("tragoul"));
  }

  @Test
  void anAllBlankScopeCollapsesToEverything() {
    assertThat(AbilityScope.abilities("", "   ").unscoped()).isTrue();
    assertThat(AbilityScope.skills().unscoped()).isTrue();
  }

  @Test
  void scopesWithTheSameEntriesAreEqual() {
    assertThat(AbilityScope.abilities("a", "b")).isEqualTo(AbilityScope.abilities("A", "B"));
    assertThat(AbilityScope.abilities("a")).isNotEqualTo(AbilityScope.abilities("b"));
    assertThat(AbilityScope.everything()).hasToString("AbilityScope[every ability]");
  }

  @Test
  void aProviderCannotHandAScopeThatAdaptDidNotBuild() throws Exception {
    assertThat(Modifier.isFinal(AbilityScope.class.getModifiers())).isTrue();

    for (Constructor<?> constructor : AbilityScope.class.getDeclaredConstructors()) {
      assertThat(Modifier.isPublic(constructor.getModifiers())).isFalse();
      assertThat(Modifier.isProtected(constructor.getModifiers())).isFalse();
    }

    for (Method method : new Method[]{AbilityUsePolicy.class.getMethod("scope"),
        AbilityCostProvider.class.getMethod("scope")}) {
      assertThat(method.getReturnType()).isEqualTo(AbilityScope.class);
      assertThat(Collection.class.isAssignableFrom(method.getReturnType())).isFalse();
      assertThat(Map.class.isAssignableFrom(method.getReturnType())).isFalse();
    }
  }
}

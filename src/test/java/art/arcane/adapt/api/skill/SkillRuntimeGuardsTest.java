package art.arcane.adapt.api.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillRuntimeGuardsTest {
  @Test
  void xpGrantsRequirePositiveFiniteAmounts() {
    assertThat(SkillRuntimeGuards.isValidXp(1D)).isTrue();
    assertThat(SkillRuntimeGuards.isValidXp(Double.MIN_VALUE)).isTrue();
    assertThat(SkillRuntimeGuards.isValidXp(0D)).isFalse();
    assertThat(SkillRuntimeGuards.isValidXp(-1D)).isFalse();
    assertThat(SkillRuntimeGuards.isValidXp(Double.NaN)).isFalse();
    assertThat(SkillRuntimeGuards.isValidXp(Double.POSITIVE_INFINITY)).isFalse();
    assertThat(SkillRuntimeGuards.isValidXp(Double.NEGATIVE_INFINITY)).isFalse();
  }
}

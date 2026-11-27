package art.arcane.adapt.content.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillAxesTest {
  @Test
  void combatXpAddsTheFlatHitToTheDamageScalar() {
    assertThat(SkillAxes.combatXp(5D, 7D, 6D)).isEqualTo(47D);
  }

  @Test
  void uncreditedHitsPayNothing() {
    assertThat(SkillAxes.combatXp(5D, 7D, 0D)).isZero();
    assertThat(SkillAxes.combatXp(5D, 7D, Double.NaN)).isZero();
  }

  @Test
  void negativeTuningCannotSubtractXp() {
    assertThat(SkillAxes.combatXp(-5D, 7D, 2D)).isEqualTo(14D);
    assertThat(SkillAxes.combatXp(5D, -7D, 2D)).isEqualTo(5D);
  }
}

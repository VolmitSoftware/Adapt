package art.arcane.adapt.content.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillTragOulTest {
  @Test
  void fullLossAppliesWhenPlayerHasEnoughXp() {
    assertThat(SkillTragOul.deathXpLossFor(1000D, 250D)).isEqualTo(250D);
  }

  @Test
  void lossClampsToCurrentXpWhenBelowConfiguredLoss() {
    assertThat(SkillTragOul.deathXpLossFor(100D, 250D)).isEqualTo(100D);
  }

  @Test
  void zeroXpYieldsZeroLoss() {
    assertThat(SkillTragOul.deathXpLossFor(0D, 250D)).isZero();
  }

  @Test
  void negativeStoredXpYieldsZeroLoss() {
    assertThat(SkillTragOul.deathXpLossFor(-50D, 250D)).isZero();
  }

  @Test
  void negativeConfiguredValueIsTreatedAsMagnitude() {
    assertThat(SkillTragOul.deathXpLossFor(1000D, -250D)).isEqualTo(250D);
  }

  @Test
  void exactBalanceIsDrainedToZeroNotBelow() {
    assertThat(SkillTragOul.deathXpLossFor(250D, 250D)).isEqualTo(250D);
  }
}

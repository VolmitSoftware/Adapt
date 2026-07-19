package art.arcane.adapt.content.adaptation.agility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AgilitySuperJumpTest {
  @Test
  void jumpHeightScalesLinearlyWithLevel() {
    assertThat(AgilitySuperJump.jumpHeight(0.23D, 0.23D, 1)).isCloseTo(0.46D, within(1.0E-9D));
    assertThat(AgilitySuperJump.jumpHeight(0.23D, 0.23D, 2)).isCloseTo(0.69D, within(1.0E-9D));
    assertThat(AgilitySuperJump.jumpHeight(0.23D, 0.23D, 3)).isCloseTo(0.92D, within(1.0E-9D));
  }

  @Test
  void jumpStrengthBonusOffsetsVanillaBaseSoFinalJumpMatchesConfiguredHeight() {
    assertThat(AgilitySuperJump.jumpStrengthBonus(0.46D)).isCloseTo(0.04D, within(1.0E-9D));
    assertThat(AgilitySuperJump.jumpStrengthBonus(0.69D)).isCloseTo(0.27D, within(1.0E-9D));
    assertThat(AgilitySuperJump.jumpStrengthBonus(0.92D)).isCloseTo(0.50D, within(1.0E-9D));
  }

  @Test
  void jumpStrengthBonusAtVanillaBaseIsZero() {
    assertThat(AgilitySuperJump.jumpStrengthBonus(0.42D)).isCloseTo(0.0D, within(1.0E-9D));
  }

  @Test
  void jumpStrengthBonusBelowVanillaBaseWeakensJumpLikeLegacyVelocityClobber() {
    assertThat(AgilitySuperJump.jumpStrengthBonus(0.30D)).isCloseTo(-0.12D, within(1.0E-9D));
  }
}

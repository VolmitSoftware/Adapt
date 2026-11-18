package art.arcane.adapt.content.adaptation.tragoul;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TragoulSkeletalServantScalingTest {
  @Test
  void healthModifierAmountMatchesLegacyBaseValueDelta() {
    assertThat(TragoulSkeletalServant.servantAttributeBonus(1, 3.0D)).isCloseTo(3.0D, within(1.0e-9D));
    assertThat(TragoulSkeletalServant.servantAttributeBonus(3, 3.0D)).isCloseTo(9.0D, within(1.0e-9D));
    assertThat(TragoulSkeletalServant.servantAttributeBonus(5, 3.0D)).isCloseTo(15.0D, within(1.0e-9D));
  }

  @Test
  void attackModifierAmountMatchesLegacyBaseValueDelta() {
    assertThat(TragoulSkeletalServant.servantAttributeBonus(1, 1.0D)).isCloseTo(1.0D, within(1.0e-9D));
    assertThat(TragoulSkeletalServant.servantAttributeBonus(3, 1.0D)).isCloseTo(3.0D, within(1.0e-9D));
    assertThat(TragoulSkeletalServant.servantAttributeBonus(5, 1.0D)).isCloseTo(5.0D, within(1.0e-9D));
  }

  @Test
  void zeroLevelGrantsNoBonus() {
    assertThat(TragoulSkeletalServant.servantAttributeBonus(0, 3.0D)).isCloseTo(0.0D, within(1.0e-9D));
    assertThat(TragoulSkeletalServant.servantAttributeBonus(0, 1.0D)).isCloseTo(0.0D, within(1.0e-9D));
  }
}

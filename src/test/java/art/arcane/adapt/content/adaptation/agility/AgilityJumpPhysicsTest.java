package art.arcane.adapt.content.adaptation.agility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AgilityJumpPhysicsTest {
  @Test
  void vanillaStrengthMatchesTheVanillaApex() {
    assertThat(AgilityJumpPhysics.heightForStrength(AgilityJumpPhysics.VANILLA_JUMP_STRENGTH))
        .isCloseTo(1.252203352512D, within(1.0E-12D));
  }

  @Test
  void heightAndStrengthConversionsRoundTrip() {
    double[] heights = {1.5D, 1.75D, 2D, 2.5D};
    for (double height : heights) {
      double strength = AgilityJumpPhysics.strengthForHeight(height);
      assertThat(AgilityJumpPhysics.heightForStrength(strength)).isCloseTo(height, within(1.0E-9D));
    }
  }

  @Test
  void invalidValuesCannotProduceUnsafeAttributeAmounts() {
    assertThat(AgilityJumpPhysics.heightForStrength(Double.NaN)).isZero();
    assertThat(AgilityJumpPhysics.heightForStrength(Double.POSITIVE_INFINITY)).isZero();
    assertThat(AgilityJumpPhysics.strengthForHeight(Double.NaN)).isZero();
    assertThat(AgilityJumpPhysics.strengthForHeight(-1D)).isZero();
    assertThat(AgilityJumpPhysics.bonusForHeight(-1D)).isZero();
  }
}

package art.arcane.adapt.content.adaptation.agility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AgilitySlipstreamSlideSlowTest {
  @Test
  void slideFrictionReductionClampsToAttributeRange() {
    assertThat(AgilitySlipstreamSlide.slideFrictionReduction(-1D)).isZero();
    assertThat(AgilitySlipstreamSlide.slideFrictionReduction(0.9D)).isCloseTo(0.9D, within(1.0e-9D));
    assertThat(AgilitySlipstreamSlide.slideFrictionReduction(4D)).isEqualTo(1D);
    assertThat(AgilitySlipstreamSlide.slideFrictionReduction(Double.NaN)).isZero();
    assertThat(AgilitySlipstreamSlide.slideFrictionReduction(Double.POSITIVE_INFINITY)).isZero();
  }

  @Test
  void slideDefaultsRemoveMostGroundFriction() {
    AgilitySlipstreamSlide.Config config = new AgilitySlipstreamSlide.Config();
    assertThat(config.slideFrictionReduction).isBetween(0.75D, 1D);
  }

  @Test
  void slowAmountMatchesVanillaSlownessPerAmplifierLevel() {
    assertThat(AgilitySlipstreamSlide.slowAmount(0)).isCloseTo(-0.15D, within(1.0e-9D));
    assertThat(AgilitySlipstreamSlide.slowAmount(1)).isCloseTo(-0.30D, within(1.0e-9D));
    assertThat(AgilitySlipstreamSlide.slowAmount(2)).isCloseTo(-0.45D, within(1.0e-9D));
  }

  @Test
  void slowAmountClampsNegativeAmplifiersToLevelOne() {
    assertThat(AgilitySlipstreamSlide.slowAmount(-1)).isCloseTo(-0.15D, within(1.0e-9D));
    assertThat(AgilitySlipstreamSlide.slowAmount(-100)).isCloseTo(-0.15D, within(1.0e-9D));
  }

  @Test
  void slowAmountCapsAtFullStop() {
    assertThat(AgilitySlipstreamSlide.slowAmount(6)).isCloseTo(-1.0D, within(1.0e-9D));
    assertThat(AgilitySlipstreamSlide.slowAmount(100)).isCloseTo(-1.0D, within(1.0e-9D));
  }

  @Test
  void slowDurationTicksClampsToLegacyMinimum() {
    assertThat(AgilitySlipstreamSlide.slowDurationTicks(0)).isEqualTo(5);
    assertThat(AgilitySlipstreamSlide.slowDurationTicks(-20)).isEqualTo(5);
    assertThat(AgilitySlipstreamSlide.slowDurationTicks(40)).isEqualTo(40);
  }
}

package art.arcane.adapt.content.adaptation.herbalism;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HerbalismRootedFootingTest {
  @Test
  void absorbedDamageNeverExceedsTheConfiguredCap() {
    assertThat(HerbalismRootedFooting.absorbedDamage(0.25D, 1, 1D)).isEqualTo(0.25D);
    assertThat(HerbalismRootedFooting.absorbedDamage(2D, 1, 1D)).isEqualTo(1D);
  }

  @Test
  void absorbedDamageRejectsInvalidCostsAndEmptyFood() {
    assertThat(HerbalismRootedFooting.absorbedDamage(2D, 0, 1D)).isZero();
    assertThat(HerbalismRootedFooting.absorbedDamage(2D, 1, 0D)).isZero();
    assertThat(HerbalismRootedFooting.absorbedDamage(0D, 1, 1D)).isZero();
    assertThat(HerbalismRootedFooting.absorbedDamage(Double.NaN, 1, 1D)).isZero();
    assertThat(HerbalismRootedFooting.absorbedDamage(2D, 1, Double.POSITIVE_INFINITY)).isZero();
  }
}

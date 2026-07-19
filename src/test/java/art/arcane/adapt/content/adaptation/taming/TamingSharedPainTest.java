package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class TamingSharedPainTest {
  @Test
  void redirectPercentCannotExceedTheIncomingHit() {
    assertThat(TamingSharedPain.clampRedirectPercent(1.5D, 2D)).isEqualTo(1D);
    assertThat(TamingSharedPain.clampRedirectPercent(-0.5D, 1D)).isZero();
  }

  @Test
  void redirectedDamageSplitsEvenlyAcrossHealthyCompanions() {
    double[] shares = TamingSharedPain.damageShares(9D, new double[]{10D, 10D, 10D});

    assertThat(shares).containsExactly(3D, 3D, 3D);
  }

  @Test
  void lowHealthCompanionCapacityRedistributesToTheRestOfThePack() {
    double[] shares = TamingSharedPain.damageShares(9D, new double[]{1D, 10D, 10D});

    assertThat(shares[0]).isEqualTo(1D);
    assertThat(shares[1]).isCloseTo(4D, offset(1.0E-9));
    assertThat(shares[2]).isCloseTo(4D, offset(1.0E-9));
  }

  @Test
  void damageDistributionNeverExceedsAvailableCompanionHealth() {
    double[] shares = TamingSharedPain.damageShares(20D, new double[]{2D, 3D});

    assertThat(shares).containsExactly(2D, 3D);
  }

  @Test
  void transferredDamageCountsHealthAndAbsorptionActuallyConsumed() {
    assertThat(TamingSharedPain.transferredDamage(10D, 4D, 8D, 3D, 5D)).isEqualTo(3D);
    assertThat(TamingSharedPain.transferredDamage(10D, 0D, 3D, 0D, 4D)).isEqualTo(4D);
    assertThat(TamingSharedPain.transferredDamage(10D, 0D, 10D, 0D, 4D)).isZero();
  }
}

package art.arcane.adapt.api.world;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptPlayerFoodChargeTest {
  @Test
  void fractionalRemainderNeverBurnsAnExtraFoodPoint() {
    AdaptPlayer.FoodCharge charge = AdaptPlayer.chargeFood(20, 1.0D, 2.5D);

    assertThat(charge.food()).isEqualTo(19);
    assertThat(charge.saturation()).isEqualTo(0D);
  }

  @Test
  void saturationCoversTheWholeCostWhenItCan() {
    AdaptPlayer.FoodCharge charge = AdaptPlayer.chargeFood(20, 5.0D, 3.0D);

    assertThat(charge.food()).isEqualTo(20);
    assertThat(charge.saturation()).isEqualTo(2.0D);
  }

  @Test
  void wholeFoodPointsAreChargedAfterSaturationIsDrained() {
    AdaptPlayer.FoodCharge charge = AdaptPlayer.chargeFood(20, 2.0D, 5.0D);

    assertThat(charge.food()).isEqualTo(17);
    assertThat(charge.saturation()).isEqualTo(0D);
  }

  @Test
  void fractionalCostBelowOneIsTakenFromSaturationOnly() {
    AdaptPlayer.FoodCharge charge = AdaptPlayer.chargeFood(20, 0.75D, 0.5D);

    assertThat(charge.food()).isEqualTo(20);
    assertThat(charge.saturation()).isEqualTo(0.25D);
  }

  @Test
  void nonPositiveAndNonFiniteCostsChargeNothing() {
    assertThat(AdaptPlayer.chargeFood(20, 3.0D, 0D).food()).isEqualTo(20);
    assertThat(AdaptPlayer.chargeFood(20, 3.0D, 0D).saturation()).isEqualTo(3.0D);
    assertThat(AdaptPlayer.chargeFood(20, 3.0D, -4D).food()).isEqualTo(20);
    assertThat(AdaptPlayer.chargeFood(20, 3.0D, Double.NaN).food()).isEqualTo(20);
    assertThat(AdaptPlayer.chargeFood(20, 3.0D, Double.POSITIVE_INFINITY).food()).isEqualTo(20);
  }

  @Test
  void foodNeverGoesNegative() {
    AdaptPlayer.FoodCharge charge = AdaptPlayer.chargeFood(2, 0D, 9.0D);

    assertThat(charge.food()).isZero();
    assertThat(charge.saturation()).isEqualTo(0D);
  }
}

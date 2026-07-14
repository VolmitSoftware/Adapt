package art.arcane.adapt.content.adaptation.agility;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AgilityRollLandingTest {
  @Test
  void absorbedIsClampedToCapWhenHungerRoundingWouldOvershoot() {
    AgilityRollLanding.RollAbsorption absorption = AgilityRollLanding.computeRollAbsorption(10D, 0.65D, 0.65D, 20);

    assertThat(absorption.hungerCost()).isEqualTo(5);
    assertThat(absorption.absorbed()).isEqualTo(6.5D, within(1.0E-9D));
  }

  @Test
  void absorbedStaysProportionalWhenFoodIsTheLimit() {
    AgilityRollLanding.RollAbsorption absorption = AgilityRollLanding.computeRollAbsorption(10D, 0.8D, 1.4D, 2);

    assertThat(absorption.hungerCost()).isEqualTo(2);
    assertThat(absorption.absorbed()).isEqualTo(2D / 1.4D, within(1.0E-9D));
  }

  @Test
  void zeroFoodAbsorbsNothing() {
    AgilityRollLanding.RollAbsorption absorption = AgilityRollLanding.computeRollAbsorption(10D, 0.65D, 0.65D, 0);

    assertThat(absorption.hungerCost()).isZero();
    assertThat(absorption.absorbed()).isZero();
  }

  @Test
  void zeroDamageAbsorbsNothing() {
    AgilityRollLanding.RollAbsorption absorption = AgilityRollLanding.computeRollAbsorption(0D, 0.8D, 1.4D, 20);

    assertThat(absorption.hungerCost()).isZero();
    assertThat(absorption.absorbed()).isZero();
  }

  @Test
  void absorbedNeverExceedsCapAndHungerNeverExceedsFood() {
    double[] damages = {0.5D, 1D, 4D, 10D, 25D, 100D};
    double[] reductions = {0.22D, 0.5D, 0.65D, 0.8D};
    double[] hungerRates = {0.1D, 0.65D, 1.4D, 2.3D};
    int[] foodLevels = {0, 1, 3, 7, 20};

    for (double damage : damages) {
      for (double reduction : reductions) {
        for (double hungerPerDamage : hungerRates) {
          for (int foodLevel : foodLevels) {
            AgilityRollLanding.RollAbsorption absorption =
                AgilityRollLanding.computeRollAbsorption(damage, reduction, hungerPerDamage, foodLevel);
            double cap = damage * reduction;

            assertThat(absorption.absorbed()).isLessThanOrEqualTo(cap + 1.0E-9D);
            assertThat(absorption.absorbed()).isGreaterThanOrEqualTo(0D);
            assertThat(absorption.hungerCost()).isBetween(0, foodLevel);
          }
        }
      }
    }
  }
}

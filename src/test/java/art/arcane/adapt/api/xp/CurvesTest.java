package art.arcane.adapt.api.xp;

import art.arcane.adapt.AdaptTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CurvesTest extends AdaptTestBase {
  private static final double MAX_ERROR = 0.000001;

  @Test
  @DisplayName("analytic inverses recover the level that produced the xp")
  void analyticInversesRoundTrip() {
    assertRoundTrips(Curves.ADAPT_BALANCED);
    assertRoundTrips(Curves.LINEAR_EXPONENTIAL_1);
  }

  @Test
  @DisplayName("linear exponential 2 and 3 resolve levels through bisection")
  void linearExponentialFamiliesRoundTrip() {
    assertRoundTrips(Curves.LINEAR_EXPONENTIAL_2);
    assertRoundTrips(Curves.LINEAR_EXPONENTIAL_3);
  }

  @Test
  @DisplayName("XL100L7 sits between XL80L7 and XL160L7")
  void xl100SitsInsideItsFamilyOrdering() {
    for (int level = 1; level <= 100; level += 9) {
      double below = Curves.XL80L7.getCurve().getXPForLevel(level);
      double middle = Curves.XL100L7.getCurve().getXPForLevel(level);
      double above = Curves.XL160L7.getCurve().getXPForLevel(level);
      assertThat(middle).isGreaterThan(below);
      assertThat(middle).isLessThan(above);
    }
  }

  private void assertRoundTrips(Curves family) {
    NewtonCurve curve = family.getCurve();
    for (int level = 1; level <= 100; level++) {
      double xp = curve.getXPForLevel(level);
      double recovered = curve.computeLevelForXP(xp, MAX_ERROR);
      assertThat(recovered)
          .as("%s level %d", family.name(), level)
          .isCloseTo(level, within(0.05));
    }
  }
}

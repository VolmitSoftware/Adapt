package art.arcane.adapt.content.adaptation.ranged;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RangedArrowRecoveryChanceTest {
  @Test
  void chanceUsesConfiguredEntryForInBoundsLevels() {
    double[] chances = {10, 20, 30};
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 1)).isEqualTo(0.10D);
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 2)).isEqualTo(0.20D);
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 3)).isEqualTo(0.30D);
  }

  @Test
  void chanceClampsToLastEntryWhenOperatorShrinksTable() {
    double[] chances = {10, 20, 30};
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 8)).isEqualTo(0.30D);
  }

  @Test
  void chanceClampsToFirstEntryForNonPositiveLevels() {
    double[] chances = {10, 20, 30};
    assertThat(RangedArrowRecovery.chanceFromTable(chances, 0)).isEqualTo(0.10D);
    assertThat(RangedArrowRecovery.chanceFromTable(chances, -3)).isEqualTo(0.10D);
  }

  @Test
  void chanceIsZeroForMissingOrEmptyTable() {
    assertThat(RangedArrowRecovery.chanceFromTable(new double[0], 1)).isEqualTo(0.0D);
    assertThat(RangedArrowRecovery.chanceFromTable(null, 1)).isEqualTo(0.0D);
  }
}

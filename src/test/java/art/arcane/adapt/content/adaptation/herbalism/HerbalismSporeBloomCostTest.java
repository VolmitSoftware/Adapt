package art.arcane.adapt.content.adaptation.herbalism;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HerbalismSporeBloomCostTest {
  @Test
  void sporeCostScalesOnePerLevelWithTheDefaults() {
    assertThat(HerbalismSporeBloom.sporeCost(1D, 1D, 1)).isEqualTo(1);
    assertThat(HerbalismSporeBloom.sporeCost(1D, 1D, 2)).isEqualTo(2);
    assertThat(HerbalismSporeBloom.sporeCost(1D, 1D, 3)).isEqualTo(3);
    assertThat(HerbalismSporeBloom.sporeCost(1D, 1D, 4)).isEqualTo(4);
    assertThat(HerbalismSporeBloom.sporeCost(1D, 1D, 5)).isEqualTo(5);
  }

  @Test
  void sporeCostIsNeverBelowOne() {
    assertThat(HerbalismSporeBloom.sporeCost(0D, 0D, 1)).isEqualTo(1);
    assertThat(HerbalismSporeBloom.sporeCost(-5D, -5D, 4)).isEqualTo(1);
    assertThat(HerbalismSporeBloom.sporeCost(1D, 1D, 0)).isEqualTo(1);
    assertThat(HerbalismSporeBloom.sporeCost(1D, 1D, -3)).isEqualTo(1);
  }

  @Test
  void sporeCostRoundsFractionalScaling() {
    assertThat(HerbalismSporeBloom.sporeCost(1D, 0.5D, 4)).isEqualTo(3);
    assertThat(HerbalismSporeBloom.sporeCost(2D, 1.5D, 3)).isEqualTo(5);
  }

  @Test
  void sporeCostRejectsNonFiniteConfiguration() {
    assertThat(HerbalismSporeBloom.sporeCost(Double.NaN, 1D, 3)).isEqualTo(1);
    assertThat(HerbalismSporeBloom.sporeCost(1D, Double.POSITIVE_INFINITY, 3)).isEqualTo(1);
  }
}

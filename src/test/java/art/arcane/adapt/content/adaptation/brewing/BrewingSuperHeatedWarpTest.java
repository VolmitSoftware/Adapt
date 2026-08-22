package art.arcane.adapt.content.adaptation.brewing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BrewingSuperHeatedWarpTest {
  @Test
  void zeroFireAndLavaKeepsVanillaBrewSpeed() {
    assertThat(BrewingSuperHeated.computeWarpTicks(253, 0.5, 0.9, 0, 0)).isZero();
    assertThat(BrewingSuperHeated.computeWarpTicks(1000, 0.5, 0.9, 0, 0)).isZero();
    assertThat(BrewingSuperHeated.computeWarpTicks(253, 0, 0, 4, 5)).isZero();
  }

  @Test
  void warpScalesWithBoostAndSourceCount() {
    assertThat(BrewingSuperHeated.computeWarpTicks(1000, 0.5, 0.9, 1, 0)).isEqualTo(10);
    assertThat(BrewingSuperHeated.computeWarpTicks(1000, 0.5, 0.9, 0, 1)).isEqualTo(18);
    assertThat(BrewingSuperHeated.computeWarpTicks(1000, 0.5, 0.9, 1, 1)).isEqualTo(28);
  }

  @Test
  void anyHeatSourceProducesAtLeastOneTickOfWarp() {
    assertThat(BrewingSuperHeated.computeWarpTicks(253, 0.037, 0, 1, 0)).isGreaterThanOrEqualTo(1);
  }

  @Test
  void moreSourcesWarpFaster() {
    assertThat(BrewingSuperHeated.computeWarpTicks(253, 0.14, 0.69, 5, 0))
        .isGreaterThan(BrewingSuperHeated.computeWarpTicks(253, 0.14, 0.69, 1, 0));
    assertThat(BrewingSuperHeated.computeWarpTicks(253, 0.14, 0.69, 0, 5))
        .isGreaterThan(BrewingSuperHeated.computeWarpTicks(253, 0.14, 0.69, 0, 1));
  }
}

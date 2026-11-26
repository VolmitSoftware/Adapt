package art.arcane.adapt.content.adaptation.herbalism;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HerbalismGrowthAuraSampleCountTest {
  @Test
  void flooredAtThreeForTinyRadii() {
    assertThat(HerbalismGrowthPulses.sampleCountForRadius(0D)).isEqualTo(3);
    assertThat(HerbalismGrowthPulses.sampleCountForRadius(1D)).isEqualTo(3);
    assertThat(HerbalismGrowthPulses.sampleCountForRadius(1.5D)).isEqualTo(3);
  }

  @Test
  void scalesWithRadiusSquared() {
    assertThat(HerbalismGrowthPulses.sampleCountForRadius(4D)).isEqualTo(16);
    assertThat(HerbalismGrowthPulses.sampleCountForRadius(9D)).isEqualTo(81);
    assertThat(HerbalismGrowthPulses.sampleCountForRadius(12D)).isEqualTo(144);
  }

  @Test
  void cappedAtTwoHundredFiftySix() {
    assertThat(HerbalismGrowthPulses.sampleCountForRadius(16D)).isEqualTo(256);
    assertThat(HerbalismGrowthPulses.sampleCountForRadius(18D)).isEqualTo(256);
    assertThat(HerbalismGrowthPulses.sampleCountForRadius(100D)).isEqualTo(256);
  }

  @Test
  void neverDecreasesAsRadiusGrows() {
    int previous = 0;
    for (double radius = 0D; radius <= 32D; radius += 0.25D) {
      int samples = HerbalismGrowthPulses.sampleCountForRadius(radius);
      assertThat(samples).isGreaterThanOrEqualTo(previous);
      previous = samples;
    }
  }
}

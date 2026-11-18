package art.arcane.adapt.content.adaptation.herbalism;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HerbalismGrowthAuraSampleCountTest {
  @Test
  void flooredAtThreeForTinyRadii() {
    assertThat(HerbalismGrowthAura.sampleCountForRadius(0D)).isEqualTo(3);
    assertThat(HerbalismGrowthAura.sampleCountForRadius(1D)).isEqualTo(3);
    assertThat(HerbalismGrowthAura.sampleCountForRadius(1.5D)).isEqualTo(3);
  }

  @Test
  void scalesWithRadiusSquared() {
    assertThat(HerbalismGrowthAura.sampleCountForRadius(4D)).isEqualTo(16);
    assertThat(HerbalismGrowthAura.sampleCountForRadius(9D)).isEqualTo(81);
    assertThat(HerbalismGrowthAura.sampleCountForRadius(12D)).isEqualTo(144);
  }

  @Test
  void cappedAtTwoHundredFiftySix() {
    assertThat(HerbalismGrowthAura.sampleCountForRadius(16D)).isEqualTo(256);
    assertThat(HerbalismGrowthAura.sampleCountForRadius(18D)).isEqualTo(256);
    assertThat(HerbalismGrowthAura.sampleCountForRadius(100D)).isEqualTo(256);
  }

  @Test
  void neverDecreasesAsRadiusGrows() {
    int previous = 0;
    for (double radius = 0D; radius <= 32D; radius += 0.25D) {
      int samples = HerbalismGrowthAura.sampleCountForRadius(radius);
      assertThat(samples).isGreaterThanOrEqualTo(previous);
      previous = samples;
    }
  }
}

package art.arcane.adapt.content.adaptation.herbalism;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HerbalismWorldWorkBudgetTest {
  @Test
  void beeShepherdWorkNeverExceedsItsBudget() {
    assertThat(HerbalismBeeShepherd.workFor(1000, 32)).isEqualTo(32);
    assertThat(HerbalismBeeShepherd.workFor(1000, 96)).isEqualTo(96);
    assertThat(HerbalismBeeShepherd.workFor(1000, 8)).isEqualTo(8);
    assertThat(HerbalismBeeShepherd.workFor(1000, 16)).isEqualTo(16);
  }

  @Test
  void growthAuraWorkNeverExceedsItsBudget() {
    assertThat(HerbalismGrowthAura.workFor(1000, 32)).isEqualTo(32);
    assertThat(HerbalismGrowthAura.workFor(1000, 16)).isEqualTo(16);
  }

  @Test
  void growthAuraPreservesTheThreeSampleCeiling() {
    assertThat(HerbalismGrowthAura.sampleCountForRadius(0D)).isZero();
    assertThat(HerbalismGrowthAura.sampleCountForRadius(0.5D)).isEqualTo(1);
    assertThat(HerbalismGrowthAura.sampleCountForRadius(2D)).isEqualTo(3);
    assertThat(HerbalismGrowthAura.sampleCountForRadius(100D)).isEqualTo(3);
  }
}

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
    assertThat(HerbalismGrowthPulses.workFor(1000, 32)).isEqualTo(32);
    assertThat(HerbalismGrowthPulses.workFor(1000, 16)).isEqualTo(16);
  }

  @Test
  void compostCascadeScanNeverExceedsItsBudget() {
    assertThat(HerbalismField.workFor(42875, 24576)).isEqualTo(24576);
    assertThat(HerbalismField.workFor(1_000_000, 24576)).isEqualTo(24576);
    assertThat(HerbalismField.workFor(1331, 24576)).isEqualTo(1331);
  }

  @Test
  void compostCascadeScanClampsNonsenseInput() {
    assertThat(HerbalismField.workFor(-500, 24576)).isZero();
    assertThat(HerbalismField.workFor(42875, -1)).isZero();
    assertThat(HerbalismField.workFor(0, 24576)).isZero();
  }
}

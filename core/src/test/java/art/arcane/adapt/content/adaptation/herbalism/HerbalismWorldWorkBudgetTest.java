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
  void compostCascadeScanNeverExceedsItsBudget() {
    assertThat(HerbalismCompostCascade.workFor(42875, 24576)).isEqualTo(24576);
    assertThat(HerbalismCompostCascade.workFor(1_000_000, 24576)).isEqualTo(24576);
    assertThat(HerbalismCompostCascade.workFor(1331, 24576)).isEqualTo(1331);
  }

  @Test
  void compostCascadeScanClampsNonsenseInput() {
    assertThat(HerbalismCompostCascade.workFor(-500, 24576)).isZero();
    assertThat(HerbalismCompostCascade.workFor(42875, -1)).isZero();
    assertThat(HerbalismCompostCascade.workFor(0, 24576)).isZero();
  }
}

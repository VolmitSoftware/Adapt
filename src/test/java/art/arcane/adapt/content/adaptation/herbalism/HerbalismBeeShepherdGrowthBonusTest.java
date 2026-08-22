package art.arcane.adapt.content.adaptation.herbalism;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HerbalismBeeShepherdGrowthBonusTest {
  @Test
  void herdedBeesMultiplyGrowthAttempts() {
    assertThat(HerbalismBeeShepherd.growthAttemptsWithBees(20, 1, 5, 0.15D)).isEqualTo(23);
    assertThat(HerbalismBeeShepherd.growthAttemptsWithBees(20, 3, 5, 0.15D)).isEqualTo(29);
    assertThat(HerbalismBeeShepherd.growthAttemptsWithBees(20, 5, 5, 0.15D)).isEqualTo(35);
  }

  @Test
  void bonusStopsScalingPastTheConfiguredBeeCap() {
    assertThat(HerbalismBeeShepherd.growthAttemptsWithBees(20, 8, 5, 0.15D)).isEqualTo(35);
    assertThat(HerbalismBeeShepherd.growthAttemptsWithBees(20, 500, 5, 0.15D)).isEqualTo(35);
  }

  @Test
  void noBeesLeavesTheBaseAttemptsUntouched() {
    assertThat(HerbalismBeeShepherd.growthAttemptsWithBees(20, 0, 5, 0.15D)).isEqualTo(20);
    assertThat(HerbalismBeeShepherd.growthAttemptsWithBees(20, -4, 5, 0.15D)).isEqualTo(20);
  }

  @Test
  void invalidBonusConfigurationNeverReducesAttempts() {
    assertThat(HerbalismBeeShepherd.growthAttemptsWithBees(20, 5, 0, 0.15D)).isEqualTo(20);
    assertThat(HerbalismBeeShepherd.growthAttemptsWithBees(20, 5, 5, 0D)).isEqualTo(20);
    assertThat(HerbalismBeeShepherd.growthAttemptsWithBees(20, 5, 5, -1D)).isEqualTo(20);
    assertThat(HerbalismBeeShepherd.growthAttemptsWithBees(20, 5, 5, Double.NaN)).isEqualTo(20);
    assertThat(HerbalismBeeShepherd.growthAttemptsWithBees(0, 5, 5, 0.15D)).isZero();
  }
}

package art.arcane.adapt.content.adaptation.axe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AxeBarkHideTest {
  @Test
  void nextStacksIncrementsUntilCap() {
    assertThat(AxeBarkHide.nextStacks(0, 4)).isEqualTo(1);
    assertThat(AxeBarkHide.nextStacks(3, 4)).isEqualTo(4);
    assertThat(AxeBarkHide.nextStacks(4, 4)).isEqualTo(4);
  }

  @Test
  void currentStacksReturnsZeroWithoutState() {
    assertThat(AxeBarkHide.currentStacks(null, 1000L, 5000L)).isZero();
  }

  @Test
  void currentStacksKeepsStacksInsideGraceWindow() {
    AxeBarkHide.BarkState state = new AxeBarkHide.BarkState(3, 1000L, false);
    assertThat(AxeBarkHide.currentStacks(state, 6000L, 5000L)).isEqualTo(3);
  }

  @Test
  void currentStacksResetsAfterGraceWindow() {
    AxeBarkHide.BarkState state = new AxeBarkHide.BarkState(3, 1000L, false);
    assertThat(AxeBarkHide.currentStacks(state, 6001L, 5000L)).isZero();
  }

  @Test
  void absorptionPointsMatchLegacyPotionAmplifierMath() {
    assertThat(AxeBarkHide.absorptionPoints(1)).isEqualTo(4D);
    assertThat(AxeBarkHide.absorptionPoints(2)).isEqualTo(8D);
    assertThat(AxeBarkHide.absorptionPoints(4)).isEqualTo(16D);
  }

  @Test
  void shouldRefillOnStackGrowth() {
    assertThat(AxeBarkHide.shouldRefill(1, 2, false)).isTrue();
  }

  @Test
  void shouldNotRefillOnAtCapRefresh() {
    assertThat(AxeBarkHide.shouldRefill(4, 4, false)).isFalse();
  }

  @Test
  void shouldRefillAtCapAfterCeilingLoss() {
    assertThat(AxeBarkHide.shouldRefill(4, 4, true)).isTrue();
  }

  @Test
  void graceExpiryCollapsesCeilingSoVanillaClampClearsHearts() {
    AxeBarkHide.BarkState state = new AxeBarkHide.BarkState(4, 1000L, false);
    int expiredStacks = AxeBarkHide.currentStacks(state, 1000L + 5000L + 1L, 5000L);
    assertThat(expiredStacks).isZero();
    assertThat(AxeBarkHide.absorptionPoints(expiredStacks)).isZero();
  }
}

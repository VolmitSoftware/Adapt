package art.arcane.adapt.content.adaptation.rift;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiftDescentFallProtectionTest {
  @Test
  void fallProtectionTicksMatchesLegacySlowFallingDuration() {
    assertThat(RiftDescent.fallProtectionTicks(5.0D)).isEqualTo(100);
    assertThat(RiftDescent.fallProtectionTicks(2.75D)).isEqualTo(55);
    assertThat(RiftDescent.fallProtectionTicks(0.5D)).isEqualTo(10);
  }

  @Test
  void fractionalSecondsTruncateLikeLegacyPotionDurationCast() {
    assertThat(RiftDescent.fallProtectionTicks(0.04D)).isEqualTo(0);
    assertThat(RiftDescent.fallProtectionTicks(0.09D)).isEqualTo(1);
  }

  @Test
  void nonPositiveCooldownYieldsNoProtectionWindow() {
    assertThat(RiftDescent.fallProtectionTicks(0.0D)).isEqualTo(0);
    assertThat(RiftDescent.fallProtectionTicks(-3.0D)).isLessThanOrEqualTo(0);
  }
}

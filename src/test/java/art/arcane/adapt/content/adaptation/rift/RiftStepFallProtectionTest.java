package art.arcane.adapt.content.adaptation.rift;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RiftStepFallProtectionTest {
  @Test
  void fallProtectionAppliesForPositiveDurations() {
    assertThat(RiftStep.shouldApplyFallProtection(1)).isTrue();
    assertThat(RiftStep.shouldApplyFallProtection(60)).isTrue();
  }

  @Test
  void fallProtectionSkipsZeroDurationLikeLegacyNoOpPotion() {
    assertThat(RiftStep.shouldApplyFallProtection(0)).isFalse();
  }

  @Test
  void fallProtectionSkipsNegativeDurationsToAvoidPermanentModifierLeak() {
    assertThat(RiftStep.shouldApplyFallProtection(-20)).isFalse();
  }
}

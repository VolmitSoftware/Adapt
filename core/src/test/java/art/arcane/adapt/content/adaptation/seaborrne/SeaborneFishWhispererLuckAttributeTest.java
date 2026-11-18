package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.platform.value.Vec3;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeaborneFishWhispererLuckAttributeTest {
  @Test
  void luckAmountMatchesLegacyPotionAmplifierPlusOne() {
    assertThat(SeaborneFishWhisperer.luckAmount(1, 5)).isEqualTo(1);
    assertThat(SeaborneFishWhisperer.luckAmount(3, 5)).isEqualTo(3);
    assertThat(SeaborneFishWhisperer.luckAmount(5, 5)).isEqualTo(5);
  }

  @Test
  void luckAmountClampsAtMaxLevelTier() {
    assertThat(SeaborneFishWhisperer.luckAmount(9, 5)).isEqualTo(5);
    assertThat(SeaborneFishWhisperer.luckAmount(0, 5)).isEqualTo(1);
  }

  @Test
  void charmSessionsStartOnlyAfterTheirGap() {
    assertThat(SeaborneFishWhisperer.startsNewCharmSession(null, 10_000L)).isTrue();
    assertThat(SeaborneFishWhisperer.startsNewCharmSession(1_000L, 12_999L)).isFalse();
    assertThat(SeaborneFishWhisperer.startsNewCharmSession(1_000L, 13_000L)).isTrue();
  }

  @Test
  void velocityCreditRequiresARealFiniteChange() {
    Vec3 stationary = Vec3.ZERO;
    Vec3 moved = new Vec3(0.1D, 0D, 0D);
    Vec3 invalid = new Vec3(Double.NaN, 0D, 0D);

    assertThat(SeaborneFishWhisperer.hasVelocityChange(stationary, moved)).isTrue();
    assertThat(SeaborneFishWhisperer.hasVelocityChange(stationary, stationary)).isFalse();
    assertThat(SeaborneFishWhisperer.hasVelocityChange(stationary, invalid)).isFalse();
    assertThat(SeaborneFishWhisperer.hasVelocityChange(null, moved)).isFalse();
  }
}

package art.arcane.adapt.content.adaptation.seaborrne;

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
}

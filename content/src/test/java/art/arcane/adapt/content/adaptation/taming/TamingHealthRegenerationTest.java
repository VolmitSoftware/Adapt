package art.arcane.adapt.content.adaptation.taming;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TamingHealthRegenerationTest {
  @Test
  void healingUsesTheConfiguredAmountWithoutCrossingMaxHealth() {
    assertThat(TameRegeneration.actualHealing(10D, 20D, 6D)).isEqualTo(6D);
    assertThat(TameRegeneration.actualHealing(18D, 20D, 6D)).isEqualTo(2D);
  }

  @Test
  void invalidOrUnneededHealingDoesNotProduceCredit() {
    assertThat(TameRegeneration.actualHealing(20D, 20D, 6D)).isZero();
    assertThat(TameRegeneration.actualHealing(10D, 20D, 0D)).isZero();
    assertThat(TameRegeneration.actualHealing(10D, 20D, Double.NaN)).isZero();
  }
}

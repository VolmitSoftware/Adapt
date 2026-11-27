package art.arcane.adapt.content.adaptation.nether;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NetherSoulStriderAttributeTest {
  @Test
  void strideBonusScalesWithLevelAndMatchesDefaultsAtMax() {
    double low = NetherSoulStrider.strideBonus(0.2D, 0.20D, 0.10D);
    double max = NetherSoulStrider.strideBonus(1.0D, 0.20D, 0.10D);

    assertThat(low).isCloseTo(0.10D, within(1.0E-9D));
    assertThat(max).isCloseTo(0.50D, within(1.0E-9D));
    assertThat(max).isGreaterThan(low);
  }

  @Test
  void strideBonusIsZeroWhenBaseIsNonPositive() {
    assertThat(NetherSoulStrider.strideBonus(1.0D, 0D, 0.10D)).isEqualTo(0D);
    assertThat(NetherSoulStrider.strideBonus(1.0D, -0.5D, 0.10D)).isEqualTo(0D);
  }

  @Test
  void strideBonusReproducesLegacyStrideSpeedTarget() {
    double levelPercent = 0.6D;
    double base = 0.20D;
    double factor = 0.10D;

    double legacyTarget = NetherSoulStrider.strideSpeed(levelPercent, base, factor);
    double attributeTarget = base * (1D + NetherSoulStrider.strideBonus(levelPercent, base, factor));

    assertThat(attributeTarget).isCloseTo(legacyTarget, within(1.0E-9D));
  }

  @Test
  void burstSpeedBonusMatchesSpeedPotionParity() {
    assertThat(NetherSoulStrider.burstSpeedBonus(0)).isCloseTo(0.2D, within(1.0E-9D));
    assertThat(NetherSoulStrider.burstSpeedBonus(1)).isCloseTo(0.4D, within(1.0E-9D));
    assertThat(NetherSoulStrider.burstSpeedBonus(2)).isCloseTo(0.6D, within(1.0E-9D));
  }
}

package art.arcane.adapt.content.adaptation.nether;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class NetherStriderBondAttributeTest {
  @Test
  void striderSpeedBonusMatchesSpeedPotionParity() {
    assertThat(NetherStriderBond.striderSpeedBonus(0)).isCloseTo(0.2D, within(1.0E-9D));
    assertThat(NetherStriderBond.striderSpeedBonus(1)).isCloseTo(0.4D, within(1.0E-9D));
    assertThat(NetherStriderBond.striderSpeedBonus(2)).isCloseTo(0.6D, within(1.0E-9D));
  }

  @Test
  void striderSpeedBonusScalesWithDefaultAmplifierCurve() {
    int lowAmplifier = NetherStriderBond.striderSpeedAmplifier(0.25D, 0D, 1.5D);
    int maxAmplifier = NetherStriderBond.striderSpeedAmplifier(1.0D, 0D, 1.5D);

    assertThat(NetherStriderBond.striderSpeedBonus(lowAmplifier)).isCloseTo(0.2D, within(1.0E-9D));
    assertThat(NetherStriderBond.striderSpeedBonus(maxAmplifier)).isCloseTo(0.6D, within(1.0E-9D));
    assertThat(NetherStriderBond.striderSpeedBonus(maxAmplifier))
        .isGreaterThan(NetherStriderBond.striderSpeedBonus(lowAmplifier));
  }

  @Test
  void striderSpeedBonusNeverDropsBelowSpeedOneParity() {
    int floorAmplifier = NetherStriderBond.striderSpeedAmplifier(0D, -5D, 0D);

    assertThat(floorAmplifier).isZero();
    assertThat(NetherStriderBond.striderSpeedBonus(floorAmplifier)).isCloseTo(0.2D, within(1.0E-9D));
  }
}

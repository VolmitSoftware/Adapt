package art.arcane.adapt.content.adaptation.unarmed;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class UnarmedIronFistsBreakSpeedTest {
  @Test
  void breakSpeedBonusMatchesHasteLevelParity() {
    assertThat(UnarmedIronFists.breakSpeedBonus(0)).isCloseTo(0.2D, within(1.0e-9D));
    assertThat(UnarmedIronFists.breakSpeedBonus(1)).isCloseTo(0.4D, within(1.0e-9D));
    assertThat(UnarmedIronFists.breakSpeedBonus(2)).isCloseTo(0.6D, within(1.0e-9D));
  }

  @Test
  void hasteAmplifierScalesWithLevelPercent() {
    assertThat(UnarmedIronFists.hasteAmplifier(0.0D, 2.0D)).isEqualTo(0);
    assertThat(UnarmedIronFists.hasteAmplifier(0.5D, 2.0D)).isEqualTo(1);
    assertThat(UnarmedIronFists.hasteAmplifier(1.0D, 2.0D)).isEqualTo(2);
  }

  @Test
  void hasteAmplifierRoundsToNearestLevel() {
    assertThat(UnarmedIronFists.hasteAmplifier(0.2D, 2.0D)).isEqualTo(0);
    assertThat(UnarmedIronFists.hasteAmplifier(0.3D, 2.0D)).isEqualTo(1);
    assertThat(UnarmedIronFists.hasteAmplifier(0.8D, 2.0D)).isEqualTo(2);
  }

  @Test
  void hasteAmplifierNeverGoesNegative() {
    assertThat(UnarmedIronFists.hasteAmplifier(-1.0D, 2.0D)).isEqualTo(0);
    assertThat(UnarmedIronFists.hasteAmplifier(0.5D, -4.0D)).isEqualTo(0);
  }
}

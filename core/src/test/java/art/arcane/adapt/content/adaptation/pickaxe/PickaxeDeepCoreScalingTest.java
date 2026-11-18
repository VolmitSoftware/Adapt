package art.arcane.adapt.content.adaptation.pickaxe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PickaxeDeepCoreScalingTest {
  @Test
  void hasteAmplifierScalesWithLevelFromBase() {
    assertThat(PickaxeDeepCore.hasteAmplifier(2, 5, 1)).isEqualTo(2);
    assertThat(PickaxeDeepCore.hasteAmplifier(2, 5, 2)).isEqualTo(3);
    assertThat(PickaxeDeepCore.hasteAmplifier(2, 5, 3)).isEqualTo(4);
  }

  @Test
  void hasteAmplifierCapsAtMaxAmplifier() {
    assertThat(PickaxeDeepCore.hasteAmplifier(2, 5, 4)).isEqualTo(5);
    assertThat(PickaxeDeepCore.hasteAmplifier(2, 5, 10)).isEqualTo(5);
    assertThat(PickaxeDeepCore.hasteAmplifier(2, 3, 5)).isEqualTo(3);
  }

  @Test
  void hasteMultiplierMirrorsVanillaHastePerLevelBonus() {
    assertThat(PickaxeDeepCore.hasteMultiplier(0)).isCloseTo(0.2D, within(1.0e-9));
    assertThat(PickaxeDeepCore.hasteMultiplier(2)).isCloseTo(0.6D, within(1.0e-9));
    assertThat(PickaxeDeepCore.hasteMultiplier(4)).isCloseTo(1.0D, within(1.0e-9));
    assertThat(PickaxeDeepCore.hasteMultiplier(5)).isCloseTo(1.2D, within(1.0e-9));
  }

  @Test
  void defaultConfigLevelsProduceHasteThreeFourFiveMultipliers() {
    assertThat(PickaxeDeepCore.hasteMultiplier(PickaxeDeepCore.hasteAmplifier(2, 5, 1))).isCloseTo(0.6D, within(1.0e-9));
    assertThat(PickaxeDeepCore.hasteMultiplier(PickaxeDeepCore.hasteAmplifier(2, 5, 2))).isCloseTo(0.8D, within(1.0e-9));
    assertThat(PickaxeDeepCore.hasteMultiplier(PickaxeDeepCore.hasteAmplifier(2, 5, 3))).isCloseTo(1.0D, within(1.0e-9));
  }
}

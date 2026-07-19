package art.arcane.adapt.content.adaptation.pickaxe;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PickaxeObsidianRushTest {
  @Test
  void rushAmplifierScalesWithLevelBelowCap() {
    assertThat(PickaxeObsidianRush.rushAmplifier(3, 7, 1)).isEqualTo(4);
    assertThat(PickaxeObsidianRush.rushAmplifier(3, 7, 2)).isEqualTo(5);
    assertThat(PickaxeObsidianRush.rushAmplifier(3, 7, 3)).isEqualTo(6);
  }

  @Test
  void rushAmplifierClampsAtMaxAmplifier() {
    assertThat(PickaxeObsidianRush.rushAmplifier(3, 7, 4)).isEqualTo(7);
    assertThat(PickaxeObsidianRush.rushAmplifier(3, 7, 100)).isEqualTo(7);
  }

  @Test
  void rushSpeedBonusMatchesHasteParityPerAmplifierLevel() {
    assertThat(PickaxeObsidianRush.rushSpeedBonus(0)).isCloseTo(0.2D, within(1.0E-9));
    assertThat(PickaxeObsidianRush.rushSpeedBonus(3)).isCloseTo(0.8D, within(1.0E-9));
    assertThat(PickaxeObsidianRush.rushSpeedBonus(7)).isCloseTo(1.6D, within(1.0E-9));
  }

  @Test
  void defaultConfigLevelsMapToLegacyHasteMultipliers() {
    assertThat(PickaxeObsidianRush.rushSpeedBonus(PickaxeObsidianRush.rushAmplifier(3, 7, 1))).isCloseTo(1.0D, within(1.0E-9));
    assertThat(PickaxeObsidianRush.rushSpeedBonus(PickaxeObsidianRush.rushAmplifier(3, 7, 2))).isCloseTo(1.2D, within(1.0E-9));
    assertThat(PickaxeObsidianRush.rushSpeedBonus(PickaxeObsidianRush.rushAmplifier(3, 7, 3))).isCloseTo(1.4D, within(1.0E-9));
  }
}

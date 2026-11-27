package art.arcane.adapt.content.adaptation.sword;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SwordsBladeFlowAttributeTest {
  @Test
  void effectDurationTicksIsAlwaysPositiveSoTimedApplyNeverFallsBackToPermanent() {
    assertThat(SwordFlow.effectDurationTicks(0L)).isEqualTo(20);
    assertThat(SwordFlow.effectDurationTicks(-500L)).isEqualTo(20);
    assertThat(SwordFlow.effectDurationTicks(1L)).isEqualTo(20);
    assertThat(SwordFlow.effectDurationTicks(4_000L)).isEqualTo(80);
    assertThat(SwordFlow.effectDurationTicks(Long.MAX_VALUE)).isGreaterThan(0);
  }

  @Test
  void attackSpeedBonusScalesPerStackAndClampsNegativeStacksToZero() {
    assertThat(SwordFlow.attackSpeedBonus(-3)).isZero();
    assertThat(SwordFlow.attackSpeedBonus(2)).isCloseTo(0.20D, within(1.0E-9D));
    assertThat(SwordFlow.attackSpeedBonus(6)).isCloseTo(0.60D, within(1.0E-9D));
  }
}

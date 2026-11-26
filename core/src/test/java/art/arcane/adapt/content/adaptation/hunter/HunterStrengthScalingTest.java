package art.arcane.adapt.content.adaptation.hunter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HunterStrengthScalingTest {
  @Test
  void strengthDamageBonusMatchesStrengthAmplifierParity() {
    assertEquals(6.0D, HunterStrengthBuff.strengthDamageBonus(1), 1.0e-9D);
    assertEquals(9.0D, HunterStrengthBuff.strengthDamageBonus(2), 1.0e-9D);
    assertEquals(18.0D, HunterStrengthBuff.strengthDamageBonus(5), 1.0e-9D);
  }

  @Test
  void buffDurationTicksScalesWithLevel() {
    assertEquals(25L, HunterStrengthBuff.buffDurationTicks(25, 1, 0L, false));
    assertEquals(100L, HunterStrengthBuff.buffDurationTicks(25, 4, 0L, false));
    assertEquals(80L, HunterStrengthBuff.buffDurationTicks(40, 2, 0L, true));
  }

  @Test
  void buffDurationTicksExtendsRemainingOnlyWhenStacking() {
    assertEquals(80L, HunterStrengthBuff.buffDurationTicks(25, 2, 30L, true));
    assertEquals(50L, HunterStrengthBuff.buffDurationTicks(25, 2, 30L, false));
  }

  @Test
  void buffDurationTicksClampsNegativeRemaining() {
    assertEquals(25L, HunterStrengthBuff.buffDurationTicks(25, 1, -40L, true));
  }

  @Test
  void buffDurationTicksNonPositiveBaseYieldsZero() {
    assertEquals(0L, HunterStrengthBuff.buffDurationTicks(0, 3, 500L, true));
    assertEquals(0L, HunterStrengthBuff.buffDurationTicks(-25, 2, 500L, true));
    assertEquals(0L, HunterStrengthBuff.buffDurationTicks(25, 0, 500L, true));
  }
}

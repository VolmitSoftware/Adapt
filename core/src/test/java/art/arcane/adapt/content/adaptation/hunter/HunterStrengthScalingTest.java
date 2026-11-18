package art.arcane.adapt.content.adaptation.hunter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HunterStrengthScalingTest {
  @Test
  void strengthDamageBonusMatchesStrengthAmplifierParity() {
    assertEquals(6.0D, HunterStrength.strengthDamageBonus(1), 1.0e-9D);
    assertEquals(9.0D, HunterStrength.strengthDamageBonus(2), 1.0e-9D);
    assertEquals(18.0D, HunterStrength.strengthDamageBonus(5), 1.0e-9D);
  }

  @Test
  void buffDurationTicksScalesWithLevel() {
    assertEquals(25L, HunterStrength.buffDurationTicks(25, 1, 0L, false));
    assertEquals(100L, HunterStrength.buffDurationTicks(25, 4, 0L, false));
    assertEquals(80L, HunterStrength.buffDurationTicks(40, 2, 0L, true));
  }

  @Test
  void buffDurationTicksExtendsRemainingOnlyWhenStacking() {
    assertEquals(80L, HunterStrength.buffDurationTicks(25, 2, 30L, true));
    assertEquals(50L, HunterStrength.buffDurationTicks(25, 2, 30L, false));
  }

  @Test
  void buffDurationTicksClampsNegativeRemaining() {
    assertEquals(25L, HunterStrength.buffDurationTicks(25, 1, -40L, true));
  }

  @Test
  void buffDurationTicksNonPositiveBaseYieldsZero() {
    assertEquals(0L, HunterStrength.buffDurationTicks(0, 3, 500L, true));
    assertEquals(0L, HunterStrength.buffDurationTicks(-25, 2, 500L, true));
    assertEquals(0L, HunterStrength.buffDurationTicks(25, 0, 500L, true));
  }
}

package art.arcane.adapt.content.adaptation.hunter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HunterLuckScalingTest {
  @Test
  void luckBonusMatchesLuckEffectAmplifierParity() {
    assertEquals(2.0D, HunterLuck.luckBonus(1), 1.0e-9D);
    assertEquals(4.0D, HunterLuck.luckBonus(3), 1.0e-9D);
    assertEquals(6.0D, HunterLuck.luckBonus(5), 1.0e-9D);
  }

  @Test
  void unluckAmountMirrorsUnluckEffectAmplifierParity() {
    assertEquals(-6.0D, HunterLuck.unluckAmount(6, 1), 1.0e-9D);
    assertEquals(-2.0D, HunterLuck.unluckAmount(6, 5), 1.0e-9D);
    assertEquals(-1.0D, HunterLuck.unluckAmount(6, 6), 1.0e-9D);
  }

  @Test
  void unluckAmountIsNeutralWhenLevelExceedsBaseByOne() {
    assertEquals(0.0D, HunterLuck.unluckAmount(6, 7), 1.0e-9D);
  }

  @Test
  void buffDurationTicksScalesWithLevel() {
    assertEquals(100L, HunterLuck.buffDurationTicks(100, 1, 0L, false));
    assertEquals(300L, HunterLuck.buffDurationTicks(100, 3, 0L, false));
  }

  @Test
  void buffDurationTicksExtendsRemainingOnlyWhenStacking() {
    assertEquals(260L, HunterLuck.buffDurationTicks(100, 2, 60L, true));
    assertEquals(200L, HunterLuck.buffDurationTicks(100, 2, 60L, false));
  }

  @Test
  void buffDurationTicksClampsNegativeRemaining() {
    assertEquals(100L, HunterLuck.buffDurationTicks(100, 1, -40L, true));
  }

  @Test
  void buffDurationTicksNonPositiveBaseYieldsZero() {
    assertEquals(0L, HunterLuck.buffDurationTicks(0, 3, 500L, true));
    assertEquals(0L, HunterLuck.buffDurationTicks(-50, 2, 500L, true));
    assertEquals(0L, HunterLuck.buffDurationTicks(100, 0, 500L, true));
  }

  @Test
  void penaltyDurationTicksUsesFlatBase() {
    assertEquals(50L, HunterLuck.penaltyDurationTicks(50, 0L, false));
    assertEquals(50L, HunterLuck.penaltyDurationTicks(50, 30L, false));
  }

  @Test
  void penaltyDurationTicksExtendsRemainingOnlyWhenStacking() {
    assertEquals(80L, HunterLuck.penaltyDurationTicks(50, 30L, true));
    assertEquals(50L, HunterLuck.penaltyDurationTicks(50, -10L, true));
  }

  @Test
  void penaltyDurationTicksNonPositiveBaseYieldsZero() {
    assertEquals(0L, HunterLuck.penaltyDurationTicks(0, 500L, true));
    assertEquals(0L, HunterLuck.penaltyDurationTicks(-5, 500L, true));
  }
}

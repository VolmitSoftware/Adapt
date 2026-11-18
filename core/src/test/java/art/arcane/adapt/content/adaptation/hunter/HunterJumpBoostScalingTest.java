package art.arcane.adapt.content.adaptation.hunter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HunterJumpBoostScalingTest {
  @Test
  void jumpStrengthBonusMatchesJumpBoostAmplifierParity() {
    assertEquals(0.2D, HunterJumpBoost.jumpStrengthBonus(1), 1.0e-9D);
    assertEquals(0.3D, HunterJumpBoost.jumpStrengthBonus(2), 1.0e-9D);
    assertEquals(0.6D, HunterJumpBoost.jumpStrengthBonus(5), 1.0e-9D);
  }

  @Test
  void safeFallBonusAddsOneBlockPerAmplifierTier() {
    assertEquals(2.0D, HunterJumpBoost.safeFallBonus(1), 1.0e-9D);
    assertEquals(3.0D, HunterJumpBoost.safeFallBonus(2), 1.0e-9D);
    assertEquals(6.0D, HunterJumpBoost.safeFallBonus(5), 1.0e-9D);
  }

  @Test
  void buffDurationTicksScalesWithLevel() {
    assertEquals(100L, HunterJumpBoost.buffDurationTicks(100, 1, 0L, false));
    assertEquals(300L, HunterJumpBoost.buffDurationTicks(100, 3, 0L, false));
    assertEquals(80L, HunterJumpBoost.buffDurationTicks(40, 2, 0L, true));
  }

  @Test
  void buffDurationTicksExtendsRemainingOnlyWhenStacking() {
    assertEquals(260L, HunterJumpBoost.buffDurationTicks(100, 2, 60L, true));
    assertEquals(200L, HunterJumpBoost.buffDurationTicks(100, 2, 60L, false));
  }

  @Test
  void buffDurationTicksClampsNegativeRemaining() {
    assertEquals(100L, HunterJumpBoost.buffDurationTicks(100, 1, -40L, true));
  }

  @Test
  void buffDurationTicksNonPositiveBaseYieldsZero() {
    assertEquals(0L, HunterJumpBoost.buffDurationTicks(0, 3, 500L, true));
    assertEquals(0L, HunterJumpBoost.buffDurationTicks(-50, 2, 500L, true));
    assertEquals(0L, HunterJumpBoost.buffDurationTicks(100, 0, 500L, true));
  }
}

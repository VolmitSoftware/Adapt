package art.arcane.adapt.content.skill;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillSeaborneTest {
  @Test
  void normalSwimmersRetainAirBasedQualification() {
    assertThat(SkillSeaborne.qualifiesForPassiveSwimXp(true, false, 299, 300, false, false)).isTrue();
    assertThat(SkillSeaborne.qualifiesForPassiveSwimXp(false, true, 299, 300, false, false)).isTrue();
    assertThat(SkillSeaborne.qualifiesForPassiveSwimXp(true, true, 300, 300, false, true)).isFalse();
  }

  @Test
  void waterBreathingRequiresRecentMovementInWater() {
    assertThat(SkillSeaborne.qualifiesForPassiveSwimXp(true, true, 300, 300, true, true)).isTrue();
    assertThat(SkillSeaborne.qualifiesForPassiveSwimXp(true, true, 299, 300, true, false)).isFalse();
    assertThat(SkillSeaborne.qualifiesForPassiveSwimXp(false, true, 299, 300, true, true)).isFalse();
  }

  @Test
  void conduitPowerUsesTheWaterBreathingPath() {
    assertThat(SkillSeaborne.hasWaterBreathingEffect(false, true)).isTrue();
    assertThat(SkillSeaborne.hasWaterBreathingEffect(true, false)).isTrue();
    assertThat(SkillSeaborne.hasWaterBreathingEffect(false, false)).isFalse();
  }

  @Test
  void movementAtTheGraceBoundaryRemainsRecent() {
    assertThat(SkillSeaborne.isRecentWaterMovement(7500L, 10000L, 2500L)).isTrue();
    assertThat(SkillSeaborne.isRecentWaterMovement(7499L, 10000L, 2500L)).isFalse();
  }

  @Test
  void missingOrFutureMovementIsNotRecent() {
    assertThat(SkillSeaborne.isRecentWaterMovement(null, 10000L, 2500L)).isFalse();
    assertThat(SkillSeaborne.isRecentWaterMovement(10001L, 10000L, 2500L)).isFalse();
    assertThat(SkillSeaborne.isRecentWaterMovement(10000L, 10000L, -1L)).isFalse();
  }

  @Test
  void defaultWaterBreathingBonusDoublesPassiveXp() {
    assertThat(SkillSeaborne.passiveSwimXp(0.4D, 1D, false, 1D)).isEqualTo(0.4D);
    assertThat(SkillSeaborne.passiveSwimXp(0.4D, 1D, true, 1D)).isEqualTo(0.8D);
    assertThat(SkillSeaborne.passiveSwimXp(0.4D, 2D, true, 1D)).isEqualTo(1.6D);
  }

  @Test
  void nonPositiveBonusCannotReduceBaseXp() {
    assertThat(SkillSeaborne.passiveSwimXp(0.4D, 1D, true, 0D)).isEqualTo(0.4D);
    assertThat(SkillSeaborne.passiveSwimXp(0.4D, 1D, true, -2D)).isEqualTo(0.4D);
  }

  @Test
  void nonFiniteBonusFallsBackToBaseXp() {
    assertThat(SkillSeaborne.passiveSwimXp(0.4D, 1D, true, Double.NaN)).isEqualTo(0.4D);
    assertThat(SkillSeaborne.passiveSwimXp(0.4D, 1D, true, Double.POSITIVE_INFINITY)).isEqualTo(0.4D);
  }
}

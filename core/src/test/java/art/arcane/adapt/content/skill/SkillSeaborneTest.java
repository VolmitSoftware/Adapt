package art.arcane.adapt.content.skill;

import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.testkit.FakeLiving;
import art.arcane.adapt.testkit.FakeWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillSeaborneTest {
  private static final FakeWorld WORLD = new FakeWorld("world");

  @Test
  void normalSwimmersRequireMovementAndAirLoss() {
    assertThat(SkillSeaborne.qualifiesForPassiveSwimXp(true, false, 299, 300, false, false)).isFalse();
    assertThat(SkillSeaborne.qualifiesForPassiveSwimXp(false, true, 299, 300, false, false)).isFalse();
    assertThat(SkillSeaborne.qualifiesForPassiveSwimXp(true, true, 300, 300, false, true)).isFalse();
    assertThat(SkillSeaborne.qualifiesForPassiveSwimXp(true, true, 299, 300, false, true)).isTrue();
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

  @Test
  void swimDistanceIgnoresVerticalBubbleColumnMovement() {
    assertThat(SkillSeaborne.horizontalDistance(new Loc(WORLD, 0D, 40D, 0D), new Loc(WORLD, 0D, 44D, 0D))).isZero();
    assertThat(SkillSeaborne.horizontalDistance(new Loc(WORLD, 0D, 40D, 0D), new Loc(WORLD, 3D, 41D, 4D))).isEqualTo(5D);
  }

  @Test
  void seaCreatureKillXpAddsAMaxHealthBonus() {
    assertThat(SkillSeaborne.seaCreatureKillXp(150D, 5D, 30D)).isEqualTo(300D);
    assertThat(SkillSeaborne.seaCreatureKillXp(150D, 5D, Double.NaN)).isEqualTo(150D);
    assertThat(SkillSeaborne.seaCreatureKillXp(150D, -5D, 30D)).isEqualTo(150D);
  }

  @Test
  void seaCreaturesComeFromTraitsAndTheAquaticList() {
    Loc at = new Loc(WORLD, 0D, 40D, 0D);
    FakeLiving squid = new FakeLiving(Id.minecraft("squid"), at);
    FakeLiving fish = new FakeLiving(Id.of("somemod", "eel"), at);
    fish.trait(EntityTrait.FISH);
    FakeLiving cow = new FakeLiving(Id.minecraft("cow"), at);

    assertThat(SkillSeaborne.isSeaCreature(squid)).isTrue();
    assertThat(SkillSeaborne.isSeaCreature(new FakeLiving(Id.minecraft("drowned"), at))).isTrue();
    assertThat(SkillSeaborne.isSeaCreature(new FakeLiving(Id.minecraft("axolotl"), at))).isTrue();
    assertThat(SkillSeaborne.isSeaCreature(fish)).isTrue();
    assertThat(SkillSeaborne.isSeaCreature(cow)).isFalse();
  }
}

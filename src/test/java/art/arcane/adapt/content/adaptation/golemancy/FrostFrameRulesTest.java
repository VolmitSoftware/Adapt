package art.arcane.adapt.content.adaptation.golemancy;

import org.bukkit.Location;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class FrostFrameRulesTest {
  private static final List<Integer> HEALTH = List.of(4, 8, 12, 16, 20);
  private static final List<Integer> SNOWBALL = List.of(1, 2, 2, 3, 4);
  private static final List<Double> WARM = List.of(1.0D, 0.5D, 0D, 0D, 0D);
  private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-0000000000f1");
  private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000f2");

  @Test
  void healthAndSnowballDamageFollowTheLevelTables() {
    assertThat(FrostFrameRules.atLevel(HEALTH, 0)).isZero();
    assertThat(FrostFrameRules.atLevel(HEALTH, 1)).isEqualTo(4D);
    assertThat(FrostFrameRules.atLevel(HEALTH, 5)).isEqualTo(20D);
    assertThat(FrostFrameRules.atLevel(HEALTH, 9)).isEqualTo(20D);
    assertThat(FrostFrameRules.atLevel(SNOWBALL, 3)).isEqualTo(2D);
    assertThat(FrostFrameRules.atLevel(List.of(-3), 1)).isZero();
    assertThat(FrostFrameRules.atLevel(Arrays.asList(1, null), 2)).isZero();
  }

  @Test
  void theExtraSnowballFliesAtTheTargetWithALiftAtSnowballSpeed() {
    Vector velocity = FrostFrameRules.snowballVelocity(new Location(null, 0D, 64D, 0D), new Location(null, 10D, 64D, 0D));

    assertThat(velocity.length()).isCloseTo(1.6D, offset(1e-9));
    assertThat(velocity.getX()).isGreaterThan(0D);
    assertThat(velocity.getY()).isCloseTo(velocity.getX() * 0.2D, offset(1e-9));
    assertThat(velocity.getZ()).isZero();
    assertThat(FrostFrameRules.snowballVelocity(new Location(null, 1D, 1D, 1D), new Location(null, 1D, 1D, 1D)))
        .isEqualTo(new Vector(0D, 1.6D, 0D));
  }

  @Test
  void snowballHitsDealTheLevelDamageButNeverLessThanVanilla() {
    assertThat(FrostFrameRules.hitDamage(0D, SNOWBALL, 1)).isEqualTo(1D);
    assertThat(FrostFrameRules.hitDamage(0D, SNOWBALL, 5)).isEqualTo(4D);
    assertThat(FrostFrameRules.hitDamage(3D, SNOWBALL, 2)).isEqualTo(3D);
    assertThat(FrostFrameRules.hitDamage(0D, SNOWBALL, 0)).isZero();
  }

  @Test
  void onlyTheGolemsTargetOrAHostileTakesSnowballEffects() {
    assertThat(FrostFrameRules.affects(TARGET, TARGET, false)).isTrue();
    assertThat(FrostFrameRules.affects(TARGET, OTHER, true)).isTrue();
    assertThat(FrostFrameRules.affects(null, OTHER, true)).isTrue();
    assertThat(FrostFrameRules.affects(TARGET, OTHER, false)).isFalse();
    assertThat(FrostFrameRules.affects(null, OTHER, false)).isFalse();
  }

  @Test
  void onlyExtraSnowballsResetTheDamageImmunityOfTheirTargetOrAHostile() {
    assertThat(FrostFrameRules.resetsImmunity(true, TARGET, TARGET, false)).isTrue();
    assertThat(FrostFrameRules.resetsImmunity(true, TARGET, OTHER, true)).isTrue();
    assertThat(FrostFrameRules.resetsImmunity(true, TARGET, OTHER, false)).isFalse();
    assertThat(FrostFrameRules.resetsImmunity(false, TARGET, TARGET, false)).isFalse();
    assertThat(FrostFrameRules.resetsImmunity(false, TARGET, OTHER, true)).isFalse();
  }

  @Test
  void rainAndWaterNeverHurtAndWarmBiomeDamageShrinksByLevel() {
    assertThat(FrostFrameRules.weatherDamage(DamageCause.DROWNING, 1D, 0, WARM)).isEqualTo(1D);
    assertThat(FrostFrameRules.weatherDamage(DamageCause.DROWNING, 1D, 1, WARM)).isZero();
    assertThat(FrostFrameRules.weatherDamage(DamageCause.MELTING, 1D, 0, WARM)).isEqualTo(1D);
    assertThat(FrostFrameRules.weatherDamage(DamageCause.MELTING, 1D, 1, WARM)).isEqualTo(1D);
    assertThat(FrostFrameRules.weatherDamage(DamageCause.MELTING, 1D, 2, WARM)).isCloseTo(0.5D, offset(1e-9));
    assertThat(FrostFrameRules.weatherDamage(DamageCause.MELTING, 1D, 3, WARM)).isZero();
    assertThat(FrostFrameRules.weatherDamage(DamageCause.MELTING, 1D, 5, WARM)).isZero();
    assertThat(FrostFrameRules.weatherDamage(DamageCause.FIRE, 2D, 5, WARM)).isEqualTo(2D);
    assertThat(FrostFrameRules.weatherDamage(DamageCause.MELTING, 1D, 2, List.of(-1D, -1D))).isZero();
    assertThat(FrostFrameRules.weatherDamage(DamageCause.MELTING, 1D, 2, List.of(Double.NaN, Double.NaN))).isEqualTo(1D);
  }

  @Test
  void chillAndTheExtraSnowballUnlockAtTheirLevels() {
    assertThat(GolemancyRules.unlocked(2, 3)).isFalse();
    assertThat(GolemancyRules.unlocked(3, 3)).isTrue();
    assertThat(GolemancyRules.unlocked(5, 3)).isTrue();
    assertThat(FrostFrameRules.extraShot(3, 4, 0.25D, 0D)).isFalse();
    assertThat(FrostFrameRules.extraShot(4, 4, 0.25D, 0.24D)).isTrue();
    assertThat(FrostFrameRules.extraShot(4, 4, 0.25D, 0.25D)).isFalse();
    assertThat(FrostFrameRules.extraShot(5, 4, 0D, 0D)).isFalse();
  }

  @Test
  void frostbiteFreezesOnTheFourthHitWithinTheWindowThenCoolsDown() {
    FrostFrameRules.FrostbiteState state = null;
    long[] times = {0L, 1_000L, 2_000L};
    for (long time : times) {
      state = FrostFrameRules.frostbite(state, time, 4, 5_000L, 10_000L);
      assertThat(state.triggered()).isFalse();
    }

    state = FrostFrameRules.frostbite(state, 3_000L, 4, 5_000L, 10_000L);

    assertThat(state.triggered()).isTrue();
    assertThat(state.cooldownUntil()).isEqualTo(13_000L);
    for (long time = 4_000L; time < 13_000L; time += 1_000L) {
      state = FrostFrameRules.frostbite(state, time, 4, 5_000L, 10_000L);
      assertThat(state.triggered()).as("hit at %s during the cooldown", time).isFalse();
    }
    for (long time = 13_000L; time < 16_000L; time += 1_000L) {
      state = FrostFrameRules.frostbite(state, time, 4, 5_000L, 10_000L);
      assertThat(state.triggered()).isFalse();
    }
    state = FrostFrameRules.frostbite(state, 16_000L, 4, 5_000L, 10_000L);
    assertThat(state.triggered()).isTrue();
  }

  @Test
  void frostbiteHitsOlderThanTheWindowDoNotCount() {
    FrostFrameRules.FrostbiteState state = null;
    for (long time : new long[] {0L, 2_000L, 4_000L, 6_000L, 8_000L}) {
      state = FrostFrameRules.frostbite(state, time, 4, 5_000L, 10_000L);
      assertThat(state.triggered()).as("hit at %s", time).isFalse();
    }
    assertThat(FrostFrameRules.expired(state, 12_000L, 5_000L)).isFalse();
    assertThat(FrostFrameRules.expired(state, 13_000L, 5_000L)).isTrue();
  }

  @Test
  void aFrozenTargetStaysTrackedUntilItsCooldownEnds() {
    FrostFrameRules.FrostbiteState state = null;
    for (long time : new long[] {0L, 100L, 200L, 300L}) {
      state = FrostFrameRules.frostbite(state, time, 4, 5_000L, 10_000L);
    }

    assertThat(state.triggered()).isTrue();
    assertThat(FrostFrameRules.expired(state, 9_000L, 5_000L)).isFalse();
    assertThat(FrostFrameRules.expired(state, 10_300L, 5_000L)).isTrue();
  }

  @Test
  void aThresholdOfOneFreezesOnEveryHitOutsideTheCooldown() {
    FrostFrameRules.FrostbiteState first = FrostFrameRules.frostbite(null, 0L, 1, 5_000L, 0L);
    FrostFrameRules.FrostbiteState second = FrostFrameRules.frostbite(first, 50L, 1, 5_000L, 0L);

    assertThat(first.triggered()).isTrue();
    assertThat(second.triggered()).isTrue();
  }

  @Test
  void frostbiteKeepsTheTargetFullyFrozenForTheDurationWithoutLoweringALongerFreeze() {
    assertThat(FrostFrameRules.frostbiteFreezeTicks(0, 140, 100)).isEqualTo(340);
    assertThat(FrostFrameRules.frostbiteFreezeTicks(60, 140, 1)).isEqualTo(142);
    assertThat(FrostFrameRules.frostbiteFreezeTicks(500, 140, 100)).isEqualTo(500);
    assertThat(FrostFrameRules.frostbiteFreezeTicks(0, 140, 0)).isEqualTo(142);
    assertThat(FrostFrameRules.frostbiteFreezeTicks(0, 140, Integer.MAX_VALUE)).isEqualTo(Integer.MAX_VALUE);
  }
}

package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.GolemCapacity;
import art.arcane.adapt.api.golem.GolemCoreType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GathererRulesTest {
  private static final List<Integer> RADII = List.of(4, 6, 8, 10, 12);
  private static final List<Integer> CAPS = List.of(0, 500, 1500, 1500, 1500);
  private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
  private static final UUID STRANGER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
  private static final int THIRTY_SECONDS = 600;

  @Test
  void thePickupRadiusFollowsTheLevelAndTreatsLevelZeroAsLevelOne() {
    assertThat(GathererRules.pickupRadius(RADII, 1)).isEqualTo(4D);
    assertThat(GathererRules.pickupRadius(RADII, 3)).isEqualTo(8D);
    assertThat(GathererRules.pickupRadius(RADII, 5)).isEqualTo(12D);
    assertThat(GathererRules.pickupRadius(RADII, 9)).isEqualTo(12D);
    assertThat(GathererRules.pickupRadius(RADII, 0)).isEqualTo(4D);
    assertThat(GathererRules.pickupRadius(List.of(-3), 1)).isZero();
    assertThat(GathererRules.pickupRadius(List.of(), 2)).isZero();
  }

  @Test
  void essenceStorageStartsAtLevelTwoAndGrowsAtLevelThree() {
    assertThat(GathererRules.essenceCap(CAPS, 0)).isZero();
    assertThat(GathererRules.essenceCap(CAPS, 1)).isZero();
    assertThat(GathererRules.essenceCap(CAPS, 2)).isEqualTo(500);
    assertThat(GathererRules.essenceCap(CAPS, 3)).isEqualTo(1500);
    assertThat(GathererRules.essenceCap(CAPS, 7)).isEqualTo(1500);
    assertThat(GathererRules.essenceCap(List.of(-5), 1)).isZero();
    assertThat(GathererRules.essenceCap(List.of(), 3)).isZero();
  }

  @Test
  void perksUnlockAtTheirLevel() {
    assertThat(GathererRules.unlocked(3, 4)).isFalse();
    assertThat(GathererRules.unlocked(4, 4)).isTrue();
    assertThat(GathererRules.unlocked(5, 4)).isTrue();
    assertThat(GathererRules.unlocked(5, 0)).isFalse();
  }

  @Test
  void itemsThrownByAnotherPlayerWaitThirtySecondsAndItemsAnotherPlayerOwnsAreNeverTaken() {
    assertThat(GathererRules.foreign(STRANGER, null, OWNER, 599, THIRTY_SECONDS)).isTrue();
    assertThat(GathererRules.foreign(STRANGER, null, OWNER, 600, THIRTY_SECONDS)).isFalse();
    assertThat(GathererRules.foreign(STRANGER, null, OWNER, 0, 0)).isFalse();
    assertThat(GathererRules.foreign(null, STRANGER, OWNER, 10, THIRTY_SECONDS)).isTrue();
    assertThat(GathererRules.foreign(null, STRANGER, OWNER, 72_000, THIRTY_SECONDS)).isTrue();
    assertThat(GathererRules.foreign(null, STRANGER, OWNER, 0, 0)).isTrue();
    assertThat(GathererRules.foreign(OWNER, OWNER, OWNER, 0, THIRTY_SECONDS)).isFalse();
    assertThat(GathererRules.foreign(STRANGER, OWNER, OWNER, 600, THIRTY_SECONDS)).isFalse();
    assertThat(GathererRules.foreign(null, null, OWNER, 0, THIRTY_SECONDS)).isFalse();
  }

  @Test
  void anItemStillUnderPickupDelayIsSkipped() {
    assertThat(GathererRules.waiting(10)).isTrue();
    assertThat(GathererRules.waiting(Short.MAX_VALUE)).isTrue();
    assertThat(GathererRules.waiting(0)).isFalse();
    assertThat(GathererRules.waiting(-1)).isFalse();
  }

  @Test
  void essenceRoomIsTheCapMinusThePointsStored() {
    assertThat(GathererRules.essenceRoom(0, 500)).isEqualTo(500);
    assertThat(GathererRules.essenceRoom(499, 500)).isEqualTo(1);
    assertThat(GathererRules.essenceRoom(500, 500)).isZero();
    assertThat(GathererRules.essenceRoom(900, 500)).isZero();
    assertThat(GathererRules.essenceRoom(-4, 500)).isEqualTo(500);
    assertThat(GathererRules.essenceRoom(10, 0)).isZero();
  }

  @Test
  void anOrbGivesUpOnlyAsMuchExperienceAsTheGolemHasRoomFor() {
    assertThat(GathererRules.absorbed(7, 500)).isEqualTo(7);
    assertThat(GathererRules.absorbed(70, 30)).isEqualTo(30);
    assertThat(GathererRules.absorbed(70, 0)).isZero();
    assertThat(GathererRules.absorbed(-4, 30)).isZero();
  }

  @Test
  void anOrbsTotalIsItsValueTimesItsCountWithoutOverflow() {
    assertThat(GathererRules.orbTotal(7, 1)).isEqualTo(7);
    assertThat(GathererRules.orbTotal(7, 3)).isEqualTo(21);
    assertThat(GathererRules.orbTotal(7, 0)).isEqualTo(7);
    assertThat(GathererRules.orbTotal(-2, 3)).isZero();
    assertThat(GathererRules.orbTotal(Integer.MAX_VALUE, 4)).isEqualTo(Integer.MAX_VALUE);
  }

  @Test
  void gatherersCarryHalfAgainAsMuchFromLevelFive() {
    List<GolemCoreType> gatherer = List.of(GolemCoreType.GATHERER);

    assertThat(GathererRules.carryBonus(gatherer, 5, 5, 0.5D)).isEqualTo(GolemCapacity.Contribution.bonus(0.5D));
    assertThat(GathererRules.carryBonus(gatherer, 4, 5, 0.5D)).isEqualTo(GolemCapacity.Contribution.NONE);
    assertThat(GathererRules.carryBonus(List.of(GolemCoreType.GUARDIAN), 5, 5, 0.5D)).isEqualTo(GolemCapacity.Contribution.NONE);
    assertThat(GathererRules.carryBonus(List.of(GolemCoreType.GUARDIAN, GolemCoreType.GATHERER), 5, 5, 0.5D))
        .isEqualTo(GolemCapacity.Contribution.bonus(0.5D));
    assertThat(GolemCapacity.carry(16, List.of(GathererRules.carryBonus(gatherer, 5, 5, 0.5D)))).isEqualTo(24);
  }

  @Test
  void statValuesDropTrailingZeros() {
    assertThat(GathererRules.blocks(3D)).isEqualTo("3");
    assertThat(GathererRules.blocks(1.5D)).isEqualTo("1.5");
    assertThat(GathererRules.blocks(0.25D)).isEqualTo("0.25");
  }

  @Test
  void loadClampsKeepRadiiTimesAndFractionsInRange() {
    assertThat(GathererRules.radius(3D, 16D)).isEqualTo(3D);
    assertThat(GathererRules.radius(40D, 16D)).isEqualTo(16D);
    assertThat(GathererRules.radius(-1D, 16D)).isZero();
    assertThat(GathererRules.radius(Double.NaN, 16D)).isZero();
    assertThat(GathererRules.seconds(30, 600)).isEqualTo(30);
    assertThat(GathererRules.seconds(-5, 600)).isZero();
    assertThat(GathererRules.seconds(9000, 3600)).isEqualTo(3600);
    assertThat(GolemancyRules.nonNegative(0.5D)).isEqualTo(0.5D);
    assertThat(GolemancyRules.nonNegative(-0.5D)).isZero();
    assertThat(GolemancyRules.nonNegative(Double.POSITIVE_INFINITY)).isZero();
  }
}

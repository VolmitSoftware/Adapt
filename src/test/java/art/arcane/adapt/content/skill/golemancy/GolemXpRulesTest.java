package art.arcane.adapt.content.skill.golemancy;

import org.bukkit.Raid;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GolemXpRulesTest {
  @Test
  void killValueAddsTheVictimsScaledMaxHealthToTheBase() {
    assertThat(GolemXpRules.killValue(15D, 20D, 1.0D)).isCloseTo(35D, offset(1e-9));
    assertThat(GolemXpRules.killValue(15D, 100D, 0.5D)).isCloseTo(65D, offset(1e-9));
    assertThat(GolemXpRules.killValue(15D, 0D, 1.0D)).isCloseTo(15D, offset(1e-9));
  }

  @Test
  void witnessXpPaysTheWitnessShareOfTheKillValue() {
    assertThat(GolemXpRules.witnessXp(35D, 0.35D, false, 0.3D)).isCloseTo(12.25D, offset(1e-9));
  }

  @Test
  void witnessXpAppliesTheSpawnerMultiplierToSpawnerMobs() {
    assertThat(GolemXpRules.witnessXp(35D, 0.35D, true, 0.3D)).isCloseTo(3.675D, offset(1e-9));
    assertThat(GolemXpRules.witnessXp(35D, 0.35D, true, 1.0D)).isCloseTo(12.25D, offset(1e-9));
    assertThat(GolemXpRules.witnessXp(35D, 0.35D, true, 0D)).isZero();
  }

  @Test
  void witnessXpRejectsInvalidInputs() {
    assertThat(GolemXpRules.witnessXp(-35D, 0.35D, false, 0.3D)).isZero();
    assertThat(GolemXpRules.witnessXp(35D, -0.35D, false, 0.3D)).isZero();
    assertThat(GolemXpRules.witnessXp(Double.NaN, 0.35D, false, 0.3D)).isZero();
    assertThat(GolemXpRules.witnessXp(35D, Double.POSITIVE_INFINITY, false, 0.3D)).isZero();
    assertThat(GolemXpRules.witnessXp(35D, 0.35D, true, Double.NaN)).isZero();
    assertThat(GolemXpRules.killValue(Double.NaN, 20D, 1.0D)).isZero();
  }

  @Test
  void repairXpPaysPerPointOfPaidHealth() {
    assertThat(GolemXpRules.repairXp(25D, 2.0D)).isCloseTo(50D, offset(1e-9));
    assertThat(GolemXpRules.repairXp(0D, 2.0D)).isZero();
    assertThat(GolemXpRules.repairXp(-3D, 2.0D)).isZero();
    assertThat(GolemXpRules.repairXp(25D, Double.NaN)).isZero();
  }

  @Test
  void witnessGolemResolvesIronAndSnowGolemsAndSnowGolemSnowballs() {
    IronGolem iron = mock(IronGolem.class);
    Snowman snow = mock(Snowman.class);
    Snowball snowGolemShot = mock(Snowball.class);
    when(snowGolemShot.getShooter()).thenReturn(snow);
    Snowball playerShot = mock(Snowball.class);
    when(playerShot.getShooter()).thenReturn(mock(Player.class));

    assertThat(GolemXpRules.witnessGolem(iron)).isSameAs(iron);
    assertThat(GolemXpRules.witnessGolem(snow)).isSameAs(snow);
    assertThat(GolemXpRules.witnessGolem(snowGolemShot)).isSameAs(snow);
    assertThat(GolemXpRules.witnessGolem(playerShot)).isNull();
    assertThat(GolemXpRules.witnessGolem(mock(CopperGolem.class))).isNull();
    assertThat(GolemXpRules.witnessGolem(mock(Zombie.class))).isNull();
    assertThat(GolemXpRules.witnessGolem(null)).isNull();
  }

  @Test
  void effectiveDamageExcludesOverkill() {
    assertThat(GolemXpRules.effectiveDamage(10D, 4D)).isCloseTo(4D, offset(1e-9));
    assertThat(GolemXpRules.effectiveDamage(3D, 20D)).isCloseTo(3D, offset(1e-9));
    assertThat(GolemXpRules.effectiveDamage(20D, 20D)).isCloseTo(20D, offset(1e-9));
    assertThat(GolemXpRules.effectiveDamage(5D, 0D)).isZero();
    assertThat(GolemXpRules.effectiveDamage(0D, 20D)).isZero();
    assertThat(GolemXpRules.effectiveDamage(-2D, 20D)).isZero();
    assertThat(GolemXpRules.effectiveDamage(5D, -1D)).isZero();
    assertThat(GolemXpRules.effectiveDamage(Double.NaN, 20D)).isZero();
    assertThat(GolemXpRules.effectiveDamage(5D, Double.NaN)).isZero();
    assertThat(GolemXpRules.effectiveDamage(Double.POSITIVE_INFINITY, 20D)).isCloseTo(20D, offset(1e-9));
  }

  @ParameterizedTest
  @CsvSource({
      "10, 3.0, 2.0, false, 0.3, 60",
      "10, 3.0, 1.0, false, 0.3, 30",
      "10, 3.0, 0.35, false, 0.3, 10.5",
      "10, 3.0, 2.0, true, 0.3, 18",
      "4, 3.0, 1.0, true, 1.0, 12",
      "10, 0, 2.0, false, 0.3, 0",
      "10, 3.0, 0, false, 0.3, 0",
      "0, 3.0, 2.0, false, 0.3, 0"
  })
  void assistXpScalesDealtDamageByTheAssistBodyAndSpawnerMultipliers(double dealt, double assist, double body, boolean spawner,
                                                                     double spawnerMultiplier, double expected) {
    assertThat(GolemXpRules.assistXp(dealt, assist, body, spawner, spawnerMultiplier)).isCloseTo(expected, offset(1e-9));
  }

  @ParameterizedTest
  @CsvSource({
      "35, false, 1.5, false, 0.3, 35",
      "39, true, 1.5, false, 0.3, 58.5",
      "35, false, 1.5, true, 0.3, 10.5",
      "39, true, 1.5, true, 0.3, 17.55",
      "39, true, 1.0, false, 0.3, 39",
      "35, false, 1.5, true, 0, 0"
  })
  void ownedKillXpAppliesTheRaidAndSpawnerMultipliers(double killValue, boolean raid, double raidMultiplier, boolean spawner,
                                                      double spawnerMultiplier, double expected) {
    assertThat(GolemXpRules.ownedKillXp(killValue, raid, raidMultiplier, spawner, spawnerMultiplier)).isCloseTo(expected, offset(1e-9));
  }

  @ParameterizedTest
  @CsvSource({
      "8, 1.5, false, 0.3, 12",
      "8, 1.5, true, 0.3, 3.6",
      "8, 0, false, 0.3, 0",
      "0, 1.5, false, 0.3, 0"
  })
  void tankXpScalesDamageTakenByTheTankMultiplier(double taken, double tank, boolean spawner, double spawnerMultiplier, double expected) {
    assertThat(GolemXpRules.tankXp(taken, tank, spawner, spawnerMultiplier)).isCloseTo(expected, offset(1e-9));
  }

  @Test
  void ownedXpRejectsInvalidInputs() {
    assertThat(GolemXpRules.assistXp(Double.NaN, 3D, 2D, false, 0.3D)).isZero();
    assertThat(GolemXpRules.assistXp(10D, -3D, 2D, false, 0.3D)).isZero();
    assertThat(GolemXpRules.assistXp(10D, 3D, Double.POSITIVE_INFINITY, false, 0.3D)).isZero();
    assertThat(GolemXpRules.assistXp(10D, 3D, 2D, true, Double.NaN)).isZero();
    assertThat(GolemXpRules.ownedKillXp(35D, true, -1.5D, false, 0.3D)).isZero();
    assertThat(GolemXpRules.ownedKillXp(35D, true, Double.NaN, false, 0.3D)).isZero();
    assertThat(GolemXpRules.ownedKillXp(-35D, false, 1.5D, false, 0.3D)).isZero();
    assertThat(GolemXpRules.tankXp(8D, Double.NaN, false, 0.3D)).isZero();
    assertThat(GolemXpRules.tankXp(-8D, 1.5D, false, 0.3D)).isZero();
  }

  @Test
  void ownedKillXpIgnoresAnInvalidRaidMultiplierOutsideRaids() {
    assertThat(GolemXpRules.ownedKillXp(35D, false, Double.NaN, false, 0.3D)).isCloseTo(35D, offset(1e-9));
  }

  @ParameterizedTest
  @CsvSource({
      "0, 48, 0.25, 1.0",
      "2304, 48, 0.25, 1.0",
      "2304.0001, 48, 0.25, 0.25",
      "1000000, 48, 0.25, 0.25",
      "Infinity, 48, 0.25, 0.25",
      "NaN, 48, 0.25, 0.25",
      "100, 0, 0.25, 0.25",
      "0, 0, 0.25, 1.0",
      "100, -5, 0.25, 0.25",
      "100, NaN, 0.25, 0.25",
      "1000000, 48, 0, 0",
      "1000000, 48, -1, 0",
      "1000000, 48, NaN, 0",
      "1000000, 48, 2.0, 1.0"
  })
  void ownerDistanceFactorPaysInFullOnlyWithinTheProximityRadius(double distanceSquared, double radius, double distantMultiplier,
                                                                 double expected) {
    assertThat(GolemXpRules.ownerDistanceFactor(distanceSquared, radius, distantMultiplier)).isCloseTo(expected, offset(1e-9));
  }

  @Test
  void golemSourceResolvesSnowGolemSnowballsToTheirShooter() {
    Snowman snow = mock(Snowman.class);
    Snowball golemShot = mock(Snowball.class);
    when(golemShot.getShooter()).thenReturn(snow);
    Player player = mock(Player.class);
    Snowball playerShot = mock(Snowball.class);
    when(playerShot.getShooter()).thenReturn(player);
    Arrow skeletonArrow = mock(Arrow.class);
    when(skeletonArrow.getShooter()).thenReturn(mock(Skeleton.class));
    IronGolem iron = mock(IronGolem.class);

    assertThat(GolemXpRules.golemSource(golemShot)).isSameAs(snow);
    assertThat(GolemXpRules.golemSource(iron)).isSameAs(iron);
    assertThat(GolemXpRules.golemSource(playerShot)).isSameAs(playerShot);
    assertThat(GolemXpRules.golemSource(skeletonArrow)).isSameAs(skeletonArrow);
    assertThat(GolemXpRules.golemSource(null)).isNull();
  }

  @Test
  void attackerResolvesProjectilesToTheirShootingEntity() {
    Skeleton skeleton = mock(Skeleton.class);
    Arrow arrow = mock(Arrow.class);
    when(arrow.getShooter()).thenReturn(skeleton);
    Arrow dispensed = mock(Arrow.class);
    Zombie zombie = mock(Zombie.class);

    assertThat(GolemXpRules.attacker(arrow)).isSameAs(skeleton);
    assertThat(GolemXpRules.attacker(dispensed)).isSameAs(dispensed);
    assertThat(GolemXpRules.attacker(zombie)).isSameAs(zombie);
    assertThat(GolemXpRules.attacker(null)).isNull();
  }

  @Test
  void inActiveRaidRequiresARaiderInAnOngoingRaid() {
    assertThat(GolemXpRules.inActiveRaid(raider(Raid.RaidStatus.ONGOING))).isTrue();
    assertThat(GolemXpRules.inActiveRaid(raider(Raid.RaidStatus.VICTORY))).isFalse();
    assertThat(GolemXpRules.inActiveRaid(raider(Raid.RaidStatus.LOSS))).isFalse();
    assertThat(GolemXpRules.inActiveRaid(raider(Raid.RaidStatus.STOPPED))).isFalse();
    Pillager patrol = mock(Pillager.class);
    when(patrol.isPatrolLeader()).thenReturn(true);
    assertThat(GolemXpRules.inActiveRaid(patrol)).isFalse();
    assertThat(GolemXpRules.inActiveRaid(mock(Zombie.class))).isFalse();
    assertThat(GolemXpRules.inActiveRaid(null)).isFalse();
  }

  private static Pillager raider(Raid.RaidStatus status) {
    Raid raid = mock(Raid.class);
    when(raid.getStatus()).thenReturn(status);
    Pillager pillager = mock(Pillager.class);
    when(pillager.getRaid()).thenReturn(raid);
    return pillager;
  }
}

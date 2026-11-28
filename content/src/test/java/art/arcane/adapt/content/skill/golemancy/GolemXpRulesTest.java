package art.arcane.adapt.content.skill.golemancy;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.platform.entity.AnimalOps;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.ProjectileRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GolemXpRulesTest extends AdaptTestBase {
  @Test
  void killValueAddsTheVictimsScaledMaxHealthToTheBase() {
    assertThat(GolemXpRules.killValue(15D, 20D, 1.0D)).isCloseTo(35D, offset(1e-9));
    assertThat(GolemXpRules.killValue(15D, 100D, 0.5D)).isCloseTo(65D, offset(1e-9));
    assertThat(GolemXpRules.killValue(15D, 0D, 1.0D)).isCloseTo(15D, offset(1e-9));
  }

  @Test
  void witnessXpPaysTheWitnessShareOfTheKillValue() {
    assertThat(GolemXpRules.witnessXp(35D, 0.35D)).isCloseTo(12.25D, offset(1e-9));
  }

  @Test
  void witnessXpRejectsInvalidInputs() {
    assertThat(GolemXpRules.witnessXp(-35D, 0.35D)).isZero();
    assertThat(GolemXpRules.witnessXp(35D, -0.35D)).isZero();
    assertThat(GolemXpRules.witnessXp(Double.NaN, 0.35D)).isZero();
    assertThat(GolemXpRules.witnessXp(35D, Double.POSITIVE_INFINITY)).isZero();
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
    MobRef iron = mob("iron_golem");
    MobRef snow = mob("snow_golem");
    ProjectileRef snowGolemShot = projectile("snowball", snow);
    ProjectileRef playerShot = projectile("snowball", mock(PlayerRef.class));

    assertThat(GolemXpRules.witnessGolem(iron)).isSameAs(iron);
    assertThat(GolemXpRules.witnessGolem(snow)).isSameAs(snow);
    assertThat(GolemXpRules.witnessGolem(snowGolemShot)).isSameAs(snow);
    assertThat(GolemXpRules.witnessGolem(playerShot)).isNull();
    assertThat(GolemXpRules.witnessGolem(mob("copper_golem"))).isNull();
    assertThat(GolemXpRules.witnessGolem(mob("zombie"))).isNull();
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
      "10, 3.0, 2.0, 60",
      "10, 3.0, 1.0, 30",
      "10, 3.0, 0.35, 10.5",
      "4, 3.0, 1.0, 12",
      "10, 0, 2.0, 0",
      "10, 3.0, 0, 0",
      "0, 3.0, 2.0, 0"
  })
  void assistXpScalesDealtDamageByTheAssistAndBodyMultipliers(double dealt, double assist, double body, double expected) {
    assertThat(GolemXpRules.assistXp(dealt, assist, body)).isCloseTo(expected, offset(1e-9));
  }

  @ParameterizedTest
  @CsvSource({
      "35, false, 1.5, 35",
      "39, true, 1.5, 58.5",
      "39, true, 1.0, 39"
  })
  void ownedKillXpAppliesTheRaidMultiplier(double killValue, boolean raid, double raidMultiplier, double expected) {
    assertThat(GolemXpRules.ownedKillXp(killValue, raid, raidMultiplier)).isCloseTo(expected, offset(1e-9));
  }

  @ParameterizedTest
  @CsvSource({
      "8, 1.5, 12",
      "8, 0, 0",
      "0, 1.5, 0"
  })
  void tankXpScalesDamageTakenByTheTankMultiplier(double taken, double tank, double expected) {
    assertThat(GolemXpRules.tankXp(taken, tank)).isCloseTo(expected, offset(1e-9));
  }

  @Test
  void ownedXpRejectsInvalidInputs() {
    assertThat(GolemXpRules.assistXp(Double.NaN, 3D, 2D)).isZero();
    assertThat(GolemXpRules.assistXp(10D, -3D, 2D)).isZero();
    assertThat(GolemXpRules.assistXp(10D, 3D, Double.POSITIVE_INFINITY)).isZero();
    assertThat(GolemXpRules.ownedKillXp(35D, true, -1.5D)).isZero();
    assertThat(GolemXpRules.ownedKillXp(35D, true, Double.NaN)).isZero();
    assertThat(GolemXpRules.ownedKillXp(-35D, false, 1.5D)).isZero();
    assertThat(GolemXpRules.tankXp(8D, Double.NaN)).isZero();
    assertThat(GolemXpRules.tankXp(-8D, 1.5D)).isZero();
  }

  @Test
  void ownedKillXpIgnoresAnInvalidRaidMultiplierOutsideRaids() {
    assertThat(GolemXpRules.ownedKillXp(35D, false, Double.NaN)).isCloseTo(35D, offset(1e-9));
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
    MobRef snow = mob("snow_golem");
    ProjectileRef golemShot = projectile("snowball", snow);
    PlayerRef player = mock(PlayerRef.class);
    ProjectileRef playerShot = projectile("snowball", player);
    ProjectileRef skeletonArrow = projectile("arrow", mob("skeleton"));
    MobRef iron = mob("iron_golem");

    assertThat(GolemXpRules.golemSource(golemShot)).isSameAs(snow);
    assertThat(GolemXpRules.golemSource(iron)).isSameAs(iron);
    assertThat(GolemXpRules.golemSource(playerShot)).isSameAs(playerShot);
    assertThat(GolemXpRules.golemSource(skeletonArrow)).isSameAs(skeletonArrow);
    assertThat(GolemXpRules.golemSource(null)).isNull();
  }

  @Test
  void attackerResolvesProjectilesToTheirShootingEntity() {
    MobRef skeleton = mob("skeleton");
    ProjectileRef arrow = projectile("arrow", skeleton);
    ProjectileRef dispensed = projectile("arrow", null);
    MobRef zombie = mob("zombie");

    assertThat(GolemXpRules.attacker(arrow)).isSameAs(skeleton);
    assertThat(GolemXpRules.attacker(dispensed)).isSameAs(dispensed);
    assertThat(GolemXpRules.attacker(zombie)).isSameAs(zombie);
    assertThat(GolemXpRules.attacker(null)).isNull();
  }

  @Test
  void inActiveRaidRequiresARaiderInAnOngoingRaid() {
    AnimalOps animals = mock(AnimalOps.class);
    when(AdaptCore.platform().host().animals()).thenReturn(animals);
    assertThat(GolemXpRules.inActiveRaid(raider(animals, RaidStatus.ONGOING))).isTrue();
    assertThat(GolemXpRules.inActiveRaid(raider(animals, RaidStatus.VICTORY))).isFalse();
    assertThat(GolemXpRules.inActiveRaid(raider(animals, RaidStatus.LOSS))).isFalse();
    assertThat(GolemXpRules.inActiveRaid(raider(animals, RaidStatus.STOPPED))).isFalse();
    MobRef patrol = mob("pillager");
    assertThat(GolemXpRules.inActiveRaid(patrol)).isFalse();
    assertThat(GolemXpRules.inActiveRaid(mob("zombie"))).isFalse();
    assertThat(GolemXpRules.inActiveRaid(null)).isFalse();
  }

  private static MobRef raider(AnimalOps animals, RaidStatus status) {
    MobRef pillager = mob("pillager");
    when(animals.inActiveRaid(pillager)).thenReturn(status == RaidStatus.ONGOING);
    return pillager;
  }

  private static MobRef mob(String type) {
    MobRef mob = mock(MobRef.class);
    when(mob.type()).thenReturn(Id.minecraft(type));
    return mob;
  }

  private static ProjectileRef projectile(String type, EntityRef shooter) {
    ProjectileRef projectile = mock(ProjectileRef.class);
    when(projectile.type()).thenReturn(Id.minecraft(type));
    when(projectile.shooter()).thenReturn(shooter);
    return projectile;
  }

  private enum RaidStatus {
    ONGOING,
    VICTORY,
    LOSS,
    STOPPED
  }
}

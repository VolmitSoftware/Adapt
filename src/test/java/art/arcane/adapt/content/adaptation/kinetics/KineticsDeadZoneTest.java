package art.arcane.adapt.content.adaptation.kinetics;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KineticsDeadZoneTest {
  @Test
  void configDefaultsAreSane() {
    KineticsDeadZone.Config config = new KineticsDeadZone.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isGreaterThan(1);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
  }

  @Test
  void configMatchesCardValues() {
    KineticsDeadZone.Config config = new KineticsDeadZone.Config();
    assertThat(config.maxLevel).isEqualTo(3);
    assertThat(config.deadZoneRangeBase).isEqualTo(2.0D);
    assertThat(config.deadZoneRangeFactor).isEqualTo(1.0D);
    assertThat(config.shoveForceBase).isEqualTo(0.5D);
    assertThat(config.shoveForceFactor).isEqualTo(0.6D);
    assertThat(config.riposteWindowTicks).isEqualTo(30);
    assertThat(config.riposteBonusBase).isEqualTo(0.2D);
    assertThat(config.riposteBonusFactor).isEqualTo(0.4D);
    assertThat(config.cooldownMs).isEqualTo(3000L);
  }

  @Test
  void deadZoneRangeGrowsWithLevel() {
    KineticsDeadZone.Config config = new KineticsDeadZone.Config();
    double rangeAtLevelOne = config.deadZoneRangeBase + (levelPercent(1, config.maxLevel) * config.deadZoneRangeFactor);
    double rangeAtMaxLevel = config.deadZoneRangeBase + (levelPercent(config.maxLevel, config.maxLevel) * config.deadZoneRangeFactor);
    assertThat(rangeAtLevelOne).isGreaterThan(0D);
    assertThat(rangeAtMaxLevel).isGreaterThan(rangeAtLevelOne);
    assertThat(rangeAtMaxLevel).isCloseTo(3.0D, offset(1e-9));
  }

  @Test
  void shoveForceGrowsWithLevel() {
    KineticsDeadZone.Config config = new KineticsDeadZone.Config();
    double forceAtLevelOne = config.shoveForceBase + (levelPercent(1, config.maxLevel) * config.shoveForceFactor);
    double forceAtMaxLevel = config.shoveForceBase + (levelPercent(config.maxLevel, config.maxLevel) * config.shoveForceFactor);
    assertThat(forceAtLevelOne).isGreaterThan(0D);
    assertThat(forceAtMaxLevel).isGreaterThan(forceAtLevelOne);
  }

  @Test
  void riposteBonusGrowsWithLevel() {
    KineticsDeadZone.Config config = new KineticsDeadZone.Config();
    double bonusAtLevelOne = config.riposteBonusBase + (levelPercent(1, config.maxLevel) * config.riposteBonusFactor);
    double bonusAtMaxLevel = config.riposteBonusBase + (levelPercent(config.maxLevel, config.maxLevel) * config.riposteBonusFactor);
    assertThat(bonusAtLevelOne).isGreaterThan(0D);
    assertThat(bonusAtMaxLevel).isGreaterThan(bonusAtLevelOne);
    assertThat(bonusAtMaxLevel).isCloseTo(0.6D, offset(1e-9));
  }

  @Test
  void damageHandlerRunsAtHighestPriorityAndIgnoresCancelled() throws ReflectiveOperationException {
    Method handler = KineticsDeadZone.class.getDeclaredMethod("on", EntityDamageByEntityEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void inDeadZoneAcceptsDistancesWithinRange() {
    assertThat(KineticsDeadZone.inDeadZone(1.5D, 2.0D)).isTrue();
    assertThat(KineticsDeadZone.inDeadZone(0D, 2.0D)).isTrue();
    assertThat(KineticsDeadZone.inDeadZone(2.0D, 2.0D)).isTrue();
  }

  @Test
  void inDeadZoneRejectsDistancesBeyondRange() {
    assertThat(KineticsDeadZone.inDeadZone(2.01D, 2.0D)).isFalse();
    assertThat(KineticsDeadZone.inDeadZone(10D, 3.0D)).isFalse();
  }

  @Test
  void inDeadZoneRejectsInvalidInputs() {
    assertThat(KineticsDeadZone.inDeadZone(Double.NaN, 2.0D)).isFalse();
    assertThat(KineticsDeadZone.inDeadZone(1.0D, Double.NaN)).isFalse();
    assertThat(KineticsDeadZone.inDeadZone(-1.0D, 2.0D)).isFalse();
    assertThat(KineticsDeadZone.inDeadZone(1.0D, 0D)).isFalse();
    assertThat(KineticsDeadZone.inDeadZone(1.0D, -2.0D)).isFalse();
  }

  @Test
  void riposteActiveWithinDeadline() {
    assertThat(KineticsDeadZone.riposteActive(1000L, 1500L)).isTrue();
    assertThat(KineticsDeadZone.riposteActive(1500L, 1500L)).isTrue();
  }

  @Test
  void riposteInactiveAfterDeadline() {
    assertThat(KineticsDeadZone.riposteActive(1501L, 1500L)).isFalse();
  }

  @Test
  void riposteInactiveWithoutDeadline() {
    assertThat(KineticsDeadZone.riposteActive(1000L, 0L)).isFalse();
    assertThat(KineticsDeadZone.riposteActive(1000L, -50L)).isFalse();
  }

  @Test
  void shoveVectorPushesHorizontallyAwayFromDefender() {
    Vector defender = new Vector(0D, 64D, 0D);
    Vector attacker = new Vector(2D, 64D, 0D);
    Vector shove = KineticsDeadZone.shoveVector(defender, attacker, 0.5D);
    assertThat(shove.getX()).isCloseTo(0.5D, offset(1e-9));
    assertThat(shove.getY()).isCloseTo(0.25D, offset(1e-9));
    assertThat(shove.getZ()).isCloseTo(0D, offset(1e-9));
  }

  @Test
  void shoveVectorNormalizesDiagonalDirections() {
    Vector defender = new Vector(0D, 64D, 0D);
    Vector attacker = new Vector(1D, 64D, 1D);
    Vector shove = KineticsDeadZone.shoveVector(defender, attacker, 1.0D);
    double horizontal = Math.sqrt((shove.getX() * shove.getX()) + (shove.getZ() * shove.getZ()));
    assertThat(horizontal).isCloseTo(1.0D, offset(1e-9));
    assertThat(shove.getY()).isCloseTo(0.25D, offset(1e-9));
  }

  @Test
  void shoveVectorHandlesOverlappingPositions() {
    Vector defender = new Vector(3D, 64D, 3D);
    Vector attacker = new Vector(3D, 64D, 3D);
    Vector shove = KineticsDeadZone.shoveVector(defender, attacker, 0.8D);
    double horizontal = Math.sqrt((shove.getX() * shove.getX()) + (shove.getZ() * shove.getZ()));
    assertThat(horizontal).isCloseTo(0.8D, offset(1e-9));
    assertThat(shove.getY()).isCloseTo(0.25D, offset(1e-9));
  }

  @Test
  void shoveVectorClampsNegativeForceToZero() {
    Vector defender = new Vector(0D, 64D, 0D);
    Vector attacker = new Vector(2D, 64D, 0D);
    Vector shove = KineticsDeadZone.shoveVector(defender, attacker, -1.0D);
    assertThat(shove.getX()).isCloseTo(0D, offset(1e-9));
    assertThat(shove.getZ()).isCloseTo(0D, offset(1e-9));
    assertThat(shove.getY()).isCloseTo(0.25D, offset(1e-9));
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}

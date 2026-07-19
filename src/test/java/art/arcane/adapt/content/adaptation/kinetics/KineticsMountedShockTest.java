package art.arcane.adapt.content.adaptation.kinetics;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KineticsMountedShockTest {
  @Test
  void configDefaultsAreSane() {
    KineticsMountedShock.Config config = new KineticsMountedShock.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isGreaterThan(1);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
  }

  @Test
  void configMatchesCardValues() {
    KineticsMountedShock.Config config = new KineticsMountedShock.Config();
    assertThat(config.maxLevel).isEqualTo(5);
    assertThat(config.mountSpeedFactorBase).isEqualTo(1.0D);
    assertThat(config.mountSpeedFactorFactor).isEqualTo(1.5D);
    assertThat(config.bonusCapBase).isEqualTo(0.4D);
    assertThat(config.bonusCapFactor).isEqualTo(0.6D);
    assertThat(config.cooldownMs).isEqualTo(2000L);
  }

  @Test
  void mountSpeedFactorGrowsWithLevel() {
    KineticsMountedShock.Config config = new KineticsMountedShock.Config();
    double factorAtLevelOne = config.mountSpeedFactorBase + (levelPercent(1, config.maxLevel) * config.mountSpeedFactorFactor);
    double factorAtMaxLevel = config.mountSpeedFactorBase + (levelPercent(config.maxLevel, config.maxLevel) * config.mountSpeedFactorFactor);
    assertThat(factorAtLevelOne).isGreaterThan(0D);
    assertThat(factorAtMaxLevel).isGreaterThan(factorAtLevelOne);
    assertThat(factorAtMaxLevel).isCloseTo(2.5D, offset(1e-9));
  }

  @Test
  void bonusCapGrowsWithLevel() {
    KineticsMountedShock.Config config = new KineticsMountedShock.Config();
    double capAtLevelOne = config.bonusCapBase + (levelPercent(1, config.maxLevel) * config.bonusCapFactor);
    double capAtMaxLevel = config.bonusCapBase + (levelPercent(config.maxLevel, config.maxLevel) * config.bonusCapFactor);
    assertThat(capAtLevelOne).isGreaterThan(0D);
    assertThat(capAtMaxLevel).isGreaterThan(capAtLevelOne);
    assertThat(capAtMaxLevel).isCloseTo(1.0D, offset(1e-9));
  }

  @Test
  void damageHandlerRunsAtHighestPriorityAndIgnoresCancelled() throws ReflectiveOperationException {
    Method handler = KineticsMountedShock.class.getDeclaredMethod("on", EntityDamageByEntityEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void mountedBonusScalesWithSpeed() {
    assertThat(KineticsMountedShock.mountedBonus(0.3D, 1.0D, 0.4D)).isCloseTo(0.3D, offset(1e-9));
    assertThat(KineticsMountedShock.mountedBonus(0.2D, 1.5D, 1.0D)).isCloseTo(0.3D, offset(1e-9));
  }

  @Test
  void mountedBonusSaturatesAtCap() {
    assertThat(KineticsMountedShock.mountedBonus(1.0D, 1.0D, 0.4D)).isCloseTo(0.4D, offset(1e-9));
    assertThat(KineticsMountedShock.mountedBonus(5.0D, 2.5D, 1.0D)).isCloseTo(1.0D, offset(1e-9));
  }

  @Test
  void mountedBonusIsZeroForStationaryMount() {
    assertThat(KineticsMountedShock.mountedBonus(0D, 1.0D, 0.4D)).isEqualTo(0D);
  }

  @Test
  void mountedBonusRejectsNegativeSpeed() {
    assertThat(KineticsMountedShock.mountedBonus(-0.5D, 1.0D, 0.4D)).isEqualTo(0D);
  }

  @Test
  void mountedBonusRejectsNonFiniteInputs() {
    assertThat(KineticsMountedShock.mountedBonus(Double.NaN, 1.0D, 0.4D)).isEqualTo(0D);
    assertThat(KineticsMountedShock.mountedBonus(0.3D, Double.NaN, 0.4D)).isEqualTo(0D);
    assertThat(KineticsMountedShock.mountedBonus(0.3D, 1.0D, Double.NaN)).isEqualTo(0D);
    assertThat(KineticsMountedShock.mountedBonus(Double.POSITIVE_INFINITY, 1.0D, 0.4D)).isEqualTo(0D);
  }

  @Test
  void mountedBonusNeverGoesNegativeWithNegativeCap() {
    assertThat(KineticsMountedShock.mountedBonus(0.3D, 1.0D, -1.0D)).isEqualTo(0D);
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}

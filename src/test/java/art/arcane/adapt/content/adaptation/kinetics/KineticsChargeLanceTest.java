package art.arcane.adapt.content.adaptation.kinetics;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class KineticsChargeLanceTest {
  @Test
  void configDefaultsAreSane() {
    KineticsChargeLance.Config config = new KineticsChargeLance.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isGreaterThan(1);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
    assertThat(config.minSpeed).isGreaterThan(0D);
    assertThat(config.cooldownMs).isGreaterThan(0L);
  }

  @Test
  void speedDamageFactorGrowsWithLevel() {
    KineticsChargeLance.Config config = new KineticsChargeLance.Config();
    double factorAtLevelOne = config.speedDamageFactorBase + (levelPercent(1, config.maxLevel) * config.speedDamageFactorFactor);
    double factorAtMaxLevel = config.speedDamageFactorBase + (levelPercent(config.maxLevel, config.maxLevel) * config.speedDamageFactorFactor);
    assertThat(factorAtLevelOne).isGreaterThan(0D);
    assertThat(factorAtMaxLevel).isGreaterThan(factorAtLevelOne);
  }

  @Test
  void bonusCapGrowsWithLevel() {
    KineticsChargeLance.Config config = new KineticsChargeLance.Config();
    double capAtLevelOne = config.bonusCapBase + (levelPercent(1, config.maxLevel) * config.bonusCapFactor);
    double capAtMaxLevel = config.bonusCapBase + (levelPercent(config.maxLevel, config.maxLevel) * config.bonusCapFactor);
    assertThat(capAtLevelOne).isGreaterThan(0D);
    assertThat(capAtMaxLevel).isGreaterThan(capAtLevelOne);
  }

  @Test
  void damageHandlerRunsHighestAndIgnoresCancelled() throws ReflectiveOperationException {
    Method handler = KineticsChargeLance.class.getDeclaredMethod("on", EntityDamageByEntityEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void chargeBonusIsZeroBelowMinSpeed() {
    assertThat(KineticsChargeLance.chargeBonus(0.1D, 0.18D, 1.0D, 1.0D)).isZero();
    assertThat(KineticsChargeLance.chargeBonus(0D, 0.18D, 1.0D, 1.0D)).isZero();
  }

  @Test
  void chargeBonusScalesWithSpeedAtOrAboveMinSpeed() {
    assertThat(KineticsChargeLance.chargeBonus(0.18D, 0.18D, 1.0D, 1.0D)).isCloseTo(0.18D, within(1.0E-9D));
    assertThat(KineticsChargeLance.chargeBonus(0.5D, 0.18D, 0.8D, 1.0D)).isCloseTo(0.4D, within(1.0E-9D));
  }

  @Test
  void chargeBonusIsCappedAtCap() {
    assertThat(KineticsChargeLance.chargeBonus(2.0D, 0.18D, 1.0D, 0.5D)).isEqualTo(0.5D);
    assertThat(KineticsChargeLance.chargeBonus(10.0D, 0.18D, 2.0D, 1.25D)).isEqualTo(1.25D);
  }

  @Test
  void chargeBonusRejectsNonFiniteSpeed() {
    assertThat(KineticsChargeLance.chargeBonus(Double.NaN, 0.18D, 1.0D, 1.0D)).isZero();
    assertThat(KineticsChargeLance.chargeBonus(Double.POSITIVE_INFINITY, 0.18D, 1.0D, 1.0D)).isZero();
    assertThat(KineticsChargeLance.chargeBonus(Double.NEGATIVE_INFINITY, 0.18D, 1.0D, 1.0D)).isZero();
  }

  @Test
  void chargeBonusNeverGoesNegative() {
    assertThat(KineticsChargeLance.chargeBonus(1.0D, 0.18D, -2.0D, 1.0D)).isZero();
    assertThat(KineticsChargeLance.chargeBonus(1.0D, 0.18D, 1.0D, -0.5D)).isZero();
  }

  @Test
  void horizontalSpeedIgnoresVerticalComponent() {
    assertThat(KineticsChargeLance.horizontalSpeed(3.0D, 4.0D)).isEqualTo(5.0D);
    assertThat(KineticsChargeLance.horizontalSpeed(0D, 0D)).isZero();
    assertThat(KineticsChargeLance.horizontalSpeed(-3.0D, -4.0D)).isEqualTo(5.0D);
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}

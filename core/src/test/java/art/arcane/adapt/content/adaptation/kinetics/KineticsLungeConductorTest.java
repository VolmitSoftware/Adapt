package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.EntityLungeEvent;
import art.arcane.adapt.platform.value.Vec3;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KineticsLungeConductorTest {
  @Test
  void configDefaultsAreSane() {
    KineticsLungeConductor.Config config = new KineticsLungeConductor.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isGreaterThan(1);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
  }

  @Test
  void configMatchesCardValues() {
    KineticsLungeConductor.Config config = new KineticsLungeConductor.Config();
    assertThat(config.maxLevel).isEqualTo(3);
    assertThat(config.powerBonusBase).isEqualTo(1D);
    assertThat(config.powerBonusFactor).isEqualTo(2D);
    assertThat(config.dashBoostBase).isEqualTo(0.2D);
    assertThat(config.dashBoostFactor).isEqualTo(0.3D);
    assertThat(config.cooldownMs).isEqualTo(2500L);
  }

  @Test
  void powerBonusGrowsWithLevel() {
    KineticsLungeConductor.Config config = new KineticsLungeConductor.Config();
    int bonusAtLevelOne = (int) Math.round(config.powerBonusBase + (levelPercent(1, config.maxLevel) * config.powerBonusFactor));
    int bonusAtMaxLevel = (int) Math.round(config.powerBonusBase + (levelPercent(config.maxLevel, config.maxLevel) * config.powerBonusFactor));
    assertThat(bonusAtLevelOne).isGreaterThanOrEqualTo(1);
    assertThat(bonusAtMaxLevel).isGreaterThan(bonusAtLevelOne);
  }

  @Test
  void dashBoostGrowsWithLevel() {
    KineticsLungeConductor.Config config = new KineticsLungeConductor.Config();
    double boostAtLevelOne = config.dashBoostBase + (levelPercent(1, config.maxLevel) * config.dashBoostFactor);
    double boostAtMaxLevel = config.dashBoostBase + (levelPercent(config.maxLevel, config.maxLevel) * config.dashBoostFactor);
    assertThat(boostAtLevelOne).isGreaterThan(0D);
    assertThat(boostAtMaxLevel).isGreaterThan(boostAtLevelOne);
    assertThat(boostAtMaxLevel).isCloseTo(0.5D, offset(1e-9));
  }

  @Test
  void powerHandlerRunsAtHighestPriorityAndIgnoresCancelled() throws ReflectiveOperationException {
    Method handler = KineticsLungeConductor.class.getDeclaredMethod("on", EntityLungeEvent.class);
    Handle policy = handler.getAnnotation(Handle.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void finalizerRunsAtMonitorPriorityAndIgnoresCancelled() throws ReflectiveOperationException {
    Method handler = KineticsLungeConductor.class.getDeclaredMethod("finalizeLunge", EntityLungeEvent.class);
    Handle policy = handler.getAnnotation(Handle.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void boostedPowerAddsBonus() {
    assertThat(KineticsLungeConductor.boostedPower(2, 3)).isEqualTo(5);
    assertThat(KineticsLungeConductor.boostedPower(0, 1)).isEqualTo(1);
    assertThat(KineticsLungeConductor.boostedPower(4, 0)).isEqualTo(4);
  }

  @Test
  void boostedPowerIgnoresNegativeBonus() {
    assertThat(KineticsLungeConductor.boostedPower(2, -5)).isEqualTo(2);
  }

  @Test
  void assistVelocityAddsScaledDirection() {
    Vec3 current = new Vec3(0.2D, 0D, 0D);
    Vec3 direction = new Vec3(1D, 0D, 0D);
    Vec3 result = KineticsLungeConductor.assistVelocity(current, direction, 0.3D);
    assertThat(result.x()).isCloseTo(0.5D, offset(1e-9));
    assertThat(result.y()).isCloseTo(0D, offset(1e-9));
    assertThat(result.z()).isCloseTo(0D, offset(1e-9));
  }

  @Test
  void assistVelocityPreservesVerticalComponent() {
    Vec3 current = new Vec3(0.1D, 0.5D, 0.1D);
    Vec3 direction = new Vec3(0D, 1D, 0D);
    Vec3 result = KineticsLungeConductor.assistVelocity(current, direction, 0.5D);
    assertThat(result.y()).isCloseTo(0.5D, offset(1e-9));
  }

  @Test
  void assistVelocityPreservesDownwardMotion() {
    Vec3 current = new Vec3(0D, -0.3D, 0D);
    Vec3 direction = new Vec3(0.5D, 0.1D, 0.5D);
    Vec3 result = KineticsLungeConductor.assistVelocity(current, direction, 0.2D);
    assertThat(result.y()).isCloseTo(-0.3D, offset(1e-9));
  }

  @Test
  void assistVelocityRejectsInvalidOrNegativeBoosts() {
    Vec3 current = new Vec3(0.2D, 0.1D, 0.3D);
    Vec3 direction = new Vec3(1D, 0D, 0D);
    assertThat(KineticsLungeConductor.assistVelocity(current, direction, Double.NaN)).isEqualTo(current);
    assertThat(KineticsLungeConductor.assistVelocity(current, direction, -1D)).isEqualTo(current);
    assertThat(KineticsLungeConductor.assistVelocity(current, null, 0.4D)).isEqualTo(current);
  }

  @Test
  void assistVelocityDoesNotMutateInputs() {
    Vec3 current = new Vec3(0.2D, 0.1D, 0.3D);
    Vec3 direction = new Vec3(1D, 0D, 0D);
    KineticsLungeConductor.assistVelocity(current, direction, 0.4D);
    assertThat(current.x()).isCloseTo(0.2D, offset(1e-9));
    assertThat(current.y()).isCloseTo(0.1D, offset(1e-9));
    assertThat(current.z()).isCloseTo(0.3D, offset(1e-9));
    assertThat(direction.x()).isCloseTo(1D, offset(1e-9));
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}

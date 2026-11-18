package art.arcane.adapt.content.adaptation.kinetics;

import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KineticsSoftCatchTest {
  @Test
  void configDefaultsAreSane() {
    KineticsSoftCatch.Config config = new KineticsSoftCatch.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isEqualTo(5);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
    assertThat(config.reductionBase).isCloseTo(0.35D, offset(1.0E-9D));
    assertThat(config.reductionFactor).isCloseTo(0.45D, offset(1.0E-9D));
    assertThat(config.postBounceGraceTicks).isEqualTo(30);
    assertThat(config.xpPerDamagePrevented).isCloseTo(1.5D, offset(1.0E-9D));
    assertThat(config.xpPerEventCap).isCloseTo(50D, offset(1.0E-9D));
  }

  @Test
  void reductionGrowsWithLevel() {
    KineticsSoftCatch.Config config = new KineticsSoftCatch.Config();
    double atLevelOne = KineticsSoftCatch.reduction(config.reductionBase, config.reductionFactor, levelPercent(1, config.maxLevel));
    double atMaxLevel = KineticsSoftCatch.reduction(config.reductionBase, config.reductionFactor, levelPercent(config.maxLevel, config.maxLevel));
    assertThat(atLevelOne).isCloseTo(0.44D, offset(1.0E-9D));
    assertThat(atMaxLevel).isCloseTo(0.8D, offset(1.0E-9D));
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void reductionClampsToUnitRangeAndRejectsNaN() {
    assertThat(KineticsSoftCatch.reduction(2.0D, 1.0D, 1.0D)).isCloseTo(1.0D, offset(1.0E-9D));
    assertThat(KineticsSoftCatch.reduction(-1.0D, 0D, 0D)).isCloseTo(0D, offset(1.0E-9D));
    assertThat(KineticsSoftCatch.reduction(Double.NaN, 0.45D, 0.5D)).isCloseTo(0D, offset(1.0E-9D));
  }

  @Test
  void reducedDamageScalesByReduction() {
    assertThat(KineticsSoftCatch.reducedDamage(10D, 0.35D)).isCloseTo(6.5D, offset(1.0E-9D));
    assertThat(KineticsSoftCatch.reducedDamage(10D, 1.0D)).isCloseTo(0D, offset(1.0E-9D));
    assertThat(KineticsSoftCatch.reducedDamage(10D, 0D)).isCloseTo(10D, offset(1.0E-9D));
  }

  @Test
  void reducedDamageGuardsBadInputs() {
    assertThat(KineticsSoftCatch.reducedDamage(Double.NaN, 0.5D)).isCloseTo(0D, offset(1.0E-9D));
    assertThat(KineticsSoftCatch.reducedDamage(Double.POSITIVE_INFINITY, 0.5D)).isCloseTo(0D, offset(1.0E-9D));
    assertThat(KineticsSoftCatch.reducedDamage(-4D, 0.5D)).isCloseTo(0D, offset(1.0E-9D));
    assertThat(KineticsSoftCatch.reducedDamage(0D, 0.5D)).isCloseTo(0D, offset(1.0E-9D));
    assertThat(KineticsSoftCatch.reducedDamage(10D, Double.NaN)).isCloseTo(10D, offset(1.0E-9D));
    assertThat(KineticsSoftCatch.reducedDamage(10D, 2.0D)).isCloseTo(0D, offset(1.0E-9D));
    assertThat(KineticsSoftCatch.reducedDamage(10D, -1.0D)).isCloseTo(10D, offset(1.0E-9D));
  }

  @Test
  void rewardXpScalesAndCapsSafely() {
    assertThat(KineticsSoftCatch.rewardXp(10D, 1.5D, 50D)).isCloseTo(15D, offset(1.0E-9D));
    assertThat(KineticsSoftCatch.rewardXp(100D, 1.5D, 50D)).isCloseTo(50D, offset(1.0E-9D));
    assertThat(KineticsSoftCatch.rewardXp(Double.POSITIVE_INFINITY, 1.5D, 50D)).isZero();
    assertThat(KineticsSoftCatch.rewardXp(10D, -1.5D, 50D)).isZero();
    assertThat(KineticsSoftCatch.rewardXp(10D, 1.5D, -50D)).isZero();
  }

  @Test
  void withinGraceHonorsWindowEdges() {
    assertThat(KineticsSoftCatch.withinGrace(1500L, 0L, 30)).isTrue();
    assertThat(KineticsSoftCatch.withinGrace(1501L, 0L, 30)).isFalse();
    assertThat(KineticsSoftCatch.withinGrace(100L, 0L, 30)).isTrue();
    assertThat(KineticsSoftCatch.withinGrace(100L, 0L, 0)).isFalse();
    assertThat(KineticsSoftCatch.withinGrace(100L, 0L, -5)).isFalse();
  }

  @Test
  void isLandingRequiresGroundTouchAfterAirborne() {
    assertThat(KineticsSoftCatch.isLanding(true, true)).isTrue();
    assertThat(KineticsSoftCatch.isLanding(true, false)).isFalse();
    assertThat(KineticsSoftCatch.isLanding(false, true)).isFalse();
    assertThat(KineticsSoftCatch.isLanding(false, false)).isFalse();
  }

  @Test
  void landingSurfacePrefersBedAtPlayerFeet() {
    assertThat(KineticsSoftCatch.landingSurface(Id.minecraft("red_bed"), Id.minecraft("stone"))).isEqualTo(Id.minecraft("red_bed"));
  }

  @Test
  void landingSurfacePrefersPowderSnowAtPlayerFeet() {
    assertThat(KineticsSoftCatch.landingSurface(Id.minecraft("powder_snow"), Id.minecraft("dirt"))).isEqualTo(Id.minecraft("powder_snow"));
  }

  @Test
  void landingSurfaceFallsBackToBlockBelow() {
    assertThat(KineticsSoftCatch.landingSurface(Id.minecraft("air"), Id.minecraft("slime_block"))).isEqualTo(Id.minecraft("slime_block"));
  }

  @Test
  void fallDamageHandlerRunsHighestAndIgnoresCancelled() throws ReflectiveOperationException {
    Method handler = KineticsSoftCatch.class.getDeclaredMethod("on", EntityDamageEvent.class);
    Handle policy = handler.getAnnotation(Handle.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void bounceObserverRunsAtMonitorPriority() throws ReflectiveOperationException {
    Method handler = KineticsSoftCatch.class.getDeclaredMethod("on", PlayerMoveEvent.class);
    Handle policy = handler.getAnnotation(Handle.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}

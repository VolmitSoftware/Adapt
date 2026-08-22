package art.arcane.adapt.content.adaptation.kinetics;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class KineticsImpalePinTest {
  @Test
  void configDefaultsAreSane() {
    KineticsImpalePin.Config config = new KineticsImpalePin.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isGreaterThan(1);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
    assertThat(config.sweetMin).isGreaterThan(0D);
    assertThat(config.sweetMaxBase).isGreaterThan(config.sweetMin);
    assertThat(config.targetCooldownMs).isGreaterThan(0L);
  }

  @Test
  void sweetMaxGrowsWithLevel() {
    KineticsImpalePin.Config config = new KineticsImpalePin.Config();
    double maxAtLevelOne = config.sweetMaxBase + (levelPercent(1, config.maxLevel) * config.sweetMaxFactor);
    double maxAtMaxLevel = config.sweetMaxBase + (levelPercent(config.maxLevel, config.maxLevel) * config.sweetMaxFactor);
    assertThat(maxAtLevelOne).isGreaterThan(config.sweetMin);
    assertThat(maxAtMaxLevel).isGreaterThan(maxAtLevelOne);
  }

  @Test
  void slowTierGrowsWithLevel() {
    KineticsImpalePin.Config config = new KineticsImpalePin.Config();
    int tierAtLevelOne = KineticsImpalePin.slowTier(config.slowTierBase, config.slowTierFactor, levelPercent(1, config.maxLevel));
    int tierAtMaxLevel = KineticsImpalePin.slowTier(config.slowTierBase, config.slowTierFactor, levelPercent(config.maxLevel, config.maxLevel));
    assertThat(tierAtLevelOne).isGreaterThanOrEqualTo(0);
    assertThat(tierAtMaxLevel).isGreaterThan(tierAtLevelOne);
    assertThat(tierAtMaxLevel).isEqualTo(2);
  }

  @Test
  void durationGrowsWithLevel() {
    KineticsImpalePin.Config config = new KineticsImpalePin.Config();
    int durationAtLevelOne = KineticsImpalePin.durationTicks(config.durationTicksBase, config.durationTicksFactor, levelPercent(1, config.maxLevel));
    int durationAtMaxLevel = KineticsImpalePin.durationTicks(config.durationTicksBase, config.durationTicksFactor, levelPercent(config.maxLevel, config.maxLevel));
    assertThat(durationAtLevelOne).isGreaterThan(0);
    assertThat(durationAtMaxLevel).isGreaterThan(durationAtLevelOne);
  }

  @Test
  void damageHandlerRunsHighestAndIgnoresCancelled() throws ReflectiveOperationException {
    Method handler = KineticsImpalePin.class.getDeclaredMethod("on", EntityDamageByEntityEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void inSweetRangeAcceptsInsideBand() {
    assertThat(KineticsImpalePin.inSweetRange(4.0D, 3.0D, 5.0D)).isTrue();
  }

  @Test
  void inSweetRangeBoundariesAreInclusive() {
    assertThat(KineticsImpalePin.inSweetRange(3.0D, 3.0D, 5.0D)).isTrue();
    assertThat(KineticsImpalePin.inSweetRange(5.0D, 3.0D, 5.0D)).isTrue();
  }

  @Test
  void inSweetRangeRejectsOutsideBand() {
    assertThat(KineticsImpalePin.inSweetRange(2.9D, 3.0D, 5.0D)).isFalse();
    assertThat(KineticsImpalePin.inSweetRange(5.1D, 3.0D, 5.0D)).isFalse();
    assertThat(KineticsImpalePin.inSweetRange(0D, 3.0D, 5.0D)).isFalse();
  }

  @Test
  void inSweetRangeRejectsNonFiniteDistance() {
    assertThat(KineticsImpalePin.inSweetRange(Double.NaN, 3.0D, 5.0D)).isFalse();
    assertThat(KineticsImpalePin.inSweetRange(Double.POSITIVE_INFINITY, 3.0D, 5.0D)).isFalse();
    assertThat(KineticsImpalePin.inSweetRange(Double.NEGATIVE_INFINITY, 3.0D, 5.0D)).isFalse();
  }

  @Test
  void rangeDistanceUsesEyeToNearestHitboxPoint() {
    BoundingBox bounds = new BoundingBox(3D, 0D, -0.5D, 4D, 2D, 0.5D);
    double distance = KineticsImpalePin.distanceToBounds(0D, 1.62D, 0D, bounds);
    assertThat(distance).isEqualTo(3D);
  }

  @Test
  void rangeDistanceAccountsForVerticalSeparation() {
    BoundingBox bounds = new BoundingBox(3D, 4D, -0.5D, 4D, 6D, 0.5D);
    double distance = KineticsImpalePin.distanceToBounds(0D, 1D, 0D, bounds);
    assertThat(distance).isCloseTo(Math.sqrt(18D), org.assertj.core.data.Offset.offset(1.0E-9D));
  }

  @Test
  void rangeDistanceRejectsInvalidEyeCoordinates() {
    BoundingBox bounds = new BoundingBox(3D, 0D, -0.5D, 4D, 2D, 0.5D);
    assertThat(KineticsImpalePin.distanceToBounds(Double.NaN, 1D, 0D, bounds)).isNaN();
    assertThat(KineticsImpalePin.distanceToBounds(0D, 1D, 0D, null)).isNaN();
  }

  @Test
  void slowTierNeverGoesNegative() {
    assertThat(KineticsImpalePin.slowTier(-5.0D, 0D, 0D)).isZero();
    assertThat(KineticsImpalePin.slowTier(0D, -2.0D, 1.0D)).isZero();
  }

  @Test
  void durationTicksClampsToMinimum() {
    assertThat(KineticsImpalePin.durationTicks(0D, 0D, 0D)).isEqualTo(10);
    assertThat(KineticsImpalePin.durationTicks(-100D, 0D, 1.0D)).isEqualTo(10);
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}

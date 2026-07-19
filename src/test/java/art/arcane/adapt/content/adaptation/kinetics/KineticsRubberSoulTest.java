package art.arcane.adapt.content.adaptation.kinetics;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KineticsRubberSoulTest {
  @Test
  void configDefaultsAreSane() {
    KineticsRubberSoul.Config config = new KineticsRubberSoul.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isEqualTo(5);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
    assertThat(config.bouncinessBase).isCloseTo(0.15D, offset(1.0E-9D));
    assertThat(config.bouncinessFactor).isCloseTo(0.35D, offset(1.0E-9D));
    assertThat(config.softBlockBonusBase).isCloseTo(0.3D, offset(1.0E-9D));
    assertThat(config.softBlockBonusFactor).isCloseTo(0.5D, offset(1.0E-9D));
    assertThat(config.bonusWindowTicks).isEqualTo(40L);
  }

  @Test
  void bouncinessGrowsWithLevel() {
    KineticsRubberSoul.Config config = new KineticsRubberSoul.Config();
    double atLevelOne = KineticsRubberSoul.bounciness(config.bouncinessBase, config.bouncinessFactor, levelPercent(1, config.maxLevel));
    double atMaxLevel = KineticsRubberSoul.bounciness(config.bouncinessBase, config.bouncinessFactor, levelPercent(config.maxLevel, config.maxLevel));
    assertThat(atLevelOne).isCloseTo(0.22D, offset(1.0E-9D));
    assertThat(atMaxLevel).isCloseTo(0.5D, offset(1.0E-9D));
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void softBlockBonusGrowsWithLevel() {
    KineticsRubberSoul.Config config = new KineticsRubberSoul.Config();
    double atLevelOne = KineticsRubberSoul.softBlockBonus(config.softBlockBonusBase, config.softBlockBonusFactor, levelPercent(1, config.maxLevel));
    double atMaxLevel = KineticsRubberSoul.softBlockBonus(config.softBlockBonusBase, config.softBlockBonusFactor, levelPercent(config.maxLevel, config.maxLevel));
    assertThat(atLevelOne).isCloseTo(0.4D, offset(1.0E-9D));
    assertThat(atMaxLevel).isCloseTo(0.8D, offset(1.0E-9D));
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void scalingClampsNegativeAndNaN() {
    assertThat(KineticsRubberSoul.bounciness(-1.0D, 0D, 0D)).isCloseTo(0D, offset(1.0E-9D));
    assertThat(KineticsRubberSoul.bounciness(Double.NaN, 0.35D, 0.5D)).isCloseTo(0D, offset(1.0E-9D));
    assertThat(KineticsRubberSoul.softBlockBonus(-1.0D, 0D, 0D)).isCloseTo(0D, offset(1.0E-9D));
    assertThat(KineticsRubberSoul.softBlockBonus(Double.NaN, 0.5D, 0.5D)).isCloseTo(0D, offset(1.0E-9D));
  }

  @Test
  void isLandingRequiresGroundTouchAfterAirborne() {
    assertThat(KineticsRubberSoul.isLanding(true, true)).isTrue();
    assertThat(KineticsRubberSoul.isLanding(true, false)).isFalse();
    assertThat(KineticsRubberSoul.isLanding(false, true)).isFalse();
    assertThat(KineticsRubberSoul.isLanding(false, false)).isFalse();
  }

  @Test
  void moveHandlerObservesAtMonitorPriority() throws ReflectiveOperationException {
    Method handler = KineticsRubberSoul.class.getDeclaredMethod("on", PlayerMoveEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void declaresPassiveTick() throws ReflectiveOperationException {
    Method tick = KineticsRubberSoul.class.getDeclaredMethod("onTick");
    assertThat(tick).isNotNull();
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}

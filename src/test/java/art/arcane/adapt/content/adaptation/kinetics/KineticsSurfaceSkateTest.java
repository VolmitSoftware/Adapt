package art.arcane.adapt.content.adaptation.kinetics;

import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KineticsSurfaceSkateTest {
  @Test
  void configDefaultsAreSane() {
    KineticsSurfaceSkate.Config config = new KineticsSurfaceSkate.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isEqualTo(5);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
    assertThat(config.slideFrictionBase).isCloseTo(0.15D, offset(1.0E-9D));
    assertThat(config.slideFrictionFactor).isCloseTo(0.35D, offset(1.0E-9D));
    assertThat(config.gripFrictionBase).isCloseTo(0.2D, offset(1.0E-9D));
    assertThat(config.gripFrictionFactor).isCloseTo(0.4D, offset(1.0E-9D));
  }

  @Test
  void slideFrictionGrowsWithLevel() {
    KineticsSurfaceSkate.Config config = new KineticsSurfaceSkate.Config();
    double atLevelOne = KineticsSurfaceSkate.slideFriction(config.slideFrictionBase, config.slideFrictionFactor, levelPercent(1, config.maxLevel));
    double atMaxLevel = KineticsSurfaceSkate.slideFriction(config.slideFrictionBase, config.slideFrictionFactor, levelPercent(config.maxLevel, config.maxLevel));
    assertThat(atLevelOne).isCloseTo(0.22D, offset(1.0E-9D));
    assertThat(atMaxLevel).isCloseTo(0.5D, offset(1.0E-9D));
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void gripFrictionGrowsWithLevel() {
    KineticsSurfaceSkate.Config config = new KineticsSurfaceSkate.Config();
    double atLevelOne = KineticsSurfaceSkate.gripFriction(config.gripFrictionBase, config.gripFrictionFactor, levelPercent(1, config.maxLevel));
    double atMaxLevel = KineticsSurfaceSkate.gripFriction(config.gripFrictionBase, config.gripFrictionFactor, levelPercent(config.maxLevel, config.maxLevel));
    assertThat(atLevelOne).isCloseTo(0.28D, offset(1.0E-9D));
    assertThat(atMaxLevel).isCloseTo(0.6D, offset(1.0E-9D));
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void frictionScalingClampsNegativeAndNaN() {
    assertThat(KineticsSurfaceSkate.slideFriction(-1.0D, 0D, 0D)).isCloseTo(0D, offset(1.0E-9D));
    assertThat(KineticsSurfaceSkate.slideFriction(Double.NaN, 0.35D, 0.5D)).isCloseTo(0D, offset(1.0E-9D));
    assertThat(KineticsSurfaceSkate.gripFriction(-1.0D, 0D, 0D)).isCloseTo(0D, offset(1.0E-9D));
    assertThat(KineticsSurfaceSkate.gripFriction(Double.NaN, 0.4D, 0.5D)).isCloseTo(0D, offset(1.0E-9D));
  }

  @Test
  void stanceDecisionsRequireCurrentPlayerStateAndActiveLevel() {
    assertThat(KineticsSurfaceSkate.shouldSlide(true, 1)).isTrue();
    assertThat(KineticsSurfaceSkate.shouldSlide(false, 1)).isFalse();
    assertThat(KineticsSurfaceSkate.shouldSlide(true, 0)).isFalse();
    assertThat(KineticsSurfaceSkate.shouldGrip(true, 1)).isTrue();
    assertThat(KineticsSurfaceSkate.shouldGrip(false, 1)).isFalse();
    assertThat(KineticsSurfaceSkate.shouldGrip(true, 0)).isFalse();
  }

  @Test
  void periodicReconciliationIsEnabled() throws ReflectiveOperationException {
    KineticsSurfaceSkate adaptation = new KineticsSurfaceSkate();
    Method handler = KineticsSurfaceSkate.class.getDeclaredMethod("onTick");
    assertThat(handler.getDeclaringClass()).isEqualTo(KineticsSurfaceSkate.class);
    assertThat(adaptation.getInterval()).isEqualTo(1000L);
  }

  @Test
  void sprintHandlerIsRegistered() throws ReflectiveOperationException {
    Method handler = KineticsSurfaceSkate.class.getDeclaredMethod("on", PlayerToggleSprintEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void sneakHandlerIsRegistered() throws ReflectiveOperationException {
    Method handler = KineticsSurfaceSkate.class.getDeclaredMethod("on", PlayerToggleSneakEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}

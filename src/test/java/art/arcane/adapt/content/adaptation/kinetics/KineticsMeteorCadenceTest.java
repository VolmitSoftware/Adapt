package art.arcane.adapt.content.adaptation.kinetics;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class KineticsMeteorCadenceTest {
  @Test
  void configDefaultsAreSane() {
    KineticsMeteorCadence.Config config = new KineticsMeteorCadence.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isGreaterThan(1);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
  }

  @Test
  void gravityBoostGrowsWithLevel() {
    KineticsMeteorCadence.Config config = new KineticsMeteorCadence.Config();
    double atLevelOne = config.gravityBoostBase + (levelPercent(1, config.maxLevel) * config.gravityBoostFactor);
    double atMaxLevel = config.gravityBoostBase + (levelPercent(config.maxLevel, config.maxLevel) * config.gravityBoostFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void dragCutGrowsWithLevel() {
    KineticsMeteorCadence.Config config = new KineticsMeteorCadence.Config();
    double atLevelOne = config.dragCutBase + (levelPercent(1, config.maxLevel) * config.dragCutFactor);
    double atMaxLevel = config.dragCutBase + (levelPercent(config.maxLevel, config.maxLevel) * config.dragCutFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void isDivingRequiresAirborneDescentSneakAndMace() {
    assertThat(KineticsMeteorCadence.isDiving(false, -0.2D, true, true)).isTrue();
    assertThat(KineticsMeteorCadence.isDiving(true, -0.2D, true, true)).isFalse();
    assertThat(KineticsMeteorCadence.isDiving(false, 0.2D, true, true)).isFalse();
    assertThat(KineticsMeteorCadence.isDiving(false, 0D, true, true)).isFalse();
    assertThat(KineticsMeteorCadence.isDiving(false, -0.2D, false, true)).isFalse();
    assertThat(KineticsMeteorCadence.isDiving(false, -0.2D, true, false)).isFalse();
  }

  @Test
  void isDivingRejectsNaNDelta() {
    assertThat(KineticsMeteorCadence.isDiving(false, Double.NaN, true, true)).isFalse();
  }

  @Test
  void moveHandlerObservesAtMonitorAndIgnoresCancelled() throws ReflectiveOperationException {
    Method handler = KineticsMeteorCadence.class.getDeclaredMethod("on", PlayerMoveEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void sneakHandlerIsRegistered() throws ReflectiveOperationException {
    Method handler = KineticsMeteorCadence.class.getDeclaredMethod("on", PlayerToggleSneakEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}

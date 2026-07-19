package art.arcane.adapt.content.adaptation.kinetics;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class KineticsPhalanxReachTest {
  @Test
  void configDefaultsAreSane() {
    KineticsPhalanxReach.Config config = new KineticsPhalanxReach.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isGreaterThan(1);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
  }

  @Test
  void reachGrowsWithLevel() {
    KineticsPhalanxReach.Config config = new KineticsPhalanxReach.Config();
    double reachAtLevelOne = config.reachBase + (levelPercent(1, config.maxLevel) * config.reachFactor);
    double reachAtMaxLevel = config.reachBase + (levelPercent(config.maxLevel, config.maxLevel) * config.reachFactor);
    assertThat(reachAtLevelOne).isGreaterThan(0D);
    assertThat(reachAtMaxLevel).isGreaterThan(reachAtLevelOne);
  }

  @Test
  void heldItemHandlerRunsAtMonitorAndIgnoresCancelled() throws ReflectiveOperationException {
    Method handler = KineticsPhalanxReach.class.getDeclaredMethod("on", PlayerItemHeldEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void shouldApplyReachRequiresActiveLevelAndSpear() {
    assertThat(KineticsPhalanxReach.shouldApplyReach(1, true)).isTrue();
    assertThat(KineticsPhalanxReach.shouldApplyReach(5, true)).isTrue();
    assertThat(KineticsPhalanxReach.shouldApplyReach(0, true)).isFalse();
    assertThat(KineticsPhalanxReach.shouldApplyReach(-1, true)).isFalse();
    assertThat(KineticsPhalanxReach.shouldApplyReach(3, false)).isFalse();
    assertThat(KineticsPhalanxReach.shouldApplyReach(0, false)).isFalse();
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}

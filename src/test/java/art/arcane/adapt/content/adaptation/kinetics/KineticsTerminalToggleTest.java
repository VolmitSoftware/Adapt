package art.arcane.adapt.content.adaptation.kinetics;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KineticsTerminalToggleTest {
  @Test
  void configDefaultsAreSane() {
    KineticsTerminalToggle.Config config = new KineticsTerminalToggle.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isGreaterThan(1);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
  }

  @Test
  void configMatchesCardValues() {
    KineticsTerminalToggle.Config config = new KineticsTerminalToggle.Config();
    assertThat(config.maxLevel).isEqualTo(3);
    assertThat(config.dragDeltaBase).isCloseTo(0.2D, offset(1e-9));
    assertThat(config.dragDeltaFactor).isCloseTo(0.4D, offset(1e-9));
    assertThat(config.gravityDeltaBase).isCloseTo(0.2D, offset(1e-9));
    assertThat(config.gravityDeltaFactor).isCloseTo(0.4D, offset(1e-9));
    assertThat(config.minAirTicks).isEqualTo(6);
  }

  @Test
  void dragDeltaGrowsWithLevel() {
    KineticsTerminalToggle.Config config = new KineticsTerminalToggle.Config();
    double atLevelOne = config.dragDeltaBase + (levelPercent(1, config.maxLevel) * config.dragDeltaFactor);
    double atMaxLevel = config.dragDeltaBase + (levelPercent(config.maxLevel, config.maxLevel) * config.dragDeltaFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
    assertThat(atMaxLevel).isCloseTo(config.dragDeltaBase + config.dragDeltaFactor, offset(1e-9));
  }

  @Test
  void gravityDeltaGrowsWithLevel() {
    KineticsTerminalToggle.Config config = new KineticsTerminalToggle.Config();
    double atLevelOne = config.gravityDeltaBase + (levelPercent(1, config.maxLevel) * config.gravityDeltaFactor);
    double atMaxLevel = config.gravityDeltaBase + (levelPercent(config.maxLevel, config.maxLevel) * config.gravityDeltaFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void shouldToggleRequiresAirborneBeyondMinAirTicks() {
    assertThat(KineticsTerminalToggle.shouldToggle(true, 20, 6)).isFalse();
    assertThat(KineticsTerminalToggle.shouldToggle(false, 5, 6)).isFalse();
    assertThat(KineticsTerminalToggle.shouldToggle(false, 6, 6)).isTrue();
    assertThat(KineticsTerminalToggle.shouldToggle(false, 20, 6)).isTrue();
    assertThat(KineticsTerminalToggle.shouldToggle(false, 0, 0)).isTrue();
  }

  @Test
  void nextModeAlternatesBetweenDiveAndHang() {
    assertThat(KineticsTerminalToggle.nextMode(KineticsTerminalToggle.MODE_NONE)).isEqualTo(KineticsTerminalToggle.MODE_DIVE);
    assertThat(KineticsTerminalToggle.nextMode(KineticsTerminalToggle.MODE_DIVE)).isEqualTo(KineticsTerminalToggle.MODE_HANG);
    assertThat(KineticsTerminalToggle.nextMode(KineticsTerminalToggle.MODE_HANG)).isEqualTo(KineticsTerminalToggle.MODE_DIVE);
  }

  @Test
  void elapsedAirTicksUsesGameTimeInsteadOfMoveCount() {
    assertThat(KineticsTerminalToggle.elapsedAirTicks(106, 100)).isEqualTo(6);
    assertThat(KineticsTerminalToggle.elapsedAirTicks(100, 100)).isZero();
    assertThat(KineticsTerminalToggle.elapsedAirTicks(99, 100)).isZero();
    assertThat(KineticsTerminalToggle.elapsedAirTicks(100, -1)).isZero();
  }

  @Test
  void diveCutsDragAndBoostsGravity() {
    assertThat(KineticsTerminalToggle.dragAmount(KineticsTerminalToggle.MODE_DIVE, 0.3D)).isCloseTo(-0.3D, offset(1e-9));
    assertThat(KineticsTerminalToggle.gravityAmount(KineticsTerminalToggle.MODE_DIVE, 0.25D)).isCloseTo(0.25D, offset(1e-9));
  }

  @Test
  void hangBoostsDragAndCutsGravity() {
    assertThat(KineticsTerminalToggle.dragAmount(KineticsTerminalToggle.MODE_HANG, 0.3D)).isCloseTo(0.3D, offset(1e-9));
    assertThat(KineticsTerminalToggle.gravityAmount(KineticsTerminalToggle.MODE_HANG, 0.25D)).isCloseTo(-0.25D, offset(1e-9));
  }

  @Test
  void sneakToggleHandlerIsRegistered() throws ReflectiveOperationException {
    Method handler = KineticsTerminalToggle.class.getDeclaredMethod("on", PlayerToggleSneakEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void moveHandlerObservesAtMonitor() throws ReflectiveOperationException {
    Method handler = KineticsTerminalToggle.class.getDeclaredMethod("on", PlayerMoveEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}

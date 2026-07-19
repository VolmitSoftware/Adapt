package art.arcane.adapt.content.adaptation.kinetics;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KineticsHeavyFrameTest {
  @Test
  void configDefaultsAreSane() {
    KineticsHeavyFrame.Config config = new KineticsHeavyFrame.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isGreaterThan(1);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
  }

  @Test
  void configMatchesCardValues() {
    KineticsHeavyFrame.Config config = new KineticsHeavyFrame.Config();
    assertThat(config.maxLevel).isEqualTo(5);
    assertThat(config.kbResistBase).isCloseTo(0.3D, offset(1e-9));
    assertThat(config.kbResistFactor).isCloseTo(0.5D, offset(1e-9));
    assertThat(config.explosionResistBase).isCloseTo(0.3D, offset(1e-9));
    assertThat(config.explosionResistFactor).isCloseTo(0.5D, offset(1e-9));
    assertThat(config.speedPenaltyBase).isCloseTo(0.15D, offset(1e-9));
    assertThat(config.speedPenaltyFactor).isCloseTo(0.15D, offset(1e-9));
  }

  @Test
  void kbResistGrowsWithLevel() {
    KineticsHeavyFrame.Config config = new KineticsHeavyFrame.Config();
    double atLevelOne = config.kbResistBase + (levelPercent(1, config.maxLevel) * config.kbResistFactor);
    double atMaxLevel = config.kbResistBase + (levelPercent(config.maxLevel, config.maxLevel) * config.kbResistFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
    assertThat(atMaxLevel).isCloseTo(config.kbResistBase + config.kbResistFactor, offset(1e-9));
  }

  @Test
  void explosionResistGrowsWithLevel() {
    KineticsHeavyFrame.Config config = new KineticsHeavyFrame.Config();
    double atLevelOne = config.explosionResistBase + (levelPercent(1, config.maxLevel) * config.explosionResistFactor);
    double atMaxLevel = config.explosionResistBase + (levelPercent(config.maxLevel, config.maxLevel) * config.explosionResistFactor);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
  }

  @Test
  void speedPenaltyGrowsWithLevel() {
    KineticsHeavyFrame.Config config = new KineticsHeavyFrame.Config();
    double atLevelOne = config.speedPenaltyBase + (levelPercent(1, config.maxLevel) * config.speedPenaltyFactor);
    double atMaxLevel = config.speedPenaltyBase + (levelPercent(config.maxLevel, config.maxLevel) * config.speedPenaltyFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
    assertThat(atMaxLevel).isLessThan(1D);
  }

  @Test
  void plantEligibleRequiresMaceOrSpear() {
    assertThat(KineticsHeavyFrame.plantEligible(true, false)).isTrue();
    assertThat(KineticsHeavyFrame.plantEligible(false, true)).isTrue();
    assertThat(KineticsHeavyFrame.plantEligible(true, true)).isTrue();
    assertThat(KineticsHeavyFrame.plantEligible(false, false)).isFalse();
  }

  @Test
  void shouldPlantSupportsSelectingAnEligibleWeaponWhileAlreadySneaking() {
    assertThat(KineticsHeavyFrame.shouldPlant(true, true, false, 1)).isTrue();
    assertThat(KineticsHeavyFrame.shouldPlant(true, false, true, 1)).isTrue();
    assertThat(KineticsHeavyFrame.shouldPlant(false, true, false, 1)).isFalse();
    assertThat(KineticsHeavyFrame.shouldPlant(true, false, false, 1)).isFalse();
    assertThat(KineticsHeavyFrame.shouldPlant(true, true, false, 0)).isFalse();
  }

  @Test
  void periodicReconciliationIsEnabled() throws ReflectiveOperationException {
    KineticsHeavyFrame adaptation = new KineticsHeavyFrame();
    Method handler = KineticsHeavyFrame.class.getDeclaredMethod("onTick");
    assertThat(handler.getDeclaringClass()).isEqualTo(KineticsHeavyFrame.class);
    assertThat(adaptation.getInterval()).isEqualTo(1000L);
  }

  @Test
  void sneakToggleHandlerIsRegistered() throws ReflectiveOperationException {
    Method handler = KineticsHeavyFrame.class.getDeclaredMethod("on", PlayerToggleSneakEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  @Test
  void heldItemHandlerObservesAtMonitor() throws ReflectiveOperationException {
    Method handler = KineticsHeavyFrame.class.getDeclaredMethod("on", PlayerItemHeldEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}

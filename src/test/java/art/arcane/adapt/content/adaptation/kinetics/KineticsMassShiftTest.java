package art.arcane.adapt.content.adaptation.kinetics;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.offset;

class KineticsMassShiftTest {
  @Test
  void configDefaultsAreSane() {
    KineticsMassShift.Config config = new KineticsMassShift.Config();
    assertThat(config.enabled).isTrue();
    assertThat(config.maxLevel).isGreaterThan(1);
    assertThat(config.baseCost).isGreaterThan(0);
    assertThat(config.initialCost).isGreaterThan(0);
    assertThat(config.costFactor).isGreaterThan(0D);
  }

  @Test
  void configMatchesCardValues() {
    KineticsMassShift.Config config = new KineticsMassShift.Config();
    assertThat(config.maxLevel).isEqualTo(3);
    assertThat(config.baseCost).isEqualTo(6);
    assertThat(config.initialCost).isEqualTo(5);
    assertThat(config.titanScaleBase).isCloseTo(0.25D, offset(1e-9));
    assertThat(config.titanScaleFactor).isCloseTo(0.35D, offset(1e-9));
    assertThat(config.pocketScaleBase).isCloseTo(0.25D, offset(1e-9));
    assertThat(config.pocketScaleFactor).isCloseTo(0.25D, offset(1e-9));
    assertThat(config.titanSpeedPenalty).isCloseTo(0.15D, offset(1e-9));
    assertThat(config.pocketSpeedBonus).isCloseTo(0.1D, offset(1e-9));
    assertThat(config.durationTicks).isEqualTo(200);
    assertThat(config.cooldownMs).isEqualTo(30000L);
  }

  @Test
  void titanScaleGrowsWithLevel() {
    KineticsMassShift.Config config = new KineticsMassShift.Config();
    double atLevelOne = config.titanScaleBase + (levelPercent(1, config.maxLevel) * config.titanScaleFactor);
    double atMaxLevel = config.titanScaleBase + (levelPercent(config.maxLevel, config.maxLevel) * config.titanScaleFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
    assertThat(atMaxLevel).isCloseTo(config.titanScaleBase + config.titanScaleFactor, offset(1e-9));
  }

  @Test
  void pocketScaleGrowsWithLevel() {
    KineticsMassShift.Config config = new KineticsMassShift.Config();
    double atLevelOne = config.pocketScaleBase + (levelPercent(1, config.maxLevel) * config.pocketScaleFactor);
    double atMaxLevel = config.pocketScaleBase + (levelPercent(config.maxLevel, config.maxLevel) * config.pocketScaleFactor);
    assertThat(atLevelOne).isGreaterThan(0D);
    assertThat(atMaxLevel).isGreaterThan(atLevelOne);
    assertThat(atMaxLevel).isLessThan(1D);
  }

  @Test
  void nextFormCyclesNormalTitanPocket() {
    assertThat(KineticsMassShift.nextForm(KineticsMassShift.FORM_NORMAL)).isEqualTo(KineticsMassShift.FORM_TITAN);
    assertThat(KineticsMassShift.nextForm(KineticsMassShift.FORM_TITAN)).isEqualTo(KineticsMassShift.FORM_POCKET);
    assertThat(KineticsMassShift.nextForm(KineticsMassShift.FORM_POCKET)).isEqualTo(KineticsMassShift.FORM_NORMAL);
  }

  @Test
  void nextFormTreatsUnknownFormsAsPocketExit() {
    assertThat(KineticsMassShift.nextForm(-1)).isEqualTo(KineticsMassShift.FORM_NORMAL);
    assertThat(KineticsMassShift.nextForm(99)).isEqualTo(KineticsMassShift.FORM_NORMAL);
  }

  @Test
  void storedFormCyclesAfterTimedModifiersExpire() {
    KineticsMassShift.Config config = new KineticsMassShift.Config();
    assertThat(config.cooldownMs).isGreaterThan(config.durationTicks * 50L);
    assertThat(KineticsMassShift.nextFormFromStored(KineticsMassShift.FORM_TITAN)).isEqualTo(KineticsMassShift.FORM_POCKET);
    assertThat(KineticsMassShift.nextFormFromStored(KineticsMassShift.FORM_POCKET)).isEqualTo(KineticsMassShift.FORM_NORMAL);
    assertThat(KineticsMassShift.nextFormFromStored(null)).isEqualTo(KineticsMassShift.FORM_TITAN);
  }

  @Test
  void swapHandler_cancelsAtHighestAndIgnoresCancelled() throws ReflectiveOperationException {
    Method handler = KineticsMassShift.class.getDeclaredMethod("on", PlayerSwapHandItemsEvent.class);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.HIGHEST);
    assertThat(policy.ignoreCancelled()).isTrue();
  }

  private static double levelPercent(int level, int maxLevel) {
    return Math.min(Math.max(0D, (double) level / maxLevel), 1D);
  }
}

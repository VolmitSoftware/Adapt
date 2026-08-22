package art.arcane.adapt.content.adaptation.kinetics;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

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
  void lookDirectionSelectsTitanPocketOrNeutral() {
    assertThat(KineticsMassShift.formForPitch(-45F, KineticsMassShift.LOOK_PITCH_THRESHOLD))
        .isEqualTo(KineticsMassShift.FORM_TITAN);
    assertThat(KineticsMassShift.formForPitch(45F, KineticsMassShift.LOOK_PITCH_THRESHOLD))
        .isEqualTo(KineticsMassShift.FORM_POCKET);
    assertThat(KineticsMassShift.formForPitch(0F, KineticsMassShift.LOOK_PITCH_THRESHOLD))
        .isEqualTo(KineticsMassShift.FORM_NORMAL);
  }

  @Test
  void invalidPitchSelectsNeutralAndThresholdIsClamped() {
    assertThat(KineticsMassShift.formForPitch(Float.NaN, KineticsMassShift.LOOK_PITCH_THRESHOLD))
        .isEqualTo(KineticsMassShift.FORM_NORMAL);
    assertThat(KineticsMassShift.formForPitch(-6F, 0F)).isEqualTo(KineticsMassShift.FORM_TITAN);
    assertThat(KineticsMassShift.formForPitch(6F, 0F)).isEqualTo(KineticsMassShift.FORM_POCKET);
  }

  @Test
  void combatAndHealthScalarIsExactlyTwentyPercent() {
    assertThat(KineticsMassShift.combatScalar(KineticsMassShift.FORM_TITAN))
        .isCloseTo(0.2D, offset(1e-9));
    assertThat(KineticsMassShift.combatScalar(KineticsMassShift.FORM_POCKET))
        .isCloseTo(-0.2D, offset(1e-9));
    assertThat(KineticsMassShift.combatScalar(KineticsMassShift.FORM_NORMAL)).isZero();
  }

  @Test
  void formsUseVisibleSpeedEffectsWithoutCombatPotionStacking() {
    assertThat(KineticsMassShift.movementEffect(KineticsMassShift.FORM_TITAN))
        .isEqualTo(KineticsMassShift.FormMovementEffect.SLOWNESS);
    assertThat(KineticsMassShift.movementEffect(KineticsMassShift.FORM_POCKET))
        .isEqualTo(KineticsMassShift.FormMovementEffect.SPEED);
    assertThat(KineticsMassShift.movementEffect(KineticsMassShift.FORM_NORMAL))
        .isEqualTo(KineticsMassShift.FormMovementEffect.NONE);
  }

  @Test
  void cleanupSignatureRejectsStrongerAndForeignEffects() {
    assertThat(KineticsMassShift.isOwnedFormEffectSignature(0, 60, true, false, true)).isTrue();
    assertThat(KineticsMassShift.isOwnedFormEffectSignature(1, 60, true, false, true)).isFalse();
    assertThat(KineticsMassShift.isOwnedFormEffectSignature(0, 600, true, false, true)).isFalse();
    assertThat(KineticsMassShift.isOwnedFormEffectSignature(0, 60, false, false, true)).isFalse();
    assertThat(KineticsMassShift.isOwnedFormEffectSignature(0, 60, true, true, true)).isFalse();
  }

  @Test
  void obsoleteTimedAndMovementConfigurationIsRemoved() {
    Set<String> fields = Arrays.stream(KineticsMassShift.Config.class.getDeclaredFields())
        .map(Field::getName)
        .collect(Collectors.toSet());
    assertThat(fields).doesNotContain("titanSpeedPenalty", "pocketSpeedBonus", "durationTicks", "cooldownMs");
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

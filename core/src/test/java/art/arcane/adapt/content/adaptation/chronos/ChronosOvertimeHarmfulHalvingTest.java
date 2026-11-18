package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.platform.entity.EffectCategory;
import art.arcane.adapt.platform.event.entity.EntityPotionEffectEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChronosOvertimeHarmfulHalvingTest {
  @Test
  void harmfulDurationIsHalvedAtTheDefaultMultiplier() {
    assertThat(ChronosOvertime.shortenedHarmfulDurationTicks(200, 0.5)).isEqualTo(100);
    assertThat(ChronosOvertime.shortenedHarmfulDurationTicks(3600, 0.5)).isEqualTo(1800);
  }

  @Test
  void harmfulDurationRoundsToTheNearestWholeTick() {
    assertThat(ChronosOvertime.shortenedHarmfulDurationTicks(201, 0.5)).isEqualTo(101);
    assertThat(ChronosOvertime.shortenedHarmfulDurationTicks(100, 0.33)).isEqualTo(33);
  }

  @Test
  void harmfulDurationNeverDropsBelowASingleTick() {
    assertThat(ChronosOvertime.shortenedHarmfulDurationTicks(1, 0.5)).isEqualTo(1);
    assertThat(ChronosOvertime.shortenedHarmfulDurationTicks(200, 0)).isEqualTo(1);
  }

  @Test
  void harmfulDurationLeavesEmptyDurationsAlone() {
    assertThat(ChronosOvertime.shortenedHarmfulDurationTicks(0, 0.5)).isEqualTo(0);
    assertThat(ChronosOvertime.shortenedHarmfulDurationTicks(-40, 0.5)).isEqualTo(0);
  }

  @Test
  void harmfulDurationNeverLengthensAnIncomingEffect() {
    assertThat(ChronosOvertime.shortenedHarmfulDurationTicks(200, 1.5)).isEqualTo(200);
    assertThat(ChronosOvertime.shortenedHarmfulDurationTicks(200, Double.NaN)).isEqualTo(200);
    assertThat(ChronosOvertime.shortenedHarmfulDurationTicks(200, Double.POSITIVE_INFINITY)).isEqualTo(200);
  }

  @Test
  void harmfulDurationClampsNegativeMultipliersToTheTickFloor() {
    assertThat(ChronosOvertime.shortenedHarmfulDurationTicks(200, -1)).isEqualTo(1);
  }

  @Test
  void harmfulDurationIsMonotonicWithTheMultiplier() {
    assertThat(ChronosOvertime.shortenedHarmfulDurationTicks(600, 0.25))
        .isLessThan(ChronosOvertime.shortenedHarmfulDurationTicks(600, 0.5));
    assertThat(ChronosOvertime.shortenedHarmfulDurationTicks(600, 0.5))
        .isLessThan(ChronosOvertime.shortenedHarmfulDurationTicks(600, 0.75));
  }

  @Test
  void shorteningOnlyAppliesToHarmfulEffectsAtMaxLevelWhenEnabled() {
    assertThat(ChronosOvertime.shortensHarmfulEffect(
        true, true, EffectCategory.HARMFUL, EntityPotionEffectEvent.Action.ADDED)).isTrue();
    assertThat(ChronosOvertime.shortensHarmfulEffect(
        true, true, EffectCategory.HARMFUL, EntityPotionEffectEvent.Action.CHANGED)).isTrue();
  }

  @Test
  void shorteningIsDisabledBelowMaxLevelOrByConfig() {
    assertThat(ChronosOvertime.shortensHarmfulEffect(
        true, false, EffectCategory.HARMFUL, EntityPotionEffectEvent.Action.ADDED)).isFalse();
    assertThat(ChronosOvertime.shortensHarmfulEffect(
        false, true, EffectCategory.HARMFUL, EntityPotionEffectEvent.Action.ADDED)).isFalse();
  }

  @Test
  void shorteningIgnoresBeneficialAndNeutralCategories() {
    assertThat(ChronosOvertime.shortensHarmfulEffect(
        true, true, EffectCategory.BENEFICIAL, EntityPotionEffectEvent.Action.ADDED)).isFalse();
    assertThat(ChronosOvertime.shortensHarmfulEffect(
        true, true, EffectCategory.NEUTRAL, EntityPotionEffectEvent.Action.ADDED)).isFalse();
    assertThat(ChronosOvertime.shortensHarmfulEffect(
        true, true, null, EntityPotionEffectEvent.Action.ADDED)).isFalse();
  }

  @Test
  void shorteningIgnoresRemovalAndClearActions() {
    assertThat(ChronosOvertime.shortensHarmfulEffect(
        true, true, EffectCategory.HARMFUL, EntityPotionEffectEvent.Action.REMOVED)).isFalse();
    assertThat(ChronosOvertime.shortensHarmfulEffect(
        true, true, EffectCategory.HARMFUL, EntityPotionEffectEvent.Action.CLEARED)).isFalse();
  }
}

package art.arcane.adapt.content.skill;

import art.arcane.adapt.api.adaptation.ReceiveCancelledEvents;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.entity.AreaEffectCloudApplyEvent;
import art.arcane.adapt.platform.event.entity.PotionSplashEvent;
import art.arcane.adapt.platform.event.player.PlayerItemConsumeEvent;
import art.arcane.adapt.platform.event.world.NightSkipEvent;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SkillChronosTest {
  @Test
  void offhandClockTakesPrecedenceOverInventoryClock() {
    assertThat(SkillChronos.resolveClockXpMultiplier(true, true, 3D, 2D)).isEqualTo(3D);
    assertThat(SkillChronos.resolveClockXpMultiplier(true, false, 3D, 2D)).isEqualTo(3D);
    assertThat(SkillChronos.resolveClockXpMultiplier(false, true, 3D, 2D)).isEqualTo(2D);
    assertThat(SkillChronos.resolveClockXpMultiplier(false, false, 3D, 2D)).isEqualTo(1D);
  }

  @Test
  void invalidClockMultipliersFailSafeWithoutCreatingInvalidXp() {
    assertThat(SkillChronos.resolveClockXpMultiplier(true, true, Double.NaN, 2D)).isEqualTo(1D);
    assertThat(SkillChronos.resolveClockXpMultiplier(false, true, 3D, -2D)).isEqualTo(1D);
    assertThat(SkillChronos.resolveClockXpMultiplier(true, true, 0D, 2D)).isZero();
  }

  @Test
  void speedPotionDetectionCoversNormalLongAndStrongBasePotions() {
    assertThat(SkillChronos.speedPotionAmplifier(Id.minecraft("swiftness"), List.of())).isZero();
    assertThat(SkillChronos.speedPotionAmplifier(Id.minecraft("long_swiftness"), List.of())).isZero();
    assertThat(SkillChronos.speedPotionAmplifier(Id.minecraft("strong_swiftness"), List.of())).isEqualTo(1);
    assertThat(SkillChronos.speedPotionAmplifier(Id.minecraft("water"), List.of())).isEqualTo(-1);
  }

  @Test
  void speedPotionXpCombinesStrengthDiminishingReturnsAndClockState() {
    assertThat(SkillChronos.speedPotionXp(120D, 1.5D, 0.15D, 0.25D, 0, 0, 3D)).isEqualTo(360D);
    assertThat(SkillChronos.speedPotionXp(120D, 1.5D, 0.15D, 0.25D, 1, 0, 2D)).isEqualTo(360D);
    assertThat(SkillChronos.speedPotionXp(120D, 1.5D, 0.15D, 0.25D, 0, 1, 1D)).isEqualTo(102D);
    assertThat(SkillChronos.speedPotionXp(120D, 1.5D, 0.15D, 0.25D, 0, 100, 1D)).isEqualTo(30D);
  }

  @Test
  void speedPotionRewardCooldownHasInclusiveBoundaryAndHandlesClockRollback() {
    assertThat(SkillChronos.speedPotionRewardReady(1000L, 0L, 1000L)).isTrue();
    assertThat(SkillChronos.speedPotionRewardReady(1999L, 1000L, 1000L)).isFalse();
    assertThat(SkillChronos.speedPotionRewardReady(2000L, 1000L, 1000L)).isTrue();
    assertThat(SkillChronos.speedPotionRewardReady(999L, 1000L, 1000L)).isTrue();
  }

  @Test
  void aSkippedNightAndTheMorningAfterItShareOneSleepDay() {
    assertThat(SkillChronos.sleepDay(13000L)).isEqualTo(1L);
    assertThat(SkillChronos.sleepDay(23999L)).isEqualTo(1L);
    assertThat(SkillChronos.sleepDay(24000L)).isEqualTo(1L);
    assertThat(SkillChronos.sleepDay(24000L * 5 + 18000L)).isEqualTo(6L);
  }

  @Test
  void consecutiveNightsGrowTheStreakAndAMissedNightResetsIt() {
    assertThat(SkillChronos.nextSleepStreak(true, 4L, 5L, 3L)).isEqualTo(4L);
    assertThat(SkillChronos.nextSleepStreak(true, 3L, 5L, 3L)).isEqualTo(1L);
    assertThat(SkillChronos.nextSleepStreak(false, 4L, 5L, 3L)).isEqualTo(1L);
    assertThat(SkillChronos.nextSleepStreak(true, Long.MIN_VALUE, 5L, 0L)).isEqualTo(1L);
  }

  @Test
  void streakBonusAddsFifteenPerExtraNightUpToTheCap() {
    assertThat(SkillChronos.sleepThroughNightXp(150D, 15D, 300D, 1L)).isEqualTo(150D);
    assertThat(SkillChronos.sleepThroughNightXp(150D, 15D, 300D, 2L)).isEqualTo(165D);
    assertThat(SkillChronos.sleepThroughNightXp(150D, 15D, 300D, 3L)).isEqualTo(180D);
    assertThat(SkillChronos.sleepThroughNightXp(150D, 15D, 300D, 21L)).isEqualTo(450D);
    assertThat(SkillChronos.sleepThroughNightXp(150D, 15D, 300D, 100L)).isEqualTo(450D);
  }

  @Test
  void configDefaultsProvideRequestedClockAndBurstTuning() throws ReflectiveOperationException {
    SkillChronos.Config config = new SkillChronos.Config();

    assertThat(config.clockOffhandXpMultiplier).isEqualTo(5D);
    assertThat(config.clockInventoryXpMultiplier).isEqualTo(2D);
    assertThat(config.sleepThroughNightXp).isEqualTo(150D);
    assertThat(config.sleepStreakBonusXp).isEqualTo(15D);
    assertThat(config.sleepStreakBonusCap).isEqualTo(300D);
    assertThat(config.speedPotionBaseXP).isEqualTo(120D);
    assertThat(config.speedPotionRewardCooldown).isEqualTo(1000L);
    assertThat(config.speedPotionDiminishingFloor).isGreaterThan(0D);

    Field[] fields = SkillChronos.Config.class.getDeclaredFields();
    assertThat(fields).extracting(Field::getName)
        .doesNotContain("sleepAttemptXP", "sleepSkipXP", "sleepXP", "sleepCooldown");
  }

  @Test
  void exactLegacySpeedRewardMigratesWithoutOverwritingCustomValues() {
    SkillChronos.Config legacy = new SkillChronos.Config();
    legacy.speedPotionBaseXP = 45D;
    SkillChronos.normalizeLoadedConfigValues(legacy);
    assertThat(legacy.speedPotionBaseXP).isEqualTo(120D);

    SkillChronos.Config custom = new SkillChronos.Config();
    custom.speedPotionBaseXP = 75D;
    SkillChronos.normalizeLoadedConfigValues(custom);
    assertThat(custom.speedPotionBaseXP).isEqualTo(75D);
  }

  @Test
  void rewardEventsObserveOnlyUncancelledOutcomesAtMonitorPriority() throws ReflectiveOperationException {
    assertHandlerPolicy(NightSkipEvent.class);
    assertHandlerPolicy(PlayerItemConsumeEvent.class);
    assertHandlerPolicy(PotionSplashEvent.class);
    assertHandlerPolicy(AreaEffectCloudApplyEvent.class);
  }

  private void assertHandlerPolicy(Class<?> eventType) throws ReflectiveOperationException {
    Method handler = SkillChronos.class.getDeclaredMethod("on", eventType);
    Handle policy = handler.getAnnotation(Handle.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(handler.isAnnotationPresent(ReceiveCancelledEvents.class)).isFalse();
  }
}

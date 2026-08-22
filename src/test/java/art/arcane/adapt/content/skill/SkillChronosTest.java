package art.arcane.adapt.content.skill;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.potion.PotionType;
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
    assertThat(SkillChronos.speedPotionAmplifier(PotionType.SWIFTNESS, List.of())).isZero();
    assertThat(SkillChronos.speedPotionAmplifier(PotionType.LONG_SWIFTNESS, List.of())).isZero();
    assertThat(SkillChronos.speedPotionAmplifier(PotionType.STRONG_SWIFTNESS, List.of())).isEqualTo(1);
    assertThat(SkillChronos.speedPotionAmplifier(PotionType.WATER, List.of())).isEqualTo(-1);
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
  void sleepRewardsOnlyAcceptedBedEntries() {
    for (PlayerBedEnterEvent.BedEnterResult result : PlayerBedEnterEvent.BedEnterResult.values()) {
      assertThat(SkillChronos.isSuccessfulBedEntry(result))
          .isEqualTo(result == PlayerBedEnterEvent.BedEnterResult.OK);
    }
    assertThat(SkillChronos.isSuccessfulBedEntry(null)).isFalse();
  }

  @Test
  void configDefaultsProvideRequestedClockAndBurstTuning() throws ReflectiveOperationException {
    SkillChronos.Config config = new SkillChronos.Config();

    assertThat(config.clockOffhandXpMultiplier).isEqualTo(3D);
    assertThat(config.clockInventoryXpMultiplier).isEqualTo(2D);
    assertThat(config.sleepXP).isEqualTo(150D);
    assertThat(config.speedPotionBaseXP).isEqualTo(120D);
    assertThat(config.speedPotionRewardCooldown).isEqualTo(1000L);
    assertThat(config.speedPotionDiminishingFloor).isGreaterThan(0D);

    Field[] fields = SkillChronos.Config.class.getDeclaredFields();
    assertThat(fields).extracting(Field::getName)
        .doesNotContain("sleepAttemptXP", "sleepSkipXP");
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
    assertHandlerPolicy(PlayerBedEnterEvent.class);
    assertHandlerPolicy(PlayerItemConsumeEvent.class);
    assertHandlerPolicy(PotionSplashEvent.class);
    assertHandlerPolicy(AreaEffectCloudApplyEvent.class);
  }

  private void assertHandlerPolicy(Class<?> eventType) throws ReflectiveOperationException {
    Method handler = SkillChronos.class.getDeclaredMethod("on", eventType);
    EventHandler policy = handler.getAnnotation(EventHandler.class);
    assertThat(policy).isNotNull();
    assertThat(policy.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(policy.ignoreCancelled()).isTrue();
  }
}

package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerFishEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SeaborneFishersFantasyTuningTest {
  @Test
  void defaultChanceScalesFromTenToThirtyFivePercent() {
    SeaborneFishersFantasy.Config config = new SeaborneFishersFantasy.Config();

    assertThat(SeaborneCatches.bonusChance(1, config.maxLevel, config.bonusChanceAtLevelOne, config.bonusChanceAtMaxLevel))
        .isCloseTo(0.10D, offset(1.0E-9D));
    assertThat(SeaborneCatches.bonusChance(7, config.maxLevel, config.bonusChanceAtLevelOne, config.bonusChanceAtMaxLevel))
        .isCloseTo(0.35D, offset(1.0E-9D));
  }

  @Test
  void defaultVanillaXpScalesLinearlyAndCaps() {
    SeaborneFishersFantasy.Config config = new SeaborneFishersFantasy.Config();

    assertThat(SeaborneCatches.rewardXp(1, config.vanillaXpAtLevelOne, config.vanillaXpPerAdditionalLevel, config.maximumVanillaXpPerCatch)).isEqualTo(2);
    assertThat(SeaborneCatches.rewardXp(7, config.vanillaXpAtLevelOne, config.vanillaXpPerAdditionalLevel, config.maximumVanillaXpPerCatch)).isEqualTo(8);
    assertThat(config.skillXpOnSuccess).isEqualTo(8D);
    assertThat(config.cooldownMillis).isEqualTo(5000L);
  }

  @Test
  void invalidChanceAndRewardConfigNormalizesSafely() {
    SeaborneFishersFantasy.Config config = new SeaborneFishersFantasy.Config();
    config.bonusChanceAtLevelOne = Double.NaN;
    config.bonusChanceAtMaxLevel = Double.POSITIVE_INFINITY;
    config.maximumVanillaXpPerCatch = -1;
    config.skillXpOnSuccess = Double.NaN;

    config.normalizeForPersistence();

    assertThat(config.bonusChanceAtLevelOne).isZero();
    assertThat(config.bonusChanceAtMaxLevel).isZero();
    assertThat(config.maximumVanillaXpPerCatch).isZero();
    assertThat(config.skillXpOnSuccess).isZero();
  }

  @Test
  void rewardRunsOnlyAfterCommittedCatch() throws Exception {
    Method handler = SeaborneFishersFantasy.class.getDeclaredMethod("on", PlayerFishEvent.class);
    Handle annotation = handler.getAnnotation(Handle.class);

    assertThat(annotation.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(annotation.ignoreCancelled()).isTrue();
  }
}

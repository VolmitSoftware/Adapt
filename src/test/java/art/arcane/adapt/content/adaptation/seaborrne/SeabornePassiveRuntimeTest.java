package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.AdaptTestBase;
import org.bukkit.potion.PotionEffect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeabornePassiveRuntimeTest extends AdaptTestBase {
  @Test
  void swimDistanceTracksHorizontalMovementAndCapsPacketJumps() {
    assertThat(SeaborneSpeed.trackedHorizontalDistance(0.3D, 0.4D)).isCloseTo(0.5D, offset(0.0000001D));
    assertThat(SeaborneSpeed.trackedHorizontalDistance(3D, 4D)).isEqualTo(4D);
    assertThat(SeaborneSpeed.trackedHorizontalDistance(Double.NaN, 1D)).isZero();
  }

  @Test
  void turtleEffectsRetainAFullHundredTickRefreshBuffer() {
    assertThat(SeaborneTurtlesVision.shouldRefreshEffect(501, 0, 0, 500)).isFalse();
    assertThat(SeaborneTurtlesVision.shouldRefreshEffect(500, 0, 0, 500)).isTrue();
    assertThat(SeaborneTurtlesVision.shouldRefreshEffect(1, 0, 0, -20)).isFalse();
  }

  @Test
  void turtleMinerCancelsTheSeparateFloatingPenalty() {
    double underwaterBoost = 1D + SeaborneTurtlesMiningSpeed.multiplierScalar(1.4D);
    double floatingCompensation = 1D + SeaborneTurtlesMiningSpeed.multiplierScalar(5D);
    double standingSpeed = 0.2D * underwaterBoost;
    double floatingSpeed = 0.2D * 0.2D * underwaterBoost * floatingCompensation;

    assertThat(floatingSpeed).isCloseTo(standingSpeed, offset(0.0000001D));
    assertThat(SeaborneTurtlesMiningSpeed.multiplierScalar(1D)).isZero();
    assertThat(SeaborneTurtlesMiningSpeed.multiplierScalar(Double.NaN)).isZero();
    assertThat(SeaborneTurtlesMiningSpeed.multiplierScalar(100D)).isEqualTo(9D);
    assertThat(SeaborneTurtlesMiningSpeed.attributeDurationTicks(-1L)).isEqualTo(20L);
    assertThat(SeaborneTurtlesMiningSpeed.attributeDurationTicks(Long.MAX_VALUE)).isEqualTo(1200L);
    assertThat(SeaborneTurtlesMiningSpeed.refreshIntervalMillis(-1)).isEqualTo(250);
    assertThat(SeaborneTurtlesMiningSpeed.refreshIntervalMillis(Integer.MAX_VALUE)).isEqualTo(10_000);
  }

  @Test
  void turtleVisionCreditsElapsedUnderwaterTicksWithoutPreCredit() {
    assertThat(SeaborneTurtlesVision.elapsedUnderwaterTicks(null, 4000L)).isZero();
    assertThat(SeaborneTurtlesVision.elapsedUnderwaterTicks(1000L, 4000L)).isEqualTo(60D);
    assertThat(SeaborneTurtlesVision.elapsedUnderwaterTicks(1000L, 20_000L)).isEqualTo(160D);
  }

  @Test
  void turtleVisionClampsHotloadedTimingKnobs() {
    assertThat(SeaborneTurtlesVision.effectDurationTicks(-1)).isEqualTo(20);
    assertThat(SeaborneTurtlesVision.effectDurationTicks(Integer.MAX_VALUE)).isEqualTo(6000);
    assertThat(SeaborneTurtlesVision.refreshIntervalMillis(-1)).isEqualTo(250);
    assertThat(SeaborneTurtlesVision.refreshIntervalMillis(Integer.MAX_VALUE)).isEqualTo(10_000);
  }

  @Test
  void turtleRefreshIntervalsApplyDuringHotload() {
    SeaborneTurtlesMiningSpeed miner = new SeaborneTurtlesMiningSpeed();
    SeaborneTurtlesMiningSpeed.Config minerConfig = new SeaborneTurtlesMiningSpeed.Config();
    minerConfig.refreshIntervalMillis = 500;
    miner.onConfigReload(null, minerConfig);

    SeaborneTurtlesVision vision = new SeaborneTurtlesVision();
    SeaborneTurtlesVision.Config visionConfig = new SeaborneTurtlesVision.Config();
    visionConfig.refreshIntervalMillis = 750;
    vision.onConfigReload(null, visionConfig);

    assertThat(miner.getInterval()).isEqualTo(500L);
    assertThat(vision.getInterval()).isEqualTo(750L);
  }

  @Test
  void turtleVisionRemovesOnlyItsOwnedManagedEffect() {
    PotionEffect managed = mock(PotionEffect.class);
    PotionEffect stronger = mock(PotionEffect.class);
    when(stronger.getAmplifier()).thenReturn(1);

    assertThat(SeaborneTurtlesVision.mayRemoveNightVision(false, managed)).isFalse();
    assertThat(SeaborneTurtlesVision.mayRemoveNightVision(true, stronger)).isFalse();
    assertThat(SeaborneTurtlesVision.mayRemoveNightVision(true, managed)).isTrue();
  }

}

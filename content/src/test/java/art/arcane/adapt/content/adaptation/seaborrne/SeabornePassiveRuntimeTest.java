package art.arcane.adapt.content.adaptation.seaborrne;

import art.arcane.adapt.testkit.TestContent;
import art.arcane.adapt.EngineTestBase;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SeabornePassiveRuntimeTest extends EngineTestBase {
  @Test
  void swimDistanceTracksHorizontalMovementAndCapsPacketJumps() {
    assertThat(SeaborneSpeed.trackedHorizontalDistance(0.3D, 0.4D)).isCloseTo(0.5D, offset(0.0000001D));
    assertThat(SeaborneSpeed.trackedHorizontalDistance(3D, 4D)).isEqualTo(4D);
    assertThat(SeaborneSpeed.trackedHorizontalDistance(Double.NaN, 1D)).isZero();
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
  void oxygenCreditsElapsedUnderwaterTicksWithoutPreCredit() {
    assertThat(SeaborneOxygen.elapsedUnderwaterTicks(null, 4000L)).isZero();
    assertThat(SeaborneOxygen.elapsedUnderwaterTicks(1000L, 4000L)).isEqualTo(60D);
    assertThat(SeaborneOxygen.elapsedUnderwaterTicks(1000L, 20_000L)).isEqualTo(160D);
  }

  @Test
  void turtleMinerRefreshIntervalAppliesDuringHotload() {
    SeaborneTurtlesMiningSpeed miner = TestContent.adaptation(Id.adapt("seaborne"), SeaborneTurtlesMiningSpeed::new);
    SeaborneTurtlesMiningSpeed.Config minerConfig = new SeaborneTurtlesMiningSpeed.Config();
    minerConfig.refreshIntervalMillis = 500;
    miner.onConfigReload(null, minerConfig);

    assertThat(miner.getInterval()).isEqualTo(500L);
  }

  @Test
  void nightSightRemovesOnlyItsOwnedManagedEffect() {
    EffectInstance managed = EffectInstance.of(Id.minecraft("night_vision"), 0, 0).withParticles(false).withIcon(false);
    EffectInstance stronger = EffectInstance.of(Id.minecraft("night_vision"), 0, 1).withParticles(false).withIcon(false);

    assertThat(SeaborneNightSight.mayRemove(false, managed)).isFalse();
    assertThat(SeaborneNightSight.mayRemove(true, stronger)).isFalse();
    assertThat(SeaborneNightSight.mayRemove(true, managed)).isTrue();
  }
}

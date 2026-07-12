package art.arcane.adapt.content.adaptation.seaborrne;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class SeabornePassiveRuntimeTest {
  @Test
  void swimDistanceTracksHorizontalMovementAndCapsPacketJumps() {
    assertThat(SeaborneSpeed.trackedHorizontalDistance(0.3D, 0.4D)).isCloseTo(0.5D, offset(0.0000001D));
    assertThat(SeaborneSpeed.trackedHorizontalDistance(3D, 4D)).isEqualTo(4D);
    assertThat(SeaborneSpeed.trackedHorizontalDistance(Double.NaN, 1D)).isZero();
  }

  @Test
  void dolphinGraceRefreshesOnlyNearExpiryOrForAWeakerEffect() {
    assertThat(SeaborneSpeed.shouldRefreshEffect(41, 3, 3)).isFalse();
    assertThat(SeaborneSpeed.shouldRefreshEffect(40, 3, 3)).isTrue();
    assertThat(SeaborneSpeed.shouldRefreshEffect(200, 2, 3)).isTrue();
    assertThat(SeaborneSpeed.shouldRefreshEffect(1, 4, 3)).isFalse();
  }

  @Test
  void turtleEffectsRetainAFullHundredTickRefreshBuffer() {
    assertThat(SeaborneTurtlesVision.shouldRefreshEffect(501, 0, 0)).isFalse();
    assertThat(SeaborneTurtlesVision.shouldRefreshEffect(500, 0, 0)).isTrue();
    assertThat(SeaborneTurtlesMiningSpeed.shouldRefreshEffect(101, 1, 1)).isFalse();
    assertThat(SeaborneTurtlesMiningSpeed.shouldRefreshEffect(100, 1, 1)).isTrue();
  }

  @Test
  void turtleVisionCreditsElapsedUnderwaterTicksWithoutPreCredit() {
    assertThat(SeaborneTurtlesVision.elapsedUnderwaterTicks(null, 4000L)).isZero();
    assertThat(SeaborneTurtlesVision.elapsedUnderwaterTicks(1000L, 4000L)).isEqualTo(60D);
    assertThat(SeaborneTurtlesVision.elapsedUnderwaterTicks(1000L, 20_000L)).isEqualTo(160D);
  }

  @Test
  void oxygenRestorationIsBoundedByMissingAirAndWorksWhileDrowning() {
    assertThat(SeaborneOxygen.restoreAir(200, 300, 15)).isEqualTo(15);
    assertThat(SeaborneOxygen.restoreAir(295, 300, 15)).isEqualTo(5);
    assertThat(SeaborneOxygen.restoreAir(-10, 300, 15)).isEqualTo(15);
    assertThat(SeaborneOxygen.restoreAir(300, 300, 15)).isZero();
    assertThat(SeaborneOxygen.restoreAir(200, 300, 0)).isZero();
  }
}

package art.arcane.adapt.content.adaptation.seaborrne;

import org.bukkit.potion.PotionEffect;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SeabornePassiveRuntimeTest {
  @Test
  void swimDistanceTracksHorizontalMovementAndCapsPacketJumps() {
    assertThat(SeaborneSpeed.trackedHorizontalDistance(0.3D, 0.4D)).isCloseTo(0.5D, offset(0.0000001D));
    assertThat(SeaborneSpeed.trackedHorizontalDistance(3D, 4D)).isEqualTo(4D);
    assertThat(SeaborneSpeed.trackedHorizontalDistance(Double.NaN, 1D)).isZero();
  }

  @Test
  void turtleEffectsRetainAFullHundredTickRefreshBuffer() {
    assertThat(SeaborneTurtlesVision.shouldRefreshEffect(501, 0, 0)).isFalse();
    assertThat(SeaborneTurtlesVision.shouldRefreshEffect(500, 0, 0)).isTrue();
  }

  @Test
  void turtleVisionCreditsElapsedUnderwaterTicksWithoutPreCredit() {
    assertThat(SeaborneTurtlesVision.elapsedUnderwaterTicks(null, 4000L)).isZero();
    assertThat(SeaborneTurtlesVision.elapsedUnderwaterTicks(1000L, 4000L)).isEqualTo(60D);
    assertThat(SeaborneTurtlesVision.elapsedUnderwaterTicks(1000L, 20_000L)).isEqualTo(160D);
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

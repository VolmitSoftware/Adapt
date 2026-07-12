package art.arcane.adapt.content.adaptation.discovery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveryScanScalingTest {
  @Test
  void keenEyeRangeAndGlimmerHoldFloorsAndScaleUp() {
    assertThat(DiscoveryKeenEye.range(0.0D, 10D, 14D)).isEqualTo(10D);
    assertThat(DiscoveryKeenEye.range(1.0D, 10D, 14D)).isEqualTo(24D);
    assertThat(DiscoveryKeenEye.glimmerDurationTicks(0.0D, 12D, 28D)).isEqualTo(12);
    assertThat(DiscoveryKeenEye.glimmerDurationTicks(1.0D, 12D, 28D)).isEqualTo(40);
    assertThat(DiscoveryKeenEye.range(0.5D, -100D, 0D)).isEqualTo(4D);
  }

  @Test
  void sixthSenseRangeScalesAndStaysAboveFloor() {
    assertThat(DiscoverySixthSense.detectionRange(0.0D, 48D, 112D)).isEqualTo(48D);
    assertThat(DiscoverySixthSense.detectionRange(1.0D, 48D, 112D)).isEqualTo(160D);
    assertThat(DiscoverySixthSense.detectionRange(0.0D, 4D, 0D)).isEqualTo(16D);
  }

  @Test
  void trailblazerSpeedDurationScalesAndKeepsMinimum() {
    assertThat(DiscoveryTrailblazer.speedDurationTicks(1.0D, 80D, 120D)).isEqualTo(200);
    assertThat(DiscoveryTrailblazer.speedDurationTicks(0.0D, 5D, 0D)).isEqualTo(20);
    assertThat(DiscoveryTrailblazer.firstVisitXp(0.2D, 40D, 160D)).isEqualTo(72D);
  }

  @Test
  void relicAppraiserXpScalesWithLevel() {
    assertThat(DiscoveryRelicAppraiser.appraiseXp(0.0D, 60D, 180D)).isEqualTo(60D);
    assertThat(DiscoveryRelicAppraiser.appraiseXp(1.0D, 60D, 180D)).isEqualTo(240D);
  }
}

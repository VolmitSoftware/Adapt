package art.arcane.adapt.api.golem;

import art.arcane.adapt.api.golem.node.NodeRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GolemWorkSettingsTest {
  @Test
  void defaultsMatchThePlan() {
    GolemWorkSettings defaults = GolemWorkSettings.defaults();

    assertThat(defaults.taskLeaseSeconds()).isEqualTo(20);
    assertThat(defaults.baseWorkRadius()).isEqualTo(32D);
    assertThat(defaults.stuckTimeoutSeconds()).isEqualTo(4);
    assertThat(defaults.leaseMillis()).isEqualTo(20_000L);
    assertThat(defaults.stuckMillis()).isEqualTo(4_000L);
    assertThat(defaults.stuckReleasesBeforePause()).isEqualTo(3);
    assertThat(defaults.stuckPauseSeconds()).isEqualTo(15);
    assertThat(defaults.stuckPauseMillis()).isEqualTo(15_000L);
  }

  @Test
  void theDefaultWorkRadiusReachesTheEdgeOfTheLargestNodeArea() {
    assertThat(GolemWorkSettings.defaults().baseWorkRadius()).isGreaterThanOrEqualTo(NodeRecord.MAX_RADIUS);
    assertThat(GolemWorkSettings.MAX_WORK_RADIUS).isGreaterThan(NodeRecord.MAX_RADIUS);
  }

  @Test
  void valuesAreClampedToSafeRanges() {
    GolemWorkSettings low = new GolemWorkSettings(0, 0D, 0, 0, -5);
    GolemWorkSettings high = new GolemWorkSettings(100_000, 10_000D, 1_000, 1_000, 100_000);
    GolemWorkSettings broken = new GolemWorkSettings(20, Double.NaN, 4, 3, 15);

    assertThat(low.taskLeaseSeconds()).isEqualTo(GolemWorkSettings.MIN_LEASE_SECONDS);
    assertThat(low.baseWorkRadius()).isEqualTo(GolemWorkSettings.MIN_WORK_RADIUS);
    assertThat(low.stuckTimeoutSeconds()).isEqualTo(GolemWorkSettings.MIN_STUCK_SECONDS);
    assertThat(low.stuckReleasesBeforePause()).isEqualTo(GolemWorkSettings.MIN_STUCK_RELEASES);
    assertThat(low.stuckPauseSeconds()).isZero();
    assertThat(high.taskLeaseSeconds()).isEqualTo(GolemWorkSettings.MAX_LEASE_SECONDS);
    assertThat(high.baseWorkRadius()).isEqualTo(GolemWorkSettings.MAX_WORK_RADIUS);
    assertThat(high.stuckTimeoutSeconds()).isEqualTo(GolemWorkSettings.MAX_STUCK_SECONDS);
    assertThat(high.stuckReleasesBeforePause()).isEqualTo(GolemWorkSettings.MAX_STUCK_RELEASES);
    assertThat(high.stuckPauseSeconds()).isEqualTo(GolemWorkSettings.MAX_STUCK_PAUSE_SECONDS);
    assertThat(broken.baseWorkRadius()).isEqualTo(32D);
  }
}

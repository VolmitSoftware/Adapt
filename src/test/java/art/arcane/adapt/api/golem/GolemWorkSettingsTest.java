package art.arcane.adapt.api.golem;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GolemWorkSettingsTest {
  @Test
  void defaultsMatchThePlan() {
    GolemWorkSettings defaults = GolemWorkSettings.defaults();

    assertThat(defaults.taskLeaseSeconds()).isEqualTo(20);
    assertThat(defaults.baseWorkRadius()).isEqualTo(24D);
    assertThat(defaults.stuckTimeoutSeconds()).isEqualTo(4);
    assertThat(defaults.leaseMillis()).isEqualTo(20_000L);
    assertThat(defaults.stuckMillis()).isEqualTo(4_000L);
  }

  @Test
  void valuesAreClampedToSafeRanges() {
    GolemWorkSettings low = new GolemWorkSettings(0, 0D, 0);
    GolemWorkSettings high = new GolemWorkSettings(100_000, 10_000D, 1_000);
    GolemWorkSettings broken = new GolemWorkSettings(20, Double.NaN, 4);

    assertThat(low.taskLeaseSeconds()).isEqualTo(GolemWorkSettings.MIN_LEASE_SECONDS);
    assertThat(low.baseWorkRadius()).isEqualTo(GolemWorkSettings.MIN_WORK_RADIUS);
    assertThat(low.stuckTimeoutSeconds()).isEqualTo(GolemWorkSettings.MIN_STUCK_SECONDS);
    assertThat(high.taskLeaseSeconds()).isEqualTo(GolemWorkSettings.MAX_LEASE_SECONDS);
    assertThat(high.baseWorkRadius()).isEqualTo(GolemWorkSettings.MAX_WORK_RADIUS);
    assertThat(high.stuckTimeoutSeconds()).isEqualTo(GolemWorkSettings.MAX_STUCK_SECONDS);
    assertThat(broken.baseWorkRadius()).isEqualTo(24D);
  }
}

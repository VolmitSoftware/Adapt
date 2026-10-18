package art.arcane.adapt.api.golem;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GolemWritePolicyTest {
  @Test
  void firstChangeAsksForOneScheduledFlush() {
    GolemWritePolicy policy = new GolemWritePolicy();

    assertThat(policy.isDirty()).isFalse();
    assertThat(policy.markDirty(1_000L)).isTrue();
    assertThat(policy.markDirty(1_500L)).isFalse();
    assertThat(policy.markDirty(5_999L)).isFalse();
    assertThat(policy.isDirty()).isTrue();
  }

  @Test
  void flushFallsDueFiveSecondsAfterTheFirstChange() {
    GolemWritePolicy policy = new GolemWritePolicy();
    policy.markDirty(1_000L);
    policy.markDirty(4_000L);

    assertThat(GolemWritePolicy.DEBOUNCE_MILLIS).isEqualTo(5_000L);
    assertThat(policy.isDue(5_999L)).isFalse();
    assertThat(policy.remainingMillis(5_999L)).isEqualTo(1L);
    assertThat(policy.isDue(6_000L)).isTrue();
    assertThat(policy.remainingMillis(6_000L)).isZero();
  }

  @Test
  void constantChangesCannotPostponeTheFlush() {
    GolemWritePolicy policy = new GolemWritePolicy();
    policy.markDirty(0L);
    for (long now = 100L; now < 5_000L; now += 100L) {
      policy.markDirty(now);
    }

    assertThat(policy.isDue(5_000L)).isTrue();
  }

  @Test
  void flushingResetsTheWindow() {
    GolemWritePolicy policy = new GolemWritePolicy();
    policy.markDirty(0L);
    policy.flushed();

    assertThat(policy.isDirty()).isFalse();
    assertThat(policy.isDue(99_000L)).isFalse();
    assertThat(policy.remainingMillis(99_000L)).isZero();
    assertThat(policy.markDirty(10_000L)).isTrue();
    assertThat(policy.isDue(14_999L)).isFalse();
    assertThat(policy.isDue(15_000L)).isTrue();
  }

  @Test
  void cleanPolicyIsNeverDue() {
    GolemWritePolicy policy = new GolemWritePolicy();

    assertThat(policy.isDue(Long.MAX_VALUE)).isFalse();
  }

  @Test
  void delayTicksRoundUpAndStayPositive() {
    assertThat(GolemWritePolicy.ticks(5_000L)).isEqualTo(100);
    assertThat(GolemWritePolicy.ticks(1L)).isEqualTo(1);
    assertThat(GolemWritePolicy.ticks(0L)).isEqualTo(1);
    assertThat(GolemWritePolicy.ticks(51L)).isEqualTo(2);
  }
}

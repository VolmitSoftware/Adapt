package art.arcane.adapt.client.protocol;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrameRateLimiterTest {
  @Test
  void framesBeyondThePerSecondBudgetAreDropped() {
    FrameRateLimiter limiter = new FrameRateLimiter(3, 3, 10_000L);

    assertThat(limiter.admit(0L, 4)).isEqualTo(FrameRateLimiter.Verdict.ACCEPT);
    assertThat(limiter.admit(10L, 4)).isEqualTo(FrameRateLimiter.Verdict.ACCEPT);
    assertThat(limiter.admit(20L, 4)).isEqualTo(FrameRateLimiter.Verdict.ACCEPT);
    assertThat(limiter.admit(30L, 4)).isEqualTo(FrameRateLimiter.Verdict.DROP);
    assertThat(limiter.admit(1000L, 4)).isEqualTo(FrameRateLimiter.Verdict.ACCEPT);
  }

  @Test
  void oversizedFramesAreViolations() {
    FrameRateLimiter limiter = new FrameRateLimiter();

    assertThat(limiter.admit(0L, FrameLimits.MAX_C2S_BYTES + 1)).isEqualTo(FrameRateLimiter.Verdict.DROP);
    assertThat(limiter.summary()).contains("1 client protocol violations").contains("exceeds");
  }

  @Test
  void repeatedViolationsInsideTheWindowReset() {
    FrameRateLimiter limiter = new FrameRateLimiter(60, 3, 10_000L);

    assertThat(limiter.violation(0L, "a")).isEqualTo(FrameRateLimiter.Verdict.DROP);
    assertThat(limiter.violation(100L, "b")).isEqualTo(FrameRateLimiter.Verdict.DROP);
    assertThat(limiter.violation(200L, "c")).isEqualTo(FrameRateLimiter.Verdict.RESET);
  }

  @Test
  void violationsSpreadBeyondTheWindowOnlyDrop() {
    FrameRateLimiter limiter = new FrameRateLimiter(60, 3, 10_000L);

    assertThat(limiter.violation(0L, "a")).isEqualTo(FrameRateLimiter.Verdict.DROP);
    assertThat(limiter.violation(6_000L, "b")).isEqualTo(FrameRateLimiter.Verdict.DROP);
    assertThat(limiter.violation(12_000L, "c")).isEqualTo(FrameRateLimiter.Verdict.DROP);
  }
}

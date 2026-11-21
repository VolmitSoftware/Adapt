package art.arcane.adapt.client.protocol;

import java.util.Arrays;

/**
 * Per-player inbound budget: at most {@code maxFramesPerSecond} frames in any second and frames no larger than
 * {@link FrameLimits#MAX_C2S_BYTES}. Each refusal is a violation; {@code violationLimit} violations inside the window
 * return {@link Verdict#RESET}.
 */
public final class FrameRateLimiter {
  private static final long SECOND_MILLIS = 1000L;

  private final long[] frameTimes;
  private final long[] violationTimes;
  private final long violationWindowMillis;
  private int frameCursor;
  private int violationCursor;
  private int violations;
  private String lastViolation = "";

  public FrameRateLimiter() {
    this(FrameLimits.MAX_FRAMES_PER_SECOND, FrameLimits.VIOLATION_LIMIT, FrameLimits.VIOLATION_WINDOW_MILLIS);
  }

  public FrameRateLimiter(int maxFramesPerSecond, int violationLimit, long violationWindowMillis) {
    this.frameTimes = new long[Math.max(1, maxFramesPerSecond)];
    this.violationTimes = new long[Math.max(1, violationLimit - 1)];
    this.violationWindowMillis = Math.max(1L, violationWindowMillis);
    Arrays.fill(frameTimes, Long.MIN_VALUE);
    Arrays.fill(violationTimes, Long.MIN_VALUE);
  }

  public synchronized Verdict admit(long nowMillis, int payloadBytes) {
    if (payloadBytes > FrameLimits.MAX_C2S_BYTES) {
      return violation(nowMillis, "frame of " + payloadBytes + " bytes exceeds " + FrameLimits.MAX_C2S_BYTES);
    }
    long oldest = frameTimes[frameCursor];
    if (oldest != Long.MIN_VALUE && nowMillis - oldest < SECOND_MILLIS) {
      return violation(nowMillis, "more than " + frameTimes.length + " frames in one second");
    }
    frameTimes[frameCursor] = nowMillis;
    frameCursor = (frameCursor + 1) % frameTimes.length;
    return Verdict.ACCEPT;
  }

  public synchronized Verdict violation(long nowMillis, String reason) {
    violations++;
    lastViolation = reason;
    long oldest = violationTimes[violationCursor];
    violationTimes[violationCursor] = nowMillis;
    violationCursor = (violationCursor + 1) % violationTimes.length;
    if (oldest != Long.MIN_VALUE && nowMillis - oldest < violationWindowMillis) {
      return Verdict.RESET;
    }
    return Verdict.DROP;
  }

  public synchronized String summary() {
    return violations + " client protocol violations, last: " + lastViolation;
  }

  public enum Verdict {
    ACCEPT,
    DROP,
    RESET
  }
}

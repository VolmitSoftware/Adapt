package art.arcane.adapt.api.golem.reveal;

public record RevealSettings(double revealRange, double labelRange, int pollTicks) {
  public static final double MIN_RANGE = 1D;
  public static final double MAX_REVEAL_RANGE = 128D;
  public static final double MAX_LABEL_RANGE = 64D;
  public static final int MIN_POLL_TICKS = 1;
  public static final int MAX_POLL_TICKS = 200;
  private static final double DEFAULT_REVEAL_RANGE = 32D;
  private static final double DEFAULT_LABEL_RANGE = 8D;
  private static final int DEFAULT_POLL_TICKS = 10;
  private static final double DISPLAY_VIEW_BLOCKS = 64D;
  private static final long MILLIS_PER_TICK = 50L;

  public RevealSettings {
    revealRange = clamp(revealRange, DEFAULT_REVEAL_RANGE, MAX_REVEAL_RANGE);
    labelRange = clamp(labelRange, DEFAULT_LABEL_RANGE, MAX_LABEL_RANGE);
    pollTicks = Math.max(MIN_POLL_TICKS, Math.min(MAX_POLL_TICKS, pollTicks));
  }

  public static RevealSettings defaults() {
    return new RevealSettings(DEFAULT_REVEAL_RANGE, DEFAULT_LABEL_RANGE, DEFAULT_POLL_TICKS);
  }

  public long pollMillis() {
    return pollTicks * MILLIS_PER_TICK;
  }

  public float labelViewRange() {
    return (float) (labelRange / DISPLAY_VIEW_BLOCKS);
  }

  private static double clamp(double value, double fallback, double maximum) {
    if (!Double.isFinite(value)) {
      return fallback;
    }
    return Math.max(MIN_RANGE, Math.min(maximum, value));
  }
}

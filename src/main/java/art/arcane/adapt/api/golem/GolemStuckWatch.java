package art.arcane.adapt.api.golem;

final class GolemStuckWatch {
  static final double MIN_PROGRESS = 0.5D;

  private double best = Double.NaN;
  private long since;

  void begin(long nowMillis, double distance) {
    best = distance;
    since = nowMillis;
  }

  Progress track(long nowMillis, double distance, long timeoutMillis) {
    if (Double.isNaN(best)) {
      begin(nowMillis, distance);
      return Progress.MOVING;
    }
    if (best - distance >= MIN_PROGRESS) {
      begin(nowMillis, distance);
      return Progress.IMPROVED;
    }
    return nowMillis - since >= timeoutMillis ? Progress.STUCK : Progress.MOVING;
  }

  void reset() {
    best = Double.NaN;
  }

  enum Progress {
    MOVING,
    IMPROVED,
    STUCK
  }
}
